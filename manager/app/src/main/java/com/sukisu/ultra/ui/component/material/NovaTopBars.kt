package com.sukisu.ultra.ui.component.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LargeFlexibleTopAppBar as MaterialLargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar as MaterialTopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.ui.component.FloatingTopBarPill
import com.sukisu.ultra.ui.component.floatingTopBarPillColor
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableProgressiveBlur
import com.sukisu.ultra.ui.theme.LocalTopBarBandProgress
import com.sukisu.ultra.ui.theme.LocalTopBarPillFade
import com.sukisu.ultra.ui.theme.LocalTopBarStyle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

/**
 * Style-aware replacement for the Material 3 small [MaterialTopAppBar].
 *
 * Standard mode forwards every argument to the platform bar, so existing screens keep their
 * large/tinted app bar. Floating mode swaps the bar for an MD3 expressive center-aligned bar
 * with round tonal controls and a transparent surface.
 */
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding,
) {
    if (!LocalTopBarStyle.current.isFloating) {
        MaterialTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            expandedHeight = expandedHeight,
            windowInsets = windowInsets,
            colors = colors,
            scrollBehavior = scrollBehavior,
            contentPadding = contentPadding,
        )
        return
    }
    FloatingMaterialSmallTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * The subtitle overload of [MaterialTopAppBar] (MD3 expressive).
 *
 * Screens such as the theme-store author page use it for a second line; floating mode keeps the
 * same title/subtitle column but centers it and drops the bar surface.
 */
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding,
) {
    if (!LocalTopBarStyle.current.isFloating) {
        MaterialTopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            titleHorizontalAlignment = titleHorizontalAlignment,
            expandedHeight = expandedHeight,
            windowInsets = windowInsets,
            colors = colors,
            scrollBehavior = scrollBehavior,
            contentPadding = contentPadding,
        )
        return
    }
    FloatingMaterialSmallTopAppBar(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Style-aware replacement for the MD3 large flexible app bar.
 *
 * Floating mode keeps the real large flexible bar so its title still collapses into the small
 * row and the search field below follows it up; only the surface is dropped and the controls fade
 * into glass circles as the title folds away.
 */
@Composable
fun LargeFlexibleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    collapsedHeight: Dp = TopAppBarDefaults.LargeAppBarCollapsedHeight,
    expandedHeight: Dp = if (subtitle != null) {
        TopAppBarDefaults.LargeFlexibleAppBarWithSubtitleExpandedHeight
    } else {
        TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight
    },
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    if (!LocalTopBarStyle.current.isFloating) {
        // Feed the collapse progress to the scaffold's veil so the blur band fades with the bar.
        val bandProgress = LocalTopBarBandProgress.current
        val bandState = scrollBehavior?.state
        if (bandState != null) {
            LaunchedEffect(bandState) {
                snapshotFlow { bandState.collapsedFraction.coerceIn(0f, 1f) }
                    .collect { bandProgress.fraction = it }
            }
        }
        MaterialLargeFlexibleTopAppBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            actions = actions,
            titleHorizontalAlignment = titleHorizontalAlignment,
            collapsedHeight = collapsedHeight,
            expandedHeight = expandedHeight,
            windowInsets = windowInsets,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
        return
    }
    FloatingMaterialLargeTopAppBar(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        navigationIcon = navigationIcon,
        actions = actions,
        collapsedHeight = collapsedHeight,
        expandedHeight = expandedHeight,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun FloatingMaterialSmallTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    // The page still applies the bar's nested-scroll connection. With no collapsible bar the
    // connection would otherwise eat the first scroll to "hide" a bar that is pinned, leaving a
    // dead zone at the top of every list. Zero the range so the connection is a no-op.
    if (scrollBehavior != null) {
        SideEffect {
            val state = scrollBehavior.state
            if (state.heightOffsetLimit != 0f) state.heightOffsetLimit = 0f
            if (state.heightOffset != 0f) state.heightOffset = 0f
        }
    }
    val scheme = MaterialTheme.colorScheme
    val pillColor = floatingTopBarPillColor(scheme.surfaceContainerHigh)
    val pillBorder = scheme.outlineVariant.copy(
        alpha = if (LocalTopBarStyle.current.isGlass) 0.7f else 0.35f,
    )
    // MD3E draws app-bar controls on a tonal container; the bar itself stays transparent so the
    // wallpaper keeps its full-bleed look. Content colours from the screen's palette are kept.
    val floatingColors = colors.copy(
        containerColor = floatingMaterialSurfaceColor(colors.containerColor),
        scrolledContainerColor = floatingMaterialSurfaceColor(colors.scrolledContainerColor),
    )
    val floatingNavigationIcon: @Composable () -> Unit = {
        FloatingTopBarPill(
            color = pillColor,
            // The pill fills the 48dp control slot; keep the bar's usual horizontal inset so the
            // circle does not sit against the screen edge.
            modifier = Modifier.padding(start = 10.dp),
            shape = CircleShape,
            borderColor = pillBorder,
        ) {
            navigationIcon()
        }
    }
    val floatingActions: @Composable RowScope.() -> Unit = {
        FloatingTopBarPill(
            color = pillColor,
            modifier = Modifier.padding(end = 10.dp),
            shape = CircleShape,
            borderColor = pillBorder,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
    if (subtitle == null) {
        CenterAlignedTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = floatingNavigationIcon,
            actions = floatingActions,
            // Pinned chrome: the page scroll behaviour must not collapse the floating row.
            scrollBehavior = null,
            windowInsets = windowInsets,
            colors = floatingColors,
            contentPadding = contentPadding,
        )
    } else {
        MaterialTopAppBar(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
            navigationIcon = floatingNavigationIcon,
            actions = floatingActions,
            titleHorizontalAlignment = Alignment.CenterHorizontally,
            scrollBehavior = null,
            windowInsets = windowInsets,
            colors = floatingColors,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun FloatingMaterialLargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier,
    subtitle: (@Composable () -> Unit)?,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    collapsedHeight: Dp,
    expandedHeight: Dp,
    windowInsets: WindowInsets,
    colors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val extraPx = with(density) {
        (expandedHeight - collapsedHeight).toPx().coerceAtLeast(0f)
    }
    val bottomPaddingPx = with(density) { 24.dp.toPx() }
    val state = scrollBehavior?.state
    val fractionProvider: () -> Float = remember(state) {
        {
            if (state != null) {
                // Read the offset unconditionally: the limit is a plain field that is only filled
                // in by the SideEffect below, and this read is what subscribes measure/draw to
                // the scroll state for the whole lifetime of the bar.
                val offset = state.heightOffset
                val limit = state.heightOffsetLimit
                if (limit != 0f) (offset / limit).coerceIn(0f, 1f) else 0f
            } else {
                0f
            }
        }
    }
    // Both title rows are always composed; only the visible one should reach accessibility
    // services so the page title is not announced twice. Reading this derived boolean in
    // composition only invalidates when it flips, not on every scroll frame.
    val largeTitleActive by remember(state) {
        derivedStateOf {
            if (state != null) {
                val offset = state.heightOffset
                val limit = state.heightOffsetLimit
                limit == 0f || (offset / limit) < 0.5f
            } else {
                true
            }
        }
    }
    // Publish the collapse progress for the scaffold veil: the frosted band fades in while content
    // scrolls under the bar and fades out again when the list returns to the top.
    val bandProgress = LocalTopBarBandProgress.current
    LaunchedEffect(state) {
        if (state == null) {
            bandProgress.fraction = 1f
        } else {
            snapshotFlow { fractionProvider() }.collect { bandProgress.fraction = it }
        }
    }
    // The custom layout below draws the collapse from the shared state, so it also owns the
    // collapse range. Keep the current fraction when the range is (re)established, which happens
    // on first composition and when switching between the standard and floating bars.
    if (scrollBehavior != null) {
        SideEffect {
            val current = scrollBehavior.state
            val limit = -extraPx
            if (current.heightOffsetLimit != limit) {
                val fraction = if (current.heightOffsetLimit != 0f) {
                    (current.heightOffset / current.heightOffsetLimit).coerceIn(0f, 1f)
                } else {
                    0f
                }
                current.heightOffsetLimit = limit
                current.heightOffset = limit * fraction
            }
        }
    }
    val pillColor = floatingTopBarPillColor(scheme.surfaceContainerHigh)
    val pillBorder = scheme.outlineVariant.copy(
        alpha = if (LocalTopBarStyle.current.isGlass) 0.7f else 0.35f,
    )
    val pillVisibility = rememberFloatingPillVisibility(scrollBehavior)
    val floatingColors = colors.copy(
        containerColor = floatingMaterialSurfaceColor(colors.containerColor),
        scrolledContainerColor = floatingMaterialSurfaceColor(colors.scrolledContainerColor),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(floatingColors.containerColor)
            .windowInsetsPadding(windowInsets)
            .clipToBounds()
    ) {
        // Measures the collapsing area: the bar height falls from expandedHeight to
        // collapsedHeight. The large title is bottom-aligned in that area, slides up with the
        // scroll offset and fades out before it reaches the transparent top row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val collapsedPx = collapsedHeight.roundToPx()
                    val expandedPx = collapsedPx + extraPx.roundToInt()
                    val fraction = fractionProvider().coerceIn(0f, 1f)
                    val barHeight = collapsedPx +
                        (extraPx * (1f - fraction)).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = 0,
                            maxWidth = constraints.maxWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        )
                    )
                    val width = if (constraints.maxWidth == Constraints.Infinity) {
                        placeable.width
                    } else {
                        constraints.maxWidth
                    }
                    layout(width, barHeight) {
                        // The expanded row starts below the collapsed row; keep the large title
                        // inside that row (m3 reduces the bottom padding when the title is tall)
                        // and slide it up by the scroll offset as the bar collapses.
                        val rowTop = collapsedPx
                        val rowHeight = (expandedPx - collapsedPx).coerceAtLeast(0)
                        val titleY = rowTop + (
                            rowHeight - placeable.height - bottomPaddingPx.roundToInt()
                            ).coerceAtLeast(0)
                        val y = titleY - (extraPx * fraction).roundToInt()
                        placeable.place(0, y)
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        alpha = 1f - (fractionProvider() * 3f).coerceIn(0f, 1f)
                    }
                    .then(
                        if (largeTitleActive) Modifier else Modifier.clearAndSetSemantics {},
                    )
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides floatingColors.titleContentColor,
                    LocalTextStyle provides typography.displaySmall,
                ) {
                    title()
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    CompositionLocalProvider(
                        LocalContentColor provides floatingColors.subtitleContentColor,
                        LocalTextStyle provides typography.titleMedium,
                    ) {
                        subtitle()
                    }
                }
            }
        }
        // Top row: navigation and actions at the edges, the collapsed title centred against the
        // whole bar width. Measuring the title against the remaining space (instead of using a
        // Row's weight) keeps it centred even when the two icon groups differ in width.
        Layout(
            content = {
                Box(
                    modifier = Modifier
                        .layoutId("nav")
                        .padding(start = 4.dp)
                ) {
                    FloatingTopBarPill(
                        color = pillColor,
                        modifier = Modifier.padding(start = 10.dp),
                        shape = CircleShape,
                        borderColor = pillBorder,
                        visibility = pillVisibility,
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides floatingColors.navigationIconContentColor,
                        ) {
                            navigationIcon()
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .layoutId("title")
                        .graphicsLayer {
                            val fraction = fractionProvider()
                            alpha = fraction
                            translationY = (1f - fraction) * 10.dp.toPx()
                        }
                        .then(
                            if (largeTitleActive) Modifier.clearAndSetSemantics {} else Modifier,
                        )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CompositionLocalProvider(
                            LocalContentColor provides floatingColors.titleContentColor,
                            LocalTextStyle provides typography.titleLarge,
                        ) {
                            title()
                        }
                        if (subtitle != null) {
                            CompositionLocalProvider(
                                LocalContentColor provides floatingColors.subtitleContentColor,
                                LocalTextStyle provides typography.labelMedium,
                            ) {
                                subtitle()
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .layoutId("actions")
                        .padding(end = 4.dp)
                ) {
                    FloatingTopBarPill(
                        color = pillColor,
                        modifier = Modifier.padding(end = 10.dp),
                        shape = CircleShape,
                        borderColor = pillBorder,
                        visibility = pillVisibility,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides floatingColors.actionIconContentColor,
                            ) {
                                actions()
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(collapsedHeight),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val navPlaceable = measurables.first { it.layoutId == "nav" }.measure(loose)
            val actionsPlaceable = measurables.first { it.layoutId == "actions" }.measure(loose)
            val maxTitleWidth = (
                constraints.maxWidth - 2 * maxOf(navPlaceable.width, actionsPlaceable.width)
                ).coerceAtLeast(0)
            val titlePlaceable = measurables.first { it.layoutId == "title" }.measure(
                constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxTitleWidth),
            )
            val rowHeight = collapsedHeight.roundToPx()
            layout(constraints.maxWidth, rowHeight) {
                val navY = (rowHeight - navPlaceable.height) / 2
                val actionsY = (rowHeight - actionsPlaceable.height) / 2
                val titleY = (rowHeight - titlePlaceable.height) / 2
                navPlaceable.placeRelative(0, navY)
                actionsPlaceable.placeRelative(
                    constraints.maxWidth - actionsPlaceable.width,
                    actionsY,
                )
                titlePlaceable.placeRelative(
                    (constraints.maxWidth - titlePlaceable.width) / 2,
                    titleY,
                )
            }
        }
    }
}

/**
 * A collapsing bar keeps its round controls visible from the start (so the floating style reads
 * even before scrolling) and strengthens them as the large title folds away; a bar without scroll
 * state uses the same resting visibility. Pinned *small* bars do not use this helper and always
 * show their circles fully.
 */
@Composable
private fun rememberFloatingPillVisibility(
    scrollBehavior: TopAppBarScrollBehavior?,
): () -> Float {
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
private fun floatingMaterialSurfaceColor(fallback: Color): Color {
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    // The Material shell paints its band from the progressive blur, so only that switch makes a
    // transparent surface safe. The glass style itself never forces the band on.
    val blurActive = LocalEnableProgressiveBlur.current && isRuntimeShaderSupported()
    return if (hasCustomBackground || blurActive) Color.Transparent else fallback
}
