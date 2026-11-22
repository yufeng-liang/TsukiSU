package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath

/**
 * 设置搜索页的 Material 3 皮肤。
 *
 * 索引数据由 [SettingsSearchIndex] 提供，这里只做过滤和展示；关键字状态留在本页内部，
 * 免得每次输入都把整张设置页重组一遍。空关键字看全量并按一级页分组，有关键字时拍平成
 * 一个列表，每行左侧仍挂着来源分组标签，用户能看出命中项属于哪一页。
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

    ExpressiveScaffold(
        containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_search_title)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "search-field", contentType = "search-field") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
            when {
                // 一条都不命中：明确给一句空态，别留一片空白让人以为是没加载完
                matched.isEmpty() -> item(key = "empty", contentType = "empty") {
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
                // 空关键字：按一级页分组，组内保持索引里的原始顺序
                keyword.isEmpty() -> entries.groupBy { it.groupRes }.forEach { (groupRes, groupEntries) ->
                    item(key = "header-" + groupRes, contentType = "header") {
                        Text(
                            text = stringResource(groupRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(items = groupEntries, key = { entry -> "entry-" + entry.id }) { entry ->
                        SettingsSearchRowMaterial(entry = entry, onPick = onPick)
                    }
                }
                // 有关键字：拍平展示，分组标签改挂在每行左侧
                else -> items(items = matched, key = { entry -> "entry-" + entry.id }) { entry ->
                    SettingsSearchRowMaterial(entry = entry, onPick = onPick)
                }
            }
        }
    }
}

/**
 * 一行搜索结果：左侧是来源分组标签，右侧是设置项标题，整行可点。
 *
 * 直接用 [ListItem] 而不是自拼 Row：它的内边距、最小高度和无障碍语义都由 Material3
 * 处理好，配色也跟兄弟页面保持一致。
 */
@Composable
private fun SettingsSearchRowMaterial(
    entry: SettingsSearchEntry,
    onPick: (SettingsSearchEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(entry) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = stringResource(entry.groupRes),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(entry.titleRes),
            style = MaterialTheme.typography.bodyLarge,
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
