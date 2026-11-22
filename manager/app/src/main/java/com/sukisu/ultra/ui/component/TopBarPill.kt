package com.sukisu.ultra.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalCardBackgroundAlpha
import com.sukisu.ultra.ui.theme.LocalTopBarStyle

/**
 * The translucent circle (or capsule) behind a floating top bar control.
 *
 * The actual blur is drawn once behind the whole floating bar by the scaffold / `BlurredBar`,
 * so sampling a page capture per control would re-enter the page layer that contains the
 * previews on the appearance page. Keeping this surface paint-only also makes it safe inside
 * previews and dialogs, where no page capture exists.
 *
 * [visibility] is read in the layer phase (not composition), so a collapsing large-title bar can
 * fade its controls in while scrolling without recomposing the bar on every frame. Passing null
 * keeps the pill fully visible, which is what pinned small bars want.
 */
@Composable
fun FloatingTopBarPill(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    borderColor: Color = Color.Unspecified,
    visibility: (() -> Float)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalTopBarStyle.current.isGlass
    val resolvedBorder = when {
        borderColor != Color.Unspecified -> borderColor
        color.alpha < 1f -> color.copy(alpha = (color.alpha + 0.12f).coerceAtMost(1f))
        else -> Color.Transparent
    }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (visibility != null) {
                        Modifier.graphicsLayer { alpha = visibility().coerceIn(0f, 1f) }
                    } else {
                        Modifier
                    }
                )
                .background(color, shape)
                .then(
                    if (glass) {
                        // Paint-only specular highlight (no backdrop sampling, so it stays safe
                        // inside previews). It keeps the pills reading as glass even when the
                        // bar behind them is frosted exactly like the plain floating style.
                        Modifier.background(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.16f),
                                0.45f to Color.White.copy(alpha = 0.04f),
                                1f to Color.Transparent,
                            ),
                            shape,
                        )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (resolvedBorder != Color.Transparent) {
                        Modifier.border(0.5.dp, resolvedBorder, shape)
                    } else {
                        Modifier
                    }
                )
        )
        content()
    }
}

/**
 * Tint for a floating top bar control.
 *
 * Without a custom background the plain floating style can stay opaque. The glass style always
 * stays clearly translucent so the frosted band and the blurred content read through the circle;
 * over a wallpaper the plain style follows the theme's card transparency as well.
 */
@Composable
fun floatingTopBarPillColor(base: Color): Color {
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val alpha = when {
        LocalTopBarStyle.current.isGlass -> 0.28f
        hasCustomBackground -> LocalCardBackgroundAlpha.current.coerceIn(0.55f, 0.9f)
        else -> 1f
    }
    return base.copy(alpha = alpha)
}
