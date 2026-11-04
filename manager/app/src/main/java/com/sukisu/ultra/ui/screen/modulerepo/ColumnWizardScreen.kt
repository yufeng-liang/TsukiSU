package com.sukisu.ultra.ui.screen.modulerepo

import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import com.sukisu.ultra.data.column.ColumnPreflight
import com.sukisu.ultra.data.column.ColumnHealthReport
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.column.ColumnCheckResult
import com.sukisu.ultra.data.column.ColumnStepStatus
import com.sukisu.ultra.data.column.ColumnStepUiState
import com.sukisu.ultra.data.column.ColumnWizardUiState
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnTopic
import com.sukisu.ultra.ui.component.material.ExpressiveConfirmDialog
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.TonalCard
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 日志默认折叠时露出的行数。够看到「正在做什么 / 为什么失败」，又不至于把步骤列表挤到屏幕外。 */
private const val LOG_COLLAPSED_LINES = 5

/** 与 `RepoColumnStep.kind` 的取值一致；只有这个类型要在卡片里原文展示命令。 */
private const val STEP_KIND_RUN_COMMAND = "runCommand"

/**
 * 成功的绿。
 *
 * 主题方案里没有语义化的 success token（primary 会跟着用户选的种子色走，选红色时「成功」和「失败」
 * 会撞色），而这一屏的对勾/叉号是用户判断「做没做成」的唯一依据，所以这里固定一档绿，按底色明暗
 * 取深/浅两种，保证两套主题下都有足够对比。
 */
private val SuccessGreenLight = Color(0xFF2E7D32)
private val SuccessGreenDark = Color(0xFF7BD88F)

/**
 * 专栏专题的执行向导。
 *
 * 与只读的 [ModuleColumnScreen] 只差一件事：这里的每一步都会被真正执行。所以界面守三条规矩：
 * 1. 动手之前先把「要做什么」摆出来（命令原文、说明、风险文案），不当黑盒；
 * 2. 高风险步骤（[RepoColumnStep.confirm] 非空）必须先过一次确认弹窗，确认是用户点的，不是默认值；
 * 3. 失败不自动重试、不自动跳过，重试与跳过都只能由用户点。
 */
@Composable
fun ColumnWizardScreen(
    topic: RepoColumnTopic,
    state: ColumnWizardUiState,
    onRunStep: (RepoColumnStep) -> Unit,
    onSkipStep: (RepoColumnStep) -> Unit,
    onRollback: () -> Unit,
    onBack: () -> Unit,
    onProvideKeybox: (String) -> String? = { null },
    /** 用户点「升级」：带 force 重跑这一步，覆盖已装的旧版本。 */
    onUpgradeStep: (RepoColumnStep) -> Unit = {},
    /** 一键执行：把不需要人看着的步骤按顺序跑完，遇到需要人工的就停下。 */
    onRunAll: () -> Unit = {},
    /** 勾选 / 取消勾选一个「可选安装」的步骤。 */
    onToggleOptIn: (RepoColumnStep) -> Unit = {},
    /** 用户按提示自己做完了（重启回来 / 装好 APK / 自己确认过），把这一步标成完成。 */
    onMarkDone: (RepoColumnStep) -> Unit = {},
    /** 预检确认框上点「继续」。 */
    onConfirmPreflight: () -> Unit = {},
    /** 预检确认框上点「取消」。 */
    onDismissPreflight: () -> Unit = {},
) {
    // P0/P1 共用 Material 实现：向导只有「执行 / 跳过 / 回滚」三个动作，
    // 两套界面没有行为差异，没必要维护两份逐参转发的副本。
    ColumnWizardScreenMaterial(
        topic = topic,
        state = state,
        onRunStep = onRunStep,
        onSkipStep = onSkipStep,
        onRollback = onRollback,
        onBack = onBack,
        onProvideKeybox = onProvideKeybox,
        onUpgradeStep = onUpgradeStep,
        onRunAll = onRunAll,
        onToggleOptIn = onToggleOptIn,
        onMarkDone = onMarkDone,
    )
    state.preflight?.let { plan ->
        ColumnPreflightDialog(
            plan = plan,
            onConfirm = onConfirmPreflight,
            onDismiss = onDismissPreflight,
        )
    }
}

@Composable
private fun ColumnWizardScreenMaterial(
    topic: RepoColumnTopic,
    state: ColumnWizardUiState,
    onRunStep: (RepoColumnStep) -> Unit,
    onSkipStep: (RepoColumnStep) -> Unit,
    onRollback: () -> Unit,
    onBack: () -> Unit,
    onProvideKeybox: (String) -> String?,
    onUpgradeStep: (RepoColumnStep) -> Unit,
    onRunAll: () -> Unit,
    onToggleOptIn: (RepoColumnStep) -> Unit,
    onMarkDone: (RepoColumnStep) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // 以 topic.steps 的顺序为准，state 只提供状态：ViewModel 还没把状态铺开时（刚进页面、状态被重建），
    // 列表照样完整，「开始第一步」也能点；否则会出现「专题有 8 步、界面一片空白」的假死。
    val orderedStates = topic.steps.map { step ->
        state.stepStates.firstOrNull { it.step.id == step.id } ?: ColumnStepUiState(step = step)
    }
    val total = orderedStates.size
    val finished = orderedStates.count {
        it.status == ColumnStepStatus.SUCCESS || it.status == ColumnStepStatus.SKIPPED
    }
    val started = orderedStates.any { it.status != ColumnStepStatus.PENDING }

    // 高风险步骤的确认弹窗；卡片按钮和底部「开始第一步」共用这一条路径，避免漏掉确认。
    var confirmStep by remember { mutableStateOf<RepoColumnStep?>(null) }
    fun requestRun(step: RepoColumnStep) {
        if (step.confirm.isNotEmpty()) confirmStep = step else onRunStep(step)
    }

    var logExpanded by rememberSaveable { mutableStateOf(false) }

    // 专题里带 ${keybox} 占位（或声明了直链）时，没有可用的密钥材料就走不到最后一步。
    // 商店只把用户选中的文件原样交给执行器写进设备，不保存、不转存、不上传。
    val needsKeybox = remember(topic) {
        topic.keybox != null || topic.steps.any { it.fileContent.contains("\${keybox}") }
    }
    var keyboxError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                keyboxError = "读取失败：文件是空的，或者没有读取权限"
                return@launch
            }
            keyboxError = onProvideKeybox(text) ?: ""
        }
    }

    confirmStep?.let { step ->
        ExpressiveConfirmDialog(
            title = stringResource(R.string.column_wizard_confirm_title),
            confirmText = stringResource(R.string.column_wizard_start),
            message = step.confirm,
            dismissText = stringResource(R.string.column_wizard_cancel),
            onConfirm = {
                confirmStep = null
                onRunStep(step)
            },
            onDismiss = { confirmStep = null },
        )
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = topic.title) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                actions = {
                    // 风险徽章常驻：滚到第 6 步时也不该忘记这个专题是高风险。
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        RiskBadge(topic.riskLevel)
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (!state.done) {
                WizardBottomBar(
                    total = total,
                    finished = finished,
                    started = started,
                    running = state.running,
                    onRunAll = onRunAll,
                    onStart = {
                        orderedStates
                            .firstOrNull { it.status == ColumnStepStatus.PENDING }
                            ?.let { requestRun(it.step) }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 没有底部操作条时（完成态 / 空列表）没人替列表让出导航栏，这里自己让。
                .then(
                    if (state.done || total == 0) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier
                    },
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.health != null) {
                item(key = "wizard-health") {
                    ColumnHealthCard(report = state.health)
                }
            }
            if (needsKeybox && !state.keyboxReady) {
                item(key = "wizard-keybox") {
                    WizardKeyboxCard(
                        error = keyboxError,
                        onPick = {
                            keyboxError = ""
                            keyboxPicker.launch(
                                arrayOf(
                                    "text/xml",
                                    "application/xml",
                                    "text/plain",
                                    "application/octet-stream",
                                ),
                            )
                        },
                    )
                }
            }
            if (state.error.isNotEmpty()) {
                item(key = "wizard-error") { WizardErrorBanner(state.error) }
            }
            if (orderedStates.isEmpty()) {
                item(key = "wizard-empty") {
                    Text(
                        text = stringResource(R.string.column_wizard_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(orderedStates, key = { it.step.id }) { uiState ->
                WizardStepCard(
                    uiState = uiState,
                    busy = state.running,
                    optedIn = uiState.step.id in state.optedIn,
                    onToggleOptIn = { onToggleOptIn(uiState.step) },
                    onRun = { requestRun(uiState.step) },
                    onSkip = { onSkipStep(uiState.step) },
                    onUpgrade = { onUpgradeStep(uiState.step) },
                    onMarkDone = { onMarkDone(uiState.step) },
                )
            }
            if (state.log.isNotEmpty()) {
                item(key = "wizard-log") {
                    WizardLogCard(
                        log = state.log,
                        expanded = logExpanded,
                        onToggle = { logExpanded = !logExpanded },
                    )
                }
            }
            if (state.done) {
                item(key = "wizard-checks") {
                    WizardChecksCard(
                        checks = state.checks,
                        onRollback = onRollback,
                        busy = state.running,
                    )
                }
            }
        }
    }
}

/** 全局错误条：例如密钥拉取失败导致整个专题都跑不下去，这时候说清楚原因比让每一步各自报错有用。 */
/**
 * 密钥材料提示卡。
 *
 * 三绿（Play Integrity）绕不开一份 Google 认证密钥，而这份材料既不能由商店托管、也不该让用户去
 * 来路不明的地方下载。所以这里只做一件事：让用户选自己信任的那份 keybox.xml，说明它会被写到哪里。
 */
@Composable
private fun WizardKeyboxCard(error: String, onPick: () -> Unit) {
    TonalCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.column_wizard_keybox_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.column_wizard_keybox_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = onPick) {
                Text(text = stringResource(R.string.column_wizard_keybox_pick))
            }
        }
    }
}

@Composable
private fun WizardErrorBanner(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WizardStepCard(
    uiState: ColumnStepUiState,
    busy: Boolean,
    optedIn: Boolean,
    onToggleOptIn: () -> Unit,
    onRun: () -> Unit,
    onSkip: () -> Unit,
    onUpgrade: () -> Unit,
    onMarkDone: () -> Unit,
) {
    val step = uiState.step
    val skipped = uiState.status == ColumnStepStatus.SKIPPED
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 「可选安装」的步骤默认不装：勾上才会进一键执行，或者直接点这一步等同勾选。
                if (step.optIn) {
                    Checkbox(
                        checked = optedIn,
                        onCheckedChange = { onToggleOptIn() },
                        enabled = !busy,
                    )
                }
                WizardStepStatusIcon(status = uiState.status)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (skipped) TextDecoration.LineThrough else null,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stepKindLabel(step.kind),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(statusLabel(uiState.status)),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor(uiState.status),
                        )
                    }
                    if (step.note.isNotEmpty()) {
                        Text(
                            text = step.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 官方只肯走群/网盘发的检测器（春秋），商店给不了直链，但能把官方入口摆出来；
                    // 链接来自索引仓，只放行 http/https，点了也只是打开浏览器，不执行任何东西。
                    if (step.link.isNotEmpty()) {
                        val context = LocalContext.current
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(step.link)))
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.column_wizard_open_link),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    // 已装但上游有新版：跳过不等于「已经是最新」。只有用户点这里才会带 force
                    // 覆盖安装——向导不会替他把旧模块换掉。
                    uiState.upgrade?.let { upgrade ->
                        TextButton(
                            onClick = onUpgrade,
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.column_wizard_upgrade,
                                    upgrade.latestVersion,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    if (uiState.message.isNotEmpty()) {
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor(uiState.status),
                        )
                    }
                }
                WizardStepActions(
                    status = uiState.status,
                    optional = step.optional,
                    enabled = !busy,
                    onRun = onRun,
                    onSkip = onSkip,
                    onMarkDone = onMarkDone,
                )
            }
            // 命令原文单独占一整行：右侧按钮占掉宽度后，窄列里折行的 shell 命令既难读也容易被误抄。
            if (step.kind == STEP_KIND_RUN_COMMAND && step.command.isNotEmpty()) {
                CommandBlock(command = step.command)
            }
        }
    }
}

/** 右侧动作区。没动作的步骤（已成功/已跳过）不占位，标题就能多留一点宽度。 */
@Composable
private fun WizardStepActions(
    status: ColumnStepStatus,
    optional: Boolean,
    enabled: Boolean,
    onRun: () -> Unit,
    onSkip: () -> Unit,
    onMarkDone: () -> Unit,
) {
    // NEEDS_USER 是「等你动手」，点它是「我做完了」，不是「重跑一遍」——重跑只会再下载一次、
    // 再弹一次安装器。所以这里接的是 onMarkDone，不是 onRun。
    val runLabel = when (status) {
        ColumnStepStatus.PENDING -> R.string.column_wizard_run
        // 跑着的这一步保留按钮但禁用：按钮突然消失会让人以为「可以点下一步了」。
        ColumnStepStatus.RUNNING -> R.string.column_wizard_run
        ColumnStepStatus.FAILED -> R.string.column_wizard_retry
        ColumnStepStatus.NEEDS_USER -> R.string.column_wizard_mark_done
        ColumnStepStatus.SUCCESS, ColumnStepStatus.SKIPPED -> null
    }
    val canSkip = optional && (
        status == ColumnStepStatus.PENDING ||
            status == ColumnStepStatus.FAILED ||
            status == ColumnStepStatus.NEEDS_USER
        )
    if (runLabel == null && !canSkip) return
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (runLabel != null) {
            Button(
                onClick = if (status == ColumnStepStatus.NEEDS_USER) onMarkDone else onRun,
                enabled = enabled && status != ColumnStepStatus.RUNNING,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(runLabel),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (canSkip) {
            TextButton(
                onClick = onSkip,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.column_wizard_skip),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 状态图标：空心圆 → 转圈 → 对勾；失败是叉，跳过是划线，需要用户处理是「人」。 */
@Composable
private fun WizardStepStatusIcon(status: ColumnStepStatus) {
    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        when (status) {
            ColumnStepStatus.PENDING -> Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )

            ColumnStepStatus.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )

            ColumnStepStatus.SUCCESS -> Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = successGreen(),
                modifier = Modifier.size(20.dp),
            )

            ColumnStepStatus.FAILED -> Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )

            ColumnStepStatus.SKIPPED -> Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }

            // 没有手型图标可用（图标集是裁剪过的），用人形表达「这一步轮到你自己动手」。
            ColumnStepStatus.NEEDS_USER -> Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 命令原文：等宽字体 + 可选中的容器，用户要能长按复制去自己跑一遍。 */
@Composable
private fun CommandBlock(command: String) {
    SelectionContainer {
        Text(
            text = command,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun WizardLogCard(
    log: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    // 折叠时只露最后几行：日志是「刚才发生了什么」，最新的永远在最下面。
    // 用 remember 缓存裁剪结果：日志每追加一行都会重组整张卡片，没必要每次都重算。
    val visible = remember(log, expanded) {
        if (expanded) log else log.takeLast(LOG_COLLAPSED_LINES)
    }
    // 拼接也只随日志内容变化重算，避免每帧都 join 整份日志。
    val logText = remember(visible) { visible.joinToString("\n") }
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.column_wizard_log_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // 只有真的折掉了东西才给「展开」：五行以内展开也没有新内容。
                if (log.size > LOG_COLLAPSED_LINES) {
                    TextButton(
                        onClick = onToggle,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (expanded) {
                                    R.string.column_wizard_collapse_log
                                } else {
                                    R.string.column_wizard_expand_log
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            SelectionContainer {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WizardChecksCard(
    checks: List<ColumnCheckResult>,
    onRollback: () -> Unit,
    busy: Boolean,
) {
    // 回滚会卸载模块、覆盖文件，误触代价高：先弹一次确认，确认后才真正执行。
    var confirmRollback by remember { mutableStateOf(false) }
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 没有自检项时不留一个空标题：只给回滚按钮，别让人以为自检结果丢了。
            if (checks.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.column_wizard_checks_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                checks.forEach { check -> CheckRow(check = check) }
            }
            // 回滚放在自检结果下面：先让用户看到「哪几项没过」，再决定要不要整体撤销。
            OutlinedButton(
                onClick = { confirmRollback = true },
                modifier = Modifier.fillMaxWidth(),
                // 有步骤在跑时禁用：一边执行一边回滚会把账本和正在写的文件搅在一起。
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = stringResource(R.string.column_wizard_rollback))
            }
        }
    }
    if (confirmRollback) {
        ExpressiveConfirmDialog(
            title = stringResource(R.string.column_wizard_rollback_confirm_title),
            message = stringResource(R.string.column_wizard_rollback_confirm_body),
            confirmText = stringResource(R.string.column_wizard_rollback_confirm_confirm),
            dismissText = stringResource(R.string.column_wizard_cancel),
            onConfirm = {
                confirmRollback = false
                onRollback()
            },
            onDismiss = { confirmRollback = false },
        )
    }
}

@Composable
private fun CheckRow(check: ColumnCheckResult) {
    val tint = if (check.passed) successGreen() else MaterialTheme.colorScheme.error
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = if (check.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(
                    if (check.passed) {
                        R.string.column_wizard_check_pass
                    } else {
                        R.string.column_wizard_check_fail
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
            if (check.detail.isNotEmpty()) {
                Text(
                    text = check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 底部固定操作条。
 *
 * 未开始：一个「开始第一步」，省得用户去列表里找第一个按钮；进行中：进度条 + 「3/8」，让人知道还剩
 * 几步；跑完之后整条隐藏，把屏幕让给自检结果与回滚。
 */
@Composable
private fun WizardBottomBar(
    total: Int,
    finished: Int,
    started: Boolean,
    running: Boolean,
    onRunAll: () -> Unit,
    onStart: () -> Unit,
) {
    if (total == 0) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!started) {
                // 主按钮是一键执行：先把能自动的全跑完；想一步步看的人走下面那个次要按钮。
                Button(
                    onClick = onRunAll,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.column_wizard_run_all))
                }
                TextButton(
                    onClick = onStart,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.column_wizard_start_first))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else finished.toFloat() / total },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.column_wizard_progress, finished, total),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                // 重启回来接着做时最有用：已经处理过的步骤会被跳过，只跑重启后才允许的那几步。
                OutlinedButton(
                    onClick = onRunAll,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.column_wizard_run_all_rest))
                }
            }
        }
    }
}

/** 状态文案对应的资源 id。 */
private fun statusLabel(status: ColumnStepStatus): Int = when (status) {
    ColumnStepStatus.PENDING -> R.string.column_wizard_status_pending
    ColumnStepStatus.RUNNING -> R.string.column_wizard_status_running
    ColumnStepStatus.SUCCESS -> R.string.column_wizard_status_success
    ColumnStepStatus.FAILED -> R.string.column_wizard_status_failed
    ColumnStepStatus.SKIPPED -> R.string.column_wizard_status_skipped
    ColumnStepStatus.NEEDS_USER -> R.string.column_wizard_status_needs_user
}

@Composable
private fun statusColor(status: ColumnStepStatus): Color = when (status) {
    ColumnStepStatus.SUCCESS -> successGreen()
    ColumnStepStatus.FAILED -> MaterialTheme.colorScheme.error
    ColumnStepStatus.NEEDS_USER -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 按底色明暗取成功色：AMOLED / 深色主题下用浅一档的绿，浅色主题下用深绿。 */
@Composable
private fun successGreen(): Color = if (MaterialTheme.colorScheme.surfaceBright.luminance() < 0.5f) {
    SuccessGreenDark
} else {
    SuccessGreenLight
}

/**
 * 设备现状体检卡。
 *
 * 只回答一个问题：「这台机器之前是不是已经配过一部分隐藏环境」。九项探针都是只读的
 * 存在性检查，它看不出「配置对不对」，所以这里只作提示，绝不用来决定跳过哪一步。
 */
@Composable
private fun ColumnHealthCard(report: ColumnHealthReport) {
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.column_health_title_short),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.column_health_score,
                        report.passedCount,
                        report.totalCount,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            HealthLevelRow(
                ok = report.dayLevelReady,
                title = stringResource(R.string.column_health_day_level),
                okText = stringResource(R.string.column_health_ready),
                noText = stringResource(R.string.column_health_not_ready),
            )
            Spacer(Modifier.height(6.dp))
            HealthLevelRow(
                ok = report.integrityLevelReady,
                title = stringResource(R.string.column_health_integrity_level),
                okText = stringResource(R.string.column_health_ready),
                noText = stringResource(R.string.column_health_not_ready),
            )
            val missing = report.checks.filter { !it.passed }
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                missing.take(3).forEach { check ->
                    Text(
                        text = "· ${check.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (missing.size > 3) {
                    Text(
                        text = "· …（还有 ${missing.size - 3} 项）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthLevelRow(ok: Boolean, title: String, okText: String, noText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (ok) okText else noText,
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 一键执行前的预检确认框。
 *
 * 存量用户最容易被静默伤到：keybox、denylist 开关、target.txt 都会被覆盖（有备份但用户不知道）。
 * 这里把「会跳过 / 会覆盖 / 会新增 / 需手动」四类摆出来，让他先看见再决定。
 */
@Composable
private fun ColumnPreflightDialog(
    plan: ColumnPreflight,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.column_preflight_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (plan.overwrites.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.column_preflight_overwrite, plan.overwrites.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    plan.overwrites.forEach {
                        Text(
                            text = "· ${it.title}：${it.reason}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (plan.skips.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.column_preflight_skip, plan.skips.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    plan.skips.forEach {
                        Text(
                            text = "· ${it.title}：${it.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (plan.news.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.column_preflight_new, plan.news.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (plan.manuals.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.column_preflight_manual, plan.manuals.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = stringResource(R.string.column_preflight_backup_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.column_preflight_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.column_preflight_cancel))
            }
        },
    )
}
