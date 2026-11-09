package com.sukisu.ultra.ui.screen.modulerepo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.EvidenceFreshness
import com.sukisu.ultra.data.model.RepoColumn
import com.sukisu.ultra.data.model.RepoColumnEvidence
import com.sukisu.ultra.data.model.RepoColumnGuide
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnTopic
import com.sukisu.ultra.data.column.ColumnProgressEntry
import com.sukisu.ultra.data.column.ColumnProgressStore
import com.sukisu.ultra.data.model.freshness
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.TonalCard
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import com.sukisu.ultra.ui.viewmodel.ModuleRepoViewModel
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 商店列表头的专栏横滑区。
 *
 * 专栏是可选内容：拉不到就整块不显示，而不是在商店页上摆一句错误——模块列表本身不依赖它。
 */
@Composable
fun ModuleColumnStrip(
    columns: List<RepoColumn>,
    onOpenColumn: (RepoColumn) -> Unit,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    if (columns.isEmpty() && !failed) return
    // 左右边距由这里补，标题与卡片用同一个值，两行左边缘才对得齐、也不会贴到屏幕边：
    // Material 商店列表自己给了 16dp contentPadding，Miuix 列表不给外层内边距、相邻卡片各自留 12dp。
    val horizontalPadding = when (LocalUiMode.current) {
        UiMode.Miuix -> 12.dp
        UiMode.Material -> 0.dp
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (columns.isEmpty()) {
            // 拉失败时留一行出口：整块隐藏会让用户以为专栏没了（v4.1.4 的用户反馈就是这个）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.module_column_load_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.module_column_retry),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            return@Column
        }
        if (columns.size == 1) {
            // 只有一个专栏时横滑没有意义，卡片直接占满整行。
            ColumnEntryCard(
                column = columns.first(),
                onClick = { onOpenColumn(columns.first()) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(columns, key = { it.id }) { column ->
                    ColumnEntryCard(column = column, onClick = { onOpenColumn(column) })
                }
            }
        }
    }
}

@Composable
private fun ColumnEntryCard(column: RepoColumn, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 底色跟商店里其他卡片一致（TonalCard 默认 surfaceBright）：之前单独用 surfaceContainerHigh
    // 比酷安卡和模块卡都暗一档，同一个列表里显得像另一套设计。
    TonalCard(
        onClick = onClick,
        modifier = if (modifier != Modifier) modifier else Modifier.widthIn(max = 236.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ColumnIconBadge(columnGlyph(column.accent))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = column.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 标题下面这行原来是「N 个专题」，换成副标题：一行说清这栏是干什么的，
                    // 专题数量在详情页里看得到，入口卡上不重复占一行。
                    Text(
                        text = column.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 商店列表头的专栏横滑区（Miuix 版）。
 *
 * 结构与 Material 版 [ModuleColumnStrip] 一一对应：标题、加载失败出口、单卡铺满 / 多卡横滑。
 * 卡片、按钮、文字都换成 Miuix 自己那套，避免在 Miuix 主题里混进 Material 组件后回落成紫色。
 */
@Composable
fun ModuleColumnStripMiuix(
    columns: List<RepoColumn>,
    onOpenColumn: (RepoColumn) -> Unit,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    if (columns.isEmpty() && !failed) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 和 Miuix 商店列表的卡片边距对齐：列表本身不给外层内边距，卡片各自留 12dp。
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (columns.isEmpty()) {
            // 拉失败时留一行出口：整块隐藏会让用户以为专栏没了（v4.1.4 的用户反馈就是这个）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixText(
                    text = stringResource(R.string.module_column_load_failed),
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                MiuixTextButton(
                    text = stringResource(R.string.module_column_retry),
                    onClick = onRetry,
                    minWidth = 0.dp,
                    minHeight = 0.dp,
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            return@Column
        }
        if (columns.size == 1) {
            // 只有一个专栏时横滑没有意义，卡片直接占满整行。
            ColumnEntryCardMiuix(
                column = columns.first(),
                onClick = { onOpenColumn(columns.first()) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(columns, key = { it.id }) { column ->
                    ColumnEntryCardMiuix(column = column, onClick = { onOpenColumn(column) })
                }
            }
        }
    }
}

@Composable
private fun ColumnEntryCardMiuix(
    column: RepoColumn,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // insideMargin 就是 AppCard 自带的内边距，内容里不再叠一层 padding。
    AppCard(
        modifier = if (modifier != Modifier) modifier else Modifier.widthIn(max = 236.dp),
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ColumnIconBadgeMiuix(columnGlyph(column.accent))
            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = column.title,
                    color = colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 副标题一行说清这栏是干什么的，专题数量在详情页里看得到，入口卡上不重复占一行。
                MiuixText(
                    text = column.subtitle,
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 专栏图标块：primary 12% 的圆角方块里放一枚 primary 图标。
 *
 * 以前整块按档位染色（root 红 / hide 紫 / integrity 青），用户反馈和商店页其它 tag 风格不统一；
 * 现在颜色只留给引导卡的小圆点，图标块统一用应用主色。
 */
@Composable
private fun ColumnIconBadge(image: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * 专栏图标：语义只按 accent 近似推断。
 *
 * material-icons 是裁剪版（`manager/app/libs/material-icons-custom.jar`），
 * 只从确认存在的 outlined 集合里挑，别写没打进去的图标名。
 */
private fun columnGlyph(accent: String): ImageVector = when (accent) {
    "root" -> Icons.Outlined.Shield
    "hide" -> Icons.Outlined.VisibilityOff
    "integrity" -> Icons.Outlined.VerifiedUser
    "tool" -> Icons.Outlined.Build
    else -> Icons.Outlined.Shield
}

/** 专栏详情页：只读展示专题与步骤；想动手就走 [ColumnWizardScreen] 逐步执行。 */
@Composable
fun ModuleColumnScreen(
    columnId: String,
    onBack: () -> Unit,
    onOpenWizard: (RepoColumnTopic) -> Unit = {},
) {
    // Route 只带 id：整份 RepoColumn（含话题、证据、长文案）序列化进 Binder 有体积上限，
    // 超过就会在导航时抛 TransactionTooLargeException；详情页按 id 从仓库列表里取回。
    val column = rememberColumnById(columnId, onMissing = { onBack() })
    if (column == null) {
        ModuleColumnLoading(onBack = onBack)
        return
    }
    // 两套 UI 各自实现：Material 用 LargeFlexibleTopAppBar + TonalCard，Miuix 用
    // TopAppBar + AppCard/SmallTitle。把 Material 组件硬塞进 Miuix 主题会「两层皮」
    // （圆角、卡片底色、标题层级都对不上），所以 Miuix 侧单独写一份。
    when (LocalUiMode.current) {
        UiMode.Miuix -> ModuleColumnScreenMiuix(
            column = column,
            onBack = onBack,
            onOpenWizard = onOpenWizard,
        )

        UiMode.Material -> ModuleColumnScreenMaterial(
            column = column,
            onBack = onBack,
            onOpenWizard = onOpenWizard,
        )
    }
}

/**
 * 按 id 从仓库列表还原专栏。
 *
 * `refreshColumns` 失败是静默的，`columnsLoaded` 会一直是 false；这里最多等 4 秒，
 * 超时或列表加载完仍找不到就回调 [onMissing]，避免用户永远停在加载态。
 */
@Composable
internal fun rememberColumnById(columnId: String, onMissing: (String) -> Unit): RepoColumn? {
    val repoViewModel = viewModel<ModuleRepoViewModel>()
    val state by repoViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(columnId) { repoViewModel.refreshColumns() }
    val column = state.columns.firstOrNull { it.id == columnId }
    var missing by remember(columnId) { mutableStateOf(false) }
    LaunchedEffect(columnId, column, state.columnsLoaded) {
        if (column != null || missing) return@LaunchedEffect
        // 4 秒足够一趟慢网请求；再久就该退回商店，而不是让用户盯着一屏加载态。
        if (!state.columnsLoaded) delay(4_000)
        if (column == null && !missing) {
            missing = true
            Log.w(MODULE_COLUMN_TAG, "找不到专栏 $columnId（columnsLoaded=${state.columnsLoaded}），返回上一页")
            onMissing(columnId)
        }
    }
    return column
}

@Composable
internal fun ModuleColumnLoading(onBack: () -> Unit) {
    // 加载态也分两套：Miuix 下若还弹 Material 的骨架，返回瞬间会闪一下另一套主题的顶栏。
    when (LocalUiMode.current) {
        UiMode.Miuix -> ModuleColumnLoadingMiuix(onBack = onBack)
        UiMode.Material -> ModuleColumnLoadingMaterial(onBack = onBack)
    }
}

@Composable
private fun ModuleColumnLoadingMaterial(onBack: () -> Unit) {
    // 解析专栏 id 期间给一个明确的加载态，别让返回后的空白看起来像卡死。
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {},
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState()),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

/** 找不到专栏时的日志 tag。 */
private const val MODULE_COLUMN_TAG = "ModuleColumnScreen"

@Composable
private fun ModuleColumnScreenMaterial(
    column: RepoColumn,
    onBack: () -> Unit,
    onOpenWizard: (RepoColumnTopic) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 每个专题的进度（做过哪步、第几步）只在进页面时读一次 SharedPreferences。
    val progressByTopic = remember(column.id) {
        ColumnProgressStore.snapshot()
            .filter { it.columnId == column.id }
            .associateBy { it.topicId }
    }
    // 引导卡按档位跳到对应专题：映射到具体 topicId 后再滚动 + 短暂高亮，避免用户滚过去找不到刚点的是哪张卡。
    var highlightedTopicId by remember(column.id) { mutableStateOf("") }
    val onGuideSelect: (String) -> Unit = { tier ->
        val topicId = when (tier) {
            "day", "game" -> "pass-bank"
            "integrity" -> "pass-integrity"
            else -> column.topics.firstOrNull { it.tier == tier }?.id
        }
        val topic = column.topics.firstOrNull { it.id == topicId }
            ?: column.topics.firstOrNull { it.tier == tier && it.steps.isNotEmpty() }
        if (topic != null) {
            scope.launch {
                listState.animateScrollToItem(columnTopicsStartIndex(column) + column.topics.indexOf(topic))
                highlightedTopicId = topic.id
                delay(1500)
                highlightedTopicId = ""
            }
        }
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = column.title) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 顶栏的收起动画要靠 nestedScroll 连接列表的滚动：漏了它顶栏就钉死不动。
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "column-header") { ColumnHeaderCard(column) }
            // 「怎么选」排在专题列表之前：新用户先看这一块，再决定自己做哪一档。
            if (column.guide.isNotEmpty()) {
                item(key = "column-guide") { ColumnGuideCard(column.guide, onSelectTopic = onGuideSelect) }
            }
            items(column.topics, key = { it.id }) { topic ->
                TopicCard(
                    topic = topic,
                    progress = progressByTopic[topic.id],
                    onOpenWizard = onOpenWizard,
                    highlighted = topic.id == highlightedTopicId,
                )
            }
            if (column.topics.isEmpty()) {
                item(key = "column-empty") {
                    Text(
                        text = stringResource(R.string.module_column_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnHeaderCard(column: RepoColumn) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 图标和专栏名并排：标题字号拉大，形成明确的页面主标题，说明文字只保留一句。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ColumnIconBadge(columnGlyph(column.accent))
                Text(
                    text = column.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.column_header_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopicCard(
    topic: RepoColumnTopic,
    progress: ColumnProgressEntry?,
    onOpenWizard: (RepoColumnTopic) -> Unit,
    highlighted: Boolean = false,
) {
    // 索引里的空串偶尔会混进来：先滤掉，免得渲染出一条只有圆点的空行。
    val requires = remember(topic.requires) { topic.requires.filter { it.isNotBlank() } }
    val verifyHints = remember(topic.verifyHints) { topic.verifyHints.filter { it.isNotBlank() } }
    // 没有步骤但还有前置/验证时，折叠区照样要留入口，否则那两块内容永远看不到。
    val hasDetails = topic.steps.isNotEmpty() || requires.isNotEmpty() || verifyHints.isNotEmpty()
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // 进度紧贴标题：用户要一眼看到「这张卡做到了第几步」，放摘要下面会滑进正文里。
                if (progress != null) {
                    TopicProgressChip(progress)
                }
                TierBadge(topic.tier)
            }
            if (topic.summary.isNotEmpty()) {
                Text(
                    text = topic.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 「开始执行」紧跟在摘要后面：详情页卡片很长，按钮沉底会让用户翻完 11 个步骤才找到它。
            if (topic.steps.isNotEmpty()) {
                // 主色容器 + onPrimaryContainer：和商店页其它主按钮同一套配色，不再是紫色调。
                Button(
                    onClick = { onOpenWizard(topic) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.column_wizard_open))
                }
            }
            EvidenceBlock(topic.evidence)
            if (hasDetails) {
                var detailsExpanded by remember(topic.id) { mutableStateOf(false) }
                DetailsToggle(
                    expanded = detailsExpanded,
                    stepCount = topic.steps.size,
                    onToggle = { detailsExpanded = !detailsExpanded },
                )
                // 展开/收起用高度动画，列表不会因为内容突然出现而整体跳一下。
                AnimatedVisibility(
                    visible = detailsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 「前置条件 → 步骤 → 怎么确认」按用户实际动手的顺序排。
                        if (requires.isNotEmpty()) {
                            Section(
                                title = stringResource(R.string.module_column_requires_title),
                                lines = requires,
                            )
                        }
                        topic.steps.forEachIndexed { index, step -> StepRow(index = index, step = step) }
                        if (verifyHints.isNotEmpty()) {
                            Section(
                                title = stringResource(R.string.module_column_verify_title),
                                lines = verifyHints,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「查看 N 个步骤」折叠条：整行可点，左侧图标 + 文案，右侧箭头随展开旋转。
 *
 * 以前是一个紫色 TextButton：既多占一截高度，又和页面其它文字按钮混在一起，
 * 用户看不出它下面还压着前置条件、步骤和验证清单。
 */
@Composable
private fun DetailsToggle(
    expanded: Boolean,
    stepCount: Int,
    onToggle: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "moduleColumnDetailsArrow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Article,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = when {
                    expanded -> stringResource(R.string.module_column_steps_hide)
                    stepCount > 0 -> stringResource(R.string.module_column_steps_show, stepCount)
                    else -> stringResource(R.string.module_column_details_show)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(arrowRotation),
        )
    }
}

@Composable
private fun TopicProgressChip(progress: ColumnProgressEntry) {
    val done = progress.done
    val (label, color) = if (done) {
        // 底色是 XXContainer，文字就必须用对应的 onXXContainer，否则深色主题下浅字贴浅底看不清。
        stringResource(R.string.module_column_progress_done) to MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        "${progress.settledSteps} / ${progress.totalSteps}" to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        lines.forEach { line ->
            Text(
                text = "· $line",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 「怎么选」引导卡：一条一档，用最短的话说清「装什么、能过什么、代价是什么」。
 *
 * 文案全部来自索引（远程可改，不用发版），这里只负责排版，不替用户做判断。
 */
@Composable
private fun ColumnGuideCard(guide: List<RepoColumnGuide>, onSelectTopic: (String) -> Unit = {}) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.module_column_guide_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            guide.forEach { item ->
                val tint = tierColor(item.tier)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.10f))
                        .clickable { onSelectTopic(item.tier) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    // 圆点要和标题首行对齐：Row 顶部对齐，圆点自己在一个 24dp 高的盒子里居中。
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier.height(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tint),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (item.tier == "day") {
                                Text(
                                    text = stringResource(R.string.module_column_tier_recommended),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(tint)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (item.body.isNotEmpty()) {
                            Text(
                                text = item.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 档位徽章：写在专题标题旁边，让人一眼看出「这个专题属于哪一档」。tier 为空或未知时不显示。
 *
 * 中性样式：三档不再各染一色（颜色只留在引导卡的小圆点上），和商店页其它 tag 保持同一种观感。
 */
@Composable
private fun TierBadge(tier: String) {
    val labelRes = when (tier) {
        "day" -> R.string.module_column_tier_day
        "integrity" -> R.string.module_column_tier_integrity
        "game" -> R.string.module_column_tier_game
        "tool" -> R.string.module_column_tier_tool
        else -> null
    } ?: return
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun tierColor(tier: String): Color = when (tier) {
    "day" -> MaterialTheme.colorScheme.primary
    "integrity" -> MaterialTheme.colorScheme.tertiary
    "game" -> MaterialTheme.colorScheme.error
    "tool" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outline
}

/**
 * 证据区：一块浅色底的紧凑块，一行结论 + 一行出处。
 *
 * 原来是「圆点 + 颜色代表时效 + 三行文字」，用户看不懂那个颜色、也嫌它占高度；
 * 现在底色中性，时效只在结论里用词区分（历史验证 / 日期未知），不再给整块染色。
 */
@Composable
private fun EvidenceBlock(evidence: RepoColumnEvidence) {
    // freshness 默认自己取一次 now；这里固定同一个时间点，避免跨秒前后不一致。
    val now = remember { System.currentTimeMillis() }
    val month = evidenceMonth(evidence.verifiedAt)
    val status = when {
        month.isEmpty() -> stringResource(R.string.module_column_evidence_unknown)
        evidence.freshness(now) == EvidenceFreshness.GRAY ->
            stringResource(R.string.module_column_evidence_stale)
        else -> stringResource(R.string.module_column_evidence_verified)
    }
    val tested = stringResource(R.string.module_column_evidence_tested)
    // 只取 device：索引里 device 常写「多机型」这种有信息量的值，kernel 多半是「不限定」，
    // 拼进去只会把一行结论撑长。
    val testedOn = evidence.device.takeIf { it.isNotEmpty() }
    val headline = listOfNotNull(
        status,
        month.takeIf { it.isNotEmpty() }?.let { "$it $tested" },
        testedOn,
    ).joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (evidence.source.isNotEmpty()) {
            // 出处保留原文、最多两行：它写着「这条证据是谁验的」，删掉就成了没人背书的结论。
            Text(
                text = evidence.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StepRow(index: Int, step: RepoColumnStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (step.optional) {
                    Text(
                        text = stringResource(R.string.module_column_step_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stepKindLabel(step.kind),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (step.note.isNotEmpty()) {
                Text(
                    text = step.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val sourceLabel = stepSourceLabel(step)
            if (sourceLabel.isNotEmpty()) {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 步骤类型标签：详情页与向导页共用同一套措辞。 */
@Composable
internal fun stepKindLabel(kind: String): String = stringResource(
    when (kind) {
        "installModule" -> R.string.module_column_step_kind_install_module
        "installApp" -> R.string.module_column_step_kind_install_app
        "writeConfig" -> R.string.module_column_step_kind_write_config
        "writeFile" -> R.string.module_column_step_kind_write_file
        "writeHmaConfig" -> R.string.module_column_step_kind_write_hma_config
        "runCommand" -> R.string.module_column_step_kind_run_command
        "verify" -> R.string.module_column_step_kind_verify
        "reboot" -> R.string.module_column_step_kind_reboot
        else -> R.string.module_column_step_kind_ask_user
    }
)

private fun stepSourceLabel(step: RepoColumnStep): String = when (step.source.kind) {
    "upstream" -> listOf(step.source.repo, step.source.pinnedVersion, step.source.assetPattern)
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

    "updateJson" -> step.source.url
    "store" -> step.source.moduleId
    else -> ""
}

/**
 * 从 evidence.verifiedAt 里取出 `yyyy-MM`；解析不了就返回空串，由调用方降级成「日期未知」。
 *
 * 只留年月是有意为之：证据是社区整理的实测记录，精确到日反而容易被读成「这条最近才验过」。
 */
private fun evidenceMonth(verifiedAt: String): String {
    val raw = verifiedAt.trim()
    if (raw.isEmpty()) return ""
    val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return ""
    return "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
}

// ---------------------------------------------------------------------------------------------
// Miuix 版详情页
//
// 内容结构与 Material 版一一对应（专栏头 → 档位引导 → 专题卡 → 证据/步骤），但骨架、卡片、
// 字号换成 Miuix 自己那套：AppScaffold + TopAppBar + AppCard + SmallTitle。只在 Miuix 主题下
// 走这里，Material 那份（ModuleColumnScreenMaterial 及其私有组件）原样保留不动。
// 组件命名统一加 Miuix 后缀，避免与上面同名的 Material 私有函数冲突。
// ---------------------------------------------------------------------------------------------

/**
 * 专题卡在列表里的起始下标。
 *
 * 引导卡点击后要 animateScrollToItem 跳到对应档位的专题，而 header、引导标题/引导卡、
 * 专题分组标题这几块都是可选的，下标得跟着一起算——写死成 2 或 3 会差一两行。
 */
private fun columnTopicsStartIndex(column: RepoColumn): Int =
    1 + (if (column.guide.isNotEmpty()) 2 else 0) + (if (column.topics.isNotEmpty()) 1 else 0)

@Composable
private fun ModuleColumnScreenMiuix(
    column: RepoColumn,
    onBack: () -> Unit,
    onOpenWizard: (RepoColumnTopic) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    // 有自定义背景图或开了模糊时顶栏保持透明，内容从它下面滚过去——Miuix 顶栏的常态。
    val barColor = if (backdrop != null || hasCustomBackground) Color.Transparent else colorScheme.surface
    val layoutDirection = LocalLayoutDirection.current

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 每个专题的进度（做过哪步、第几步）只在进页面时读一次 SharedPreferences。
    val progressByTopic = remember(column.id) {
        ColumnProgressStore.snapshot()
            .filter { it.columnId == column.id }
            .associateBy { it.topicId }
    }
    // 引导卡按档位跳到对应专题：映射到具体 topicId 后再滚动 + 短暂高亮，避免用户滚过去找不到刚点的是哪张卡。
    var highlightedTopicId by remember(column.id) { mutableStateOf("") }
    val onGuideSelect: (String) -> Unit = { tier ->
        val topicId = when (tier) {
            "day", "game" -> "pass-bank"
            "integrity" -> "pass-integrity"
            else -> column.topics.firstOrNull { it.tier == tier }?.id
        }
        val topic = column.topics.firstOrNull { it.id == topicId }
            ?: column.topics.firstOrNull { it.tier == tier && it.steps.isNotEmpty() }
        if (topic != null) {
            scope.launch {
                listState.animateScrollToItem(columnTopicsStartIndex(column) + column.topics.indexOf(topic))
                highlightedTopicId = topic.id
                delay(1500)
                highlightedTopicId = ""
            }
        }
    }

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = column.title,
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            MiuixIcon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    // 详情页没有顶栏动作，留空占位避免内容贴着标题。
                    actions = { },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        // 这一页没有下拉框/弹层，空的 popup 宿主只会多一层命中测试。
        popupHost = { },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                // 顶栏的收起动画要靠 nestedScroll 连接列表的滚动：漏了它顶栏就钉死不动。
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            // 左右边距并进 contentPadding 而不是 Modifier.padding：这样内容还能从顶栏下面滚过去。
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "column-header") { ModuleColumnHeaderMiuix(column) }
            // 「怎么选」排在专题列表之前：新用户先看这一块，再决定自己做哪一档。
            if (column.guide.isNotEmpty()) {
                item(key = "column-guide-title") {
                    SmallTitle(
                        text = stringResource(R.string.module_column_guide_title),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                item(key = "column-guide") {
                    ColumnGuideCardMiuix(column.guide, onSelectTopic = onGuideSelect)
                }
            }
            if (column.topics.isNotEmpty()) {
                item(key = "column-topics-title") {
                    SmallTitle(
                        text = stringResource(R.string.module_column_topic_count, column.topics.size),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            items(column.topics, key = { it.id }) { topic ->
                TopicCardMiuix(
                    topic = topic,
                    progress = progressByTopic[topic.id],
                    onOpenWizard = onOpenWizard,
                    highlighted = topic.id == highlightedTopicId,
                )
            }
            if (column.topics.isEmpty()) {
                item(key = "column-empty") {
                    MiuixText(
                        text = stringResource(R.string.module_column_empty),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * 专栏头卡（Miuix 版）：图标块 + 副标题 + 描述 + 只读提示。
 *
 * 顶栏标题已经是专栏名，这里不再重复一遍标题行；图标跟商店列表卡走同一套 accent 映射。
 */
@Composable
private fun ModuleColumnHeaderMiuix(column: RepoColumn) {
    // insideMargin 就是 AppCard 自带的内边距（默认 0），这里给 16dp，content 里不再叠一层 padding。
    AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 图标和专栏名并排：标题字号拉大，形成明确的页面主标题，说明文字只保留一句。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ColumnIconBadgeMiuix(columnGlyph(column.accent))
                MiuixText(
                    text = column.title,
                    color = colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            MiuixText(
                text = stringResource(R.string.column_header_intro),
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }
    }
}

/** 专栏图标块（Miuix 版）：主色 12% 的圆角方块里放一枚主色图标，和商店页入口卡同一套观感。 */
@Composable
private fun ColumnIconBadgeMiuix(image: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        MiuixIcon(
            imageVector = image,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * 「怎么选」引导卡（Miuix 版）：一条一档，用最短的话说清「装什么、能过什么、代价是什么」。
 *
 * 卡片本身的标题在 Miuix 里提到卡外用 SmallTitle 做分组标题，所以卡内只剩三条可选行。
 * 文案全部来自索引（远程可改，不用发版），这里只负责排版，不替用户做判断。
 */
@Composable
private fun ColumnGuideCardMiuix(guide: List<RepoColumnGuide>, onSelectTopic: (String) -> Unit = {}) {
    AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            guide.forEach { item ->
                val tint = tierColorMiuix(item.tier)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.10f))
                        .clickable { onSelectTopic(item.tier) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    // 圆点要和标题首行对齐：Row 顶部对齐，圆点自己在一个 24dp 高的盒子里居中。
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tint),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MiuixText(
                                text = item.title,
                                color = colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (item.tier == "day") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tint),
                                ) {
                                    MiuixText(
                                        text = stringResource(R.string.module_column_tier_recommended),
                                        color = colorScheme.onPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight(750),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        if (item.body.isNotEmpty()) {
                            MiuixText(
                                text = item.body,
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicCardMiuix(
    topic: RepoColumnTopic,
    progress: ColumnProgressEntry?,
    onOpenWizard: (RepoColumnTopic) -> Unit,
    highlighted: Boolean = false,
) {
    // 索引里的空串偶尔会混进来：先滤掉，免得渲染出一条只有圆点的空行。
    val requires = remember(topic.requires) { topic.requires.filter { it.isNotBlank() } }
    val verifyHints = remember(topic.verifyHints) { topic.verifyHints.filter { it.isNotBlank() } }
    // 没有步骤但还有前置/验证时，折叠区照样要留入口，否则那两块内容永远看不到。
    val hasDetails = topic.steps.isNotEmpty() || requires.isNotEmpty() || verifyHints.isNotEmpty()
    var detailsExpanded by remember(topic.id) { mutableStateOf(false) }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlighted) {
                    Modifier.border(2.dp, colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            ),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiuixText(
                    text = topic.title,
                    modifier = Modifier.weight(1f),
                    color = colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // 进度紧贴标题：用户要一眼看到「这张卡做到了第几步」，放摘要下面会滑进正文里。
                if (progress != null) {
                    TopicProgressChipMiuix(progress)
                }
                TierBadgeMiuix(topic.tier)
            }
            if (topic.summary.isNotEmpty()) {
                MiuixText(
                    text = topic.summary,
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
            }
            // 「开始执行」紧跟在摘要后面：详情页卡片很长，按钮沉底会让用户翻完十几个步骤才找到它。
            if (topic.steps.isNotEmpty()) {
                MiuixButton(
                    onClick = { onOpenWizard(topic) },
                    modifier = Modifier.fillMaxWidth(),
                    // miuix Button 默认是灰底次要样式，主操作要用 primary 才够醒目（与商店页一致）。
                    colors = MiuixButtonDefaults.buttonColorsPrimary(),
                ) {
                    MiuixText(text = stringResource(R.string.column_wizard_open))
                }
            }
            EvidenceBlockMiuix(topic.evidence)
            if (hasDetails) {
                DetailsToggleMiuix(
                    expanded = detailsExpanded,
                    stepCount = topic.steps.size,
                    onToggle = { detailsExpanded = !detailsExpanded },
                )
                // 展开/收起用高度动画，列表不会因为内容突然出现而整体跳一下。
                AnimatedVisibility(
                    visible = detailsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 「前置条件 → 步骤 → 怎么确认」按用户实际动手的顺序排。
                        if (requires.isNotEmpty()) {
                            SectionMiuix(
                                title = stringResource(R.string.module_column_requires_title),
                                lines = requires,
                            )
                        }
                        topic.steps.forEachIndexed { index, step -> StepRowMiuix(index = index, step = step) }
                        if (verifyHints.isNotEmpty()) {
                            SectionMiuix(
                                title = stringResource(R.string.module_column_verify_title),
                                lines = verifyHints,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「查看 N 个步骤」折叠条（Miuix 版）：整行可点，左侧图标 + 文案，右侧箭头随展开旋转。
 *
 * 不用 TextButton：这一条压在卡片底部，做成整行点击区比一个居中的文字按钮更好点。
 */
@Composable
private fun DetailsToggleMiuix(
    expanded: Boolean,
    stepCount: Int,
    onToggle: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "moduleColumnDetailsArrowMiuix",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiuixIcon(
                imageVector = Icons.Outlined.Article,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
            MiuixText(
                text = when {
                    expanded -> stringResource(R.string.module_column_steps_hide)
                    stepCount > 0 -> stringResource(R.string.module_column_steps_show, stepCount)
                    else -> stringResource(R.string.module_column_details_show)
                },
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
            )
        }
        MiuixIcon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .size(20.dp)
                .rotate(arrowRotation),
        )
    }
}

@Composable
private fun TopicProgressChipMiuix(progress: ColumnProgressEntry) {
    val done = progress.done
    val label = if (done) {
        stringResource(R.string.module_column_progress_done)
    } else {
        "${progress.settledSteps} / ${progress.totalSteps}"
    }
    // Miuix 的 Colors 没有 Material 那套 tertiary 容器配对，未完成态用 tertiaryContainer 的容器色，
    // 完成态用 primaryContainer——和设置页里「已选中」的观感一致。
    val container = if (done) colorScheme.primaryContainer else colorScheme.tertiaryContainer
    val content = if (done) colorScheme.onPrimaryContainer else colorScheme.onTertiaryContainer
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container),
    ) {
        MiuixText(
            text = label,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight(650),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun SectionMiuix(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MiuixText(
            text = title,
            color = colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        lines.forEach { line ->
            MiuixText(
                text = "· $line",
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * 档位徽章（Miuix 版）：写在专题标题旁边，让人一眼看出「这个专题属于哪一档」。
 *
 * 中性样式：三档不再各染一色（颜色只留在引导卡的小圆点上），和列表里其它 tag 保持同一种观感。
 */
@Composable
private fun TierBadgeMiuix(tier: String) {
    val labelRes = when (tier) {
        "day" -> R.string.module_column_tier_day
        "integrity" -> R.string.module_column_tier_integrity
        "game" -> R.string.module_column_tier_game
        "tool" -> R.string.module_column_tier_tool
        else -> null
    } ?: return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colorScheme.onSurface.copy(alpha = 0.06f)),
    ) {
        MiuixText(
            text = stringResource(labelRes),
            color = colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * 档位配色。
 *
 * Miuix 的 Colors 没有 `tertiary`，这里换成语义最接近的一组：day→primary、integrity→secondary、
 * game→error、tool→outline（工具档最少见，中性灰正好）。
 */
@Composable
private fun tierColorMiuix(tier: String): Color = when (tier) {
    "day" -> colorScheme.primary
    "integrity" -> colorScheme.secondary
    "game" -> colorScheme.error
    "tool" -> colorScheme.outline
    else -> colorScheme.outline
}

/**
 * 证据区（Miuix 版）：一块浅底圆角的紧凑块，一行结论 + 一行出处。
 *
 * 底色用 `onSurface` 5% 而不是 surfaceVariant：后者在 Miuix 的深浅两套主题里明暗不统一，
 * 5% 的淡灰在两套主题下都读得出「这是一块附注」，又不会抢卡片本身的底色。
 */
@Composable
private fun EvidenceBlockMiuix(evidence: RepoColumnEvidence) {
    // freshness 默认自己取一次 now；这里固定同一个时间点，避免跨秒前后不一致。
    val now = remember { System.currentTimeMillis() }
    val month = evidenceMonth(evidence.verifiedAt)
    val status = when {
        month.isEmpty() -> stringResource(R.string.module_column_evidence_unknown)
        evidence.freshness(now) == EvidenceFreshness.GRAY ->
            stringResource(R.string.module_column_evidence_stale)
        else -> stringResource(R.string.module_column_evidence_verified)
    }
    val tested = stringResource(R.string.module_column_evidence_tested)
    // 只取 device：索引里 device 常写「多机型」这种有信息量的值，kernel 多半是「不限定」，
    // 拼进去只会把一行结论撑长。
    val testedOn = evidence.device.takeIf { it.isNotEmpty() }
    val headline = listOfNotNull(
        status,
        month.takeIf { it.isNotEmpty() }?.let { "$it $tested" },
        testedOn,
    ).joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MiuixText(
            text = headline,
            color = colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
        )
        if (evidence.source.isNotEmpty()) {
            // 出处保留原文、最多两行：它写着「这条证据是谁验的」，删掉就成了没人背书的结论。
            MiuixText(
                text = evidence.source,
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StepRowMiuix(index: Int, step: RepoColumnStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            MiuixText(
                text = (index + 1).toString(),
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MiuixText(
                    text = step.title,
                    modifier = Modifier.weight(1f),
                    color = colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (step.optional) {
                    MiuixText(
                        text = stringResource(R.string.module_column_step_optional),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                    )
                }
            }
            // 步骤类型与向导页共用同一套措辞，避免同一个动作在两页上叫两个名字。
            MiuixText(
                text = stepKindLabel(step.kind),
                color = colorScheme.primary,
                fontSize = 11.sp,
            )
            if (step.note.isNotEmpty()) {
                MiuixText(
                    text = step.note,
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
            val sourceLabel = stepSourceLabel(step)
            if (sourceLabel.isNotEmpty()) {
                MiuixText(
                    text = sourceLabel,
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ModuleColumnLoadingMiuix(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val barColor = if (backdrop != null || hasCustomBackground) Color.Transparent else colorScheme.surface
    val layoutDirection = LocalLayoutDirection.current
    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = "",
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            MiuixIcon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    actions = { },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            MiuixCircularProgressIndicator()
        }
    }
}
