package com.sukisu.ultra.ui.util

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.sukisu.ultra.ai.api.AiHomeCardBounds
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

/**
 * AI 助手的 Container Transform（容器变换）过渡。
 *
 * miuix-nav 的 relativeDepth = animatedTop - entryIndex，因此：
 * - Incoming（正在进入的聊天页）：深度从 -1 变到 0；
 * - Outgoing（正在退出的聊天页）：深度从 0 变到 -1。
 * 两者都取 progress = -depth（0 表示已停在目标页，1 表示完全贴合卡片）。
 *
 * 缩放围绕页面中心，同时把页面中心按 progress 平移向卡片中心；progress = 1 时页面
 * 正好覆盖卡片矩形，progress = 0 时恢复全屏。Top（settled）与 Covered（被设置页等
 * 盖住）不执行变换，避免从设置页返回时聊天页被错误缩小。
 */
val AiCardTransition = navGraphicsTransition { scope ->
    val role = scope.role
    val animating = role == NavRole.Incoming || role == NavRole.Outgoing
    val width = scope.layoutSize.width.toFloat()
    val height = scope.layoutSize.height.toFloat()
    if (!animating || !AiHomeCardBounds.valid || width <= 0f || height <= 0f) {
        return@navGraphicsTransition
    }

    val progress = (-scope.relativeDepth).coerceIn(0f, 1f)
    if (progress <= 0.01f) return@navGraphicsTransition

    val settled = 1f - progress
    scaleX = lerp(AiHomeCardBounds.width, 1f, settled)
    scaleY = lerp(AiHomeCardBounds.height, 1f, settled)
    transformOrigin = TransformOrigin(0.5f, 0.5f)
    translationX = (AiHomeCardBounds.centerX - 0.5f) * width * progress
    translationY = (AiHomeCardBounds.centerY - 0.5f) * height * progress
    shape = RoundedCornerShape((28f * progress).dp)
    clip = true
}
