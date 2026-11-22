package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.proxy.ProxyHealth
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.Storefront
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.IconTile
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedDropdownItem
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.data.repository.ModuleContributionPrefs
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.LocaleHelper
import com.sukisu.ultra.ui.util.customText
import com.sukisu.ultra.ui.viewmodel.MainPageRequest
import kotlinx.coroutines.delay

/**
 * 设置一级页（Material 3）。
 *
 * 结构只剩两块：外观与语言（4 行 inline，不跳页）与三个分组入口。
 * 分组内容见 [ModuleSettingsMaterial]、[RootSecurityMaterial]、[MaintenanceMaterial]。
 *
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
    sectionRequest: MainPageRequest? = null,
    onSectionConsumed: (Long) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val anchorRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }

    // 只有落在一级页的锚点会走到这里（二级页在 SettingPager 里就被导航掉了）。
    LaunchedEffect(sectionRequest?.token) {
        val request = sectionRequest ?: return@LaunchedEffect
        delay(120)
        val anchor = SettingsSection.normalize(request.section)
        // cardFor 兜底到 APPEARANCE（一级页唯一既有外观卡），没有锚点就什么都不滚
        val requester = anchorRequesters[SettingsSection.cardFor(anchor)]
        requester?.bringIntoView()
        onSectionConsumed(request.token)
    }

    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    ExpressiveScaffold(
        containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopBar(scrollBehavior = scrollBehavior, onSearch = actions.onOpenSearch)
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsAnchor(SettingsSection.APPEARANCE, anchorRequesters) {
                AppearanceColumn(uiState = uiState, actions = actions, registry = anchorRequesters)
            }
            ModuleUploadColumn(actions = actions, registry = anchorRequesters)
            GroupEntryColumn(actions = actions, registry = anchorRequesters)
            AboutColumn(actions = actions, registry = anchorRequesters)
            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
    }
}

/** 外观与语言：4 行都是 inline 操作，直接在一级页改完，不跳页。 */
@Composable
private fun AppearanceColumn(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    registry: MutableMap<String, BringIntoViewRequester>,
) {
    // 四行各自注册行级 requester：搜索/AI 跳到「语言」时只滚到那一行，而不是整张卡
    fun requester(anchor: String): BringIntoViewRequester =
        registry.getOrPut(anchor) { BringIntoViewRequester() }

    val languageSystemLabel = stringResource(id = R.string.settings_language_system)
    val languageTags = remember { listOf(LocaleHelper.SYSTEM) + LocaleHelper.SUPPORTED_TAGS }
    val languageNames = remember(languageSystemLabel) {
        languageTags.map { if (it.isEmpty()) languageSystemLabel else LocaleHelper.displayName(it) }
    }
    val themeTitle = customText(CustomTextKey.PAGE_APPEARANCE)
    val themeStoreTitle = customText(CustomTextKey.PAGE_THEME_STORE)

    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = listOf(
            // 下拉项没有 modifier 参数，用 SettingsAnchor 包一层挂行级锚点
            {
                SettingsAnchor(SettingsSection.UI_MODE, registry) {
                    SegmentedDropdownItem(
                        icon = Icons.Filled.DisplaySettings,
                        title = customText(CustomTextKey.SETTING_UI_MODE),
                        summary = stringResource(id = R.string.settings_ui_mode_summary),
                        items = UiMode.entries.map { it.name },
                        selectedIndex = if (uiState.uiMode == UiMode.Material.value) 1 else 0,
                        onItemSelected = actions.onSetUiModeIndex
                    )
                }
            },
            {
                SettingsAnchor(SettingsSection.LANGUAGE, registry) {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Language,
                        title = customText(CustomTextKey.SETTING_LANGUAGE),
                        summary = stringResource(id = R.string.settings_language_summary),
                        items = languageNames,
                        selectedIndex = languageTags.indexOf(uiState.appLanguage).coerceAtLeast(0),
                        onItemSelected = { index -> actions.onSetLanguage(languageTags[index]) }
                    )
                }
            },
            {
                SegmentedListItem(
                    modifier = Modifier.bringIntoViewRequester(requester(SettingsSection.THEME)),
                    onClick = actions.onOpenTheme,
                    headlineContent = { Text(themeTitle) },
                    supportingContent = { Text(stringResource(id = R.string.settings_theme_summary)) },
                    leadingContent = { IconTile(Icons.Filled.Palette, themeTitle) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                )
            },
            {
                SegmentedListItem(
                    modifier = Modifier.bringIntoViewRequester(requester(SettingsSection.THEME_STORE)),
                    onClick = actions.onOpenThemeStore,
                    headlineContent = { Text(themeStoreTitle) },
                    supportingContent = { Text(stringResource(id = R.string.theme_store_summary)) },
                    leadingContent = { IconTile(Icons.Filled.Storefront, themeStoreTitle) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                )
            }
        )
    )
}

/**
 * 三个分组入口。一级页只保留这一层索引，具体开关都沉到二级页，
 * 这样一级页永远是一屏能看完的长度。
 */
@Composable
private fun GroupEntryColumn(
    actions: SettingsScreenActions,
    registry: MutableMap<String, BringIntoViewRequester>,
) {
    fun entry(
        anchor: String,
        title: String,
        summary: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onClick: () -> Unit
    ): @Composable () -> Unit = {
        SegmentedListItem(
            // 三个入口各挂行级锚点：搜索/AI 跳到「维护」时滚到这一行，而不是整张卡
            modifier = Modifier.bringIntoViewRequester(
                registry.getOrPut(anchor) { BringIntoViewRequester() },
            ),
            onClick = onClick,
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            leadingContent = { IconTile(icon, title) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
        )
    }

    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = listOf(
            entry(
                anchor = SettingsSection.MODULE_SETTINGS_ENTRY,
                title = stringResource(R.string.settings_group_module),
                summary = stringResource(R.string.settings_group_module_summary),
                icon = Icons.Filled.Extension,
                onClick = actions.onOpenModuleSettings
            ),
            entry(
                anchor = SettingsSection.ROOT_SECURITY_ENTRY,
                title = stringResource(R.string.settings_group_root_security),
                summary = stringResource(R.string.settings_group_root_security_summary),
                icon = Icons.Filled.Security,
                onClick = actions.onOpenRootSecurity
            ),
            entry(
                anchor = SettingsSection.MAINTENANCE_ENTRY,
                title = stringResource(R.string.settings_group_maintenance),
                summary = stringResource(R.string.settings_group_maintenance_summary),
                icon = Icons.Filled.BugReport,
                onClick = actions.onOpenMaintenance
            )
        )
    )
}

/**
 * 模块投稿：单独一张卡，压在「模块与扩展」分组上面。
 * 二级页里已删掉这一行，避免同一条目两处入口；投稿被暂停时整卡隐藏。
 */
@Composable
private fun ModuleUploadColumn(
    actions: SettingsScreenActions,
    registry: MutableMap<String, BringIntoViewRequester>,
) {
    if (ModuleContributionPrefs.SUSPENDED) return
    val uploadTitle = stringResource(R.string.module_upload_entry_title)
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = listOf {
            SettingsAnchor(SettingsSection.MODULE_UPLOAD, registry) {
                SegmentedListItem(
                    onClick = actions.onOpenModuleUpload,
                    headlineContent = { Text(uploadTitle) },
                    supportingContent = {
                        Text(stringResource(R.string.module_upload_entry_summary))
                    },
                    leadingContent = { IconTile(Icons.Filled.Publish, uploadTitle) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                )
            }
        }
    )
}

/** 关于：单独一张卡放在一级页最末，跟其它分组区分开。 */
@Composable
private fun AboutColumn(
    actions: SettingsScreenActions,
    registry: MutableMap<String, BringIntoViewRequester>,
) {
    val aboutTitle = customText(CustomTextKey.PAGE_ABOUT)
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = listOf {
            SettingsAnchor(SettingsSection.ABOUT, registry) {
                SegmentedListItem(
                    onClick = actions.onOpenAbout,
                    headlineContent = { Text(aboutTitle) },
                    leadingContent = { IconTile(Icons.Filled.Info, aboutTitle) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                )
            }
        }
    )
}

@Composable
private fun SettingsAnchor(
    anchor: String,
    registry: MutableMap<String, BringIntoViewRequester>,
    content: @Composable () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    SideEffect { registry[anchor] = requester }
    Box(modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
        content()
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onSearch: () -> Unit,
) {
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    LargeFlexibleTopAppBar(
        title = { Text(customText(CustomTextKey.PAGE_SETTINGS)) },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.settings_search_title)
                )
            }
        },
        colors = expressiveTopAppBarColors(
            containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

/** 延迟文案：未测 / 不可用 / 具体毫秒三种情况分开，避免把内部值 -1 ms 直接显示出来。 */
@Composable
internal fun proxyLatencyLabel(health: ProxyHealth?): String = when {
    health == null -> stringResource(R.string.settings_proxy_untested)
    !health.reachable -> stringResource(R.string.settings_proxy_unavailable)
    else -> stringResource(R.string.settings_proxy_latency, health.latencyMs)
}

/**
 * 状态点：绿=可连通、红=不可用、灰=未测。文案已经说了状态，这里只补一个一眼可见的颜色。
 * 行不可交互（非「指定代理」模式）时整体降透明度，跟 Material3 的 disabled 令牌对齐。
 */
@Composable
internal fun ProxyStatusDot(health: ProxyHealth?, enabled: Boolean = true) {
    val color = when {
        health == null -> MaterialTheme.colorScheme.outline
        health.reachable -> ProxyReachableGreen
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.38f))
    )
}

/** Material3 配色里没有「成功」语义色，测速结果又与主题无关，用一个固定的绿。 */
private val ProxyReachableGreen = Color(0xFF2E7D32)
