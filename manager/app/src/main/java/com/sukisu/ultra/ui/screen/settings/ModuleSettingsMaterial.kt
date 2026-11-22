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
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.ModuleContributionPrefs
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.IconTile
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.SegmentedSwitchItem
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.backgroundAwareColor
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.customText
import kotlinx.coroutines.delay

/** 二级页卡片的统一外边距，与拆分前的一级设置页保持一致。 */
private val CardPadding = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)

/**
 * 「模块与扩展」二级页（Material 3 皮肤）。
 *
 * 从设置一级页拆出来的 8 行：模块投稿、安装后提示分享、App Profile 模板、拓展功能、工具、
 * 默认卸载模块、安装后删除模块压缩包、更新后不保留安装包。行为与拆分前完全一致：
 * 投稿与分享不受 [KsuIsValid] 限制（没 root 也要能进、能关），其余行依赖管理器身份。
 *
 * 每行都套了 [AnchorHighlight]：设置搜索或 AI 助手带着 [highlightAnchor] 进来时，
 * 该行滚进视野并高亮 2 秒，随后回调 [onHighlightConsumed]。
 */
@Composable
internal fun ModuleSettingsMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    onBack: () -> Unit,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_group_module)) },
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
            // 投稿功能风险未解决暂时下线（ModuleContributionPrefs.SUSPENDED），整块一起隐藏。
            // 这两行不是内核能力，所以不挂在 KsuIsValid 里：没 root 也要能看到「安装后提示分享」。
            if (!ModuleContributionPrefs.SUSPENDED) {
                SegmentedColumn(
                    modifier = CardPadding,
                    content = listOf(
                        {
                            AnchorHighlight(
                                anchor = SettingsSection.MODULE_SHARE,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                // 只控制装完本地模块后要不要提示分享；上传入口在上面那一行。
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Share,
                                    title = stringResource(R.string.module_upload_after_install_title),
                                    summary = stringResource(R.string.module_upload_after_install_summary),
                                    colors = rowColors(highlighted),
                                    checked = uiState.moduleContributionEnabled,
                                    onCheckedChange = actions.onSetModuleContributionEnabled,
                                )
                            }
                        },
                    ),
                )
            }

            KsuIsValid {
                // App Profile 模板
                SegmentedColumn(
                    modifier = CardPadding,
                    content = listOf {
                        AnchorHighlight(
                            anchor = SettingsSection.PROFILE_TEMPLATES,
                            highlightAnchor = highlightAnchor,
                            onHighlightConsumed = onHighlightConsumed,
                        ) { highlighted ->
                            val profileTemplate = customText(CustomTextKey.PAGE_TEMPLATE)
                            SegmentedListItem(
                                onClick = actions.onOpenProfileTemplate,
                                colors = rowColors(highlighted),
                                headlineContent = { Text(profileTemplate) },
                                supportingContent = {
                                    Text(stringResource(R.string.settings_profile_template_summary))
                                },
                                leadingContent = { IconTile(Icons.Filled.Description, profileTemplate) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                },
                            )
                        }
                    },
                )

                // 拓展功能
                SegmentedColumn(
                    modifier = CardPadding,
                    content = listOf {
                        AnchorHighlight(
                            anchor = SettingsSection.EXTENSIONS,
                            highlightAnchor = highlightAnchor,
                            onHighlightConsumed = onHighlightConsumed,
                        ) { highlighted ->
                            val extensionsTitle = customText(CustomTextKey.PAGE_EXTENSIONS)
                            SegmentedListItem(
                                onClick = actions.onOpenExtensions,
                                colors = rowColors(highlighted),
                                headlineContent = { Text(extensionsTitle) },
                                supportingContent = {
                                    Text(stringResource(R.string.extension_section_summary))
                                },
                                leadingContent = { IconTile(Icons.Filled.Memory, extensionsTitle) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                },
                            )
                        }
                    },
                )

                // 工具
                SegmentedColumn(
                    modifier = CardPadding,
                    content = listOf {
                        AnchorHighlight(
                            anchor = SettingsSection.TOOLS,
                            highlightAnchor = highlightAnchor,
                            onHighlightConsumed = onHighlightConsumed,
                        ) { highlighted ->
                            val toolsTitle = customText(CustomTextKey.PAGE_TOOLS)
                            SegmentedListItem(
                                onClick = actions.onOpenTools,
                                colors = rowColors(highlighted),
                                headlineContent = { Text(toolsTitle) },
                                supportingContent = {
                                    Text(stringResource(R.string.settings_tools_summary))
                                },
                                leadingContent = { IconTile(Icons.Filled.Fence, toolsTitle) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                },
                            )
                        }
                    },
                )

                // 模块行为：默认卸载、装完删包、更新后删安装包。
                // 这三行原先和 WebView 调试 / 自动越狱挤在同一张卡里，拆页后按归属重新分组。
                SegmentedColumn(
                    modifier = CardPadding,
                    content = listOf(
                        {
                            AnchorHighlight(
                                anchor = SettingsSection.DEFAULT_UMOUNT_MODULES,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                SegmentedSwitchItem(
                                    icon = Icons.AutoMirrored.Filled.Rule,
                                    title = customText(CustomTextKey.SETTING_UMOUNT_MODULES_DEFAULT),
                                    summary = stringResource(R.string.settings_umount_modules_default_summary),
                                    colors = rowColors(highlighted),
                                    checked = uiState.isDefaultUmountModules,
                                    onCheckedChange = actions.onSetDefaultUmountModules,
                                )
                            }
                        },
                        {
                            AnchorHighlight(
                                anchor = SettingsSection.AUTO_DELETE_MODULE_ZIP,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Delete,
                                    title = customText(CustomTextKey.SETTING_AUTO_DELETE_MODULE_ZIP),
                                    summary = stringResource(R.string.settings_auto_delete_module_zip_summary),
                                    colors = rowColors(highlighted),
                                    checked = uiState.autoDeleteModuleZip,
                                    onCheckedChange = actions.onSetAutoDeleteModuleZip,
                                )
                            }
                        },
                        {
                            AnchorHighlight(
                                anchor = SettingsSection.AUTO_DELETE_UPDATE_APK,
                                highlightAnchor = highlightAnchor,
                                onHighlightConsumed = onHighlightConsumed,
                            ) { highlighted ->
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Delete,
                                    title = customText(CustomTextKey.SETTING_AUTO_DELETE_UPDATE_APK),
                                    summary = stringResource(R.string.settings_auto_delete_update_apk_summary),
                                    colors = rowColors(highlighted),
                                    checked = uiState.autoDeleteUpdateApk,
                                    onCheckedChange = actions.onSetAutoDeleteUpdateApk,
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
