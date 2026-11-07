package com.sukisu.ultra.ui.screen.modulerepo

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.compose.rememberKonfettiState
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import java.util.concurrent.TimeUnit
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.theme.AiAssistantMaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import com.sukisu.ultra.data.column.ColumnPreflight
import com.sukisu.ultra.data.column.ColumnHealthCheck
import com.sukisu.ultra.data.column.ColumnHealthReport
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 日志默认折叠时露出的行数。够看到「正在做什么 / 为什么失败」，又不至于把步骤列表挤到屏幕外。 */
private const val LOG_COLLAPSED_LINES = 5

/** 步骤 link 写成这个协议时表示「进 AI 助手」，不跳浏览器。 */
private const val COLUMN_LINK_AI = "sukisu://ai"

/** 步骤说明默认折叠时露出的行数：note 常是一整段，全展开会把卡片撑得比标题高好几倍。 */
private const val NOTE_COLLAPSED_LINES = 3

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
    /** 步骤里挂了「进入 AI 助手」入口时点它：把用户送到助手页发截图排查红项。 */
    onOpenAi: () -> Unit = {},
    /** 预检确认框上点「继续」。 */
    onConfirmPreflight: () -> Unit = {},
    /** 预检确认框上点「取消」。 */
    onDismissPreflight: () -> Unit = {},
    /** 庆祝 overlay 上点「去自检」：跳到自检专题，那里有「进入 AI 助手」可以发截图排查红项。 */
    onOpenSelfCheck: () -> Unit = {},
    /** 关掉庆祝 overlay。 */
    onDismissCelebration: () -> Unit = {},
    /** 重启倒计时卡片上点「立即重启」。 */
    onRebootNow: () -> Unit = {},
    /** 重启倒计时卡片上点「稍后我自己重启」。 */
    onRebootLater: () -> Unit = {},
) {
    // 测试专用：长按顶栏标题打开预览菜单。庆祝动画和重启倒计时都要跑完整个专题才会出现，
    // 而验收时不能真的执行隐藏步骤（会改设备、会重启），所以留一个只播 UI、不碰设备的入口。
    var showTestMenu by remember { mutableStateOf(false) }
    var previewCelebrate by remember { mutableStateOf(false) }
    var previewCountdown by remember { mutableStateOf(-1) }
    LaunchedEffect(previewCountdown) {
        if (previewCountdown > 0) {
            kotlinx.coroutines.delay(1_000)
            previewCountdown -= 1
        } else if (previewCountdown == 0) {
            previewCountdown = -1
        }
    }

    // 两套皮肤各自实现：这里不是换皮那么简单，卡片的构造、按钮的语义在两种设计语言里不一样，
    // 与其在一个函数里层层 if，不如两份实现共用同一批回调（执行 / 跳过 / 升级 / 回滚）。
    when (LocalUiMode.current) {
        UiMode.Miuix -> ColumnWizardScreenMiuix(
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
            onOpenAi = onOpenAi,
            onLongPressTitle = { showTestMenu = true },
        )

        UiMode.Material -> ColumnWizardScreenMaterial(
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
            onOpenAi = onOpenAi,
            onLongPressTitle = { showTestMenu = true },
        )
    }
    // 预检框两套皮肤共用 Material 版：Miuix 没有可靠的对话框 API，硬造一个只会和行为约定打架。
    // Material 模式下 AiAssistantMaterialTheme 直通（不多套一层主题），Miuix 模式下它才生成
    // 同源 MD3 调色板，免得弹框落回框架默认配色——所以统一套一层，不改变 Material 分支的行为。
    state.preflight?.let { plan ->
        AiAssistantMaterialTheme {
            ColumnPreflightDialog(
                plan = plan,
                onConfirm = onConfirmPreflight,
                onDismiss = onDismissPreflight,
            )
        }
    }
    // 跑完整个专题的庆祝：这是全流程唯一值得打断一次的地方，所以做成全屏而不是内联卡片。
    // 和预检框一样套 AiAssistantMaterialTheme：Miuix 模式下才需要它生成同源调色板。
    if (state.celebrate || previewCelebrate) {
        AiAssistantMaterialTheme {
            ColumnCelebrationOverlay(
                onOpenSelfCheck = {
                    // 预览态只关预览：既不能跳走，也不能碰真实的庆祝状态。
                    if (previewCelebrate) {
                        previewCelebrate = false
                    } else {
                        onOpenSelfCheck()
                    }
                },
                onDismiss = {
                    if (previewCelebrate) {
                        previewCelebrate = false
                    } else {
                        onDismissCelebration()
                    }
                },
            )
        }
    }
    // 重启前倒计时：手机突然黑屏会让人以为死机了，所以先明说「X 秒后重启」并给两个出口。
    val countdownSeconds = if (previewCountdown >= 0) previewCountdown else state.rebootCountdown
    if (countdownSeconds >= 0) {
        AiAssistantMaterialTheme {
            RebootCountdownOverlay(
                seconds = countdownSeconds,
                // 预览模式下这两个按钮只关预览，绝不能落到真实回调（那里会真的让设备重启）。
                onRebootNow = {
                    if (previewCountdown >= 0) previewCountdown = -1 else onRebootNow()
                },
                onRebootLater = {
                    if (previewCountdown >= 0) previewCountdown = -1 else onRebootLater()
                },
            )
        }
    }
    // 测试菜单：长按顶栏标题才会出现，正式流程永远碰不到。
    if (showTestMenu) {
        AiAssistantMaterialTheme {
            AlertDialog(
                onDismissRequest = { showTestMenu = false },
                title = { Text(stringResource(R.string.column_wizard_test_title)) },
                text = { Text(stringResource(R.string.column_wizard_test_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        showTestMenu = false
                        previewCelebrate = true
                    }) { Text(stringResource(R.string.column_wizard_test_celebrate)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showTestMenu = false
                        previewCountdown = 10
                    }) { Text(stringResource(R.string.column_wizard_test_countdown)) }
                },
            )
        }
    }
}

/**
 * 重启前的倒计时提示。
 *
 * 只说三件事：几秒后重启、为什么要重启（让隐藏配置生效）、重启完回来自动接着跑。
 * 两个按钮给用户掌控感：「立即重启」和「稍后我自己重启」（后者之后打开 App 会自动续跑）。
 */
@Composable
private fun RebootCountdownOverlay(
    seconds: Int,
    onRebootNow: () -> Unit,
    onRebootLater: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = seconds.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.reboot_countdown_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reboot_countdown_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onRebootNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                ) {
                    Text(stringResource(R.string.reboot_countdown_now), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRebootLater, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.reboot_countdown_later),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 专题跑完后的全屏庆祝。
 *
 * 只讲两件用户关心的事：①这套配置已经装好了；②去自检看实际效果，报红就发截图给 AI 助手。
 * 不写「全部成功」「100% 通过」这类我们没法保证的话——红不红要跑完自检才知道。
 */
@Composable
private fun ColumnCelebrationOverlay(
    onOpenSelfCheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    val konfetti = rememberKonfettiState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var shown by remember { mutableStateOf(false) }

    // 彩带配色跟主题走，再补三枚固定亮色。不用纯绿 / 纯红：这两个色在本 App 里已经有
    // 「通过 / 失败」的语义，撒进庆祝画面会被读错。
    val partyColors = remember(colors.primary, colors.tertiary) {
        listOf(
            colors.primary.toArgb(),
            colors.tertiary.toArgb(),
            0xFFFCE18A.toInt(),
            0xFFFF726D.toInt(),
            0xFFB48DEF.toInt(),
        )
    }
    // 第一波：从卡片背后向上炸开，星形 + 方形 + 圆形混着撒，铺满整屏。
    val burst = remember(partyColors) {
        Party(
            speed = 12f,
            maxSpeed = 34f,
            damping = 0.88f,
            spread = 360,
            colors = partyColors,
            position = Position.Relative(0.5, 0.34),
            size = listOf(Size.SMALL, Size.MEDIUM, Size.LARGE),
            shapes = listOf(Shape.Square, Shape.Circle, Shape.Polygon.STAR),
            timeToLive = 2800L,
            emitter = Emitter(duration = 140, TimeUnit.MILLISECONDS).max(140),
        )
    }
    // 第二波：延迟一点从屏幕顶端往下飘，卡片出现之后画面里还有东西在动，不会一下子静掉。
    val rain = remember(partyColors) {
        Party(
            speed = 0f,
            maxSpeed = 16f,
            damping = 0.94f,
            angle = Angle.BOTTOM,
            spread = 70,
            colors = partyColors,
            position = Position.Relative(0.5, -0.08),
            size = listOf(Size.SMALL, Size.MEDIUM),
            shapes = listOf(Shape.Square, Shape.Circle),
            timeToLive = 3400L,
            delay = 320,
            emitter = Emitter(duration = 1100, TimeUnit.MILLISECONDS).perSecond(85),
        )
    }
    // 系统里关了「动画时长缩放」（无障碍设置）就不撒彩带，只留一次静态呈现。
    val reduceMotion = remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }

    LaunchedEffect(Unit) {
        shown = true
        if (reduceMotion) return@LaunchedEffect
        konfetti.start(burst)
        kotlinx.coroutines.delay(120)
        konfetti.start(rain)
    }

    val cardScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.72f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 360f),
        label = "celebration-card",
    )
    val emojiScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.2f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 240f),
        label = "celebration-emoji",
    )
    val emojiTilt by animateFloatAsState(
        targetValue = if (shown) 0f else -22f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 150f),
        label = "celebration-tilt",
    )
    // 卡片背后一圈呼吸光晕：给「这一刻」一点重量，透明度压得很低，不抢文字对比度。
    val glow = rememberInfiniteTransition(label = "celebration-glow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "celebration-glow-alpha",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f))) {
            // 彩带画在卡片下面：粒子从卡片背后飞出来，不会糊住文字。
            KonfettiView(state = konfetti, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .graphicsLayer {
                            scaleX = cardScale
                            scaleY = cardScale
                        }
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surface,
                    tonalElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                colors.primary.copy(alpha = glowAlpha),
                                                Color.Transparent,
                                            ),
                                        ),
                                        shape = CircleShape,
                                    ),
                            )
                            Text(
                                text = "\uD83C\uDF89",
                                modifier = Modifier.graphicsLayer {
                                    scaleX = emojiScale
                                    scaleY = emojiScale
                                    rotationZ = emojiTilt
                                },
                                fontSize = 60.sp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.column_celebrate_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.column_celebrate_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(22.dp))
                        Button(
                            onClick = onOpenSelfCheck,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp),
                        ) {
                            Text(stringResource(R.string.column_celebrate_self_check))
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.column_celebrate_done))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Miuix 皮肤
//
// 下面这一组和上面对应位置的 Material 版一一对应（命名带 Miuix 后缀），行为、文案、状态机完全一样，
// 只把外壳换成 Miuix 的语言：AppScaffold / TopAppBar 做骨架，AppCard 做卡片，
// MiuixButton / TextButton 做动作，颜色全部取自 MiuixTheme.colorScheme。
// ---------------------------------------------------------------------------------------------

/**
 * Miuix 皮肤下的执行向导。
 *
 * 与 Material 版共用同一套状态、同一批回调，只换外壳。两个对话框（高风险步骤确认、一键执行预检）
 * 仍是 Material 组件：Miuix 没有可靠的对话框 API，硬造一个只会和行为约定打架。调用处用
 * [AiAssistantMaterialTheme] 单独包一层同源 MD3 调色板，免得弹框落回框架默认配色。
 */
@Composable
private fun ColumnWizardScreenMiuix(
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
    onOpenAi: () -> Unit,
    onLongPressTitle: () -> Unit = {},
) {
    val colorScheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    // 自定义背景图开着时，顶栏要透明、列表要挂上 backdrop，底图才透得上来。
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val barColor = if (backdrop != null || hasCustomBackground) {
        Color.Transparent
    } else {
        colorScheme.surface
    }
    val layoutDirection = LocalLayoutDirection.current

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
        // 弹框本身是 Material 组件，单独套一层同源调色板（Material 模式下这一层直通）。
        AiAssistantMaterialTheme {
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
    }

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onLongPressTitle() })
                    },
                ) {
                TopAppBar(
                    title = topic.title,
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack, minWidth = 36.dp, minHeight = 36.dp) {
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
                    actions = {},
                    scrollBehavior = scrollBehavior,
                )
                }
            }
        },
        bottomBar = {
            if (!state.done) {
                WizardBottomBarMiuix(
                    total = total,
                    finished = finished,
                    started = started,
                    running = state.running,
                    onRunAll = onRunAll,
                )
            }
        },
        popupHost = {},
        // 左右让给系统栏与挖孔；上下由顶栏、列表 contentPadding 与底部操作条各自负责，
        // 否则列表会被顶栏和导航栏各让一次，页面上下多出一截空白。
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                // 顶栏的收起动画要靠 nestedScroll 连接列表的滚动：漏了它顶栏就钉死不动。
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.health != null) {
                item(key = "wizard-health") {
                    ColumnHealthCardMiuix(report = state.health)
                }
            } else {
                // 体检是 root 探针，需要几秒；给个明确的中态，别让人以为这块忘了渲染。
                item(key = "wizard-health-scanning") {
                    MiuixText(
                        text = stringResource(R.string.column_wizard_health_scanning),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
            if (needsKeybox && !state.keyboxReady) {
                item(key = "wizard-keybox") {
                    WizardKeyboxCardMiuix(
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
                item(key = "wizard-error") { WizardErrorBannerMiuix(state.error) }
            }
            if (orderedStates.isEmpty()) {
                item(key = "wizard-empty") {
                    MiuixText(
                        text = stringResource(R.string.column_wizard_empty),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                    )
                }
            }
            itemsIndexed(orderedStates, key = { _, it -> it.step.id }) { index, uiState ->
                WizardStepCardMiuix(
                    index = index + 1,
                    uiState = uiState,
                    busy = state.running,
                    installed = uiState.step.source.packageName.trim() in state.installedApps,
                    optedIn = uiState.step.id in state.optedIn,
                    onToggleOptIn = { onToggleOptIn(uiState.step) },
                    onRun = { requestRun(uiState.step) },
                    onSkip = { onSkipStep(uiState.step) },
                    onUpgrade = { onUpgradeStep(uiState.step) },
                    onMarkDone = { onMarkDone(uiState.step) },
                    onOpenAi = onOpenAi,
                )
            }
            if (state.log.isNotEmpty()) {
                item(key = "wizard-log") {
                    WizardLogCardMiuix(
                        log = state.log,
                        expanded = logExpanded,
                        onToggle = { logExpanded = !logExpanded },
                    )
                }
            }
            if (state.done) {
                item(key = "wizard-checks") {
                    WizardChecksCardMiuix(
                        checks = state.checks,
                        onRollback = onRollback,
                        busy = state.running,
                    )
                }
            }
        }
    }
}

/** 设备现状体检卡（Miuix）。只提示「之前配过一部分」，绝不用来决定跳过哪一步。 */
@Composable
private fun ColumnHealthCardMiuix(report: ColumnHealthReport) {
    // 与 Material 版同一套交互：默认只给结论，点标题行展开九项明细。
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colorScheme = MiuixTheme.colorScheme
    val missing = report.checks.filter { !it.passed }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixText(
                text = stringResource(R.string.column_health_title_short),
                modifier = Modifier.weight(1f),
                color = colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            MiuixText(
                text = stringResource(
                    R.string.column_health_score,
                    report.passedCount,
                    report.totalCount,
                ),
                color = if (missing.isEmpty()) successGreenMiuix() else colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(2.dp))
            MiuixIcon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.column_health_collapse else R.string.column_health_expand,
                ),
                modifier = Modifier.size(18.dp),
                tint = colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(10.dp))
        HealthLevelRowMiuix(
            ok = report.dayLevelReady,
            title = stringResource(R.string.column_health_day_level),
            okText = stringResource(R.string.column_health_ready),
            noText = stringResource(R.string.column_health_not_ready),
        )
        Spacer(Modifier.height(6.dp))
        HealthLevelRowMiuix(
            ok = report.integrityLevelReady,
            title = stringResource(R.string.column_health_integrity_level),
            okText = stringResource(R.string.column_health_ready),
            noText = stringResource(R.string.column_health_not_ready),
        )
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            MiuixHorizontalDivider()
            Spacer(Modifier.height(8.dp))
            report.checks.forEachIndexed { index, check ->
                if (index > 0) Spacer(Modifier.height(6.dp))
                HealthCheckDetailRowMiuix(check)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            MiuixText(
                text = if (missing.isEmpty()) {
                    stringResource(R.string.column_health_all_pass)
                } else {
                    stringResource(R.string.column_health_missing_hint, missing.size)
                },
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }
    }
}

/** 明细里的一行（Miuix）：通过/未通过图标 + 探针原话。 */
@Composable
private fun HealthCheckDetailRowMiuix(check: ColumnHealthCheck) {
    val colorScheme = MiuixTheme.colorScheme
    val tint = if (check.passed) successGreenMiuix() else colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MiuixIcon(
            imageVector = if (check.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp),
            tint = tint,
        )
        MiuixText(
            text = check.detail.ifEmpty {
                stringResource(
                    if (check.passed) R.string.column_wizard_check_pass else R.string.column_wizard_check_fail,
                )
            },
            modifier = Modifier.weight(1f),
            color = colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
        )
    }
}

/** 体检卡里的一条「档位是否就绪」。 */
@Composable
private fun HealthLevelRowMiuix(ok: Boolean, title: String, okText: String, noText: String) {
    val colorScheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) colorScheme.primary else colorScheme.outline),
        )
        MiuixText(
            text = title,
            modifier = Modifier.weight(1f),
            color = colorScheme.onSurface,
            fontSize = 14.sp,
        )
        MiuixText(
            text = if (ok) okText else noText,
            color = if (ok) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
        )
    }
}

/** 密钥材料提示卡（Miuix）。用户选自己信任的那份 keybox.xml，说明它会被写到哪里。 */
@Composable
private fun WizardKeyboxCardMiuix(error: String, onPick: () -> Unit) {
    val colorScheme = MiuixTheme.colorScheme
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        MiuixText(
            text = stringResource(R.string.column_wizard_keybox_title),
            color = colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        MiuixText(
            text = stringResource(R.string.column_wizard_keybox_body),
            color = colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            MiuixText(text = error, color = colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        MiuixButton(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MiuixText(text = stringResource(R.string.column_wizard_keybox_pick), fontSize = 15.sp)
        }
    }
}

/** 全局错误条（Miuix）：例如密钥拉取失败导致整个专题都跑不下去，说清原因比让每步各自报错有用。 */
@Composable
private fun WizardErrorBannerMiuix(message: String) {
    val colorScheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixIcon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colorScheme.onErrorContainer,
            )
            MiuixText(
                text = message,
                modifier = Modifier.weight(1f),
                color = colorScheme.onErrorContainer,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * 步骤卡（Miuix）。
 *
 * 和 Material 版同样的信息密度：状态图标、标题、kind 标签、状态文字、说明（超 3 行折叠）、
 * 官方入口、升级提示、失败原因，右侧挂动作；命令原文单独占一整行，免得在窄列里折行。
 */
@Composable
private fun WizardStepCardMiuix(
    index: Int,
    uiState: ColumnStepUiState,
    busy: Boolean,
    installed: Boolean,
    optedIn: Boolean,
    onToggleOptIn: () -> Unit,
    onRun: () -> Unit,
    onSkip: () -> Unit,
    onUpgrade: () -> Unit,
    onMarkDone: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    // 「打开官方页面」要用它 startActivity；Composable 里取一次，别在 onClick 里取。
    val context = LocalContext.current
    val step = uiState.step
    val skipped = uiState.status == ColumnStepStatus.SKIPPED
    val statusColor = wizardStatusColorMiuix(uiState.status)
    // note 常是整段说明，全展开会把卡片撑得很高：默认只看 3 行，超出时才给「展开 / 收起」。
    var noteExpanded by rememberSaveable(step.id) { mutableStateOf(false) }
    // 折叠时的截断点：-1 = 还没量过。量到之后固定下来，避免每帧重排；
    // 有了它才能把「…展开」正好接在被截断的那一行末尾，而不是另起一行。
    var noteCut by remember(step.id) { mutableStateOf(-1) }
    val canSkip = step.optional && (
        uiState.status == ColumnStepStatus.PENDING ||
            uiState.status == ColumnStepStatus.FAILED ||
            uiState.status == ColumnStepStatus.NEEDS_USER
        )

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        // 标题行：序号/状态徽章 + 标题 + 右侧唯一动作。和 Material 版同一套结构。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepBadgeMiuix(index = index, status = uiState.status)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MiuixText(
                    text = step.title,
                    color = colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (skipped) TextDecoration.LineThrough else null,
                )
                // 元信息压成一行：动作类型 · 状态 · 可选。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    MiuixText(
                        text = stepKindLabel(step.kind),
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                    )
                    MiuixText(
                        text = "·",
                        color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                    MiuixText(
                        text = if (installed && uiState.status == ColumnStepStatus.PENDING) {
                            stringResource(R.string.column_wizard_installed)
                        } else {
                            stringResource(statusLabel(uiState.status))
                        },
                        color = if (installed && uiState.status == ColumnStepStatus.PENDING) {
                            successGreenMiuix()
                        } else {
                            statusColor
                        },
                        fontSize = 11.sp,
                    )
                    if (step.optional) {
                        MiuixText(
                            text = "·",
                            color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        )
                        MiuixText(
                            text = stringResource(R.string.module_column_step_optional),
                            color = colorScheme.onSurfaceVariantSummary,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (step.note.isNotEmpty()) {
                    // 与 Material 版同一套交互：「展开 / 收起」接在正文末尾、整段可点。
                    val noteSuffix = stringResource(
                        if (noteExpanded) R.string.column_wizard_note_less
                        else R.string.column_wizard_note_more,
                    )
                    val noteReserve = noteSuffix.length + 1
                    val noteDisplay = when {
                        noteExpanded -> step.note + "  " + noteSuffix
                        noteCut in 1 until step.note.length ->
                            step.note.substring(0, noteCut) + "…" + noteSuffix
                        else -> step.note
                    }
                    MiuixText(
                        text = noteDisplay,
                        modifier = Modifier.clickable { noteExpanded = !noteExpanded },
                        color = colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = if (noteExpanded) Int.MAX_VALUE else NOTE_COLLAPSED_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layout ->
                            if (!noteExpanded && noteCut < 0 && layout.hasVisualOverflow) {
                                noteCut = (layout.getLineEnd(NOTE_COLLAPSED_LINES - 1, true) - noteReserve)
                                    .coerceAtLeast(1)
                            }
                        },
                    )
                }
                // 官方只肯走群/网盘发的检测器（春秋），商店给不了直链，但能把官方入口摆出来；
                // 链接来自索引仓，只放行 http/https，点了也只是打开浏览器，不执行任何东西。
                if (step.link.isNotEmpty()) {
                    // 与 Material 版同一套：sukisu://ai 是站内「进入 AI 助手」，其余按外链处理。
                    val isAiEntry = step.link == COLUMN_LINK_AI
                    MiuixTextButton(
                        text = stringResource(
                            if (isAiEntry) R.string.column_wizard_open_ai else R.string.column_wizard_open_link,
                        ),
                        onClick = {
                            if (isAiEntry) {
                                onOpenAi()
                            } else {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(step.link)))
                                }
                            }
                        },
                        minWidth = 0.dp,
                        minHeight = 0.dp,
                        insideMargin = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                    )
                }
                // 已装但上游有新版：跳过不等于「已经是最新」。只有用户点这里才会带 force
                // 覆盖安装——向导不会替他把旧模块换掉。
                uiState.upgrade?.let { upgrade ->
                    MiuixTextButton(
                        text = stringResource(
                            R.string.column_wizard_upgrade,
                            upgrade.latestVersion,
                        ),
                        onClick = onUpgrade,
                        enabled = !busy,
                        minWidth = 0.dp,
                        minHeight = 0.dp,
                        insideMargin = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    )
                }
                if (uiState.message.isNotEmpty()) {
                    MiuixText(
                        text = uiState.message,
                        color = statusColor,
                        fontSize = 13.sp,
                    )
                }
                // 可选步骤的「纳入一键执行」开关挪到底部，和状态徽章分开：
                // 以前它和状态图标并排出现两个圆点，看起来像渲染错了。
                if (step.optIn || canSkip) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (step.optIn) {
                            MiuixCheckbox(
                                state = if (optedIn) ToggleableState.On else ToggleableState.Off,
                                onClick = onToggleOptIn,
                                enabled = !busy,
                            )
                            MiuixText(
                                text = stringResource(R.string.column_wizard_opt_in),
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (canSkip) {
                            MiuixTextButton(
                                text = stringResource(R.string.column_wizard_skip),
                                onClick = onSkip,
                                enabled = !busy,
                                minWidth = 0.dp,
                                minHeight = 0.dp,
                                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            StepPrimaryActionMiuix(
                status = uiState.status,
                enabled = !busy,
                installed = installed,
                onRun = onRun,
                onReinstall = onUpgrade,
                onMarkDone = onMarkDone,
            )
        }
        // 命令原文单独占一整行：右侧按钮占掉宽度后，窄列里折行的 shell 命令既难读也容易被误抄。
        if (step.kind == STEP_KIND_RUN_COMMAND && step.command.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            CommandBlockMiuix(command = step.command)
        }
    }
}

/** 右侧动作区（Miuix）。没动作的步骤（已成功/已跳过）不占位，标题就能多留一点宽度。 */
/**
 * 标题右侧唯一的动作（Miuix）：执行 / 重试 / 我已处理。
 *
 * 只放一个按钮。跳过不是主路径，挪到卡片下方；两个按钮并排是标题被挤成三行的直接原因。
 */
@Composable
private fun StepPrimaryActionMiuix(
    status: ColumnStepStatus,
    enabled: Boolean,
    installed: Boolean = false,
    onRun: () -> Unit,
    /** 已安装时点「重新安装」：带 force 重跑，覆盖已装的版本。 */
    onReinstall: () -> Unit,
    onMarkDone: () -> Unit,
) {
    // NEEDS_USER 是「等你动手」，点它是「我做完了」，不是「重跑一遍」——重跑只会再下载一次、
    // 再弹一次安装器。所以这里接的是 onMarkDone，不是 onRun。
    val label = when (status) {
        ColumnStepStatus.PENDING ->
            if (installed) R.string.column_wizard_reinstall else R.string.column_wizard_run
        ColumnStepStatus.RUNNING -> R.string.column_wizard_run
        ColumnStepStatus.FAILED -> R.string.column_wizard_retry
        ColumnStepStatus.NEEDS_USER -> R.string.column_wizard_done_step
        ColumnStepStatus.SUCCESS, ColumnStepStatus.SKIPPED -> null
    }
    if (label == null) return
    MiuixTextButton(
        text = stringResource(label),
        onClick = when {
            status == ColumnStepStatus.NEEDS_USER -> onMarkDone
            status == ColumnStepStatus.PENDING && installed -> onReinstall
            else -> onRun
        },
        enabled = enabled && status != ColumnStepStatus.RUNNING,
        minWidth = 0.dp,
        minHeight = 0.dp,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
    )
}

/** 步骤状态图标（Miuix）：空心圆 / 转圈 / 绿勾 / 红叉 / 灰色横线 / 人形。 */
/**
 * 步骤徽章（Miuix）：待执行时显示序号，其余状态显示对应图标。
 *
 * 以前一律画空心圆，和可选步骤的 Checkbox 并排就成了「两个圆圈」，看不出哪个是序号。
 */
@Composable
private fun StepBadgeMiuix(index: Int, status: ColumnStepStatus) {
    val colorScheme = MiuixTheme.colorScheme
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        when (status) {
            ColumnStepStatus.PENDING -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                MiuixText(
                    text = index.toString(),
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                )
            }

            ColumnStepStatus.RUNNING -> MiuixCircularProgressIndicator(
                progress = null,
                size = 18.dp,
                strokeWidth = 2.dp,
            )

            ColumnStepStatus.SUCCESS -> MiuixIcon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = successGreenMiuix(),
            )

            ColumnStepStatus.FAILED -> MiuixIcon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.error,
            )

            ColumnStepStatus.SKIPPED -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                MiuixText(
                    text = index.toString(),
                    color = colorScheme.outline,
                    fontSize = 11.sp,
                )
            }

            ColumnStepStatus.NEEDS_USER -> MiuixIcon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.primary,
            )
        }
    }
}

/** 命令原文块（Miuix）：等宽字体 + 浅底，长按可以选中复制。 */
@Composable
private fun CommandBlockMiuix(command: String) {
    val colorScheme = MiuixTheme.colorScheme
    SelectionContainer {
        MiuixText(
            text = command,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            color = colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
        )
    }
}

/** 执行日志卡（Miuix）：默认只露最后几行，展开后整段可复制。 */
@Composable
private fun WizardLogCardMiuix(log: List<String>, expanded: Boolean, onToggle: () -> Unit) {
    val colorScheme = MiuixTheme.colorScheme
    val visible = remember(log, expanded) {
        if (expanded) log else log.takeLast(LOG_COLLAPSED_LINES)
    }
    val logText = remember(visible) { visible.joinToString("\n") }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MiuixText(
                text = stringResource(R.string.column_wizard_log_title),
                modifier = Modifier.weight(1f),
                color = colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (log.size > LOG_COLLAPSED_LINES) {
                MiuixTextButton(
                    text = stringResource(
                        if (expanded) {
                            R.string.column_wizard_collapse_log
                        } else {
                            R.string.column_wizard_expand_log
                        },
                    ),
                    onClick = onToggle,
                    minWidth = 0.dp,
                    minHeight = 0.dp,
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            MiuixText(
                text = logText,
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.dp.value.sp,
            )
        }
    }
}

/** 完成后的自检结果 + 回滚（Miuix）。 */
@Composable
private fun WizardChecksCardMiuix(
    checks: List<ColumnCheckResult>,
    onRollback: () -> Unit,
    busy: Boolean,
) {
    val colorScheme = MiuixTheme.colorScheme
    var confirmRollback by remember { mutableStateOf(false) }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        if (checks.isNotEmpty()) {
            MiuixText(
                text = stringResource(R.string.column_wizard_checks_title),
                color = colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                checks.forEach { check -> CheckRowMiuix(check = check) }
            }
            Spacer(Modifier.height(12.dp))
        }
        MiuixButton(
            onClick = { confirmRollback = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = MiuixButtonDefaults.buttonColors(
                color = colorScheme.errorContainer,
                contentColor = colorScheme.onErrorContainer,
            ),
        ) {
            MiuixText(text = stringResource(R.string.column_wizard_rollback), fontSize = 15.sp)
        }
    }
    if (confirmRollback) {
        // 回滚是不可撤销的破坏性动作，和 Material 版一样先过一遍确认框。
        AiAssistantMaterialTheme {
            ExpressiveConfirmDialog(
                title = stringResource(R.string.column_wizard_rollback_confirm_title),
                confirmText = stringResource(R.string.column_wizard_rollback_confirm_confirm),
                message = stringResource(R.string.column_wizard_rollback_confirm_body),
                dismissText = stringResource(R.string.column_wizard_cancel),
                onConfirm = {
                    confirmRollback = false
                    onRollback()
                },
                onDismiss = { confirmRollback = false },
            )
        }
    }
}

/** 一条自检结果（Miuix）。 */
@Composable
private fun CheckRowMiuix(check: ColumnCheckResult) {
    val colorScheme = MiuixTheme.colorScheme
    val tint = if (check.passed) successGreenMiuix() else colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiuixIcon(
            imageVector = if (check.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MiuixText(
                text = stringResource(
                    if (check.passed) {
                        R.string.column_wizard_check_pass
                    } else {
                        R.string.column_wizard_check_fail
                    },
                ),
                color = tint,
                fontSize = 13.sp,
            )
            if (check.detail.isNotEmpty()) {
                MiuixText(
                    text = check.detail,
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * 底部固定操作条（Miuix）。
 *
 * 未开始：一个「开始第一步」，省得用户去列表里找第一个按钮；进行中：进度条 + 「3/8」，让人知道还剩
 * 几步；跑完之后整条隐藏，把屏幕让给自检结果与回滚。
 */
/**
 * 底部固定操作条（Miuix）。
 *
 * 未开始时两个动作并排一行：「一键执行」占满剩余宽度，右边是「开始第一步」文字入口；
 * 进行中显示进度和「一键执行剩余步骤」；跑完之后整条隐藏，把屏幕让给自检结果与回滚。
 *
 * 以前两个按钮上下堆叠、各自 48dp 再加内边距，底栏吃掉近 200px，正好压住最后一张步骤卡。
 */
@Composable
private fun WizardBottomBarMiuix(
    total: Int,
    finished: Int,
    started: Boolean,
    running: Boolean,
    onRunAll: () -> Unit,
) {
    if (total == 0) return
    val colorScheme = MiuixTheme.colorScheme
    MiuixSurface(color = colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!started) {
                // 与 Material 版一致：只留「一键执行」一个入口。
                MiuixButton(
                    onClick = onRunAll,
                    enabled = !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    cornerRadius = 23.dp,
                    colors = MiuixButtonDefaults.buttonColorsPrimary(),
                ) {
                    MiuixText(
                        text = stringResource(R.string.column_wizard_run_all),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiuixLinearProgressIndicator(
                        progress = if (total == 0) 0f else finished.toFloat() / total,
                        modifier = Modifier.weight(1f),
                    )
                    MiuixText(
                        text = stringResource(R.string.column_wizard_progress, finished, total),
                        color = colorScheme.onSurface,
                        fontSize = 13.sp,
                    )
                }
                // 重启回来接着做时最有用：已经处理过的步骤会被跳过，只跑重启后才允许的那几步。
                MiuixButton(
                    onClick = onRunAll,
                    enabled = !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    cornerRadius = 22.dp,
                    // 和「一键执行」同一套主色：这里的「执行剩余步骤」也是主操作，不能用默认灰底。
                    colors = MiuixButtonDefaults.buttonColorsPrimary(),
                ) {
                    MiuixText(
                        text = stringResource(R.string.column_wizard_run_all_rest),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** 按底色明暗取成功色（Miuix）：AMOLED / 深色主题下用浅一档的绿，浅色主题下用深绿。 */
@Composable
private fun successGreenMiuix(): Color =
    if (MiuixTheme.colorScheme.surface.luminance() < 0.5f) SuccessGreenDark else SuccessGreenLight

/** 步骤状态文字的颜色（Miuix）。Miuix 没有 tertiary，NEEDS_USER 用 primary 顶上。 */
@Composable
private fun wizardStatusColorMiuix(status: ColumnStepStatus): Color = when (status) {
    ColumnStepStatus.SUCCESS -> successGreenMiuix()
    ColumnStepStatus.FAILED -> MiuixTheme.colorScheme.error
    ColumnStepStatus.NEEDS_USER -> MiuixTheme.colorScheme.primary
    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
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
    onOpenAi: () -> Unit,
    onLongPressTitle: () -> Unit = {},
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
                title = {
                    Text(
                        text = topic.title,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onLongPressTitle() })
                        },
                    )
                },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                actions = {},
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
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 顶栏的收起动画要靠 nestedScroll 连接列表的滚动：漏了它顶栏就钉死不动。
                .nestedScroll(scrollBehavior.nestedScrollConnection)
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
            } else {
                // 体检是 root 探针，需要几秒；给个明确的中态，别让人以为这块忘了渲染。
                item(key = "wizard-health-scanning") {
                    Text(
                        text = stringResource(R.string.column_wizard_health_scanning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
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
            itemsIndexed(orderedStates, key = { _, it -> it.step.id }) { index, uiState ->
                WizardStepCard(
                    index = index + 1,
                    uiState = uiState,
                    busy = state.running,
                    installed = uiState.step.source.packageName.trim() in state.installedApps,
                    optedIn = uiState.step.id in state.optedIn,
                    onToggleOptIn = { onToggleOptIn(uiState.step) },
                    onRun = { requestRun(uiState.step) },
                    onSkip = { onSkipStep(uiState.step) },
                    onUpgrade = { onUpgradeStep(uiState.step) },
                    onMarkDone = { onMarkDone(uiState.step) },
                    onOpenAi = onOpenAi,
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

/**
 * 向导里的卡片外壳（Material 3 Expressive）。
 *
 * Miuix 皮肤不用这个函数：那边有自己的 WizardStepCardMiuix 等实现，圆角、底色与点击反馈各自
 * 符合自己的设计语言，不必在一个外壳里分流两套设计语言。
 */
@Composable
private fun WizardCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    content: @Composable () -> Unit,
) {
    TonalCard(modifier = modifier, containerColor = containerColor, onClick = onClick) {
        Column { content() }
    }
}

/**
 * 密钥材料提示卡。
 *
 * 三绿（Play Integrity）绕不开一份 Google 认证密钥，而这份材料既不能由商店托管、也不该让用户去
 * 来路不明的地方下载。所以这里只做一件事：让用户选自己信任的那份 keybox.xml，说明它会被写到哪里。
 */
@Composable
private fun WizardKeyboxCard(error: String, onPick: () -> Unit) {
    WizardCard {
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

/** 全局错误条：例如密钥拉取失败导致整个专题都跑不下去，这时候说清楚原因比让每一步各自报错有用。 */
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
    index: Int,
    uiState: ColumnStepUiState,
    busy: Boolean,
    installed: Boolean,
    optedIn: Boolean,
    onToggleOptIn: () -> Unit,
    onRun: () -> Unit,
    onSkip: () -> Unit,
    onUpgrade: () -> Unit,
    onMarkDone: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val step = uiState.step
    val skipped = uiState.status == ColumnStepStatus.SKIPPED
    // note 常是整段说明，全展开会把卡片撑得很高：默认只看 3 行，超出时才给「展开 / 收起」。
    var noteExpanded by rememberSaveable(step.id) { mutableStateOf(false) }
    // 折叠时的截断点：-1 = 还没量过。量到之后固定下来，避免每帧重排；
    // 有了它才能把「…展开」正好接在被截断的那一行末尾，而不是另起一行。
    var noteCut by remember(step.id) { mutableStateOf(-1) }
    val context = LocalContext.current
    val canSkip = step.optional && (
        uiState.status == ColumnStepStatus.PENDING ||
            uiState.status == ColumnStepStatus.FAILED ||
            uiState.status == ColumnStepStatus.NEEDS_USER
        )

    WizardCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 标题行：左边一个序号/状态徽章，右边只留一个动作。
            // 以前把「执行」「跳过」并排塞在标题右边，带括号的长标题会被挤成三行。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StepBadge(index = index, status = uiState.status)
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (skipped) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StepPrimaryAction(
                    status = uiState.status,
                    enabled = !busy,
                    installed = installed,
                    onRun = onRun,
                    onReinstall = onUpgrade,
                    onMarkDone = onMarkDone,
                )
            }
            // 元信息压成一行：动作类型 · 状态 · 可选。原来分成两行、又和标题挤在一列，看着像错位。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stepKindLabel(step.kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Text(
                    text = if (installed && uiState.status == ColumnStepStatus.PENDING) {
                        stringResource(R.string.column_wizard_installed)
                    } else {
                        stringResource(statusLabel(uiState.status))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (installed && uiState.status == ColumnStepStatus.PENDING) {
                        successGreen()
                    } else {
                        statusColor(uiState.status)
                    },
                )
                if (step.optional) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = stringResource(R.string.module_column_step_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (step.note.isNotEmpty()) {
                // 「展开 / 收起」不进布局，直接接在正文末尾：折叠时它落在我截断的那一行，
                // 展开时跟在最后一句后面。整段文字都能点，不用去瞄一个小按钮。
                val noteSuffix = stringResource(
                    if (noteExpanded) R.string.column_wizard_note_less
                    else R.string.column_wizard_note_more,
                )
                val noteReserve = noteSuffix.length + 1
                val noteDisplay = when {
                    noteExpanded -> step.note + "  " + noteSuffix
                    noteCut in 1 until step.note.length ->
                        step.note.substring(0, noteCut) + "…" + noteSuffix
                    else -> step.note
                }
                Text(
                    text = noteDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (noteExpanded) Int.MAX_VALUE else NOTE_COLLAPSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout ->
                        // 第一遍按全文量：被省略号截断就记住第三行的可见末尾，留出后缀宽度再截，
                        // 第二遍拼上「…展开」正好填满那一行，不会再被省略号吃掉。
                        if (!noteExpanded && noteCut < 0 && layout.hasVisualOverflow) {
                            noteCut = (layout.getLineEnd(NOTE_COLLAPSED_LINES - 1, true) - noteReserve)
                                .coerceAtLeast(1)
                        }
                    },
                    modifier = Modifier.clickable { noteExpanded = !noteExpanded },
                )
            }
            // 官方只肯走群/网盘发的检测器（春秋），商店给不了直链，但能把官方入口摆出来；
            // 链接来自索引仓，只放行 http/https，点了也只是打开浏览器，不执行任何东西。
            // 做成纯文字链接 + 小箭头：一个次要入口不该比「执行」还显眼。
            if (step.link.isNotEmpty()) {
                // 索引里的 link 有两种：外部网页（春秋官网之类）和站内入口。后者写成 sukisu://ai，
                // 点了直接进 AI 助手发截图，省得先跳浏览器再让用户自己找回来。
                val isAiEntry = step.link == COLUMN_LINK_AI
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            if (isAiEntry) {
                                onOpenAi()
                            } else {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(step.link)))
                                }
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (isAiEntry) R.string.column_wizard_open_ai else R.string.column_wizard_open_link,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (isAiEntry) {
                            Icons.Rounded.AutoAwesome
                        } else {
                            Icons.AutoMirrored.Rounded.OpenInNew
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
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
                        style = MaterialTheme.typography.labelMedium,
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
            if (step.kind == STEP_KIND_RUN_COMMAND && step.command.isNotEmpty()) {
                CommandBlock(command = step.command)
            }
            // 可选步骤的「纳入一键执行」开关放最后，和状态徽章分开：
            // 以前它和状态图标并排出现两个圆点，看起来像渲染错了。
            if (step.optIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = !busy) { onToggleOptIn() }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(
                        checked = optedIn,
                        onCheckedChange = { onToggleOptIn() },
                        enabled = !busy,
                        modifier = Modifier.scale(0.8f),
                    )
                    Text(
                        text = stringResource(R.string.column_wizard_opt_in),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (canSkip) {
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = onSkip,
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.column_wizard_skip),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (canSkip) {
                TextButton(
                    onClick = onSkip,
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
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
}

/** 右侧动作区。没动作的步骤（已成功/已跳过）不占位，标题就能多留一点宽度。 */
/**
 * 标题右侧唯一的动作：执行 / 重试 / 我已处理。
 *
 * 只放一个按钮。跳过不是主路径，挪到卡片下方；两个按钮并排是标题被挤成三行的直接原因。
 */
@Composable
private fun StepPrimaryAction(
    status: ColumnStepStatus,
    enabled: Boolean,
    installed: Boolean = false,
    onRun: () -> Unit,
    /** 已安装时点「重新安装」：带 force 重跑，覆盖已装的版本。 */
    onReinstall: () -> Unit,
    onMarkDone: () -> Unit,
) {
    // NEEDS_USER 是「等你动手」，点它是「我做完了」，不是「重跑一遍」——重跑只会再下载一次、
    // 再弹一次安装器。所以这里接的是 onMarkDone，不是 onRun。
    val label = when (status) {
        ColumnStepStatus.PENDING ->
            if (installed) R.string.column_wizard_reinstall else R.string.column_wizard_run
        ColumnStepStatus.RUNNING -> R.string.column_wizard_run
        ColumnStepStatus.FAILED -> R.string.column_wizard_retry
        ColumnStepStatus.NEEDS_USER -> R.string.column_wizard_done_step
        ColumnStepStatus.SUCCESS, ColumnStepStatus.SKIPPED -> null
    }
    if (label == null) return
    FilledTonalButton(
        onClick = when {
            status == ColumnStepStatus.NEEDS_USER -> onMarkDone
            status == ColumnStepStatus.PENDING && installed -> onReinstall
            else -> onRun
        },
        enabled = enabled && status != ColumnStepStatus.RUNNING,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 状态图标：空心圆 → 转圈 → 对勾；失败是叉，跳过是划线，需要用户处理是「人」。 */
/**
 * 步骤徽章：待执行时显示序号，其余状态显示对应图标。
 *
 * 以前一律画空心圆，和可选步骤的 Checkbox 并排就成了「两个圆圈」，看不出哪个是序号。
 */
@Composable
private fun StepBadge(index: Int, status: ColumnStepStatus) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        when (status) {
            ColumnStepStatus.PENDING -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
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
    WizardCard(modifier = Modifier.fillMaxWidth()) {
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
    WizardCard(modifier = Modifier.fillMaxWidth()) {
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
/**
 * 底部固定操作条。
 *
 * 未开始时两个动作并排一行：「一键执行」占满剩余宽度，右边是「开始第一步」文字入口；
 * 进行中显示进度和「一键执行剩余步骤」；跑完之后整条隐藏，把屏幕让给自检结果与回滚。
 *
 * 以前两个按钮上下堆叠、各自 48dp 再加内边距，底栏吃掉近 200px，正好压住最后一张步骤卡。
 */
@Composable
private fun WizardBottomBar(
    total: Int,
    finished: Int,
    started: Boolean,
    running: Boolean,
    onRunAll: () -> Unit,
) {
    if (total == 0) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!started) {
                // 只留一个入口：新手点它就是从头跑，老手点它就是接着跑，没必要拆成两个按钮
                // 让人选「一键执行」还是「开始第一步」——那两步做的是同一件事。
                Button(
                    onClick = onRunAll,
                    enabled = !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                ) {
                    Text(
                        text = stringResource(R.string.column_wizard_run_all),
                        style = MaterialTheme.typography.labelLarge,
                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text(
                        text = stringResource(R.string.column_wizard_run_all_rest),
                        style = MaterialTheme.typography.labelLarge,
                    )
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
    // 明细默认收起：进专题第一眼要的是「这台机器配过没有」这个结论，不是九行探针输出。
    // 想核对每一项再点开——结论文案与明细在同一张卡里，不跳页。
    var expanded by rememberSaveable { mutableStateOf(false) }
    val missing = report.checks.filter { !it.passed }
    WizardCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                    color = if (missing.isEmpty()) {
                        successGreen()
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.column_health_collapse else R.string.column_health_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
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
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                report.checks.forEachIndexed { index, check ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    HealthCheckDetailRow(check)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (missing.isEmpty()) {
                        stringResource(R.string.column_health_all_pass)
                    } else {
                        stringResource(R.string.column_health_missing_hint, missing.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 明细里的一行：通过/未通过图标 + 探针原话（探针没给话就退回状态文字）。 */
@Composable
private fun HealthCheckDetailRow(check: ColumnHealthCheck) {
    val tint = if (check.passed) successGreen() else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (check.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp),
        )
        Text(
            text = check.detail.ifEmpty {
                stringResource(
                    if (check.passed) R.string.column_wizard_check_pass else R.string.column_wizard_check_fail,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
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
