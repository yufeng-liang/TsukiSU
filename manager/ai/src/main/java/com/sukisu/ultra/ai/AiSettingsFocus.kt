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
 * 输入框快捷条上点「助手设置」时要落到哪一组。
 *
 * 只登记真正能滚到位的区域：连接配置（模型与推理强度都在这里）和联网搜索。
 */
enum class AiSettingsTarget { Model, Search }

/**
 * 一次性信使：聊天页写、设置页读一次就清空。
 *
 * 不落盘，所以用户自己从别处进设置页时，不会被上一次的目标带着跳。
 */
internal object AiSettingsFocus {
    private var pending: AiSettingsTarget? = null

    fun request(target: AiSettingsTarget) {
        pending = target
    }

    fun consume(): AiSettingsTarget? = pending.also { pending = null }
}

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
