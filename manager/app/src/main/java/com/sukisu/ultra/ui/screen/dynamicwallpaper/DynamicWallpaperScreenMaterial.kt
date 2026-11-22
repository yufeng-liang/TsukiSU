package com.sukisu.ultra.ui.screen.dynamicwallpaper

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.background.DynamicWallpaperFit
import com.sukisu.ultra.ui.component.material.ExpressiveDialog
import com.sukisu.ultra.ui.component.material.TonalCard
import com.sukisu.ultra.ui.theme.isInDarkTheme
import com.sukisu.ultra.ui.util.HomeLayout
import com.sukisu.ultra.ui.util.HomeStatusStyle
import com.sukisu.ultra.ui.util.ImageStorage

@Composable
fun DynamicWallpaperScreenMaterial(
    state: DynamicWallpaperPreviewUiState,
    actions: DynamicWallpaperPreviewActions,
) {
    val context = LocalContext.current
    val sizeLabel = if (state.sizeBytes > 0L) {
        Formatter.formatShortFileSize(context, state.sizeBytes)
    } else {
        ""
    }
    val formatLabel = when (state.kind) {
        ImageStorage.DynamicWallpaperKind.MP4 -> stringResource(R.string.dynamic_wallpaper_format_mp4)
        ImageStorage.DynamicWallpaperKind.WEBP -> stringResource(R.string.dynamic_wallpaper_format_webp)
        null -> ""
    }
    val hasPreview = state.previewPath != null
    // The card target previews its media inside the card stage, never behind the whole page.
    val mediaVisible = hasPreview && !state.rendererFailed && !state.isCard
    val topBarContentColor = if (mediaVisible) Color.White else MaterialTheme.colorScheme.onSurface
    val topBarSecondaryColor = if (mediaVisible) {
        Color.White.copy(alpha = 0.76f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canPick = !state.busy
    val maxPanelHeight = with(LocalDensity.current) {
        maxOf(180.dp, LocalWindowInfo.current.containerSize.height.toDp() * 0.55f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (mediaVisible) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            )
    ) {
        if (mediaVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.52f),
                            0.18f to Color.Black.copy(alpha = 0.12f),
                            0.62f to Color.Transparent,
                            0.82f to Color.Black.copy(alpha = 0.24f),
                            1f to Color.Black.copy(alpha = 0.62f),
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onBack, enabled = !state.busy) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.log_viewer_back),
                        tint = topBarContentColor,
                    )
                }
                Text(
                    text = stringResource(
                        if (state.isCard) {
                            R.string.dynamic_home_card_title
                        } else {
                            R.string.dynamic_wallpaper_title
                        }
                    ),
                    color = topBarContentColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                if (state.hasStaged) {
                    Text(
                        text = stringResource(R.string.dynamic_wallpaper_pending),
                        color = topBarContentColor,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (mediaVisible) {
                                    Color.White.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }

            if (state.isCard) {
                DynamicHomeCardStage(
                    state = state,
                    actions = actions,
                    shape = if (state.cardLayout == HomeLayout.IMMERSIVE) {
                        RoundedCornerShape(HomeStatusStyle.BannerCorner)
                    } else {
                        RoundedCornerShape(28.dp)
                    },
                    borderColor = if (isInDarkTheme()) {
                        Color.White.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                ) { compact ->
                    CardStageEmptyMaterial(
                        state = state,
                        actions = actions,
                        compact = compact,
                    )
                }
            } else if (!mediaVisible) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyPreviewMaterial(
                            rendererFailed = state.rendererFailed,
                            error = state.rendererError,
                            onRetry = actions.onRetryRenderer,
                            contentColor = topBarContentColor,
                            secondaryColor = topBarSecondaryColor,
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            TonalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxPanelHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    when {
                                        state.hasStaged && state.isCard ->
                                            R.string.dynamic_home_card_pending_title
                                        state.hasStaged -> R.string.dynamic_wallpaper_pending_title
                                        state.isCard -> R.string.dynamic_home_card_title
                                        else -> R.string.dynamic_wallpaper_title
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val detail = listOfNotNull(
                                formatLabel.takeIf { it.isNotEmpty() },
                                sizeLabel.takeIf { it.isNotEmpty() },
                            ).joinToString(" · ")
                            Text(
                                text = detail.ifEmpty {
                                    stringResource(
                                        if (state.isCard) {
                                            R.string.dynamic_home_card_summary
                                        } else {
                                            R.string.dynamic_wallpaper_empty_summary
                                        }
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.busy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (state.kind == ImageStorage.DynamicWallpaperKind.MP4 && !state.isCard) {
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.muted) {
                                    Icons.AutoMirrored.Rounded.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Rounded.VolumeUp
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.dynamic_wallpaper_mute),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(R.string.dynamic_wallpaper_mute_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.muted,
                                onCheckedChange = actions.onSetMuted,
                                enabled = !state.busy,
                            )
                        }
                        HorizontalDivider()
                    }

                    Text(
                        text = stringResource(R.string.dynamic_wallpaper_scale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ScalingChipMaterial(
                            label = stringResource(R.string.dynamic_wallpaper_fit_fit),
                            selected = state.transform.fit == DynamicWallpaperFit.FIT,
                            enabled = !state.busy,
                            onClick = { actions.onSelectFit(DynamicWallpaperFit.FIT) },
                        )
                        ScalingChipMaterial(
                            label = stringResource(R.string.dynamic_wallpaper_fit_fill),
                            selected = state.transform.fit == DynamicWallpaperFit.FILL,
                            enabled = !state.busy,
                            onClick = { actions.onSelectFit(DynamicWallpaperFit.FILL) },
                        )
                        ScalingChipMaterial(
                            label = stringResource(R.string.dynamic_wallpaper_fit_custom),
                            selected = state.transform.fit == DynamicWallpaperFit.CUSTOM,
                            enabled = !state.busy,
                            onClick = { actions.onSelectFit(DynamicWallpaperFit.CUSTOM) },
                        )
                    }
                    if (state.transform.fit == DynamicWallpaperFit.CUSTOM) {
                        Text(
                            text = stringResource(R.string.dynamic_wallpaper_adjust_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = stringResource(R.string.dynamic_wallpaper_frame_rate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ScalingChipMaterial(
                            label = stringResource(R.string.dynamic_wallpaper_frame_rate_auto),
                            selected = state.fps == 0,
                            enabled = !state.busy,
                            onClick = { actions.onSetFps(0) },
                        )
                        listOf(24, 30, 60).forEach { value ->
                            ScalingChipMaterial(
                                label = value.toString(),
                                selected = state.fps == value,
                                enabled = !state.busy,
                                onClick = { actions.onSetFps(value) },
                            )
                        }
                    }

                    if (state.kind == ImageStorage.DynamicWallpaperKind.MP4) {
                        Text(
                            text = stringResource(R.string.dynamic_wallpaper_playback_speed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(0.5f, 1f, 1.5f, 2f).forEach { value ->
                                ScalingChipMaterial(
                                    label = if (value == 1f) "1×" else "${value}×",
                                    selected = kotlin.math.abs(state.speed - value) < 0.01f,
                                    enabled = !state.busy,
                                    onClick = { actions.onSetSpeed(value) },
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.dynamic_wallpaper_playback_scope),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.hasStaged) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(
                                onClick = actions.onDiscardStaged,
                                enabled = !state.busy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(24.dp),
                            ) {
                                Text(stringResource(R.string.dynamic_wallpaper_discard))
                            }
                            Button(
                                onClick = actions.onApply,
                                enabled = !state.busy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.dynamic_wallpaper_apply))
                            }
                        }
                    } else {
                        Button(
                            onClick = actions.onPick,
                            enabled = canPick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wallpaper,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    when {
                                        state.isCard && state.hasSaved ->
                                            R.string.dynamic_home_card_replace
                                        state.hasSaved -> R.string.dynamic_wallpaper_replace
                                        else -> R.string.dynamic_wallpaper_pick
                                    }
                                )
                            )
                        }
                        if (state.hasSaved) {
                            TextButton(
                                onClick = actions.onRequestRemove,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.dynamic_wallpaper_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(
                            if (state.isCard) {
                                R.string.dynamic_home_card_limit
                            } else {
                                R.string.dynamic_wallpaper_limit
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (state.showRemoveConfirm) {
        ExpressiveDialog(
            onDismissRequest = actions.onDismissRemove,
            title = {
                Text(
                    stringResource(
                        if (state.isCard) {
                            R.string.dynamic_home_card_remove_title
                        } else {
                            R.string.dynamic_wallpaper_remove_title
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (state.isCard) {
                            R.string.dynamic_home_card_remove_message
                        } else {
                            R.string.dynamic_wallpaper_remove_message
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onConfirmRemove) {
                    Text(
                        text = stringResource(R.string.dynamic_wallpaper_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissRemove) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CardStageEmptyMaterial(
    state: DynamicWallpaperPreviewUiState,
    actions: DynamicWallpaperPreviewActions,
    compact: Boolean,
) {
    val failed = state.rendererFailed
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = failed, onClick = actions.onRetryRenderer)
            .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (compact) 22.dp else 28.dp),
        )
        Text(
            text = stringResource(
                if (failed) {
                    R.string.dynamic_wallpaper_preview_failed_title
                } else {
                    R.string.dynamic_wallpaper_empty_title
                }
            ),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (failed) {
            Text(
                text = stringResource(R.string.dynamic_wallpaper_retry),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else if (!compact) {
            Text(
                text = stringResource(R.string.dynamic_home_card_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyPreviewMaterial(
    rendererFailed: Boolean,
    error: String,
    onRetry: () -> Unit,
    contentColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayCircle,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.9f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(
                if (rendererFailed) {
                    R.string.dynamic_wallpaper_preview_failed_title
                } else {
                    R.string.dynamic_wallpaper_empty_title
                }
            ),
            color = contentColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (rendererFailed) {
                    R.string.dynamic_wallpaper_preview_failed_summary
                } else {
                    R.string.dynamic_wallpaper_empty_summary
                }
            ),
            color = secondaryColor,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (rendererFailed && error.isNotBlank()) {
            Text(
                text = error,
                color = contentColor.copy(alpha = 0.62f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
        if (rendererFailed) {
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 4.dp),
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = contentColor.copy(alpha = 0.16f),
                    contentColor = contentColor,
                ),
            ) {
                Text(stringResource(R.string.dynamic_wallpaper_retry))
            }
        }
    }
}

@Composable
private fun ScalingChipMaterial(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
    )
}
