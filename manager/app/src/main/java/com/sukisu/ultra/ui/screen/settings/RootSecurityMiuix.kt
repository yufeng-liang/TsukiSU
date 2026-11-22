package com.sukisu.ultra.ui.screen.settings

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.component.profile.TemporaryRootPreferences
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
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 功能开关的说明文案。
 *
 * 内核侧功能有三种状态：当前架构不支持、被管理器接管、被强制开启，
 * 分别给一句专门的说明，其它情况回落到各行自己的默认说明。
 */
@Composable
private fun featureStatusSummary(status: String, @StringRes fallbackRes: Int): String = when (status) {
    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
    "managed" -> stringResource(R.string.feature_status_managed_summary)
    "forced" -> stringResource(R.string.feature_status_forced_summary)
    else -> stringResource(fallbackRes)
}

/** 功能不可用时图标要变淡，和 Material 版的禁用图标观感对齐。 */
@Composable
private fun statusIconTint(enabled: Boolean): Color =
    if (enabled) colorScheme.onBackground else colorScheme.disabledOnSecondaryVariant

/**
 * Root 与安全（Miuix 皮肤）。
 *
 * 行内容与 Material 版一一对应（14 行），分三块：
 * 临时 Root 自己是一张卡；SU 兼容 / 内核卸载 / 隐藏 / 网络 / 重启 / 内置 Zygisk 十行同卡；
 * 改名与自动越狱两行在「隐藏」小节标题下同卡。
 */
@Composable
internal fun RootSecurityMiuix(
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
    // 这一页全是内核功能，没有管理器身份就一行都不显示（等价于 Material 版的 KsuIsValid 包裹）
    val ksuReady = Natives.isManager

    // 行序即 item 下标：页面级跳转按锚点反查下标，所以生成顺序必须稳定
    val slots: List<MiuixSettingsSlot> = buildList {
        if (ksuReady) {
            // 临时 Root 自带 AppCard 与 12.dp 边距，没法走分角背景，只能单独作为一行
            miuixSettingsSelfCardGroup(SettingsSection.TEMPORARY_ROOT) {
                TemporaryRootPreferences()
            }
            // 内置 Zygisk 关掉时面板行不存在，锚点跳转会被直接消费掉；显式写类型，避免 if 表达式里推断 lambda
            val builtinZygiskDashboardRow: Pair<String, @Composable () -> Unit>? =
                if (uiState.builtinZygisk) {
                    SettingsSection.BUILTIN_ZYGISK_DASHBOARD to {
                        val title = customText(CustomTextKey.ZYGISK_DASHBOARD_TITLE)
                        ArrowPreference(
                            title = title,
                            summary = stringResource(R.string.settings_builtin_zygisk_dashboard_summary),
                            startAction = {
                                MiuixSettingsRowIcon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.settings_builtin_zygisk_dashboard),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onClick = actions.onOpenBuiltinZygisk,
                        )
                    }
                } else {
                    null
                }
            miuixSettingsGroup(
                SettingsSection.SU_COMPAT to {
                    val title = customText(CustomTextKey.SETTING_SUCOMPAT)
                    val enabled = uiState.suCompatStatus == "supported"
                    OverlayDropdownPreference(
                        title = title,
                        summary = featureStatusSummary(uiState.suCompatStatus, R.string.settings_sucompat_summary),
                        items = listOf(
                            stringResource(R.string.settings_mode_enable_by_default),
                            stringResource(R.string.settings_mode_disable_until_reboot),
                            stringResource(R.string.settings_mode_disable_always),
                        ),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = title,
                                tint = statusIconTint(enabled),
                            )
                        },
                        enabled = enabled,
                        selectedIndex = uiState.suCompatMode,
                        onSelectedIndexChange = actions.onSetSuCompatMode,
                    )
                },
                SettingsSection.KERNEL_UMOUNT to {
                    val title = customText(CustomTextKey.SETTING_KERNEL_UMOUNT)
                    val enabled = uiState.kernelUmountStatus == "supported"
                    SwitchPreference(
                        title = title,
                        summary = featureStatusSummary(uiState.kernelUmountStatus, R.string.settings_kernel_umount_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.LayersClear,
                                contentDescription = title,
                                tint = statusIconTint(enabled),
                            )
                        },
                        enabled = enabled,
                        checked = uiState.isKernelUmountEnabled,
                        onCheckedChange = actions.onSetKernelUmountEnabled,
                    )
                },
                SettingsSection.SELINUX_HIDE to {
                    val title = customText(CustomTextKey.SETTING_SELINUX_HIDE)
                    val enabled = uiState.selinuxHideStatus == "supported"
                    SwitchPreference(
                        title = title,
                        summary = featureStatusSummary(uiState.selinuxHideStatus, R.string.settings_selinux_hide_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = title,
                                tint = statusIconTint(enabled),
                            )
                        },
                        enabled = enabled,
                        checked = uiState.isSelinuxHideEnabled,
                        onCheckedChange = actions.onSetSelinuxHideEnabled,
                    )
                },
                SettingsSection.AVC_SPOOF to {
                    val title = customText(CustomTextKey.SETTING_AVC_SPOOF)
                    val enabled = uiState.avcSpoofStatus == "supported"
                    SwitchPreference(
                        title = title,
                        summary = featureStatusSummary(uiState.avcSpoofStatus, R.string.settings_avc_spoof_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = title,
                                tint = statusIconTint(enabled),
                            )
                        },
                        enabled = enabled,
                        checked = uiState.isAvcSpoofEnabled,
                        onCheckedChange = actions.onSetAvcSpoofEnabled,
                    )
                },
                SettingsSection.MOUNT_HIDE to {
                    val title = customText(CustomTextKey.SETTING_MOUNT_HIDE)
                    val enabled = uiState.mountHideStatus == "supported"
                    SwitchPreference(
                        title = title,
                        summary = featureStatusSummary(uiState.mountHideStatus, R.string.settings_mount_hide_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.Policy,
                                contentDescription = title,
                                tint = statusIconTint(enabled),
                            )
                        },
                        enabled = enabled,
                        checked = uiState.isMountHideEnabled,
                        onCheckedChange = actions.onSetMountHideEnabled,
                    )
                },
                SettingsSection.SULOG to {
                    val title = customText(CustomTextKey.SETTING_SULOG)
                    val enabled = uiState.sulogStatus == "supported"
                    SwitchPreference(
                        title = title,
                        summary = featureStatusSummary(uiState.sulogStatus, R.string.settings_sulog_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.AutoMirrored.Rounded.Article,
                                contentDescription = title,
                                tint = statusIconTint(enabled),
                            )
                        },
                        enabled = enabled,
                        checked = uiState.isSulogEnabled,
                        onCheckedChange = actions.onSetSulogEnabled,
                    )
                },
                SettingsSection.ADB_ROOT to {
                    val title = customText(CustomTextKey.SETTING_ADB_ROOT)
                    val enabled = uiState.adbRootStatus == "supported"
                    SwitchPreference(
                        title = title,
                        summary = featureStatusSummary(uiState.adbRootStatus, R.string.settings_adb_root_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.Adb,
                                contentDescription = title,
                                tint = statusIconTint(enabled),
                            )
                        },
                        enabled = enabled,
                        checked = uiState.isAdbRootEnabled,
                        onCheckedChange = actions.onSetAdbRootEnabled,
                    )
                },
                SettingsSection.SHELL_SU_ADB_ONLY to {
                    val title = customText(CustomTextKey.SETTING_SHELL_SU)
                    val enabled = uiState.shellSuAdbOnlyStatus == "supported"
                    SwitchPreference(
                        title = title,
                        summary = featureStatusSummary(uiState.shellSuAdbOnlyStatus, R.string.settings_shell_su_adb_only_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = title,
                                tint = statusIconTint(enabled),
                            )
                        },
                        enabled = enabled,
                        checked = uiState.isShellSuAdbOnlyEnabled,
                        onCheckedChange = actions.onSetShellSuAdbOnlyEnabled,
                    )
                },
                SettingsSection.SOFT_REBOOT to {
                    val title = customText(CustomTextKey.SETTING_SOFT_REBOOT)
                    // late-load 内核下只能软重启，所以这一项被锁定为开启
                    val lateLoad = uiState.isLateLoadMode
                    SwitchPreference(
                        title = title,
                        summary = stringResource(R.string.settings_soft_reboot_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = title,
                                tint = if (lateLoad) colorScheme.onBackground else colorScheme.disabledOnSecondaryVariant,
                            )
                        },
                        enabled = !lateLoad,
                        checked = lateLoad || uiState.useSoftReboot,
                        onCheckedChange = actions.onSetUseSoftReboot,
                    )
                },
                SettingsSection.BUILTIN_ZYGISK to {
                    val title = customText(CustomTextKey.ZYGISK_SETTINGS_TITLE)
                    SwitchPreference(
                        title = title,
                        summary = stringResource(R.string.settings_builtin_zygisk_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = stringResource(R.string.settings_builtin_zygisk),
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = uiState.builtinZygisk,
                        onCheckedChange = { actions.onSetBuiltinZygisk(it) },
                    )
                },
                extra = builtinZygiskDashboardRow,
            )
            // Material 版把这一小节标题画在卡内，miuix 用 SmallTitle 更合适：它自带上下留白
            miuixSettingsHeader(R.string.settings_section_spoof)
            miuixSettingsGroup(
                SettingsSection.PACKAGE_RENAME to {
                    ArrowPreference(
                        title = stringResource(R.string.pkg_rename_title),
                        summary = stringResource(R.string.pkg_rename_entry_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.pkg_rename_title),
                                tint = colorScheme.onBackground,
                            )
                        },
                        onClick = actions.onOpenPackageRename,
                    )
                },
                SettingsSection.AUTO_JAILBREAK to {
                    val title = customText(CustomTextKey.SETTING_AUTO_JAILBREAK)
                    // 只有 late-load 内核才需要自动越狱，其它模式整行置灰
                    val lateLoad = uiState.isLateLoadMode
                    SwitchPreference(
                        title = title,
                        summary = stringResource(R.string.settings_auto_jailbreak_summary),
                        startAction = {
                            MiuixSettingsRowIcon(
                                imageVector = Icons.Rounded.FlashOn,
                                contentDescription = title,
                                tint = if (lateLoad) colorScheme.onBackground else colorScheme.disabledOnSecondaryVariant,
                            )
                        },
                        enabled = lateLoad,
                        checked = uiState.autoJailbreak,
                        onCheckedChange = actions.onSetAutoJailbreak,
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
                    title = stringResource(R.string.settings_group_root_security),
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
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
