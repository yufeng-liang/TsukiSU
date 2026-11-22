package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.rememberLoadingDialog
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.component.miuix.SendLogDialog
import com.sukisu.ultra.ui.component.uninstalldialog.UninstallDialog
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.customText
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 维护（Miuix 皮肤）。
 *
 * 行内容与 Material 版一一对应（6 行）：代理设置、发日志、卸载、关于不依赖 root，
 * 备份与 Web 调试只在管理器身份下出现（等价于 Material 版的 KsuIsValid）。
 * 每行单独成 item（AGENTS 第 9 条），卡片观感靠分角背景把相邻行拼回去。
 */
@Composable
internal fun MaintenanceMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    onBack: () -> Unit,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    // 有实时模糊或自定义背景时顶栏必须透明：模糊层自己会画底色，背景图本来就要透出来
    // 两者都没有时用不透明 surface，否则滚动内容会直接露在标题底下
    val translucentBar = backdrop != null || hasCustomBackground
    val barColor = if (translucentBar) Color.Transparent else colorScheme.surface
    val listState = rememberLazyListState()
    // 判据与 KsuValidCheck 一致（Natives.isManager）；不用 remember，身份变化后能跟着刷新
    val ksuReady = Natives.isManager
    val loadingDialog = rememberLoadingDialog()
    // 两个对话框的开关跟着页面走：转屏、切深浅色不该把它们弹回来
    var showSendLogDialog by rememberSaveable { mutableStateOf(false) }
    var showUninstallDialog by rememberSaveable { mutableStateOf(false) }

    // 行序即 item 下标：页面级跳转按锚点反查下标，所以生成顺序必须稳定
    val slots: List<MiuixSettingsSlot> = buildList {
        // 代理设置不需要 root：没装内核也能先配好下载代理
        miuixSettingsGroup(
            SettingsSection.GITHUB_PROXY to {
                val title = stringResource(R.string.settings_proxy_title)
                ArrowPreference(
                    title = title,
                    summary = stringResource(R.string.settings_proxy_entry_summary),
                    startAction = { MiuixSettingsRowIcon(Icons.Filled.Download, title) },
                    onClick = actions.onOpenProxySettings,
                )
            },
        )
        if (ksuReady) {
            miuixSettingsGroup(
                SettingsSection.BACKUP to {
                    val title = stringResource(R.string.backup_title)
                    ArrowPreference(
                        title = title,
                        summary = stringResource(R.string.backup_entry_summary),
                        startAction = { MiuixSettingsRowIcon(Icons.Rounded.Backup, title) },
                        onClick = actions.onOpenBackup,
                    )
                },
            )
        }
        miuixSettingsGroup(
            SettingsSection.SEND_LOG to {
                val title = customText(CustomTextKey.SETTING_SEND_LOG)
                ArrowPreference(
                    title = title,
                    startAction = { MiuixSettingsRowIcon(Icons.Rounded.BugReport, title) },
                    onClick = { showSendLogDialog = true },
                )
            },
        )
        if (ksuReady) {
            miuixSettingsGroup(
                SettingsSection.WEB_DEBUGGING to {
                    val title = customText(CustomTextKey.SETTING_WEB_DEBUGGING)
                    SwitchPreference(
                        title = title,
                        summary = stringResource(R.string.enable_web_debugging_summary),
                        startAction = { MiuixSettingsRowIcon(Icons.Rounded.DeveloperMode, title) },
                        checked = uiState.enableWebDebugging,
                        onCheckedChange = actions.onSetEnableWebDebugging,
                    )
                },
            )
        }
        // 只有 LKM 模式能卸载：GKI 模式下这一行不给点
        if (uiState.isLkmMode) {
            miuixSettingsGroup(
                SettingsSection.UNINSTALL to {
                    val title = customText(CustomTextKey.SETTING_UNINSTALL)
                    ArrowPreference(
                        title = title,
                        startAction = { MiuixSettingsRowIcon(Icons.Rounded.Delete, title) },
                        enabled = !uiState.isLateLoadMode,
                        onClick = { showUninstallDialog = true },
                    )
                },
            )
        }

    }

    AppScaffold(
        // 页面底色只跟自定义背景走：实时模糊开着时整页仍需自己的底色，
        // 否则会透出上一页（顶栏透明交给 barColor 处理）
        containerColor = if (hasCustomBackground) Color.Transparent else colorScheme.surface,
        topBar = {
            BlurredBar(backdrop = backdrop, scrollBehavior = scrollBehavior) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings_group_maintenance),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    // miuix 的返回箭头在 RTL 下要镜像，跟其它页面保持一致
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        // 顶栏高度 + 列表顶部 12dp：scrollToItem 用负偏移让出这块，否则目标行正好藏在顶栏下面
        val topOffsetPx = with(LocalDensity.current) {
            (innerPadding.calculateTopPadding() + 12.dp).roundToPx()
        }
        // 先把列表按已知下标滚过去，行自己的 requester 再把行体精确带进视野
        MiuixSettingsHighlightScroll(listState, slots, highlightAnchor, onHighlightConsumed, topOffsetPx)
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                // contentPadding 只设顶部：底部留白靠末尾 Spacer + 导航栏内边距
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 12.dp),
                overscrollEffect = null,
            ) {
                miuixSettingsSlots(slots, highlightAnchor, onHighlightConsumed)
                item(key = "bottom-spacer") {
                    Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
                }
            }
        }
    }

    // 发日志：miuix 皮肤走对话框（Material 版是底部弹层），语义一致
    SendLogDialog(
        show = showSendLogDialog,
        onDismissRequest = { showSendLogDialog = false },
        loadingDialog = loadingDialog,
    )
    UninstallDialog(
        show = showUninstallDialog,
        onDismissRequest = { showUninstallDialog = false },
    )
}
