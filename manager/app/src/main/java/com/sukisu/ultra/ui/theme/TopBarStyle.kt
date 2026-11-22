package com.sukisu.ultra.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * How page top bars are drawn.
 *
 * [STANDARD] keeps the platform-shaped bar each screen was written with: a Miuix large title
 * bar or a Material 3 large flexible app bar. [FLOATING] keeps that large title (it still folds
 * into the centred small row while scrolling) but drops the bar surface and turns the navigation
 * and action icons into round tonal controls that fade in with the collapse. The page's search
 * field stays below the title as its own pill and follows the title up. [FLOATING_GLASS] keeps
 * that layout and frosts the bar and controls with the page/wallpaper capture.
 */
enum class TopBarStyle(val value: Int) {
    STANDARD(0),
    FLOATING(1),
    FLOATING_GLASS(2);

    val isFloating: Boolean get() = this != STANDARD

    val isGlass: Boolean get() = this == FLOATING_GLASS

    companion object {
        fun fromValue(value: Int): TopBarStyle = entries.getOrElse(value) { STANDARD }
    }
}

/**
 * The style in effect for every top bar below the activity.
 *
 * This is a dynamic local: flipping it invalidates the bars that read it (including the previews
 * inside the appearance page) rather than the whole activity subtree, which keeps the style
 * switch cheap on a page full of live device previews.
 */
val LocalTopBarStyle = compositionLocalOf { TopBarStyle.STANDARD }
