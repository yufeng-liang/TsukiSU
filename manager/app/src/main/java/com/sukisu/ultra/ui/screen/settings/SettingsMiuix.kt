package com.sukisu.ultra.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.component.profile.TemporaryRootPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.ModuleContributionPrefs
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.dialog.rememberLoadingDialog
import com.sukisu.ultra.ui.component.miuix.SendLogDialog
import com.sukisu.ultra.ui.component.uninstalldialog.UninstallDialog
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.theme.backgroundAwareCardColors
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.customText
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.LocaleHelper
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import com.sukisu.ultra.ui.viewmodel.MainPageRequest
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.sukisu.ultra.data.proxy.ProxyHealth
import com.sukisu.ultra.data.proxy.ProxyMode
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
    sectionRequest: MainPageRequest? = null,
    onSectionConsumed: (Long) -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val anchorRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }
    fun requester(anchor: String): BringIntoViewRequester =
        anchorRequesters.getOrPut(anchor) { BringIntoViewRequester() }

    LaunchedEffect(sectionRequest?.token) {
        val request = sectionRequest ?: return@LaunchedEffect
        delay(120)
        val anchor = SettingsSection.normalize(request.section)
        val target = anchorRequesters[SettingsSection.cardFor(anchor)]
            ?: anchorRequesters[SettingsSection.GENERAL]
        target?.bringIntoView()
        onSectionConsumed(request.token)
    }
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val barColor = if (blurActive || hasCustomBackground) Color.Transparent else colorScheme.surface
    val loadingDialog = rememberLoadingDialog()
    val showUninstallDialog = rememberSaveable { mutableStateOf(false) }
    val showSendLogDialog = rememberSaveable { mutableStateOf(false) }

    AppScaffold(
        containerColor = if (hasCustomBackground) Color.Transparent else colorScheme.surface,
        topBar = {
            BlurredBar(backdrop = backdrop, scrollBehavior = scrollBehavior) {
                TopAppBar(
                    color = Color.Transparent,
                    title = customText(CustomTextKey.PAGE_SETTINGS),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    AppCard(
                        modifier = Modifier
                            .bringIntoViewRequester(requester(SettingsSection.GENERAL))
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        // 更新检查的三个开关已随上游挪到「关于」页，设置页不再重复展示。
                        // 模块上传的入口与「安装后提示分享」开关放在同一张卡里：
                        // 前者是主动上传，后者只控制装完本地模块后的提示，两件事都不受 root 影响。
                        // 投稿功能风险未解决暂时下线（ModuleContributionPrefs.SUSPENDED），整块一起隐藏。
                        if (!ModuleContributionPrefs.SUSPENDED) {
                            val uploadTitle = stringResource(R.string.module_upload_entry_title)
                            ArrowPreference(
                                title = uploadTitle,
                                summary = stringResource(R.string.module_upload_entry_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Publish,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = uploadTitle,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = actions.onOpenModuleUpload,
                            )
                            SwitchPreference(
                                title = stringResource(R.string.module_upload_after_install_title),
                                summary = stringResource(R.string.module_upload_after_install_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Share,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = stringResource(R.string.module_upload_after_install_title),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = uiState.moduleContributionEnabled,
                                onCheckedChange = actions.onSetModuleContributionEnabled
                            )
                        }
                        OverlayDropdownPreference(
                            title = customText(CustomTextKey.SETTING_UI_MODE),
                            summary = stringResource(id = R.string.settings_ui_mode_summary),
                            items = UiMode.entries.map { it.name },
                            startAction = {
                                Icon(
                                    Icons.Rounded.DisplaySettings,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = customText(CustomTextKey.SETTING_UI_MODE),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = if (uiState.uiMode == UiMode.Material.value) 1 else 0,
                            onSelectedIndexChange = actions.onSetUiModeIndex
                        )
                        val languageSystemLabel = stringResource(id = R.string.settings_language_system)
                        val languageTags = remember { listOf(LocaleHelper.SYSTEM) + LocaleHelper.SUPPORTED_TAGS }
                        val languageNames = remember(languageSystemLabel) {
                            languageTags.map { if (it.isEmpty()) languageSystemLabel else LocaleHelper.displayName(it) }
                        }
                        OverlayDropdownPreference(
                            title = customText(CustomTextKey.SETTING_LANGUAGE),
                            summary = stringResource(id = R.string.settings_language_summary),
                            items = languageNames,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Language,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = customText(CustomTextKey.SETTING_LANGUAGE),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = languageTags.indexOf(uiState.appLanguage).coerceAtLeast(0),
                            onSelectedIndexChange = { index -> actions.onSetLanguage(languageTags[index]) }
                        )
                        val themeTitle = customText(CustomTextKey.PAGE_APPEARANCE)
                        ArrowPreference(
                            title = themeTitle,
                            summary = stringResource(id = R.string.settings_theme_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = themeTitle,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenTheme
                        )
                    }
                }
                // GitHub 下载加速单独占一个 item：它和上面的通用卡不是同一组设置，
                // 拆出来既避免一张卡里塞太多行，也给它自己留出锚点/重组边界。
                // 状态和 Material 页共用，纯客户端配置，不需要 root。
                item {
                    val proxyMode = ProxyMode.from(uiState.proxyMode)
                    val proxyPinned = proxyMode == ProxyMode.PINNED
                    val proxyHealthById = uiState.proxyHealth.associateBy { it.id }
                    // 存下来的 id 可能已经不在列表里（下发源下线或改了 id）。这时下载会悄悄退回直连，
                    // 界面却还停在「指定代理」上，所以要和「还没选」分开提示。
                    // 列表本身为空时判断不了是 id 失效还是列表没拉到，交给下面的空态文案。
                    val proxyPinnedMissing = proxyPinned &&
                        uiState.proxyPinnedId.isNotBlank() &&
                        uiState.proxyEntries.isNotEmpty() &&
                        uiState.proxyEntries.none { it.id == uiState.proxyPinnedId }
                    AppCard(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_proxy_title),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                            color = colorScheme.onBackground,
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.settings_proxy_summary),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                            color = colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.settings_proxy_mode_official),
                            selected = proxyMode == ProxyMode.OFFICIAL,
                            onClick = { actions.onSetProxyMode(ProxyMode.OFFICIAL.value) },
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.settings_proxy_mode_auto),
                            selected = proxyMode == ProxyMode.AUTO,
                            onClick = { actions.onSetProxyMode(ProxyMode.AUTO.value) },
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.settings_proxy_mode_pinned),
                            selected = proxyPinned,
                            onClick = { actions.onSetProxyMode(ProxyMode.PINNED.value) },
                        )
                        // 选了「指定代理」但还没选具体哪一条、或选的那条已经失效时，
                        // 说明现在实际走的是直连，别让人以为还在用某个代理。
                        if (proxyPinned && (uiState.proxyPinnedId.isBlank() || proxyPinnedMissing)) {
                            Text(
                                text = stringResource(
                                    if (proxyPinnedMissing) {
                                        R.string.settings_proxy_pinned_missing
                                    } else {
                                        R.string.settings_proxy_pinned_hint
                                    }
                                ),
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 2.dp,
                                    bottom = 6.dp,
                                ),
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            )
                        }
                        if (uiState.proxyEntries.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_proxy_empty),
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 2.dp,
                                    bottom = 6.dp,
                                ),
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            )
                        }
                        uiState.proxyEntries.forEach { entry ->
                            val health = proxyHealthById[entry.id]
                            RadioButtonPreference(
                                title = entry.name,
                                summary = entry.note.takeIf { it.isNotBlank() },
                                selected = entry.id == uiState.proxyPinnedId,
                                // 和 Material 版对齐：只有「指定代理」模式下点击才写入，避免别的模式下误改配置
                                onClick = { if (proxyPinned) actions.onSetProxyPinnedId(entry.id) },
                                // 只有「指定代理」模式下才允许选某一条
                                enabled = proxyPinned,
                                startAction = { MiuixProxyStatusDot(health, enabled = proxyPinned) },
                                endActions = {
                                    Text(
                                        text = miuixProxyLatencyLabel(health),
                                        // 行的可交互性跟着模式走，禁用时延迟文字一起降透明度；
                                        // 这里不依赖库里的 LocalContentColor，改动只在本地可见。
                                        color = colorScheme.onSurfaceVariantSummary.copy(
                                            alpha = if (proxyPinned) 1f else 0.38f
                                        ),
                                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                    )
                                },
                            )
                        }
                        ArrowPreference(
                            title = stringResource(
                                if (uiState.proxyTesting) {
                                    R.string.settings_proxy_testing
                                } else {
                                    R.string.settings_proxy_test_all
                                }
                            ),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_proxy_test_all),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            endActions = {
                                if (uiState.proxyTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(start = 4.dp),
                                        size = 16.dp,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            },
                            onClick = actions.onTestProxies,
                            // 测速中禁用，避免重复点出好几轮并发探测
                            enabled = !uiState.proxyTesting,
                        )
                    }
                }
                item {
                    KsuIsValid {
                        AppCard(
                            modifier = Modifier
                                .bringIntoViewRequester(requester(SettingsSection.PROFILE_TEMPLATES))
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            colors = backgroundAwareCardColors(),
                        ) {
                            val profileTemplate = customText(CustomTextKey.PAGE_TEMPLATE)
                            ArrowPreference(
                                title = profileTemplate,
                                summary = stringResource(id = R.string.settings_profile_template_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Description,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = profileTemplate,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = actions.onOpenProfileTemplate
                            )
                        }
                        val backupTitle = stringResource(id = R.string.backup_title)
                        AppCard(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            colors = backgroundAwareCardColors(),
                        ) {
                            ArrowPreference(
                                title = backupTitle,
                                summary = stringResource(id = R.string.backup_entry_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Backup,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = backupTitle,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = actions.onOpenBackup
                            )
                        }
                        AppCard(
                            modifier = Modifier
                                .bringIntoViewRequester(requester(SettingsSection.EXTENSIONS))
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            colors = backgroundAwareCardColors(),
                        ) {
                            val extensionsTitle = customText(CustomTextKey.PAGE_EXTENSIONS)
                            ArrowPreference(
                                title = extensionsTitle,
                                summary = stringResource(R.string.extension_section_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Memory,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = extensionsTitle,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = actions.onOpenExtensions
                            )
                        }
                        val toolsTitle = customText(CustomTextKey.PAGE_TOOLS)
                        AppCard(
                            modifier = Modifier
                                .bringIntoViewRequester(requester(SettingsSection.TOOLS))
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            colors = backgroundAwareCardColors(),
                        ) {
                            ArrowPreference(
                                title = toolsTitle,
                                summary = stringResource(id = R.string.settings_tools_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.DeveloperMode,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = toolsTitle,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = {
                                    actions.onOpenTools()
                                }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(requester(SettingsSection.TEMPORARY_ROOT))
                        ) {
                            TemporaryRootPreferences()
                        }
                    }

                    KsuIsValid {
                        val suCompatModeItems = listOf(
                            stringResource(id = R.string.settings_mode_enable_by_default),
                            stringResource(id = R.string.settings_mode_disable_until_reboot),
                            stringResource(id = R.string.settings_mode_disable_always),
                        )

                        AppCard(
                            modifier = Modifier
                                .bringIntoViewRequester(requester(SettingsSection.SECURITY))
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            colors = backgroundAwareCardColors(),
                        ) {
                            val suSummary = when (uiState.suCompatStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                "forced" -> stringResource(id = R.string.feature_status_forced_summary)
                                else -> stringResource(id = R.string.settings_sucompat_summary)
                            }
                            OverlayDropdownPreference(
                                title = customText(CustomTextKey.SETTING_SUCOMPAT),
                                summary = suSummary,
                                items = suCompatModeItems,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.AdminPanelSettings,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_SUCOMPAT),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = uiState.suCompatStatus == "supported",
                                selectedIndex = uiState.suCompatMode,
                                onSelectedIndexChange = actions.onSetSuCompatMode
                            )

                            val umountSummary = when (uiState.kernelUmountStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                "forced" -> stringResource(id = R.string.feature_status_forced_summary)
                                else -> stringResource(id = R.string.settings_kernel_umount_summary)
                            }
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_KERNEL_UMOUNT),
                                summary = umountSummary,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.LayersClear,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_KERNEL_UMOUNT),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = uiState.kernelUmountStatus == "supported",
                                checked = uiState.isKernelUmountEnabled,
                                onCheckedChange = actions.onSetKernelUmountEnabled
                            )

                            val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                "forced" -> stringResource(id = R.string.feature_status_forced_summary)
                                else -> stringResource(id = R.string.settings_selinux_hide_summary)
                            }
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_SELINUX_HIDE),
                                summary = selinuxHideSummary,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Security,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_SELINUX_HIDE),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = uiState.selinuxHideStatus == "supported",
                                checked = uiState.isSelinuxHideEnabled,
                                onCheckedChange = actions.onSetSelinuxHideEnabled
                            )

                            val avcSummary = when (uiState.avcSpoofStatus) {
                                "unsupported" -> stringResource(R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(R.string.feature_status_managed_summary)
                                "forced" -> stringResource(R.string.feature_status_forced_summary)
                                else -> stringResource(R.string.settings_avc_spoof_summary)
                            }
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_AVC_SPOOF),
                                summary = avcSummary,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Security,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_AVC_SPOOF),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = uiState.avcSpoofStatus == "supported",
                                checked = uiState.isAvcSpoofEnabled,
                                onCheckedChange = actions.onSetAvcSpoofEnabled
                            )

                            val mountHideSummary = when (uiState.mountHideStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                "forced" -> stringResource(id = R.string.feature_status_forced_summary)
                                else -> stringResource(id = R.string.settings_mount_hide_summary)
                            }
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_MOUNT_HIDE),
                                summary = mountHideSummary,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Policy,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_MOUNT_HIDE),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = uiState.mountHideStatus == "supported",
                                checked = uiState.isMountHideEnabled,
                                onCheckedChange = actions.onSetMountHideEnabled
                            )

                            val sulogSummary = when (uiState.sulogStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                "forced" -> stringResource(id = R.string.feature_status_forced_summary)
                                else -> stringResource(id = R.string.settings_sulog_summary)
                            }
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_SULOG),
                                summary = sulogSummary,
                                startAction = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Article,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_SULOG),
                                        tint = if (uiState.sulogStatus == "supported") colorScheme.onBackground else colorScheme.disabledOnSecondaryVariant
                                    )
                                },
                                enabled = uiState.sulogStatus == "supported",
                                checked = uiState.isSulogEnabled,
                                onCheckedChange = actions.onSetSulogEnabled
                            )

                            val adbRootSummary = when (uiState.adbRootStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                "forced" -> stringResource(id = R.string.feature_status_forced_summary)
                                else -> stringResource(id = R.string.settings_adb_root_summary)
                            }
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_ADB_ROOT),
                                summary = adbRootSummary,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Adb,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_ADB_ROOT),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                enabled = uiState.adbRootStatus == "supported",
                                checked = uiState.isAdbRootEnabled,
                                onCheckedChange = actions.onSetAdbRootEnabled
                            )

                            val shellSuAdbOnlySummary = when (uiState.shellSuAdbOnlyStatus) {
                                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                                "forced" -> stringResource(id = R.string.feature_status_forced_summary)
                                else -> stringResource(id = R.string.settings_shell_su_adb_only_summary)
                            }
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_SHELL_SU),
                                summary = shellSuAdbOnlySummary,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.VerifiedUser,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_SHELL_SU),
                                        tint = if (uiState.shellSuAdbOnlyStatus == "supported") colorScheme.onBackground else colorScheme.disabledOnSecondaryVariant
                                    )
                                },
                                enabled = uiState.shellSuAdbOnlyStatus == "supported",
                                checked = uiState.isShellSuAdbOnlyEnabled,
                                onCheckedChange = actions.onSetShellSuAdbOnlyEnabled
                            )
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_SOFT_REBOOT),
                                summary = stringResource(id = R.string.settings_soft_reboot_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.RestartAlt,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_SOFT_REBOOT),
                                        tint = if (uiState.isLateLoadMode) colorScheme.disabledOnSecondaryVariant else colorScheme.onBackground
                                    )
                                },
                                enabled = !uiState.isLateLoadMode,
                                checked = uiState.isLateLoadMode || uiState.useSoftReboot,
                                onCheckedChange = actions.onSetUseSoftReboot
                            )
                        }

                        AppCard(
                            modifier = Modifier
                                .bringIntoViewRequester(requester(SettingsSection.MODULE_BEHAVIOUR))
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            colors = backgroundAwareCardColors(),
                        ) {
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_UMOUNT_MODULES_DEFAULT),
                                summary = stringResource(id = R.string.settings_umount_modules_default_summary),
                                startAction = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Rule,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_UMOUNT_MODULES_DEFAULT),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = uiState.isDefaultUmountModules,
                                onCheckedChange = actions.onSetDefaultUmountModules
                            )

                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_AUTO_DELETE_MODULE_ZIP),
                                summary = stringResource(id = R.string.settings_auto_delete_module_zip_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_AUTO_DELETE_MODULE_ZIP),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = uiState.autoDeleteModuleZip,
                                onCheckedChange = actions.onSetAutoDeleteModuleZip
                            )
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_WEB_DEBUGGING),
                                summary = stringResource(id = R.string.enable_web_debugging_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.DeveloperMode,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_WEB_DEBUGGING),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = uiState.enableWebDebugging,
                                onCheckedChange = actions.onSetEnableWebDebugging
                            )
                            SwitchPreference(
                                title = customText(CustomTextKey.SETTING_AUTO_JAILBREAK),
                                summary = stringResource(id = R.string.settings_auto_jailbreak_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.FlashOn,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = customText(CustomTextKey.SETTING_AUTO_JAILBREAK),
                                        tint = if (uiState.isLateLoadMode) colorScheme.onBackground else colorScheme.disabledOnSecondaryVariant
                                    )
                                },
                                enabled = uiState.isLateLoadMode,
                                checked = uiState.autoJailbreak,
                                onCheckedChange = actions.onSetAutoJailbreak
                            )
                        }
                    }

                    if (uiState.isLkmMode) {
                        AppCard(
                            modifier = Modifier
                                .bringIntoViewRequester(requester(SettingsSection.UNINSTALL))
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            colors = backgroundAwareCardColors(),
                        ) {
                            val uninstall = customText(CustomTextKey.SETTING_UNINSTALL)
                            ArrowPreference(
                                title = uninstall,
                                enabled = !uiState.isLateLoadMode,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = uninstall,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                onClick = { showUninstallDialog.value = true },
                            )
                            UninstallDialog(
                                show = showUninstallDialog.value,
                                onDismissRequest = { showUninstallDialog.value = false }
                            )
                        }
                    }

                    AppCard(
                        modifier = Modifier
                            .bringIntoViewRequester(requester(SettingsSection.DIAGNOSTICS))
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        ArrowPreference(
                            title = customText(CustomTextKey.SETTING_SEND_LOG),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = customText(CustomTextKey.SETTING_SEND_LOG),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showSendLogDialog.value = true },
                        )
                        SendLogDialog(
                            show = showSendLogDialog.value,
                            onDismissRequest = { showSendLogDialog.value = false },
                            loadingDialog = loadingDialog
                        )
                        val about = customText(CustomTextKey.PAGE_ABOUT)
                        ArrowPreference(
                            title = about,
                            startAction = {
                                Icon(
                                    Icons.Rounded.Info,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = about,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenAbout,
                        )
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

/** 延迟文案：未测 / 不可用 / 具体毫秒，避免把内部值（-1）直接显示出来。 */
@Composable
private fun miuixProxyLatencyLabel(health: ProxyHealth?): String = when {
    health == null -> stringResource(R.string.settings_proxy_untested)
    !health.reachable -> stringResource(R.string.settings_proxy_unavailable)
    else -> stringResource(R.string.settings_proxy_latency, health.latencyMs)
}

/**
 * 状态点：绿=可连通、红=不可用、灰=未测。文案已经说了状态，这里只补一个一眼可见的颜色。
 * 行不可交互（非「指定代理」模式）时整体降透明度，和 Material 版保持一致。
 */
@Composable
private fun MiuixProxyStatusDot(health: ProxyHealth?, enabled: Boolean = true) {
    val color = when {
        health == null -> colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
        health.reachable -> MiuixProxyReachableGreen
        else -> MiuixProxyUnreachableRed
    }
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.38f))
    )
}

/** Miuix 配色里没有稳定的成功/错误语义色，测速结果又与主题无关，直接用固定色。 */
private val MiuixProxyReachableGreen = Color(0xFF2E7D32)
private val MiuixProxyUnreachableRed = Color(0xFFB3261E)
