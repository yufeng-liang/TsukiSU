package com.sukisu.ultra.ui.screen.dynamicwallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.ui.component.background.DynamicWallpaperBackground
import com.sukisu.ultra.ui.component.background.DynamicWallpaperFit
import com.sukisu.ultra.ui.component.background.DynamicWallpaperTransform
import com.sukisu.ultra.ui.util.HomeLayout
import com.sukisu.ultra.ui.util.HomeStatusStyle

/** Matches the home status card's minimum height on both skins. */
private val ClassicCardMinHeight = 188.dp

/** Below this height the empty/error slot must fit in a single compact row. */
private val CompactStageHeight = 140.dp

/**
 * Live preview of the home card's dynamic background.
 *
 * The editor stages the media inside a frame that mirrors the card configured on the home page:
 * the classic full-width card, or the immersive banner with its persisted width fraction and
 * height scale. CUSTOM pinch/pan runs on the frame itself, so the normalized offsets map exactly
 * to what the card renderer will show.
 */
@Composable
internal fun DynamicHomeCardStage(
    state: DynamicWallpaperPreviewUiState,
    actions: DynamicWallpaperPreviewActions,
    shape: Shape,
    borderColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    emptyContent: @Composable (compact: Boolean) -> Unit,
) {
    val previewPath = state.previewPath
    val mediaVisible = previewPath != null && !state.rendererFailed
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        val immersive = state.cardLayout == HomeLayout.IMMERSIVE
        val targetWidth = if (immersive) {
            maxWidth * HomeStatusStyle.coerceWidth(state.cardWidthFraction)
        } else {
            maxWidth
        }
        val targetHeight = if (immersive) {
            HomeStatusStyle.scaledDp(HomeStatusStyle.BannerHeight, state.cardHeightScale)
        } else {
            ClassicCardMinHeight
        }
        val aspectRatio = targetWidth / targetHeight
        val maxFrameHeight = (maxHeight - 8.dp).coerceAtLeast(24.dp)
        var frameWidth = targetWidth
        var frameHeight = targetHeight
        if (frameHeight > maxFrameHeight) {
            frameHeight = maxFrameHeight
            frameWidth = frameHeight * aspectRatio
        }
        if (frameWidth > maxWidth) {
            frameWidth = maxWidth
            frameHeight = if (aspectRatio > 0f) frameWidth / aspectRatio else frameHeight
        }
        frameWidth = frameWidth.coerceAtLeast(1.dp)
        frameHeight = frameHeight.coerceAtLeast(1.dp)

        val currentTransform by rememberUpdatedState(state.transform)
        val onTransformChange by rememberUpdatedState(actions.onTransformChange)
        val onRendererError by rememberUpdatedState(actions.onRendererError)
        val adjustActive = mediaVisible && !state.busy &&
            state.transform.fit == DynamicWallpaperFit.CUSTOM

        Box(
            modifier = Modifier
                .size(frameWidth, frameHeight)
                .clip(shape)
                .background(containerColor)
                .border(1.dp, borderColor, shape)
        ) {
            if (previewPath != null && !state.rendererFailed) {
                key(previewPath, state.previewUpdatedAt) {
                    DynamicWallpaperBackground(
                        path = previewPath,
                        muted = true,
                        transform = state.transform,
                        fps = state.fps,
                        speed = state.speed,
                        modifier = Modifier.fillMaxSize(),
                        onError = onRendererError,
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    emptyContent(frameHeight < CompactStageHeight)
                }
            }
            if (adjustActive) {
                // The media lives in a WebView and swallows touches, so pinch/pan needs a
                // Compose layer above it, clipped by the frame.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val base = currentTransform
                                val newZoom = (base.zoom * zoom).coerceIn(
                                    DynamicWallpaperTransform.ZOOM_MIN,
                                    DynamicWallpaperTransform.ZOOM_MAX,
                                )
                                val dx = if (size.width > 0) {
                                    pan.x / (size.width * 0.5f)
                                } else {
                                    0f
                                }
                                val dy = if (size.height > 0) {
                                    pan.y / (size.height * 0.5f)
                                } else {
                                    0f
                                }
                                onTransformChange(
                                    base.copy(
                                        zoom = newZoom,
                                        offsetX = (base.offsetX + dx).coerceIn(-1f, 1f),
                                        offsetY = (base.offsetY + dy).coerceIn(-1f, 1f),
                                    )
                                )
                            }
                        }
                )
            }
        }
    }
}
