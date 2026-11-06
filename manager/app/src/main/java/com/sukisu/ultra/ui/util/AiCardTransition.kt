package com.sukisu.ultra.ui.util

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.sukisu.ultra.ai.api.AiHomeCardBounds
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

/**
 * AI 助手的 Container Transform（容器变换）过渡。
 *
 * 页面按全尺寸布局，这里只把 graphicsLayer 的 clip 形状从卡片矩形逐渐扩到全屏；
 * MainActivity 的 entry 里再用同一进度缩放/平移整页，让内容与裁剪区严格对齐。
 * 拆成「裁剪形状 + 内容变换」两层，圆角和底板更好控制。
 *
 * miuix-nav 的 relativeDepth = animatedTop - entryIndex，因此：
 * - Incoming（正在进入的聊天页）：深度从 -1 变到 0；
 * - Outgoing（正在退出的聊天页）：深度从 0 变到 -1。
 * 两者统一取 progress = -depth（progress = 1 完全贴合卡片，0 为全屏），
 * 展开度 expansion = 1 - progress。
 *
 * Top（settled）与 Covered（被设置页等盖住）不执行变换，避免从设置页返回时聊天页
 * 被错误裁切。
 */
/**
 * 容器变换用的动效曲线：欠阻尼弹簧，收尾时略微过冲一点，带回弹感。
 * 阻尼比不压太低，避免真的像弹簧来回晃。
 */
private val AiNavMotion = NavMotion(
    // 阻尼比 0.72 大约 4% 过冲：收尾时能看出一点回弹，又不会像弹簧来回晃。
    commit = NavSettleSpec.Spring(
        dampingRatio = 0.72f,
        stiffness = 420f,
        clampOvershoot = false,
    ),
    cancel = NavSettleSpec.Spring(
        dampingRatio = 1f,
        stiffness = 380f,
        clampOvershoot = true,
    ),
    // 页面进出是程序化导航，用这一档；比手势 commit 再软一点，回缩更容易看见回弹。
    programmatic = NavSettleSpec.Spring(
        dampingRatio = 0.7f,
        stiffness = 400f,
        clampOvershoot = false,
    ),
)

val AiCardTransition = navGraphicsTransition(
    motion = AiNavMotion,
    // 默认过渡给下层盖一层 dim scrim，卡片刚点下时首页会先被压暗（像闪一下）；
    // 这里的页面很快完全盖住首页，不需要这层遮罩。
    scrim = { 0f },
) { scope ->
    val role = scope.role
    // 从设置页返回聊天页时聊天页同样是 Incoming，但那时不该再从卡片「长」一次；
    // 只有「从首页推入」才做容器变换，离场（Outgoing）则总是缩回卡片。
    val enteringFromHome = role == NavRole.Incoming &&
        scope.change !is NavChange.Pop && scope.change !is NavChange.MultiPop
    val leavingToHome = role == NavRole.Outgoing
    if ((!enteringFromHome && !leavingToHome) || !AiHomeCardBounds.valid) {
        return@navGraphicsTransition
    }

    // 不夹紧到 0..1：弹簧过冲时 expansion 会略小于 0（回缩时比卡片再小一点）或略大于 1，
    // 交给下面的形状/缩放去表现这点回弹。
    val expansion = 1f + scope.relativeDepth
    shape = ExpandingCardShape(expansion)
    clip = true
    // 页面整体保持不透明，只靠裁剪形状展示「卡片长成页面」。
    alpha = 1f
}

/** 把卡片归一化 bounds 插值成一个圆角矩形裁剪区。 */
private class ExpandingCardShape(private val expansion: Float) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val t = expansion
        val cardLeft = (AiHomeCardBounds.centerX - AiHomeCardBounds.width / 2f) * size.width
        val cardTop = (AiHomeCardBounds.centerY - AiHomeCardBounds.height / 2f) * size.height
        val cardRight = (AiHomeCardBounds.centerX + AiHomeCardBounds.width / 2f) * size.width
        val cardBottom = (AiHomeCardBounds.centerY + AiHomeCardBounds.height / 2f) * size.height

        val rect = Rect(
            left = lerp(cardLeft, 0f, t),
            top = lerp(cardTop, 0f, t),
            right = lerp(cardRight, size.width, t),
            bottom = lerp(cardBottom, size.height, t),
        )
        // 卡片圆角 28dp，展开到全屏时收成直角。
        val radius = with(density) { lerp(28.dp.toPx(), 0f, t).coerceAtLeast(0f) }
        val path = Path().apply {
            addRoundRect(RoundRect(rect, CornerRadius(radius)))
        }
        return Outline.Generic(path)
    }
}

/**
 * 首页作为容器变换的背景层：自身不动，也不要让默认过渡给被盖住的页面做透明度衰减，
 * 否则聊天页缩回卡片时会变成半透明，和首页叠影。
 */
val AiHomeBackgroundTransition = navGraphicsTransition(motion = AiNavMotion) { scope ->
    if (scope.role == NavRole.Covered) {
        alpha = 1f
    }
}
