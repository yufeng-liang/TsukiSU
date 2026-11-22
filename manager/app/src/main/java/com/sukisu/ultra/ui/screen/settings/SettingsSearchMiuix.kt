package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.theme.backgroundAwareCardColors
import com.sukisu.ultra.ui.theme.backgroundAwareColor
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 设置搜索页的 miuix 皮肤。
 *
 * 骨架与设置页其它兄弟页一致：AppScaffold + BlurredBar + TopAppBar，列表接
 * scrollBehavior 的 nestedScroll，自定义背景时整页透明并把背景铺在列表底下。
 * 关键字状态留在本页内部（rememberSaveable），空关键字看全量并按一级页分组，
 * 有关键字时拍平，每行左侧仍挂着来源分组标签。
 */
@Composable
internal fun SettingsSearchMiuix(
    entries: List<SettingsSearchEntry>,
    onBack: () -> Unit,
    onPick: (SettingsSearchEntry) -> Unit,
) {
    // 关键字跟着页面走：转屏、切深浅色、返回后重进都不该丢
    var query by rememberSaveable { mutableStateOf("") }
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    // 有模糊背景或自定义背景时顶栏必须透明，否则会把背后的图切出一条硬边
    // 设备若不支持实时模糊，透明顶栏会把滚动内容露在标题底下；
    // 因此只在有自定义背景图（确实期望透明）时才透明，其余用不透明 surface
    val barColor = if (hasCustomBackground) Color.Transparent else colorScheme.surface
    val keyword = query.trim()
    // 每次重组重算过滤结果：索引只有几十项，比缓存一张会随语言变化失效的表更省心
    val matched = matchedSettingsEntriesMiuix(entries, keyword)
    // 清空按钮只在有输入时出现，空搜索框右侧不挂无效图标
    val clearAction: @Composable () -> Unit = {
        IconButton(onClick = { query = "" }) {
            Icon(
                imageVector = MiuixIcons.Basic.SearchCleanup,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariantSummary,
            )
        }
    }

    AppScaffold(
        containerColor = if (hasCustomBackground) Color.Transparent else colorScheme.surface,
        topBar = {
            BlurredBar(backdrop = backdrop, scrollBehavior = scrollBehavior) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings_search_title),
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
                item(key = "search-field") {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp),
                        label = stringResource(R.string.settings_search_hint),
                        // 搜索框没有浮动标签：提示文案一直当占位符用，输入后让位给关键字
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = backgroundAwareColor(colorScheme.secondaryContainer),
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = MiuixIcons.Basic.Search,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariantSummary,
                            )
                        },
                        trailingIcon = if (query.isEmpty()) null else clearAction,
                    )
                }
                when {
                    // 一条都不命中：明确给一句空态，别留一片空白让人以为是没加载完
                    matched.isEmpty() -> item(key = "empty") {
                        Text(
                            text = stringResource(R.string.settings_search_empty),
                            style = MiuixTheme.textStyles.body2,
                            color = colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 48.dp),
                        )
                    }
                    // 空关键字：按一级页分组。SmallTitle 自带上下留白，这里不再补 Spacer
                    keyword.isEmpty() -> entries.groupBy { it.groupRes }.forEach { (groupRes, groupEntries) ->
                        item(key = "header-" + groupRes) {
                            SmallTitle(text = stringResource(groupRes))
                        }
                        items(items = groupEntries, key = { entry -> "entry-" + entry.id }) { entry ->
                            SettingsSearchRowMiuix(entry = entry, onPick = onPick)
                        }
                    }
                    // 有关键字：拍平展示，分组标签改挂在每行左侧
                    else -> items(items = matched, key = { entry -> "entry-" + entry.id }) { entry ->
                        SettingsSearchRowMiuix(entry = entry, onPick = onPick)
                    }
                }
            }
        }
    }
}

/**
 * 一行搜索结果：整张卡片可点，内部是 miuix 的 [BasicComponent]，左侧挂来源分组标签。
 *
 * 每行单独一张卡片而不是把多行塞进同一张卡，是 AGENTS 第 9 条的要求：
 * item 里一次组合多行会让长列表掉帧。
 */
@Composable
private fun SettingsSearchRowMiuix(
    entry: SettingsSearchEntry,
    onPick: (SettingsSearchEntry) -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = backgroundAwareCardColors(),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = { onPick(entry) },
    ) {
        BasicComponent(
            title = stringResource(entry.titleRes),
            startAction = {
                Text(
                    text = stringResource(entry.groupRes),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
        )
    }
}

/**
 * 按关键字过滤索引项：标题或分组名任一命中就保留。
 *
 * 这里用 for 循环而不是 filter/map，是因为 stringResource 只能在 @Composable 作用域里
 * 调用，普通集合 lambda 里拿不到资源字符串。索引规模只有几十项，逐帧重算的代价可接受。
 */
@Composable
private fun matchedSettingsEntriesMiuix(
    entries: List<SettingsSearchEntry>,
    keyword: String,
): List<SettingsSearchEntry> {
    if (keyword.isEmpty()) return entries
    val matched = ArrayList<SettingsSearchEntry>(entries.size)
    for (entry in entries) {
        val title = stringResource(entry.titleRes)
        val group = stringResource(entry.groupRes)
        if (title.contains(keyword, ignoreCase = true) || group.contains(keyword, ignoreCase = true)) {
            matched.add(entry)
        }
    }
    return matched
}
