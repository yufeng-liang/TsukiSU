package com.sukisu.ultra.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.sukisu.ultra.ui.component.liquid.rememberCombinedBackdrop
import com.sukisu.ultra.ui.theme.LocalAppBackgroundBackdrop
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalCardBackgroundAlpha
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
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
private fun rememberBarBackdrop(backdrop: LayerBackdrop?): Backdrop? {
    if (backdrop == null) return null
    val appBackground = LocalAppBackgroundBackdrop.current
    if (appBackground != null) return rememberCombinedBackdrop(appBackground, backdrop)
    // Over a custom background the page layer alone is transparent wherever nothing is drawn,
    // so leaving it out is only safe when the layer was filled with the opaque surface color.
    return backdrop.takeIf { LocalBackgroundImagePath.current.isEmpty() }
}

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior? = null,
    blurActive: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val isScrolled by remember(scrollBehavior) {
        derivedStateOf {
            scrollBehavior?.state?.collapsedFraction?.let { it > 0.01f } ?: true
        }
    }
    val effectiveBlurActive = blurActive ?: (scrollBehavior == null || isScrolled)
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val cardAlpha = LocalCardBackgroundAlpha.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurBackdrop = rememberBarBackdrop(backdrop)

    val blurModifier = if (effectiveBlurActive && blurBackdrop != null) {
        val tintAlpha = if (hasCustomBackground) cardAlpha else 0.87f
        Modifier.textureBlur(
            backdrop = blurBackdrop,
            shape = RectangleShape,
            blurRadius = 25f,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = surfaceColor.copy(tintAlpha)),
                ),
            ),
        )
    } else if (effectiveBlurActive && hasCustomBackground) {
        Modifier.background(surfaceColor.copy(alpha = cardAlpha))
    } else if (backdrop == null && !hasCustomBackground) {
        // 顶栏模糊关掉（或设备不支持）时必须自己画底色。
        // 早先这里返回空的 Modifier，配合 TopAppBar(color = Color.Transparent)，
        // 整条 bar 就是透明的：列表滚上去时标题底下会透着内容，看着像没画顶栏。
        Modifier.background(surfaceColor)
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(blurModifier)
    ) {
        content()
    }
}
