package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.backgroundAwareColor

/**
 * 设置搜索页的 Material 3 皮肤。
 *
 * 索引数据由 [SettingsSearchIndex] 提供，这里只做过滤和展示；关键字状态留在本页内部，
 * 免得每次输入都把整张设置页重组一遍。
 *
 * 结果排版按用户定的形态：同一个一级页分组的命中项合成一张卡片，组内行之间用 0.5dp
 * 分隔线（模块页那条分隔线的同款厚度），组与组之间是不同卡片。
 */
@Composable
internal fun SettingsSearchMaterial(
    entries: List<SettingsSearchEntry>,
    onBack: () -> Unit,
    onPick: (SettingsSearchEntry) -> Unit,
) {
    // 关键字跟着页面走：转屏、切深浅色、返回后重进都不该丢
    var query by rememberSaveable { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val keyword = query.trim()
    // 每次重组重算过滤结果：索引只有几十项，比缓存一张会随语言变化失效的表更省心
    val matched = matchedSettingsEntries(entries, keyword)
    // 命中项按一级页分组；组内保持索引里的原始顺序
    val groups = matched.groupBy { it.groupRes }
    // 分组名长短不一，直接排会让右侧设置项左起点参差；这里量出最长的那个，
    // 所有卡片共用同一列宽，标题就落在同一条竖线上。
    // 量算范围取全量索引而不是当前命中结果，否则边打字边变列宽，标题会横向跳
    val labelStyle = MaterialTheme.typography.labelMedium
    val groupLabels = entries.map { it.groupRes }.distinct().associateWith { stringResource(it) }
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

    ExpressiveScaffold(
        containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        // 顶栏跟设置页同一套：大标题随滚动收成小标题，不要用单行的 TopAppBar，
        // 否则两个页面的标题行为对不上
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_search_title)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(
                    containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            // 搜索框吸顶：往下滚列表时它一直贴在顶栏下方，
            // 底色必须不透明，否则卡片会从输入框里透上来
            stickyHeader(key = "search-field", contentType = "search-field") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            // 吸顶块必须不透明：自定义背景下用近乎不透明的底色，
                            // 否则下面的卡片会从输入框里透上来
                            if (hasCustomBackground) {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ),
                ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        // 只有输入过才给清空按钮，空搜索框右侧不挂无效图标
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                )
                }
            }
            if (matched.isEmpty()) {
                // 一条都不命中：明确给一句空态，别留一片空白让人以为是没加载完
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = stringResource(R.string.settings_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                    )
                }
            } else {
                groups.forEach { (groupRes, groupEntries) ->
                    item(key = "group-" + groupRes, contentType = "group") {
                        // 底色跟一级页的分段列表一致：surfaceBright 过背景图适配
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = MaterialTheme.shapes.large,
                            color = backgroundAwareColor(MaterialTheme.colorScheme.surfaceBright),
                        ) {
                            Column {
                                groupEntries.forEachIndexed { index, entry ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                    SettingsSearchRowMaterial(
                                        groupRes = groupRes,
                                        groupLabel = groupLabels.getValue(groupRes),
                                        labelWidth = labelColumnWidth,
                                        entry = entry,
                                        onPick = onPick,
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "tail") {
                    Spacer(modifier = Modifier.height(12.dp).navigationBarsPadding())
                }
            }
        }
    }
}

/**
 * 一行搜索结果：卡片内部的一行，标题 + 右箭头，整行可点。
 *
 * 不用 [ListItem]：它的圆角与内边距是给「每条一张卡」准备的，塞进分组卡片里会在
 * 行与行之间留出奇怪的圆角缺口。
 */
@Composable
private fun SettingsSearchRowMaterial(
    groupRes: Int,
    groupLabel: String,
    labelWidth: Dp,
    entry: SettingsSearchEntry,
    onPick: (SettingsSearchEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(entry) }
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 分组标题不再挂在卡片上方，改为每行左侧的小字标签；
        // 列宽由调用方按最长标签统一给出，标签在列里居中，右侧标题左起点才一致
        Text(
            text = groupLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(labelWidth),
        )
        Text(
            text = stringResource(entry.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun matchedSettingsEntries(
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
