package com.sukisu.ultra.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.sukisu.ultra.ui.component.liquid.rememberCombinedBackdrop
import com.sukisu.ultra.ui.theme.LocalAppBackgroundBackdrop
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalCardBackgroundAlpha
import com.sukisu.ultra.ui.theme.LocalEnableProgressiveBlur
import androidx.compose.ui.graphics.graphicsLayer
import com.sukisu.ultra.ui.theme.LocalDynamicWallpaperFrameBackdrop
import com.sukisu.ultra.ui.theme.LocalTopBarBandProgress
import com.sukisu.ultra.ui.theme.LocalTopBarStyle
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Blur radius shared by the standard bar blur and its progressive variant. */
const val BarBlurRadius: Float = 25f

/**
 * Stronger radius for the floating styles: their chrome is translucent glass, so the frost has
 * to read clearly against the content scrolling underneath rather than fading into a surface
 * coloured band.
 */
const val FloatingBarBlurRadius: Float = 38f

/**
 * The glass style uses the same frost as floating but a clearer, softer band, so it keeps reading
 * as glass instead of collapsing into the plain floating treatment once blur is enabled.
 */
const val GlassBarBlurRadius: Float = 46f

/**
 * Solid-enough tint layered over the bar blur. Over a custom background the cards already use
 * [LocalCardBackgroundAlpha], so the bar follows them; without one the tint has to be strong
 * enough to keep the bar chrome readable while content scrolls underneath.
 */
const val OpaqueBarTintAlpha = 0.87f

@Composable
fun rememberBlurBackdrop(
    enableBlur: Boolean,
    allowProgressive: Boolean = true,
): LayerBackdrop? {
    // The progressive mode draws a blur band even when the plain bar blur is off, so it needs the
    // same page capture. It follows its own switch only; the glass top-bar style is chrome and
    // does not force a capture (the glass frost follows the blur switches like every other bar).
    // Callers that only want the page layer for another purpose can opt out.
    val progressiveBlur = allowProgressive &&
        LocalEnableProgressiveBlur.current &&
        isRuntimeShaderSupported()
    if (!enableBlur && !progressiveBlur) return null
    if (!isRenderEffectSupported()) return null
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        // Without a custom background the layer has to be opaque on its own. With one, the page
        // stays transparent so the wallpaper shows through, and BlurredBar adds the wallpaper
        // back underneath when it samples this layer.
        if (!hasCustomBackground) {
            drawRect(surfaceColor)
        }
        drawContent()
    }
}

/**
 * Stacks the app background under the page layer captured by [backdrop].
 *
 * Over a custom background the page layer is transparent wherever the page paints nothing, and
 * blurring those pixels bleeds black into the bar. The background image fills exactly those
 * pixels, so drawing both layers reproduces what is really on screen behind the bar.
 *
 * Returns null when only the page layer is available; [BlurredBar] then falls back to a plain
 * translucent surface instead of blurring an incomplete backdrop.
 */
@Composable
fun rememberBackdropWithAppBackground(backdrop: Backdrop?): Backdrop? {
    // A dynamic wallpaper can never be captured live; its renderer exports frame snapshots as a
    // record-only layer instead. Use that in place of the still-image capture whenever present.
    // The page layer stays mandatory: without it the caller has already decided there is no
    // usable blur (unsupported effect or blur switched off).
    val dynamicFrame = LocalDynamicWallpaperFrameBackdrop.current
    if (dynamicFrame != null && backdrop != null) {
        return rememberCombinedBackdrop(dynamicFrame, backdrop)
    }
    if (backdrop == null) return null
    val appBackground = LocalAppBackgroundBackdrop.current
    if (appBackground != null) return rememberCombinedBackdrop(appBackground, backdrop)
    // Over a custom background the page layer alone is transparent wherever nothing is drawn,
    // so leaving it out is only safe when the layer was filled with the opaque surface color.
    return backdrop.takeIf { LocalBackgroundImagePath.current.isEmpty() }
}

/**
 * Modifier that paints the progressive edge blur behind a bar.
 *
 * The blur ramps from [gradient]'s strong edge (usually the screen edge) to a fully sharp,
 * full-resolution clear end, so content fades out of blur instead of meeting the bar with a hard
 * line. Works over a custom background (the wallpaper is layered under the page capture) and
 * without one (the page layer is opaque, so only scrolling content changes).
 *
 * [shape] clips the effect, so a floating pill only blurs inside its own outline and leaves the
 * page elements around it untouched.
 */
@Composable
fun rememberProgressiveBarBlurModifier(
    backdrop: Backdrop?,
    gradient: ProgressiveBlur,
    tint: Color,
    tintAlpha: Float,
    blurRadius: Float = BarBlurRadius,
    shape: Shape = RectangleShape,
): Modifier {
    if (!isRuntimeShaderSupported()) return Modifier
    val effectiveBackdrop = rememberBackdropWithAppBackground(backdrop) ?: return Modifier
    return remember(effectiveBackdrop, gradient, tint, tintAlpha, blurRadius, shape) {
        Modifier.progressiveTextureBlur(
            backdrop = effectiveBackdrop,
            shape = shape,
            blurRadius = blurRadius,
            gradient = gradient,
            colors = BlurColors(
                blendColors = listOf(BlendColorEntry(color = tint.copy(alpha = tintAlpha))),
            ),
        )
    }
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    blurActive: Boolean? = null,
    progressiveGradient: ProgressiveBlur = ProgressiveBlur.Top,
    content: @Composable () -> Unit,
) {
    val isScrolled by remember(scrollBehavior) {
        derivedStateOf {
            scrollBehavior?.state?.collapsedFraction?.let { it > 0.01f } ?: true
        }
    }
    val topBarStyle = LocalTopBarStyle.current
    val floating = topBarStyle.isFloating
    // A floating bar has no page surface of its own, so it keeps sampling whatever scrolls under
    // it even at rest. The frost itself follows the blur switches; choosing the glass style only
    // changes the chrome (translucent pills and rim), never the blur mode.
    val effectiveBlurActive = floating || (blurActive ?: (scrollBehavior == null || isScrolled))
    val progressiveBlur = LocalEnableProgressiveBlur.current && isRuntimeShaderSupported()
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val cardAlpha = LocalCardBackgroundAlpha.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBackdropWithAppBackground(backdrop)
    val baseTintAlpha = when {
        floating && hasCustomBackground -> (cardAlpha * 0.6f).coerceIn(0.22f, 0.5f)
        floating -> 0.16f
        hasCustomBackground -> cardAlpha
        else -> OpaqueBarTintAlpha
    }
    // Glass stays distinct from plain floating even with the same blur switches: it keeps a
    // softer frost with much less tint, so the content reads through the band.
    val tintAlpha = if (topBarStyle.isGlass) baseTintAlpha * 0.55f else baseTintAlpha
    val blurRadius = when {
        topBarStyle.isGlass -> GlassBarBlurRadius
        floating -> FloatingBarBlurRadius
        else -> BarBlurRadius
    }

    val blurModifier = when {
        progressiveBlur && blurBackdrop != null -> {
            // The gradient itself is always installed; the band's alpha follows the collapse so
            // it is invisible at rest and fades in as content scrolls underneath.
            rememberProgressiveBarBlurModifier(
                backdrop = backdrop,
                gradient = progressiveGradient,
                tint = surfaceColor,
                tintAlpha = tintAlpha,
                blurRadius = blurRadius,
            )
        }
        progressiveBlur -> {
            // No sampleable wallpaper/page layer (for example a dynamic wallpaper whose frame
            // could not be captured). Keep the bar readable instead of leaving it transparent.
            Modifier.background(surfaceColor.copy(alpha = tintAlpha))
        }
        blurBackdrop != null && effectiveBlurActive -> Modifier.textureBlur(
            backdrop = blurBackdrop,
            shape = RectangleShape,
            blurRadius = blurRadius,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = surfaceColor.copy(tintAlpha)),
                ),
            ),
        )
        hasCustomBackground -> {
            // 自定义背景但没有实时模糊（设备不支持或被用户关掉）：静止态也要画一层卡片底色，
            // 否则顶栏全透明，滚动内容会直接穿到标题底下
            Modifier.background(surfaceColor.copy(alpha = cardAlpha))
        }
        backdrop == null -> {
            // 顶栏模糊关掉（或设备不支持）时必须自己画底色。
            // 早先这里返回空的 Modifier，配合 TopAppBar(color = Color.Transparent)，
            // 整条 bar 就是透明的：列表滚上去时标题底下会透着内容，看着像没画顶栏。
            Modifier.background(surfaceColor)
        }
        else -> Modifier
    }

    // Fade the frost with the collapse so the band appears while content scrolls under the bar
    // and fades out again when the list returns to the top. The no-blur scrim branches stay
    // constant so the floating chrome remains readable at rest.
    val bandVisible = progressiveBlur || (blurBackdrop != null && effectiveBlurActive)
    // Pages that do not pass their scroll behavior here still publish the bar's collapse through
    // LocalTopBarBandProgress, so the band fades everywhere a large bar is collapsing.
    val bandProgress = LocalTopBarBandProgress.current
    val bandAlpha: () -> Float = remember(scrollBehavior, bandProgress) {
        {
            val state = scrollBehavior?.state
            when {
                state == null -> bandProgress.fraction.coerceIn(0f, 1f)
                state.heightOffsetLimit == 0f -> 1f
                else -> (state.collapsedFraction * 3f).coerceIn(0f, 1f)
            }
        }
    }
    Box(modifier = modifier) {
        // Separate full-size layer: the fade must only affect the band, never the bar content on
        // top of it.
        Box(
            modifier = Modifier
                .matchParentSize()
                // The layer must wrap the blur modifier (outermost) for the alpha to apply.
                .graphicsLayer { alpha = if (bandVisible) bandAlpha() else 1f }
                .then(blurModifier),
        )
        content()
    }
}
