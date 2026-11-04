package com.sukisu.ultra.ui.screen.modulerepo

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.EvidenceFreshness
import com.sukisu.ultra.data.model.RepoColumn
import com.sukisu.ultra.data.model.RepoColumnEvidence
import com.sukisu.ultra.data.model.RepoColumnGuide
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnTopic
import com.sukisu.ultra.data.model.ageDays
import com.sukisu.ultra.data.model.freshness
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.TonalCard
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.viewmodel.ModuleRepoViewModel
import kotlinx.coroutines.delay

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
) {
    if (columns.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.module_column_section_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(columns, key = { it.id }) { column ->
                ColumnEntryCard(column = column, onClick = { onOpenColumn(column) })
            }
        }
    }
}

@Composable
private fun ColumnEntryCard(column: RepoColumn, onClick: () -> Unit) {
    TonalCard(onClick = onClick, modifier = Modifier.width(232.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentDot(column.accent)
                Text(
                    text = column.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (column.subtitle.isNotEmpty()) {
                Text(
                    text = column.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.module_column_topic_count, column.topics.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
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
    // 两套 UI 共用同一份实现：详情页是纯展示，没有 Miuix 专有的交互要复刻。
    when (LocalUiMode.current) {
        UiMode.Miuix -> ModuleColumnScreenMaterial(
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
 * `refreshColumns` 失败是静默的，`columnsLoaded` 会一直是 false；这里最多等 8 秒，
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
        if (!state.columnsLoaded) delay(8_000)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "column-header") { ColumnHeaderCard(column) }
            // 「怎么选」排在专题列表之前：新用户先看这一块，再决定自己做哪一档。
            if (column.guide.isNotEmpty()) {
                item(key = "column-guide") { ColumnGuideCard(column.guide) }
            }
            items(column.topics, key = { it.id }) { topic ->
                TopicCard(topic = topic, onOpenWizard = onOpenWizard)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccentDot(column.accent)
                Text(
                    text = column.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (column.subtitle.isNotEmpty()) {
                Text(
                    text = column.subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (column.description.isNotEmpty()) {
                Text(text = column.description, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(R.string.column_wizard_readonly_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TopicCard(topic: RepoColumnTopic, onOpenWizard: (RepoColumnTopic) -> Unit) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
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
                TierBadge(topic.tier)
                RiskBadge(topic.riskLevel)
            }
            if (topic.summary.isNotEmpty()) {
                Text(
                    text = topic.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (topic.layers.isNotEmpty()) {
                TagRow(topic.layers)
            }
            EvidenceRow(topic.evidence)
            if (topic.requires.isNotEmpty()) {
                Section(
                    title = stringResource(R.string.module_column_requires_title),
                    lines = topic.requires,
                )
            }
            if (topic.steps.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.module_column_steps_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                topic.steps.forEachIndexed { index, step -> StepRow(index = index, step = step) }
            }
            if (topic.verifyHints.isNotEmpty()) {
                Section(
                    title = stringResource(R.string.module_column_verify_title),
                    lines = topic.verifyHints,
                )
            }
            // 没有步骤的专题不进向导：点进去只会是一片空列表。
            if (topic.steps.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { onOpenWizard(topic) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.column_wizard_open))
                }
            }
        }
    }
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

@Composable
private fun TagRow(tags: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tag ->
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
private fun ColumnGuideCard(guide: List<RepoColumnGuide>) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.module_column_guide_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            guide.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tierColor(item.tier)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
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

/** 档位徽章：写在专题标题旁边，让人一眼看出「这个专题属于哪一档」。tier 为空或未知时不显示。 */
@Composable
private fun TierBadge(tier: String) {
    val labelRes = when (tier) {
        "day" -> R.string.module_column_tier_day
        "integrity" -> R.string.module_column_tier_integrity
        "game" -> R.string.module_column_tier_game
        "tool" -> R.string.module_column_tier_tool
        else -> null
    } ?: return
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun tierColor(tier: String): Color = when (tier) {
    "day" -> MaterialTheme.colorScheme.primary
    "integrity" -> MaterialTheme.colorScheme.tertiary
    "game" -> MaterialTheme.colorScheme.error
    "tool" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outline
}

/** 风险徽章：详情页与向导页共用，保证同一个专题在两处颜色一致。 */
@Composable
internal fun RiskBadge(riskLevel: String) {
    val (labelRes, container) = when (riskLevel) {
        "low" -> R.string.module_column_risk_low to MaterialTheme.colorScheme.secondaryContainer
        "medium" -> R.string.module_column_risk_medium to MaterialTheme.colorScheme.tertiaryContainer
        else -> R.string.module_column_risk_high to MaterialTheme.colorScheme.errorContainer
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 证据行：颜色代表时效，文字说清「多久以前、在哪台机器上验的」。 */
@Composable
private fun EvidenceRow(evidence: RepoColumnEvidence) {
    // ageDays/freshness 各自默认取一次 now，跨秒时可能得出不一致的结论；这里固定同一个时间点。
    val now = remember { System.currentTimeMillis() }
    val freshness = evidence.freshness(now)
    val age = evidence.ageDays(now)
    val color = freshnessColor(freshness)
    val text = when {
        age == Int.MAX_VALUE -> stringResource(R.string.module_column_evidence_unknown)
        else -> stringResource(R.string.module_column_evidence_age, age)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
            val where = listOf(evidence.device, evidence.kernel)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            if (where.isNotEmpty()) {
                Text(
                    text = where,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (evidence.source.isNotEmpty()) {
                Text(
                    text = evidence.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun AccentDot(accent: String) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(accentColor(accent)),
    )
}

@Composable
private fun accentColor(accent: String): Color = when (accent) {
    "root" -> MaterialTheme.colorScheme.error
    "hide" -> MaterialTheme.colorScheme.primary
    "integrity" -> MaterialTheme.colorScheme.tertiary
    "tool" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun freshnessColor(freshness: EvidenceFreshness): Color = when (freshness) {
    EvidenceFreshness.GREEN -> MaterialTheme.colorScheme.primary
    EvidenceFreshness.YELLOW -> MaterialTheme.colorScheme.tertiary
    EvidenceFreshness.GRAY -> MaterialTheme.colorScheme.outline
}
