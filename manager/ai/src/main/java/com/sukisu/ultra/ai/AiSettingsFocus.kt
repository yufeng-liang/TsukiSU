package com.sukisu.ultra.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 跳转落点的高亮描边：目标卡亮一圈再淡出，卡片底色不动，免得看着像被选中。
 */
@Composable
internal fun Modifier.aiFocusRing(active: Boolean, shape: Shape, color: Color): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "aiFocusRing",
    )
    return if (alpha <= 0.01f) {
        this
    } else {
        border(width = 2.dp, color = color.copy(alpha = alpha), shape = shape)
    }
}
