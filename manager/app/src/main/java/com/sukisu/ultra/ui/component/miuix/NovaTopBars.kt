package com.sukisu.ultra.ui.component.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.ui.component.FloatingTopBarPill
import com.sukisu.ultra.ui.component.floatingTopBarPillColor
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.theme.LocalEnableProgressiveBlur
import com.sukisu.ultra.ui.theme.LocalTopBarBandProgress
import com.sukisu.ultra.ui.theme.LocalTopBarPillFade
import com.sukisu.ultra.ui.theme.LocalTopBarStyle
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Style-aware replacement for Miuix [MiuixTopAppBar].
 *
 * In [com.sukisu.ultra.ui.theme.TopBarStyle.STANDARD] this is the original bar, unchanged.
 * Floating styles keep the large-title collapse (the library already moves the title into the
 * centred small row and slides `bottomContent` up), but the bar loses its surface and the
 * navigation/action controls fade into glass circles as the title collapses.
 */
@Composable
fun TopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    largeTitle: String = title,
    largeTitleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    // Publish the collapse progress for the page's BlurredBar so its frost fades in while the
    // content scrolls under the bar and fades out at the top, even on pages that do not pass
    // their scroll behavior to BlurredBar themselves.
    val bandProgress = LocalTopBarBandProgress.current
    val bandState = scrollBehavior?.state
    if (bandState != null) {
        LaunchedEffect(bandState) {
            snapshotFlow { bandState.collapsedFraction.coerceIn(0f, 1f) }
                .collect { bandProgress.fraction = it }
        }
    }
    if (!LocalTopBarStyle.current.isFloating) {
        MiuixTopAppBar(
            title = title,
            modifier = modifier,
            color = color,
            titleColor = titleColor,
            largeTitle = largeTitle,
            largeTitleColor = largeTitleColor,
            subtitle = subtitle,
            subtitleColor = subtitleColor,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            titlePadding = titlePadding,
            navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding,
            bottomContent = bottomContent,
        )
        return
    }
    FloatingMiuixLargeTopAppBar(
        title = title,
        modifier = modifier,
        color = color,
        titleColor = titleColor,
        largeTitle = largeTitle,
        largeTitleColor = largeTitleColor,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        bottomContent = bottomContent,
    )
}

/**
 * Style-aware replacement for Miuix [MiuixSmallTopAppBar].
 *
 * The small bar is already centred and pinned, so only the surface and control treatment change
 * while a floating style is active.
 */
@Composable
fun SmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    if (!LocalTopBarStyle.current.isFloating) {
        MiuixSmallTopAppBar(
            title = title,
            modifier = modifier,
            color = color,
            titleColor = titleColor,
            subtitle = subtitle,
            subtitleColor = subtitleColor,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            titlePadding = titlePadding,
            navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding,
            bottomContent = bottomContent,
        )
        return
    }
    val scheme = MiuixTheme.colorScheme
    val pillColor = floatingTopBarPillColor(scheme.surfaceContainerHigh)
    val pillBorder = scheme.outline.copy(
        alpha = if (LocalTopBarStyle.current.isGlass) 0.7f else 0.35f,
    )
    MiuixSmallTopAppBar(
        title = title,
        modifier = modifier,
        // With a blur band or a wallpaper the small bar must stay transparent so the frosted
        // backdrop shows through; without either, the caller's surface keeps it readable.
        color = floatingMiuixSurfaceColor(color),
        titleColor = titleColor,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        navigationIcon = {
            FloatingTopBarPill(
                color = pillColor,
                shape = CircleShape,
                borderColor = pillBorder,
            ) {
                navigationIcon()
            }
        },
        actions = {
            FloatingTopBarPill(
                color = pillColor,
                shape = CircleShape,
                borderColor = pillBorder,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        },
        // SmallTopAppBar pins the shared scroll state, so the page's nested-scroll connection
        // cannot collapse the floating row and stop eating the first scroll.
        scrollBehavior = scrollBehavior,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        bottomContent = bottomContent,
    )
}

/**
 * Large-title bar used by the floating styles.
 *
 * The real Miuix bar is kept so the expanded large title collapses into the centred small title
 * and `bottomContent` (the search pill) follows it up. Only the chrome changes: the surface is
 * dropped and the round controls fade in with [ScrollBehavior.state]'s collapse progress, which is
 * read in the layer phase so scrolling never recomposes the bar.
 */
@Composable
private fun FloatingMiuixLargeTopAppBar(
    title: String,
    modifier: Modifier,
    color: Color,
    titleColor: Color,
    largeTitle: String,
    largeTitleColor: Color,
    subtitle: String,
    subtitleColor: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    scrollBehavior: ScrollBehavior?,
    defaultWindowInsetsPadding: Boolean,
    titlePadding: Dp,
    navigationIconPadding: Dp,
    actionIconPadding: Dp,
    bottomContent: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val pillColor = floatingTopBarPillColor(scheme.surfaceContainerHigh)
    val pillBorder = scheme.outline.copy(
        alpha = if (LocalTopBarStyle.current.isGlass) 0.7f else 0.35f,
    )
    val pillVisibility = rememberFloatingPillVisibility(scrollBehavior)
    MiuixTopAppBar(
        title = title,
        modifier = modifier,
        // The caller already goes transparent when its page draws a blur band; only force it when
        // a band is actually possible so a bar with no backdrop stays readable.
        color = floatingMiuixSurfaceColor(color),
        titleColor = titleColor,
        largeTitle = largeTitle,
        largeTitleColor = largeTitleColor,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        navigationIcon = {
            FloatingTopBarPill(
                color = pillColor,
                shape = CircleShape,
                borderColor = pillBorder,
                visibility = pillVisibility,
            ) {
                navigationIcon()
            }
        },
        actions = {
            FloatingTopBarPill(
                color = pillColor,
                shape = CircleShape,
                borderColor = pillBorder,
                visibility = pillVisibility,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        },
        scrollBehavior = scrollBehavior,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        titlePadding = titlePadding,
        navigationIconPadding = navigationIconPadding,
        actionIconPadding = actionIconPadding,
        bottomContent = bottomContent,
    )
}

/**
 * A collapsing bar keeps its round controls visible from the start (so the floating style reads
 * even before scrolling) and strengthens them as the large title folds away; a bar without scroll
 * state uses the same resting visibility. Pinned *small* bars do not use this helper and always
 * show their circles fully.
 */
@Composable
private fun rememberFloatingPillVisibility(scrollBehavior: ScrollBehavior?): () -> Float {
    val fade = LocalTopBarPillFade.current
    return remember(scrollBehavior, fade) {
        {
            if (!fade) {
                // The switch keeps the control backgrounds fully visible at rest.
                1f
            } else {
                val state = scrollBehavior?.state
                when {
                    // Without a collapsing bar there is nothing to fade against; pinned small bars
                    // must keep their circles visible.
                    state == null -> 1f
                    state.heightOffsetLimit == 0f -> 1f
                    // The backgrounds appear only while the page scrolls under the bar: hidden at
                    // rest, fully drawn by 1/3 of the collapse.
                    else -> (state.collapsedFraction * 3f).coerceIn(0f, 1f)
                }
            }
        }
    }
}

/**
 * A floating bar can only afford a transparent surface when the page actually paints something
 * behind it: a wallpaper or a blur band. Otherwise the caller's surface stays, which keeps the
 * title and controls readable while content scrolls underneath.
 */
@Composable
private fun floatingMiuixSurfaceColor(fallback: Color): Color {
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    // The glass style does not force a transparent surface: transparency is only safe when a
    // wallpaper or an active blur band actually paints behind the bar.
    val blurActive = (LocalEnableBlur.current && isRenderEffectSupported()) ||
        (LocalEnableProgressiveBlur.current && isRuntimeShaderSupported())
    return if (hasCustomBackground || blurActive) Color.Transparent else fallback
}
