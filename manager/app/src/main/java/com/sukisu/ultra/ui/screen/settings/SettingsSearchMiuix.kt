package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 设置搜索页的 miuix 皮肤。
 *
 * 骨架与设置页其它兄弟页一致：AppScaffold + BlurredBar + TopAppBar，列表接
 * scrollBehavior 的 nestedScroll，自定义背景时整页透明并把背景铺在列表底下。
 *
 * 结果排版按用户定的形态：同一个一级页分组的命中项合成一张 AppCard，组内行之间用
 * 0.5dp 分隔线（与模块页「模块信息 / 操作按钮」之间的分隔线同款），组与组之间是不同
 * 卡片。这里刻意在 item 里一次组合多行卡片 —— AGENTS 第 9 条针对的是长列表，而本页
 * 是静态索引（31 项、单卡最多 14 行），分组卡片才是要的视觉。
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
    // 跟设置页一级页同一套判断：开了背景模糊或自定义背景时顶栏透明交给 BlurredBar，
    // 其余用不透明 surface，免得滚动内容露到标题底下
    val barColor = if (backdrop != null || hasCustomBackground) Color.Transparent else colorScheme.surface
    val keyword = query.trim()
    // 每次重组重算过滤结果：索引只有几十项，比缓存一张会随语言变化失效的表更省心
    val matched = matchedSettingsEntriesMiuix(entries, keyword)
    // 命中项按一级页分组；组内保持索引里的原始顺序
    val groups = matched.groupBy { it.groupRes }
    // 分组名长短不一，直接排会让右侧设置项左起点参差；这里量出最长的那个，
    // 所有卡片共用同一列宽，标题就落在同一条竖线上。
    // 量算范围取全量索引而不是当前命中结果，否则边打字边变列宽，标题会横向跳
    val groupLabels = entries.map { it.groupRes }.distinct().associateWith { stringResource(it) }
    val labelStyle = MiuixTheme.textStyles.body2
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelColumnWidth: Dp = remember(groupLabels, labelStyle, density, textMeasurer) {
        val maxPx = groupLabels.values.maxOfOrNull {
            textMeasurer.measure(AnnotatedString(it), labelStyle).size.width
        } ?: 0
        // +2px 余量防止取整后把最长的那个标签挤掉最后一个字形；
        // 再留 20dp 白：标签在「卡片左边框 → 标题」这段空间里居中，两侧各 10dp
        // 上限 96dp：长分组名（德/葡语）不至于把标题挤成一条缝
        (with(density) { (maxPx + 2).toDp() } + 20.dp).coerceAtMost(96.dp)
    }

    AppScaffold(
        containerColor = if (hasCustomBackground) Color.Transparent else colorScheme.surface,
        topBar = {
            BlurredBar(backdrop = backdrop, scrollBehavior = scrollBehavior) {
                // 搜索框必须常驻顶栏：miuix 的列表是往顶栏底下滚的，
                // 用 LazyColumn 的 stickyHeader 会被折叠后的顶栏盖住
                Column {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    SettingsSearchFieldMiuix(query = query, onQueryChange = { query = it })
                }
                }
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
                    .padding(horizontal = 12.dp)
                    .imePadding(),
                // 顶栏底下那部分高度由 innerPadding 让出来，列表不会钻到胶囊下面
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                if (matched.isEmpty()) {
                    // 一条都不命中：明确给一句空态，别留一片空白让人以为是没加载完
                    item(key = "empty") {
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
                } else {
                    groups.forEach { (groupRes, groupEntries) ->
                        item(key = "group-" + groupRes) {
                            // 分组标题不再挂在卡片上方，改为每行左侧的小字标签
                            val groupLabel = groupLabels.getValue(groupRes)
                            AppCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = backgroundAwareCardColors(),
                            ) {
                                groupEntries.forEachIndexed { index, entry ->
                                    if (index > 0) {
                                        // 模块页同款分隔线：0.5dp + outline 半透明
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            thickness = 0.5.dp,
                                            color = colorScheme.outline.copy(alpha = 0.5f),
                                        )
                                    }
                                    BasicComponent(
                                        title = stringResource(entry.titleRes),
                                        startAction = {
                                            // 各分组名字长短不一，统一放到等宽列里居中，
                                            // 右侧标题的左起点才不会跟着分组名抖
                                            Box(
                                                modifier = Modifier.width(labelColumnWidth),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = groupLabel,
                                                    style = labelStyle,
                                                    color = colorScheme.onSurfaceVariantSummary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        },
                                        onClick = { onPick(entry) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "tail") { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

/**
 * 页面内搜索框：照抄 SuperSearchBar 里 SearchBar 的视觉（44dp 图标位、CircleShape 胶囊、
 * surfaceContainerHigh 底色），只是底色跟着背景图走、并去掉了 popup 展开态。
 *
 * 横向不加内边距：外层 LazyColumn 已经有 12dp，胶囊左边正好和下面的卡片对齐。
 */
@Composable
private fun SettingsSearchFieldMiuix(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { focusManager.clearFocus() },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp)
            .heightIn(min = 45.dp)
            .background(backgroundAwareColor(colorScheme.surfaceContainerHigh), CircleShape),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .padding(start = 16.dp, end = 8.dp),
                    tint = colorScheme.onSurfaceContainerHigh,
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_hint),
                            style = MiuixTheme.textStyles.body1,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(start = 8.dp, end = 16.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null,
                            ) { onQueryChange("") },
                    )
                }
            }
        },
    )
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
