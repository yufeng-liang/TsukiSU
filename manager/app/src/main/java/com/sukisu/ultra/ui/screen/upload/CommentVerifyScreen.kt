package com.sukisu.ultra.ui.screen.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.navigation3.LocalNavigator

/**
 * 「评论区验证」路由的入口：一个路由、一套状态、两套主题。
 *
 * 详情页评论区在拿不到有效会话时把「去验证」指到这里。它复用投稿链路的身份屏
 * （[ModuleUploadIdentityMaterial] / [ModuleUploadIdentityMiuix]）与状态
 * （[ModuleUploadViewModel]），但走评论模式：不抄签名、不勾承诺，以 mode=comment
 * 换一张只够点赞评论的令牌；验证成功就地写盘并 pop 回详情页 —— 评论区重组时会
 * 重读会话并自动刷新列表（见 [com.sukisu.ultra.ui.viewmodel.ModuleCommentsViewModel.recheckSession]）。
 *
 * 不调 [ModuleUploadViewModel.onEnter]：那是投稿链路的入口（拉投稿列表、启动
 * 进度轮询），评论验证用不上；进页准备走 [ModuleUploadViewModel.prepareForCommentVerify]。
 */
@Composable
fun CommentVerifyScreen() {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val viewModel = viewModel<ModuleUploadViewModel>()
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.prepareForCommentVerify() }

    when (uiMode) {
        UiMode.Material -> ModuleUploadIdentityMaterial(
            state = formState,
            onExit = { navigator.pop() },
            onUidChange = viewModel::updateUid,
            onRetryUid = viewModel::retryUidLookup,
            onConsentChange = viewModel::setConsent,
            onRenewCommit = viewModel::renewIdentityCommit,
            onClearLevelBlock = viewModel::clearLevelBlock,
            onVerify = { viewModel.verifyForComment(onSuccess = { navigator.pop() }) },
            commentMode = true,
        )

        UiMode.Miuix -> ModuleUploadIdentityMiuix(
            state = formState,
            onExit = { navigator.pop() },
            onUidChange = viewModel::updateUid,
            onRetryUid = viewModel::retryUidLookup,
            onConsentChange = viewModel::setConsent,
            onRenewCommit = viewModel::renewIdentityCommit,
            onVerify = { viewModel.verifyForComment(onSuccess = { navigator.pop() }) },
            onClearLevelBlock = viewModel::clearLevelBlock,
            commentMode = true,
        )
    }
}
