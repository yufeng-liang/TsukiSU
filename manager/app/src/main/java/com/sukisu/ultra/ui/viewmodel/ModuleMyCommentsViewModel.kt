package com.sukisu.ultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukisu.ultra.ui.screen.upload.ModuleUploadPrefs
import com.sukisu.ultra.ui.util.ModuleComments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 「我的评论」页的状态板：跨模块列出我发过的评论，cursor 翻页。
 *
 * 会话同样来自投稿链路：没有会话或过期，页面直接落在「需要验证」态，
 * 引导去上传页重验，而不是挂一张空列表。
 */
class ModuleMyCommentsViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val loadFailed: Boolean = false,
        val needVerify: Boolean = false,
        val comments: List<ModuleComments.MyCommentItem> = emptyList(),
        val nextCursor: String = "",
        val paging: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var sessionToken: String = ""

    /**
     * 会话代次：每次重读会话 +1。在飞的请求带的是发起那一刻的令牌，回来时若代次已经变了
     * （用户登出、去上传页换了验证），它的结果就不再代表当前身份 —— 直接丢掉，不落地。
     */
    private var sessionEpoch = 0

    /** 在飞的那一次请求：会话变了要立刻掐掉，不能等它自己回来再把旧列表写回界面。 */
    private var fetchJob: Job? = null

    init {
        refresh()
        // 会话在别处变了（登出、换令牌）：重读一次 —— 没有会话就落「需要验证」态并清空
        // 列表，而不是留着一张上一个身份的评论列表还能点删除。
        viewModelScope.launch {
            ModuleUploadPrefs.sessionVersion.drop(1).collect { recheckSession() }
        }
    }

    /**
     * 页面重新出现，或会话在别处变了：重读会话，再决定拉不拉第一页。
     *
     * 与评论区 [ModuleCommentsViewModel.recheckSession] 对称：页面重组时调它 —— 别指望
     * ViewModel 自己知道用户刚去上传页验证完、或者刚登出。
     */
    fun recheckSession() {
        refresh(force = true)
    }

    /**
     * 重新拉第一页；会话也在这里重读（用户可能刚去上传页重新验证完）。
     *
     * [force] 是会话变更触发的：这时就算上一次请求还在飞也必须重来 —— 它带的是旧令牌，
     * 结果落地会把上一份身份的列表又写回界面（或者反过来，把「需要验证」盖回去）。
     */
    fun refresh(force: Boolean = false) {
        if (!force && _uiState.value.loading) return
        val epoch = ++sessionEpoch
        fetchJob?.cancel()
        fetchJob = null
        val session = ModuleUploadPrefs.readSession()
        val valid = session != null && session.validAt(System.currentTimeMillis())
        sessionToken = if (valid) session!!.token else ""
        if (!valid) {
            _uiState.update {
                it.copy(
                    needVerify = true,
                    loading = false,
                    loadFailed = false,
                    paging = false,
                    comments = emptyList(),
                    nextCursor = "",
                )
            }
            return
        }
        _uiState.update { it.copy(loading = true, loadFailed = false, needVerify = false, paging = false) }
        fetchJob = viewModelScope.launch {
            val result = ModuleComments.fetchMyComments(sessionToken)
            // 代次变了就说明这期间用户登出/换了验证：这次结果按旧身份取的，丢掉。
            if (epoch != sessionEpoch) return@launch
            when (result) {
                is ModuleComments.CommentsResult.Ok -> {
                    val arr = result.value.optJSONArray("comments")
                    val list = ArrayList<ModuleComments.MyCommentItem>(arr?.length() ?: 0)
                    for (i in 0 until (arr?.length() ?: 0)) {
                        arr?.optJSONObject(i)?.let { row -> ModuleComments.parseMyComment(row)?.let { list.add(it) } }
                    }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            comments = list,
                            nextCursor = result.value.optString("next_cursor", "").trim(),
                        )
                    }
                }
                ModuleComments.CommentsResult.NeedVerify -> _uiState.update {
                    it.copy(loading = false, needVerify = true)
                }
                is ModuleComments.CommentsResult.Failed -> _uiState.update {
                    it.copy(loading = false, loadFailed = true)
                }
            }
        }
    }

    /** 翻下一页。 */
    fun loadMore() {
        val s = _uiState.value
        if (s.paging || s.loading || s.nextCursor.isEmpty() || sessionToken.isEmpty()) return
        val epoch = sessionEpoch
        _uiState.update { it.copy(paging = true) }
        viewModelScope.launch {
            val result = ModuleComments.fetchMyComments(sessionToken, cursor = s.nextCursor)
            // 同上：翻页期间会话变了，这一页属于旧身份，不追加；顺手把 paging 放掉，
            // 否则它会一直是 true，把后面每一次翻页都挡在门口。
            if (epoch != sessionEpoch) {
                _uiState.update { it.copy(paging = false) }
                return@launch
            }
            when (result) {
                is ModuleComments.CommentsResult.Ok -> {
                    val arr = result.value.optJSONArray("comments")
                    val list = ArrayList<ModuleComments.MyCommentItem>(arr?.length() ?: 0)
                    for (i in 0 until (arr?.length() ?: 0)) {
                        arr?.optJSONObject(i)?.let { row -> ModuleComments.parseMyComment(row)?.let { list.add(it) } }
                    }
                    _uiState.update {
                        it.copy(
                            paging = false,
                            comments = it.comments + list,
                            nextCursor = result.value.optString("next_cursor", "").trim(),
                        )
                    }
                }
                else -> _uiState.update { it.copy(paging = false) }
            }
        }
    }
}
