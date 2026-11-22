package com.sukisu.ultra.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalCardBackgroundAlpha
import com.sukisu.ultra.ui.theme.LocalEnableProgressiveBlur
import com.sukisu.ultra.ui.util.OpaqueBarTintAlpha
import com.sukisu.ultra.ui.util.rememberProgressiveBarBlurModifier
import androidx.compose.ui.graphics.graphicsLayer
import com.sukisu.ultra.ui.theme.LocalAppBackgroundBackdrop
import com.sukisu.ultra.ui.theme.LocalDynamicWallpaperFrameBackdrop
import com.sukisu.ultra.ui.theme.LocalTopBarBandProgress
import com.sukisu.ultra.ui.theme.LocalTopBarStyle
import com.sukisu.ultra.ui.util.BarBlurRadius
import com.sukisu.ultra.ui.util.FloatingBarBlurRadius
import com.sukisu.ultra.ui.util.GlassBarBlurRadius
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/**
 * The backdrop treatment behind a transparent top bar.
 *
 * With [LocalEnableProgressiveBlur] on, the bar draws a progressive blur band that is strongest
 * at the screen edge; the band follows [LocalTopBarBandProgress] so it fades in while the page
 * scrolls under the bar and fades out again at the top. [blurBackdrop] is the page capture
 * supplied by the Material scaffold; the Miuix shell passes nothing because its top bar already
 * draws the band inside `BlurredBar`.
 *
 * Otherwise this is a soft scrim while a custom background is active. It keeps the bar and its
 * icons readable without hiding the background image, and it works the same way in both shells.
 */
@Composable
fun TopBarVeil(
    color: Color,
    blurBackdrop: Backdrop? = null,
    content: @Composable () -> Unit,
) {
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val topBarStyle = LocalTopBarStyle.current
    val floating = topBarStyle.isFloating
    // The glass style is only a translucent chrome treatment; the frost follows the progressive
    // blur switch instead of being forced on, so the switch stays meaningful in every style.
    val blurActive = LocalEnableProgressiveBlur.current && isRuntimeShaderSupported()
    val baseTintAlpha = when {
        floating && hasCustomBackground ->
            (LocalCardBackgroundAlpha.current * 0.6f).coerceIn(0.22f, 0.5f)

        floating -> 0.16f
        hasCustomBackground -> LocalCardBackgroundAlpha.current
        else -> OpaqueBarTintAlpha
    }
    // Glass has to stay visually distinct from plain floating even when both are blurred: it
    // keeps a softer frost with much less tint, so the content behind reads through the band.
    val tintAlpha = if (topBarStyle.isGlass) baseTintAlpha * 0.55f else baseTintAlpha
    val blurRadius = when {
        topBarStyle.isGlass -> GlassBarBlurRadius
        floating -> FloatingBarBlurRadius
        else -> BarBlurRadius
    }
    // The large bars publish their collapse progress; reading it in the layer phase keeps the
    // band fading per frame without recomposing the bar. Pinned bars never publish, so they keep
    // the band fully visible.
    val bandProgress = LocalTopBarBandProgress.current
    // A custom background needs some wallpaper layer to make the band sampleable. When it is
    // missing (for example no static capture and no dynamic frame yet), fall back to the scrim
    // instead of leaving the bar transparent.
    val wallpaperSampleable = LocalAppBackgroundBackdrop.current != null ||
        LocalDynamicWallpaperFrameBackdrop.current != null ||
        !hasCustomBackground
    val bandDrawn = blurActive && blurBackdrop != null && wallpaperSampleable
    val bandModifier = if (bandDrawn) {
        rememberProgressiveBarBlurModifier(
            backdrop = blurBackdrop,
            gradient = ProgressiveBlur.Top,
            tint = color,
            tintAlpha = tintAlpha,
            blurRadius = blurRadius,
        )
    } else {
        Modifier
    }
    // Keep a single Box across modes so enabling the setting does not recreate the top bar's
    // composition (and its scroll state) while the switch is being flipped.
    val scrimModifier = if (hasCustomBackground && !bandDrawn) {
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = if (floating) 0.22f else 0.28f),
                    0.65f to color.copy(alpha = if (floating) 0.08f else 0.12f),
                    1f to color.copy(alpha = 0f),
                )
            )
    } else {
        Modifier
    }
    Box(modifier = scrimModifier) {
        // Draw the band in its own full-size layer so the fade only affects the frost, never the
        // bar content on top of it.
        if (bandDrawn) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // The layer must wrap the blur modifier (outermost) for the alpha to apply.
                    .graphicsLayer { alpha = bandProgress.fraction.coerceIn(0f, 1f) }
                    .then(bandModifier),
            )
        }
        content()
    }
}
