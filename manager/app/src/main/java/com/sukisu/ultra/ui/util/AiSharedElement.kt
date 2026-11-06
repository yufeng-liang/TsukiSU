package com.sukisu.ultra.ui.util

import androidx.activity.compose.BackHandler
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.sukisu.ultra.ai.api.AiHomeCardBounds
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/** 首页 AI 卡片与聊天页共用的共享元素作用域（由 MainActivity 的 SharedTransitionLayout 提供）。 */
val LocalAiSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** 首页 AI 卡片与聊天页共用的共享元素状态。 */
val LocalAiSharedState = compositionLocalOf<SharedTransitionScope.SharedContentState?> { null }

/**
 * 共享元素状态：首页卡片和聊天页是同一个元素的两个形态。
 *
 * [pageVisible] 为 true 时卡片隐藏、页面显示，Compose 会在两者之间做真正的共享元素形变
 * （这就是 HyperOS 那种「一镜到底」的效果）。
 */
object AiSharedTransition {
    const val KEY = "ai-home-card"

    /** 聊天页这一侧是否可见（展开态/收起动画期间都是可见的）。 */
    var pageVisible by mutableStateOf(false)
        private set

    /** 首页卡片这一侧是否可见。 */
    var cardVisible by mutableStateOf(true)
        private set

    /** 收起动画进行中。 */
    var collapsing by mutableStateOf(false)
        private set

    fun open() {
        pageVisible = true
        cardVisible = false
    }

    /** 开始收起：页面侧退出共享元素、卡片先藏起来，交给页面自己的缩放动画。 */
    fun beginClose() {
        collapsing = true
        pageVisible = false
        cardVisible = false
    }

    /** 收起动画结束时把卡片重新露出来。 */
    fun revealCard() {
        cardVisible = true
        collapsing = false
    }

    fun reset() {
        pageVisible = false
        cardVisible = true
        collapsing = false
    }
}

/**
 * 给卡片或页面挂上共享元素。
 *
 * 只有在 MainActivity 提供了 [LocalAiSharedScope]/[LocalAiSharedState] 时才生效，
 * 因此在预览、测试等没有共享元素作用域的场合会自动退化成普通 Modifier。
 */
@Composable
fun Modifier.aiSharedElement(visible: Boolean): Modifier {
    val scope = LocalAiSharedScope.current ?: return this
    val state = LocalAiSharedState.current ?: return this
    return with(scope) {
        this@aiSharedElement.sharedElementWithCallerManagedVisibility(
            sharedContentState = state,
            visible = visible,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 220f) },
        )
    }
}

/**
 * 聊天页这一侧的共享元素容器：整页作为共享元素的「展开态」，收起时缩回首页卡片。
 *
 * 展开走官方共享元素（卡片矩形长成整页）；收起时改用整页等比缩回卡片矩形的缩放动画，
 * 因为「两侧同时可见」的收回方式会闪。返回键在这里等收起动画结束再出栈。
 */
@Composable
fun AiChatSharedPage(
    backEnabled: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    chat: @Composable (requestClose: () -> Unit) -> Unit,
) {
    val scopeCtx = rememberCoroutineScope()
    val pageBackground = MaterialTheme.colorScheme.background
    var closing by remember { mutableStateOf(false) }
    val closeProgress = remember { Animatable(0f) }

    // 展开：第一帧先让整页真正被测量到，再翻可见性，共享元素才有形变终点。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        AiSharedTransition.open()
    }
    DisposableEffect(Unit) {
        onDispose { AiSharedTransition.reset() }
    }

    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            // 收起交给自己的缩放动画：共享元素只负责「展开」，
            // 两个都可见的写法在收回时会闪，这里改成整页等比缩回卡片。
            AiSharedTransition.beginClose()
            scopeCtx.launch {
                closeProgress.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
                AiSharedTransition.revealCard()
                withFrameNanos { }
                onClose()
            }
        }
    }
    BackHandler(enabled = backEnabled, onBack = requestClose)

    val sharedModifier = if (closing) Modifier else Modifier.aiSharedElement(visible = AiSharedTransition.pageVisible)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(sharedModifier)
            .graphicsLayer {
                val p = closeProgress.value
                if (p > 0f) {
                    val cardW = AiHomeCardBounds.width.coerceIn(0.1f, 1f)
                    val cardH = AiHomeCardBounds.height.coerceIn(0.05f, 1f)
                    scaleX = lerp(1f, cardW, p)
                    scaleY = lerp(1f, cardH, p)
                    translationX = (AiHomeCardBounds.centerX - 0.5f) * size.width * p
                    translationY = (AiHomeCardBounds.centerY - 0.5f) * size.height * p
                    shape = RoundedCornerShape((28f * p).dp)
                    clip = p > 0.01f
                }
            }
            .background(pageBackground),
    ) {
        chat(requestClose)
    }
}
