package com.sukisu.ultra.ui.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.IconTile
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.SegmentedSwitchItem
import com.sukisu.ultra.ui.component.material.SendLogBottomSheet
import com.sukisu.ultra.ui.component.material.SnackBarHost
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.component.uninstalldialog.UninstallDialog
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.backgroundAwareColor
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.customText
import kotlinx.coroutines.delay

/** 二级页卡片的统一外边距，与拆分前的一级设置页保持一致。 */
private val CardPadding = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)

/**
 * 「诊断与维护」二级页（Material 3 皮肤）。
 *
 * 从设置一级页拆出来的 6 行：GitHub 下载加速（单独一行的小节）、备份与恢复、发送日志、
 * WebView 调试、卸载（仅 LKM 模式）、关于。发送日志沿用原来的 [SendLogBottomSheet]，
 * 卸载沿用 [UninstallDialog]，行为不变。
 */
@Composable
internal fun MaintenanceMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    onBack: () -> Unit,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = remember { SnackbarHostState() }
    val showUninstallDialog = rememberSaveable { mutableStateOf(false) }
    var showSendLogSheet by remember { mutableStateOf(false) }

    UninstallDialog(
        show = showUninstallDialog.value,
        onDismissRequest = { showUninstallDialog.value = false },
    )

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_group_maintenance)) },
                navigationIcon = { TopBarBackButton(onBack) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost) },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            // GitHub 下载加速：只留一行入口，模式/代理列表/测速都在独立子页里。
            SegmentedColumn(
                modifier = CardPadding,
                content = listOf {
                    AnchorHighlight(
                        anchor = SettingsSection.GITHUB_PROXY,
                        highlightAnchor = highlightAnchor,
                        onHighlightConsumed = onHighlightConsumed,
                    ) { highlighted ->
                        SegmentedListItem(
                            onClick = actions.onOpenProxySettings,
                            colors = rowColors(highlighted),
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.settings_proxy_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            supportingContent = {
                                Text(stringResource(R.string.settings_proxy_entry_summary))
                            },
                            leadingContent = {
                                IconTile(
                                    Icons.Filled.Download,
                                    stringResource(R.string.settings_proxy_title),
                                )
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            },
                        )
                    }
                },
            )

            KsuIsValid {
                // 备份与恢复
                SegmentedColumn(
                    modifier = CardPadding,
                    content = listOf {
                        AnchorHighlight(
                            anchor = SettingsSection.BACKUP,
                            highlightAnchor = highlightAnchor,
                            onHighlightConsumed = onHighlightConsumed,
                        ) { highlighted ->
                            val backupTitle = stringResource(R.string.backup_title)
                            SegmentedListItem(
                                onClick = actions.onOpenBackup,
                                colors = rowColors(highlighted),
                                headlineContent = { Text(backupTitle) },
                                supportingContent = {
                                    Text(stringResource(R.string.backup_entry_summary))
                                },
                                leadingContent = { IconTile(Icons.Filled.Backup, backupTitle) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                },
                            )
                        }
                    },
                )
            }

            // 发送日志
            SegmentedColumn(
                modifier = CardPadding,
                content = listOf {
                    AnchorHighlight(
                        anchor = SettingsSection.SEND_LOG,
                        highlightAnchor = highlightAnchor,
                        onHighlightConsumed = onHighlightConsumed,
                    ) { highlighted ->
                        val sendLogTitle = customText(CustomTextKey.SETTING_SEND_LOG)
                        SegmentedListItem(
                            onClick = { showSendLogSheet = true },
                            colors = rowColors(highlighted),
                            headlineContent = { Text(sendLogTitle) },
                            leadingContent = { IconTile(Icons.Filled.BugReport, sendLogTitle) },
                        )
                    }
                },
            )

            KsuIsValid {
                // WebView 调试
                SegmentedColumn(
                    modifier = CardPadding,
                    content = listOf {
                        AnchorHighlight(
                            anchor = SettingsSection.WEB_DEBUGGING,
                            highlightAnchor = highlightAnchor,
                            onHighlightConsumed = onHighlightConsumed,
                        ) { highlighted ->
                            SegmentedSwitchItem(
                                icon = Icons.Filled.DeveloperMode,
                                title = customText(CustomTextKey.SETTING_WEB_DEBUGGING),
                                summary = stringResource(R.string.enable_web_debugging_summary),
                                colors = rowColors(highlighted),
                                checked = uiState.enableWebDebugging,
                                onCheckedChange = actions.onSetEnableWebDebugging,
                            )
                        }
                    },
                )
            }

            // 卸载：只在 LKM 模式下出现（与拆分前一致），绕过期不允许卸载。
            if (uiState.isLkmMode) {
                SegmentedColumn(
                    modifier = CardPadding,
                    content = listOf {
                        AnchorHighlight(
                            anchor = SettingsSection.UNINSTALL,
                            highlightAnchor = highlightAnchor,
                            onHighlightConsumed = onHighlightConsumed,
                        ) { highlighted ->
                            val uninstall = customText(CustomTextKey.SETTING_UNINSTALL)
                            SegmentedListItem(
                                onClick = { showUninstallDialog.value = true },
                                enabled = !uiState.isLateLoadMode,
                                colors = rowColors(highlighted),
                                headlineContent = { Text(uninstall) },
                                leadingContent = { IconTile(Icons.Filled.Delete, uninstall) },
                            )
                        }
                    },
                )
            }


            Spacer(Modifier.height(8.dp))

            if (showSendLogSheet) {
                SendLogBottomSheet(
                    onDismiss = { showSendLogSheet = false },
                    snackbarHostState = snackBarHost,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 行级锚点：把行滚进视野并在被点名时高亮 2 秒。
 *
 * 页面拆成独立路由之后，滚动只能由页面自己发起（原先靠一级页的 anchorRequesters 映射表），
 * 所以每个可被跳转的行都持有自己的 [BringIntoViewRequester]。
 */
@Composable
private fun AnchorHighlight(
    anchor: String,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
    content: @Composable (highlighted: Boolean) -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    val targeted = highlightAnchor == anchor
    var highlighted by remember { mutableStateOf(false) }

    LaunchedEffect(targeted, anchor) {
        if (!targeted) {
            // 被别的行顶掉或者已经消费过：确保不会留下一个不熄灭的高亮。
            highlighted = false
            return@LaunchedEffect
        }
        // 先滚进视野再点亮，滚动过程中就能看出是哪一行。
        requester.bringIntoView()
        highlighted = true
        delay(2_000)
        highlighted = false
        onHighlightConsumed()
    }

    Box(modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
        content(highlighted)
    }
}

/**
 * 行底色。默认与 [SegmentedColumn] 自带的行底色一致；被锚点命中时换成强调色。
 *
 * 组件里的 defaultSegmentedColors() 是 private，这里按同样的配方自己拼一份，
 * 这样高亮只改底色，不动文字/勾选框的配色。
 */
@Composable
private fun rowColors(highlighted: Boolean): ListItemColors {
    val base = backgroundAwareColor(MaterialTheme.colorScheme.surfaceBright)
    val container by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else base,
        animationSpec = tween(durationMillis = 220),
        label = "settingsRowHighlight",
    )
    val colors = ListItemDefaults.segmentedColors(
        containerColor = container,
        disabledContainerColor = container,
        // 勾选状态不换底色：勾选框本身已经在说「选中了」，与组件默认行为保持一致。
        selectedContainerColor = container,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (LocalBackgroundImagePath.current.isEmpty()) return colors
    // 自定义背景图上卡片是半透明的，组件默认的禁用态前景太淡，抬一点对比度。
    return colors.copy(
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        disabledLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        disabledTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        disabledOverlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        disabledSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
    )
}
