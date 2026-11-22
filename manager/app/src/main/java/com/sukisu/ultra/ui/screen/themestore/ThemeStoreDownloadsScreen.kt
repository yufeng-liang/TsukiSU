package com.sukisu.ultra.ui.screen.themestore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.ThemeDownloadRecord
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.LargeFlexibleTopAppBar
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "My downloads": packages the store already fetched and kept on this device.
 *
 * A short press applies the cached package through the normal import dialog; a long press enters
 * multi-selection so several packages (or all of them) can be deleted without touching themes
 * that are already applied.
 */
@Composable
internal fun ThemeStoreDownloadsScreen(
    downloads: List<ThemeDownloadRecord>,
    loading: Boolean,
    previewFor: (ThemeDownloadRecord) -> File?,
    onBack: () -> Unit,
    onApply: (ThemeDownloadRecord) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) { mutableStateOf(emptySet<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val totalBytes = remember(downloads) { downloads.sumOf { it.size.coerceAtLeast(0L) } }
    // Resolve each cached cover once per list instead of hitting the disk on every frame.
    val previews = remember(downloads) {
        downloads.associate { it.key to previewFor(it) }
    }

    LaunchedEffect(downloads) {
        val available = downloads.map { it.key }.toSet()
        if (!available.containsAll(selected)) selected = selected.intersect(available)
        if (downloads.isEmpty()) selecting = false
    }

    ExpressiveScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.theme_store_downloads_title)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !loading,
                        colors = themeStoreTopBarIconColors(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.theme_store_upload_refresh),
                        )
                    }
                    IconButton(
                        onClick = {
                            selecting = !selecting
                            if (!selecting) selected = emptySet()
                        },
                        enabled = downloads.isNotEmpty(),
                        colors = themeStoreTopBarIconColors(),
                    ) {
                        Icon(
                            imageVector = if (selecting) Icons.Rounded.Close else Icons.Rounded.Check,
                            contentDescription = stringResource(
                                if (selecting) {
                                    R.string.theme_store_downloads_cancel_selection
                                } else {
                                    R.string.theme_store_downloads_select
                                },
                            ),
                        )
                    }
                    IconButton(
                        onClick = { confirmClear = true },
                        enabled = downloads.isNotEmpty(),
                        colors = themeStoreTopBarIconColors(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.theme_store_downloads_clear_all),
                        )
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            AnimatedVisibility(
                visible = selecting,
                enter = fadeIn(
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                ) + expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
                exit = fadeOut(
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                ) + shrinkVertically(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ),
            ) {
                DownloadSelectionBar(
                    allSelected = downloads.isNotEmpty() && selected.size == downloads.size,
                    selectedCount = selected.size,
                    onToggleAll = {
                        selected = if (downloads.isNotEmpty() && selected.size == downloads.size) {
                            emptySet()
                        } else {
                            downloads.map { it.key }.toSet()
                        }
                    },
                    onDelete = {
                        onDelete(selected)
                        selected = emptySet()
                        selecting = false
                    },
                )
            }
            if (!selecting && downloads.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.theme_store_downloads_summary,
                        downloads.size,
                        formatTransferBytes(totalBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            when {
                loading && downloads.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                }

                downloads.isEmpty() -> DownloadsEmpty(Modifier.weight(1f))

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 36.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(downloads, key = { it.key }) { record ->
                        DownloadCard(
                            record = record,
                            preview = previews[record.key],
                            selecting = selecting,
                            checked = record.key in selected,
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!selecting) selecting = true
                                selected = selected + record.key
                            },
                            onToggle = {
                                selected = if (record.key in selected) {
                                    selected - record.key
                                } else {
                                    selected + record.key
                                }
                            },
                            onApply = { onApply(record) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            ),
                        )
                    }
                    item(key = "hint") {
                        Text(
                            text = stringResource(R.string.theme_store_downloads_long_press_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.theme_store_downloads_clear_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.theme_store_downloads_clear_message,
                        downloads.size,
                        formatTransferBytes(totalBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                        selected = emptySet()
                        selecting = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.theme_store_downloads_clear_all))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClear = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.theme_store_upload_cancel))
                }
            },
        )
    }
}

/**
 * MD3E selection toolbar: a text action on the start side and one filled tonal action that
 * carries the count, so the destructive step is always the visually dominant element.
 */
@Composable
private fun DownloadSelectionBar(
    allSelected: Boolean,
    selectedCount: Int,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onToggleAll,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                stringResource(
                    if (allSelected) {
                        R.string.theme_store_downloads_select_none
                    } else {
                        R.string.theme_store_downloads_select_all
                    },
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        FilledTonalButton(
            onClick = onDelete,
            enabled = selectedCount > 0,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.theme_store_downloads_delete_selected, selectedCount))
        }
    }
}

@Composable
private fun DownloadCard(
    record: ThemeDownloadRecord,
    preview: File?,
    selecting: Boolean,
    checked: Boolean,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = remember(record.downloadedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.downloadedAt))
    }
    val meta = listOf(
        record.author,
        "v${record.version}",
        formatTransferBytes(record.size),
        date,
    ).filter { it.isNotBlank() }.joinToString(" · ")
    // MD3E selected state: tonal container plus a primary outline instead of an elevation jump.
    val containerColor by animateColorAsState(
        targetValue = if (checked && selecting) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            themeStoreCardColor()
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "download-card-container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked && selecting) {
            MaterialTheme.colorScheme.primary
        } else {
            themeStoreCardBorderColor()
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "download-card-border",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = {
                    if (selecting) onToggle() else onApply()
                },
                onLongClick = onLongPress,
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selecting) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle() },
                )
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    Image(
                        painter = rememberAsyncImagePainter(preview),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Wallpaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name.ifBlank { record.themeId },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (record.r18) ThemeR18Badge()
                    if (record.adaptive) AdaptiveThemeBadge()
                    if (record.dynamic) DynamicThemeBadge()
                }
            }
            if (!selecting) {
                FilledTonalIconButton(
                    onClick = onApply,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = stringResource(R.string.theme_store_downloads_open),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.FileDownload,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.theme_store_downloads_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.theme_store_downloads_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
