package com.sukisu.ultra.ui.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.IconTile
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedDropdownItem
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.SegmentedSwitchItem
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.component.profile.TemporaryRootPreferences
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.backgroundAwareColor
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.customText
import kotlinx.coroutines.delay

/** 二级页卡片的统一外边距，与拆分前的一级设置页保持一致。 */
private val CardPadding = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)

/**
 * 带小节标题的卡片：标题自己已经有 16.dp 起始缩进，外面就不要再加左内边距了。
 */
private val TitledCardPadding = Modifier.padding(end = 16.dp, bottom = 13.dp)

/** 与组件内 SegmentedOuterRadius 一致，用于临时 Root 卡片的高亮描边。 */
private val HighlightCornerRadius = RoundedCornerShape(24.dp)

/**
 * 「Root 与安全」二级页（Material 3 皮肤）。
 *
 * 从设置一级页拆出来的 14 行，分两个小节：
 * - 常规：临时 Root 默认时长、传统 SU 命令支持、卸载模块(内核级)、隐藏 SELinux 修改、
 *   AVC 日志伪装、隐藏可疑挂载、SU 日志、ADB Root、限制 Shell 仅 ADB、软重启代替重启、
 *   内置 Zygisk、BuiltZygisk 面板（内置 Zygisk 打开时才出现）。
 * - 「伪装 / 反检测」：更换包名、自动越狱。
 *
 * 行为与拆分前一致：整页依赖管理器身份（[KsuIsValid]），各行的 status 决定可编辑性。
 * 行级锚点见 [AnchorHighlight]。
 */
@Composable
internal fun RootSecurityMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    onBack: () -> Unit,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    // 锚点登记表 + 页面级兜底：锚点指向的行在当前状态下不存在时把高亮消费掉，别让它一直挂着
    val registeredAnchors = LocalSettingsAnchorRegistry.current
    SettingsAnchorFallback(registeredAnchors, highlightAnchor, onHighlightConsumed)

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_group_root_security)) },
                navigationIcon = { TopBarBackButton(onBack) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                ),
                scrollBehavior = scrollBehavior,
            )
        },
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
            KsuIsValid {
                // 临时 Root 默认时长：行体在 TemporaryRootPreferences 里，自己就是一整张卡片，
                // 这里只负责滚动定位和高亮（卡片自己画底色，改不了，所以用描边指出是哪一块）。
                AnchorHighlight(
                    anchor = SettingsSection.TEMPORARY_ROOT,
                    highlightAnchor = highlightAnchor,
                    onHighlightConsumed = onHighlightConsumed,
                ) { highlighted ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TemporaryRootPreferences()
                        val borderColor by animateColorAsState(
                            targetValue = if (highlighted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 220),
                            label = "temporaryRootHighlight",
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                // 卡片自己带 16/16/13 的外边距，描边要贴到卡片上而不是整行。
                                .padding(start = 16.dp, end = 16.dp, bottom = 13.dp)
                                .border(2.dp, borderColor, HighlightCornerRadius),
                        )
                    }
                }

                val suCompatModeItems = listOf(
                    stringResource(R.string.settings_mode_enable_by_default),
                    stringResource(R.string.settings_mode_disable_until_reboot),
                    stringResource(R.string.settings_mode_disable_always),
                )

                SegmentedColumn(
                    modifier = CardPadding,
                    content = buildList {
                        // 传统 SU 命令支持
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.SU_COMPAT,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val suSummary = when (uiState.suCompatStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_sucompat_summary)
                                }
                                SegmentedDropdownItem(
                                    icon = Icons.Filled.AdminPanelSettings,
                                    title = customText(CustomTextKey.SETTING_SUCOMPAT),
                                    summary = suSummary,
                                    items = suCompatModeItems,
                                    colors = rowColors(highlighted),
                                    enabled = uiState.suCompatStatus == "supported",
                                    selectedIndex = uiState.suCompatMode,
                                    onItemSelected = actions.onSetSuCompatMode,
                                )
                            }
                        }
                        // 卸载模块(内核级)
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.KERNEL_UMOUNT,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val umountSummary = when (uiState.kernelUmountStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_kernel_umount_summary)
                                }
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.LayersClear,
                                    title = customText(CustomTextKey.SETTING_KERNEL_UMOUNT),
                                    summary = umountSummary,
                                    colors = rowColors(highlighted),
                                    enabled = uiState.kernelUmountStatus == "supported",
                                    checked = uiState.isKernelUmountEnabled,
                                    onCheckedChange = actions.onSetKernelUmountEnabled,
                                )
                            }
                        }
                        // 隐藏 SELinux 修改
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.SELINUX_HIDE,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_selinux_hide_summary)
                                }
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Security,
                                    title = customText(CustomTextKey.SETTING_SELINUX_HIDE),
                                    summary = selinuxHideSummary,
                                    colors = rowColors(highlighted),
                                    enabled = uiState.selinuxHideStatus == "supported",
                                    checked = uiState.isSelinuxHideEnabled,
                                    onCheckedChange = actions.onSetSelinuxHideEnabled,
                                )
                            }
                        }
                        // AVC 日志伪装
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.AVC_SPOOF,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val avcSpoofSummary = when (uiState.avcSpoofStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_avc_spoof_summary)
                                }
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Security,
                                    title = customText(CustomTextKey.SETTING_AVC_SPOOF),
                                    summary = avcSpoofSummary,
                                    colors = rowColors(highlighted),
                                    enabled = uiState.avcSpoofStatus == "supported",
                                    checked = uiState.isAvcSpoofEnabled,
                                    onCheckedChange = actions.onSetAvcSpoofEnabled,
                                )
                            }
                        }
                        // 隐藏可疑挂载
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.MOUNT_HIDE,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val mountHideSummary = when (uiState.mountHideStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_mount_hide_summary)
                                }
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Policy,
                                    title = customText(CustomTextKey.SETTING_MOUNT_HIDE),
                                    summary = mountHideSummary,
                                    colors = rowColors(highlighted),
                                    enabled = uiState.mountHideStatus == "supported",
                                    checked = uiState.isMountHideEnabled,
                                    onCheckedChange = actions.onSetMountHideEnabled,
                                )
                            }
                        }
                        // SU 日志
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.SULOG,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val sulogSummary = when (uiState.sulogStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_sulog_summary)
                                }
                                SegmentedSwitchItem(
                                    icon = Icons.AutoMirrored.Filled.Article,
                                    title = customText(CustomTextKey.SETTING_SULOG),
                                    summary = sulogSummary,
                                    colors = rowColors(highlighted),
                                    enabled = uiState.sulogStatus == "supported",
                                    checked = uiState.isSulogEnabled,
                                    onCheckedChange = actions.onSetSulogEnabled,
                                )
                            }
                        }
                        // ADB Root
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.ADB_ROOT,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val adbRootSummary = when (uiState.adbRootStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_adb_root_summary)
                                }
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Adb,
                                    title = customText(CustomTextKey.SETTING_ADB_ROOT),
                                    summary = adbRootSummary,
                                    colors = rowColors(highlighted),
                                    enabled = uiState.adbRootStatus == "supported",
                                    checked = uiState.isAdbRootEnabled,
                                    onCheckedChange = actions.onSetAdbRootEnabled,
                                )
                            }
                        }
                        // 限制 Shell 仅 ADB
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.SHELL_SU_ADB_ONLY,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val shellSuAdbOnlySummary = when (uiState.shellSuAdbOnlyStatus) {
                                    "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                    "managed" -> stringResource(R.string.feature_status_managed_summary)
                                    "forced" -> stringResource(R.string.feature_status_forced_summary)
                                    else -> stringResource(R.string.settings_shell_su_adb_only_summary)
                                }
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.VerifiedUser,
                                    title = customText(CustomTextKey.SETTING_SHELL_SU),
                                    summary = shellSuAdbOnlySummary,
                                    colors = rowColors(highlighted),
                                    enabled = uiState.shellSuAdbOnlyStatus == "supported",
                                    checked = uiState.isShellSuAdbOnlyEnabled,
                                    onCheckedChange = actions.onSetShellSuAdbOnlyEnabled,
                                )
                            }
                        }
                        // 软重启代替重启
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.SOFT_REBOOT,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.RestartAlt,
                                    title = customText(CustomTextKey.SETTING_SOFT_REBOOT),
                                    summary = stringResource(R.string.settings_soft_reboot_summary),
                                    colors = rowColors(highlighted),
                                    enabled = !uiState.isLateLoadMode,
                                    checked = uiState.isLateLoadMode || uiState.useSoftReboot,
                                    onCheckedChange = actions.onSetUseSoftReboot,
                                )
                            }
                        }
                        // 内置 Zygisk
                        add {
                            AnchorHighlight(
                                anchor = SettingsSection.BUILTIN_ZYGISK,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Extension,
                                    title = customText(CustomTextKey.ZYGISK_SETTINGS_TITLE),
                                    summary = stringResource(R.string.settings_builtin_zygisk_summary),
                                    colors = rowColors(highlighted),
                                    checked = uiState.builtinZygisk,
                                    onCheckedChange = { actions.onSetBuiltinZygisk(it) },
                                )
                            }
                        }
                        // BuiltZygisk 面板：只有内置 Zygisk 打开时才出现（与拆分前一致）。
                        if (uiState.builtinZygisk) {
                            add {
                                AnchorHighlight(
                                    anchor = SettingsSection.BUILTIN_ZYGISK_DASHBOARD,
                                    highlightAnchor = highlightAnchor,
                                    onHighlightConsumed = onHighlightConsumed,
                                ) { highlighted ->
                                    val dashboardTitle = customText(CustomTextKey.ZYGISK_DASHBOARD_TITLE)
                                    SegmentedListItem(
                                        onClick = actions.onOpenBuiltinZygisk,
                                        colors = rowColors(highlighted),
                                        headlineContent = { Text(dashboardTitle) },
                                        supportingContent = {
                                            Text(stringResource(R.string.settings_builtin_zygisk_dashboard_summary))
                                        },
                                        leadingContent = {
                                            IconTile(Icons.Filled.Settings, dashboardTitle)
                                        },
                                    )
                                }
                            }
                        }
                    },
                )

                // 第二小节：伪装 / 反检测。两个设置都是「让模块和 Root 痕迹不那么显眼」，
                // 和上面那批能力开关不是一类，所以单独分节。
                SegmentedColumn(
                    modifier = TitledCardPadding,
                    title = stringResource(R.string.settings_section_spoof),
                    content = listOf(
                        {
                            AnchorHighlight(
                                anchor = SettingsSection.PACKAGE_RENAME,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                val renameTitle = stringResource(R.string.pkg_rename_title)
                                SegmentedListItem(
                                    onClick = actions.onOpenPackageRename,
                                    colors = rowColors(highlighted),
                                    headlineContent = { Text(renameTitle) },
                                    supportingContent = {
                                        Text(stringResource(R.string.pkg_rename_entry_summary))
                                    },
                                    leadingContent = { IconTile(Icons.Filled.Edit, renameTitle) },
                                    trailingContent = {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                    },
                                )
                            }
                        },
                        {
                            AnchorHighlight(
                                anchor = SettingsSection.AUTO_JAILBREAK,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.FlashOn,
                                    title = customText(CustomTextKey.SETTING_AUTO_JAILBREAK),
                                    summary = stringResource(R.string.settings_auto_jailbreak_summary),
                                    colors = rowColors(highlighted),
                                    enabled = uiState.isLateLoadMode,
                                    checked = uiState.autoJailbreak,
                                    onCheckedChange = actions.onSetAutoJailbreak,
                                )
                            }
                        },
                    ),
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
    // 自报家门：锚点随行的组合/销毁进出台账，页面级兜底只认登记过的锚点
    val registry = LocalSettingsAnchorRegistry.current
    DisposableEffect(anchor) {
        registry += anchor
        onDispose { registry -= anchor }
    }
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

