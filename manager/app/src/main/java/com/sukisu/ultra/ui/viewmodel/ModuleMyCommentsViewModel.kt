package com.sukisu.ultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukisu.ultra.ui.screen.upload.ModuleUploadPrefs
import com.sukisu.ultra.ui.util.ModuleComments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    init {
        refresh()
    }

    /** 重新拉第一页；会话也在这里重读（用户可能刚去上传页重新验证完）。 */
    fun refresh() {
        if (_uiState.value.loading) return
        val session = ModuleUploadPrefs.readSession()
        val valid = session != null && session.validAt(System.currentTimeMillis())
        sessionToken = if (valid) session!!.token else ""
        if (!valid) {
            _uiState.update { it.copy(needVerify = true, loading = false, loadFailed = false, comments = emptyList(), nextCursor = "") }
            return
        }
        _uiState.update { it.copy(loading = true, loadFailed = false, needVerify = false) }
        viewModelScope.launch {
            val result = ModuleComments.fetchMyComments(sessionToken)
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
        _uiState.update { it.copy(paging = true) }
        viewModelScope.launch {
            val result = ModuleComments.fetchMyComments(sessionToken, cursor = s.nextCursor)
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
