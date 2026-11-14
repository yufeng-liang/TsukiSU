package com.sukisu.ultra.ui.screen.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.ModuleComments
import com.sukisu.ultra.ui.util.formatRepoTime
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import com.sukisu.ultra.ui.viewmodel.ModuleMyCommentsViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 「我的评论」：我在模块商店发过的所有评论（含已删除占位），独立于上传记录。
 *
 * 两皮肤分发与上传屏一致；needVerify（无会话/过期）整页引导去上传页验证。
 */
@Composable
fun MyCommentsScreen(viewModel: ModuleMyCommentsViewModel = viewModel()) {
    val uiMode = LocalUiMode.current
    when (uiMode) {
        UiMode.Miuix -> MyCommentsScreenMiuix(viewModel)
        UiMode.Material -> MyCommentsScreenMaterial(viewModel)
    }
}

/** Material 皮肤：ExpressiveScaffold + 大标题顶栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyCommentsScreenMaterial(viewModel: ModuleMyCommentsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) { viewModel.refresh() }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { M3Text(stringResource(R.string.module_my_comments_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        when {
            state.needVerify -> MyCommentsVerifyPaneMaterial(
                onGoVerify = { navigator.push(Route.ModuleUpload) },
                innerPadding = innerPadding,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    // 首次加载：整页只有一行「加载中」，别让空态抢跑。
                    state.loading && state.comments.isEmpty() -> {
                        item(key = "loading") {
                            M3Text(
                                text = stringResource(R.string.module_my_comments_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    // 首拉失败且一页都没有：给一次重试。
                    state.loadFailed && state.comments.isEmpty() -> {
                        item(key = "load_failed") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                M3Text(
                                    text = stringResource(R.string.module_my_comments_load_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = viewModel::refresh) {
                                    M3Text(stringResource(R.string.network_retry))
                                }
                            }
                        }
                    }
                    // 空态：一句说明就够。
                    state.comments.isEmpty() -> {
                        item(key = "empty") {
                            M3Text(
                                text = stringResource(R.string.module_my_comments_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    else -> {
                        itemsIndexed(items = state.comments, key = { _, c -> c.id }) { _, comment ->
                            MyCommentRowMaterial(comment = comment)
                        }
                        if (state.nextCursor.isNotEmpty()) {
                            item(key = "load_more") {
                                TextButton(onClick = viewModel::loadMore, enabled = !state.paging) {
                                    M3Text(
                                        stringResource(
                                            if (state.paging) R.string.module_my_comments_loading
                                            else R.string.module_my_comments_load_more
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一条「我的评论」（Material）：内容 + 时间，已删除的换成灰色占位。 */
@Composable
private fun MyCommentRowMaterial(comment: ModuleComments.MyCommentItem) {
    val deleted = comment.deleted || comment.content.isEmpty()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        M3Text(
            text = if (deleted) stringResource(R.string.module_my_comments_deleted) else comment.content,
            style = MaterialTheme.typography.bodyMedium,
            color = if (deleted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        M3Text(
            text = formatRepoTime(comment.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 未登录占位（Material）：引导去上传页完成验证。 */
@Composable
private fun MyCommentsVerifyPaneMaterial(onGoVerify: () -> Unit, innerPadding: PaddingValues) {
    val layoutDirection = LocalLayoutDirection.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding() + 32.dp,
                start = innerPadding.calculateStartPadding(layoutDirection) + 24.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        M3Text(
            text = stringResource(R.string.module_my_comments_need_verify),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onGoVerify) {
            M3Text(stringResource(R.string.module_my_comments_go_verify))
        }
    }
}

/** Miuix 皮肤：AppScaffold + BlurredBar 顶栏 + AppCard 列表。 */
@Composable
private fun MyCommentsScreenMiuix(viewModel: ModuleMyCommentsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    // 模糊可用时顶栏底色透明，让毛玻璃本身当底色；否则退回表面色（与上传记录页同一套）。
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    LaunchedEffect(Unit) { viewModel.refresh() }

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.module_my_comments_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.needVerify -> MyCommentsVerifyPaneMiuix(
                    onGoVerify = { navigator.push(Route.ModuleUpload) },
                    innerPadding = innerPadding,
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        // 底边自己补导航栏：Scaffold 的 insets 只留了水平方向。
                        bottom = innerPadding.calculateBottomPadding() +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        state.loading && state.comments.isEmpty() -> {
                            item(key = "loading") {
                                Text(
                                    text = stringResource(R.string.module_my_comments_loading),
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        state.loadFailed && state.comments.isEmpty() -> {
                            item(key = "load_failed") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.module_my_comments_load_failed),
                                        fontSize = 14.sp,
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                    MiuixTextButton(
                                        text = stringResource(R.string.network_retry),
                                        onClick = viewModel::refresh,
                                    )
                                }
                            }
                        }
                        state.comments.isEmpty() -> {
                            item(key = "empty") {
                                Text(
                                    text = stringResource(R.string.module_my_comments_empty),
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        else -> {
                            itemsIndexed(items = state.comments, key = { _, c -> c.id }) { _, comment ->
                                AppCard(modifier = Modifier.fillMaxWidth()) {
                                    MyCommentRowMiuix(comment = comment)
                                }
                            }
                            if (state.nextCursor.isNotEmpty()) {
                                item(key = "load_more") {
                                    MiuixTextButton(
                                        text = stringResource(
                                            if (state.paging) R.string.module_my_comments_loading
                                            else R.string.module_my_comments_load_more
                                        ),
                                        onClick = viewModel::loadMore,
                                        enabled = !state.paging,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一条「我的评论」（Miuix）：内容 + 时间，已删除的换成灰色占位。 */
@Composable
private fun MyCommentRowMiuix(comment: ModuleComments.MyCommentItem) {
    val deleted = comment.deleted || comment.content.isEmpty()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (deleted) stringResource(R.string.module_my_comments_deleted) else comment.content,
            fontSize = 14.sp,
            color = if (deleted) colorScheme.onSurfaceVariantSummary else colorScheme.onSurface,
        )
        Text(
            text = formatRepoTime(comment.createdAt),
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 未登录占位（Miuix）：引导去上传页完成验证。 */
@Composable
private fun MyCommentsVerifyPaneMiuix(onGoVerify: () -> Unit, innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding() + 32.dp,
                start = 24.dp,
                end = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.module_my_comments_need_verify),
            fontSize = 14.sp,
            color = colorScheme.onSurfaceVariantSummary,
        )
        MiuixTextButton(
            text = stringResource(R.string.module_my_comments_go_verify),
            onClick = onGoVerify,
        )
    }
}