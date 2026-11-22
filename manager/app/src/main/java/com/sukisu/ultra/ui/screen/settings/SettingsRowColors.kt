package com.sukisu.ultra.ui.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.backgroundAwareColor

/**
 * 行底色。默认与 [SegmentedColumn] 自带的行底色一致；被锚点命中时换成强调色。
 *
 * 组件里的 defaultSegmentedColors() 是 private，这里按同样的配方自己拼一份，
 * 这样高亮只改底色，不动文字/勾选框的配色。
 *
 * 三个二级页（Root 与安全 / 模块与扩展 / 诊断与维护）共用这一份，
 * 免得各改一处后高亮时长或禁用态对比度悄悄分叉。
 */
@Composable
internal fun rowColors(highlighted: Boolean): ListItemColors {
    val base = backgroundAwareColor(MaterialTheme.colorScheme.surfaceBright)
    val container by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else base,
        animationSpec = tween(durationMillis = 220),
        label = "settingsRowHighlight",
    )
    val colors = ListItemDefaults.segmentedColors(
        containerColor = container,
        disabledContainerColor = container,
        // 勾选状态不换底色：勾选框本身已经在说「选中了」，与组件默认行为保持一致。
        selectedContainerColor = container,
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (LocalBackgroundImagePath.current.isEmpty()) return colors
    // 自定义背景图上卡片是半透明的，组件默认的禁用态前景太淡，抬一点对比度。
    return colors.copy(
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        disabledLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        disabledTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        disabledOverlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        disabledSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
    )
}
