package com.sukisu.ultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukisu.ultra.ui.screen.upload.ModuleUploadPrefs
import com.sukisu.ultra.ui.util.ModuleComments.CommentItem
import com.sukisu.ultra.ui.util.ModuleComments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 详情页评论区的状态板：拉列表、发评论、删评论、翻页，全部经由 [ModuleComments]。
 *
 * 会话从投稿链路的同一张令牌里来（[ModuleUploadPrefs.readSession]）：有且有效才带
 * 鉴权，游客身份照样能看评论——只是不能发言与点赞。
 */
class ModuleCommentsViewModel(
    private val moduleId: String,
    /** 索引带过来的初始点赞数：服务端暂无「我点过没」的查询，计数先进页面再由写回覆盖。 */
    initialLikeCount: Int = 0,
) : ViewModel() {

    /** 一屏的状态：加载态、当前页、游标、我的身份，全部集中在这里让界面只管画。 */
    data class UiState(
        val loading: Boolean = false,
        /** 首次加载失败（区别于翻页失败：前者整页空，后者只在尾部提示）。 */
        val loadFailed: Boolean = false,
        /** 给人看的一句话；空串表示没有可说的（网络层已把「不知道哪坏了」压成空串）。 */
        val error: String = "",
        /** 需要重新验证身份（401 / 令牌过期）。 */
        val needVerify: Boolean = false,
        val comments: List<CommentItem> = emptyList(),
        /** 下一页游标；空串表示没有了。 */
        val nextCursor: String = "",
        /** 正在提交评论/删除/翻页，期间输入框与按钮禁用。 */
        val submitting: Boolean = false,
        val paging: Boolean = false,
        /** 提交失败原因（发送框下面显示，不弹 toast：贴着输入框才看得到因果）。 */
        val submitError: String = "",
        val myUid: String = "",
        val myNickname: String = "",
        /** 已登录会话可用（能发言/点赞）。 */
        val canInteract: Boolean = false,
        /** 我点赞过的模块 id 集合（本会话内记忆；服务端暂无「我点过没」查询，重进页面按未点处理）。 */
        val likedModuleIds: Set<String> = emptySet(),
        /** 该模块的点赞数（进页面时从索引带过来的初值，点赞成功后用服务端回值覆盖）。 */
        val likeCount: Int = 0,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var sessionToken: String = ""

    init {
        _uiState.update { it.copy(likeCount = initialLikeCount) }
        refresh()
        readSession()
    }

    /**
     * 读投稿链路的会话；过期/不存在就按游客处理，不在这里逼用户去验证。
     *
     * 返回「令牌相比上次发生了变化（从无到有或换了一张新串）」：只有 recheckSession
     * 用得上 —— 用户刚在评论区验证页换完令牌回来，界面要把「需要验证」的卡片撤下、
     * 把列表连身份一起重刷。init 里的那一次变化不触发刷新（refresh() 本来就先跑）。
     */
    private fun readSession(): Boolean {
        val previous = sessionToken
        val session = ModuleUploadPrefs.readSession()
        sessionToken = if (session != null && session.validAt(System.currentTimeMillis())) session.token else ""
        _uiState.update {
            it.copy(
                myUid = session?.uid ?: "",
                myNickname = session?.nickname ?: "",
                canInteract = sessionToken.isNotEmpty(),
            )
        }
        return sessionToken.isNotEmpty() && sessionToken != previous
    }

    /** 重新加载第一页（下拉/进页面/发完评论后刷新）。 */
    fun refresh() {
        if (_uiState.value.loading) return
        _uiState.update { it.copy(loading = true, loadFailed = false, error = "") }
        viewModelScope.launch {
            val result = ModuleComments.fetchComments(moduleId, sessionToken = sessionToken.ifEmpty { null })
            when (result) {
                is ModuleComments.CommentsResult.Ok -> {
                    val page = ModuleComments.parseCommentPage(result.value)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadFailed = false,
                            comments = page.comments,
                            nextCursor = page.nextCursor,
                            error = "",
                        )
                    }
                }
                ModuleComments.CommentsResult.NeedVerify -> _uiState.update {
                    it.copy(loading = false, loadFailed = false, needVerify = true, canInteract = false)
                }
                is ModuleComments.CommentsResult.Failed -> _uiState.update {
                    // 空列表 + 失败 → 显示整页错误态；已有内容 → 保留旧内容只记错误。
                    it.copy(
                        loading = false,
                        loadFailed = it.comments.isEmpty(),
                        error = result.message,
                    )
                }
            }
        }
    }

    /** 翻下一页；没有下一页时是空操作。 */
    fun loadMore() {
        val s = _uiState.value
        if (s.paging || s.loading || s.nextCursor.isEmpty()) return
        _uiState.update { it.copy(paging = true) }
        viewModelScope.launch {
            val result = ModuleComments.fetchComments(moduleId, cursor = s.nextCursor, sessionToken = sessionToken.ifEmpty { null })
            when (result) {
                is ModuleComments.CommentsResult.Ok -> {
                    val page = ModuleComments.parseCommentPage(result.value)
                    _uiState.update {
                        it.copy(
                            paging = false,
                            comments = it.comments + page.comments,
                            nextCursor = page.nextCursor,
                        )
                    }
                }
                else -> _uiState.update { it.copy(paging = false) }
            }
        }
    }

    /**
     * 发一条评论（顶级或回复）。
     *
     * 成功后把新评论插进本地列表头部（顶级）或目标楼中楼尾部（回复），并刷新——
     * 本地插入让反馈即时，刷新兜底服务端排序；两者叠加最多重复一瞬，可接受。
     */
    fun submit(content: String, parentId: String? = null, replyToUid: String? = null, onDone: (Boolean) -> Unit = {}) {
        val s = _uiState.value
        if (s.submitting) return
        if (sessionToken.isEmpty()) {
            _uiState.update { it.copy(needVerify = true) }
            onDone(false)
            return
        }
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > ModuleComments.MAX_COMMENT_LENGTH) return
        _uiState.update { it.copy(submitting = true, submitError = "") }
        viewModelScope.launch {
            val result = ModuleComments.postComment(sessionToken, moduleId, trimmed, parentId, replyToUid)
            when (result) {
                is ModuleComments.CommentsResult.Ok -> {
                    val posted = ModuleComments.parsePostedComment(result.value)
                    _uiState.update { state ->
                        val newComments = when {
                            posted == null -> state.comments
                            parentId == null -> listOf(posted) + state.comments
                            else -> state.comments.map { top ->
                                if (top.id == parentId) top.copy(replies = top.replies + posted) else top
                            }
                        }
                        state.copy(submitting = false, comments = newComments, submitError = "")
                    }
                    onDone(true)
                }
                ModuleComments.CommentsResult.NeedVerify -> _uiState.update {
                    it.copy(submitting = false, needVerify = true, submitError = "")
                }
                is ModuleComments.CommentsResult.Failed -> _uiState.update {
                    it.copy(submitting = false, submitError = result.message)
                }
            }
        }
    }

    /** 删除自己的一条评论（顶级删除后服务端会把 content 置空并保留楼中楼）。 */
    fun delete(commentId: String, onDone: (Boolean) -> Unit = {}) {
        val s = _uiState.value
        if (s.submitting) return
        if (sessionToken.isEmpty()) {
            _uiState.update { it.copy(needVerify = true) }
            onDone(false)
            return
        }
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = ModuleComments.deleteComment(sessionToken, commentId)
            when (result) {
                is ModuleComments.CommentsResult.Ok -> _uiState.update { state ->
                    // 顶级：按服务端行为置成「已删除」占位（保留楼中楼）；楼中楼：整条摘掉。
                    val newComments = state.comments.map { top ->
                        when {
                            top.id == commentId -> top.copy(content = "", uid = "")
                            else -> top.copy(replies = top.replies.filterNot { it.id == commentId })
                        }
                    }
                    state.copy(submitting = false, comments = newComments)
                }
                ModuleComments.CommentsResult.NeedVerify -> _uiState.update {
                    it.copy(submitting = false, needVerify = true)
                }
                is ModuleComments.CommentsResult.Failed -> _uiState.update {
                    it.copy(submitting = false, submitError = result.message)
                }
            }
            onDone(result is ModuleComments.CommentsResult.Ok)
        }
    }

    /** 点赞/取消点赞；成功后用服务端回的计数覆盖本地值。 */
    fun toggleLike(moduleIdArg: String = moduleId, liked: Boolean, onDone: (Boolean, Int) -> Unit = { _, _ -> }) {
        val s = _uiState.value
        if (sessionToken.isEmpty()) {
            _uiState.update { it.copy(needVerify = true) }
            onDone(false, s.likeCount)
            return
        }
        viewModelScope.launch {
            val result = ModuleComments.setLike(sessionToken, moduleIdArg, liked)
            when (result) {
                is ModuleComments.CommentsResult.Ok -> {
                    val count = result.value.optInt("like_count", -1)
                    _uiState.update {
                        it.copy(
                            likeCount = if (count >= 0) count else it.likeCount,
                            likedModuleIds = if (liked) it.likedModuleIds + moduleIdArg else it.likedModuleIds - moduleIdArg,
                        )
                    }
                    onDone(true, if (count >= 0) count else s.likeCount)
                }
                else -> onDone(false, s.likeCount)
            }
        }
    }

    /**
     * 会话在别处 renewed（例如用户去评论区验证页换了一张新令牌后回来）：重读一次。
     *
     * 令牌真的到手就当场把「需要验证」撤下、把列表连身份一起刷一遍 —— 用户从验证页
     * pop 回来时详情页重组，LaunchedEffect 调的就是这里；不刷的话卡片会一直挂着，
     * 直到用户手动下拉才消失。
     */
    fun recheckSession() {
        if (readSession()) {
            _uiState.update { it.copy(needVerify = false) }
            refresh()
        }
    }
}
