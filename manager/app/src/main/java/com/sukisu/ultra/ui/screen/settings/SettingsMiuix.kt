package com.sukisu.ultra.ui.screen.settings

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
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.ModuleContributionPrefs
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.Storefront
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.theme.backgroundAwareCardColors
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.LocaleHelper
import com.sukisu.ultra.ui.util.customText
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import com.sukisu.ultra.ui.viewmodel.MainPageRequest
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 设置一级页（miuix）。
 *
 * 与 Material 版结构一致：外观与语言 4 行 inline + 三个分组入口。
 * 分组内容见 ModuleSettingsMiuix、RootSecurityMiuix、MaintenanceMiuix。
 *
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

    // 只有落在一级页的锚点会走到这里（二级页在 SettingPager 里就被导航掉了）。
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
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val barColor = if (backdrop != null || hasCustomBackground) Color.Transparent else colorScheme.surface

    AppScaffold(
        containerColor = if (backdrop != null || hasCustomBackground) Color.Transparent else colorScheme.surface,
        topBar = {
            BlurredBar(backdrop = backdrop, scrollBehavior = scrollBehavior) {
                TopAppBar(
                    color = barColor,
                    title = customText(CustomTextKey.PAGE_SETTINGS),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = actions.onOpenSearch,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Basic.Search,
                                contentDescription = stringResource(R.string.settings_search_title),
                                tint = colorScheme.onBackground,
                            )
                        }
                    }
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
                            .bringIntoViewRequester(requester(SettingsSection.APPEARANCE))
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        AppearancePreferences(uiState = uiState, actions = actions)
                    }
                }
                item {
                    AppCard(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        GroupEntryPreferences(actions = actions)
                    }
                }
                item {
                    AppCard(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            // 两行各挂一个锚点，搜索跳过来时能滚到这张卡
                            .bringIntoViewRequester(requester(SettingsSection.MODULE_UPLOAD))
                            .bringIntoViewRequester(requester(SettingsSection.ABOUT)),
                        colors = backgroundAwareCardColors(),
                    ) {
                        ExtraEntryPreferences(actions = actions)
                    }
                }
                item { Spacer(Modifier.height(bottomInnerPadding)) }
            }
        }
    }
}

/** 外观与语言：4 行 inline，不跳页。 */
@Composable
private fun AppearancePreferences(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
) {
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
    val themeStoreTitle = customText(CustomTextKey.PAGE_THEME_STORE)
    ArrowPreference(
        title = themeStoreTitle,
        summary = stringResource(id = R.string.theme_store_summary),
        startAction = {
            Icon(
                Icons.Rounded.Storefront,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = themeStoreTitle,
                tint = colorScheme.onBackground
            )
        },
        onClick = actions.onOpenThemeStore
    )
}

/** 三个分组入口：一级页只做索引，具体开关都沉到二级页。 */
/**
 * 单独提到一级页的两行：模块投稿与关于。
 * 二级页里的对应行已删除，避免同一条目两处入口；投稿被暂停时只隐藏模块投稿。
 */
@Composable
private fun ExtraEntryPreferences(actions: SettingsScreenActions) {
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
            onClick = actions.onOpenModuleUpload
        )
    }
    val aboutTitle = customText(CustomTextKey.PAGE_ABOUT)
    ArrowPreference(
        title = aboutTitle,
        startAction = {
            Icon(
                Icons.Rounded.Info,
                modifier = Modifier.padding(end = 6.dp),
                contentDescription = aboutTitle,
                tint = colorScheme.onBackground
            )
        },
        onClick = actions.onOpenAbout
    )
}

@Composable
private fun GroupEntryPreferences(actions: SettingsScreenActions) {
    @Composable
    fun entry(
        title: String,
        summary: String,
        icon: ImageVector,
        onClick: () -> Unit,
    ) {
        ArrowPreference(
            title = title,
            summary = summary,
            startAction = {
                Icon(
                    icon,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = title,
                    tint = colorScheme.onBackground
                )
            },
            onClick = onClick
        )
    }

    entry(
        title = stringResource(R.string.settings_group_module),
        summary = stringResource(R.string.settings_group_module_summary),
        icon = Icons.Rounded.Extension,
        onClick = actions.onOpenModuleSettings
    )
    entry(
        title = stringResource(R.string.settings_group_root_security),
        summary = stringResource(R.string.settings_group_root_security_summary),
        icon = Icons.Rounded.Security,
        onClick = actions.onOpenRootSecurity
    )
    entry(
        title = stringResource(R.string.settings_group_maintenance),
        summary = stringResource(R.string.settings_group_maintenance_summary),
        icon = Icons.Rounded.BugReport,
        onClick = actions.onOpenMaintenance
    )
}
