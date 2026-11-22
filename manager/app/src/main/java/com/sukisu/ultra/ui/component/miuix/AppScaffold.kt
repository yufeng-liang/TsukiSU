package com.sukisu.ultra.ui.component.miuix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sukisu.ultra.ui.component.TopBarVeil
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import androidx.compose.ui.graphics.graphicsLayer
import com.sukisu.ultra.ui.theme.LocalTopBarBandProgress
import com.sukisu.ultra.ui.theme.TopBarBandProgressState
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/**
 * A wrapper around miuix [Scaffold] that follows the custom background image.
 *
 * When [containerColor] is left unspecified, the container becomes transparent while a
 * custom background image is active and falls back to [MiuixTheme.colorScheme.surface]
 * otherwise. Passing an explicit [containerColor] always takes precedence.
 *
 * All other parameters are forwarded to [Scaffold] unchanged.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    topBarVisible: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    snackbarHost: @Composable () -> Unit = {},
    popupHost: @Composable () -> Unit = { MiuixPopupHost() },
    containerColor: Color = Color.Unspecified,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    content: @Composable (PaddingValues) -> Unit,
) {
    val resolvedContainerColor = when {
        containerColor != Color.Unspecified -> containerColor
        LocalBackgroundImagePath.current.isNotEmpty() -> Color.Transparent
        else -> MiuixTheme.colorScheme.surface
    }
    // Fade rather than snap: the search pager's scrim takes a moment to cover the page, and
    // reading the animation only in the graphics layer keeps this from recomposing the bar.
    val topBarAlpha = animateFloatAsState(
        targetValue = if (topBarVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "AppScaffoldTopBarAlpha",
    )
    // The large bars publish their collapse progress here; BlurredBar fades its band with it so
    // the frost appears while content scrolls under the bar and disappears again at the top.
    val topBarBandProgress = remember { TopBarBandProgressState() }
    CompositionLocalProvider(LocalTopBarBandProgress provides topBarBandProgress) {
        Scaffold(
            modifier = modifier,
            topBar = {
                // A full-screen search pager draws its own translucent scrim over the page. Leaving
                // the bar painted underneath makes its surface/blur band show through as a dark
                // rectangle around the search field, so the caller can fade the bar out. The
                // graphics layer keeps the slot's height, so the content behind the scrim does not
                // jump while the header is hidden.
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = topBarAlpha.value
                    }
                ) {
                    // The veil reads the top-bar style itself: over a wallpaper the floating chrome
                    // gets a lighter scrim, while the page's BlurredBar keeps supplying the blur
                    // band. Keeping the same wrapper in every style also avoids rebuilding the
                    // bar's composition (and its scroll state) when the setting is flipped.
                    TopBarVeil(MiuixTheme.colorScheme.surface) { topBar() }
                }
            },
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = floatingActionButtonPosition,
            floatingToolbar = floatingToolbar,
            floatingToolbarPosition = floatingToolbarPosition,
            snackbarHost = snackbarHost,
            popupHost = popupHost,
            containerColor = resolvedContainerColor,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}
