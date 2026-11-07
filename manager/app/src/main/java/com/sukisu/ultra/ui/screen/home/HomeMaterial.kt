package com.sukisu.ultra.ui.screen.home

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import com.sukisu.ultra.data.column.ColumnProgressEntry
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.io.File
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.KernelVersion
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.ModuleEcoBadge
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.backgroundAwareColor
import com.sukisu.ultra.ui.component.WarningLevel
import com.sukisu.ultra.ui.component.HomeCardStickerOverlays
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.IconTile
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.TonalCard
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.component.rebootlistpopup.RebootListPopup
import com.sukisu.ultra.ui.component.statustag.StatusTag
import com.sukisu.ultra.ai.api.AiFeatureBridge
import com.sukisu.ultra.ui.util.module.LatestVersionInfo
import com.sukisu.ultra.ui.util.customText
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.drawPainterCover
import androidx.compose.ui.draw.alpha
import com.sukisu.ultra.ui.util.AiSharedTransition
import com.sukisu.ultra.ui.util.aiSharedElement

private val HeroShape = RoundedCornerShape(28.dp)
private val TileShape = RoundedCornerShape(22.dp)

@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()

    ExpressiveScaffold(
        containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        topBar = { TopBar(title = customText(CustomTextKey.HOME_TITLE), scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                state = state,
                actions = actions,
            )
            if (state.showVersionMismatchWarning) {
                WarningCard(
                    customText(CustomTextKey.HOME_WARN_VERSION_SPOOF),
                    onClick = actions.onOpenVersionSpoof,
                )
            }
            if (state.checkUpdateEnabled) {
                UpdateCard(state = state, actions = actions)
            }
            if (!state.hideHomeWarnings) {
                if (state.showManagerPrBuildWarning && state.showFullStatus) {
                    WarningCard(customText(CustomTextKey.HOME_WARN_PR_BUILD), level = WarningLevel.Notice)
                }
                if (state.requiresNewKernel && state.showFullStatus) {
                    WarningCard(
                        customText(
                            if (state.canInstallKernelUpdate) CustomTextKey.HOME_WARN_REQUIRE_KERNEL
                            else CustomTextKey.HOME_WARN_REQUIRE_KERNEL_GKI
                        ),
                        onClick = if (state.canInstallKernelUpdate) actions.onInstallClick else null
                    )
                }
                if (state.requiresNewManager) {
                    WarningCard(
                        customText(CustomTextKey.HOME_WARN_REQUIRE_MANAGER)
                    )
                }
                if (state.showLkmUpdate && state.showFullStatus) {
                    WarningCard(
                        message = customText(CustomTextKey.HOME_WARN_LKM_UPDATE),
                        level = WarningLevel.Notice,
                        onClick = actions.onInstallClick,
                    )
                }
            }
            if (state.showRootWarning) {
                WarningCard(customText(CustomTextKey.HOME_WARN_ROOT_FAILED))
            }
            if (state.isPreviewMode && !state.hideHomeWarnings) {
                WarningCard(customText(CustomTextKey.HOME_WARN_PREVIEW_MODE), level = WarningLevel.Notice)
            }
            if (state.showAiHomeCard && AiFeatureBridge.isAvailable) {
                AiFeatureBridge.HomeCard(onOpenChat = actions.onOpenAi)
            }
            if (state.hideEnvCard != null) {
                HideEnvCard(
                    entry = state.hideEnvCard,
                    runningCount = state.hideEnvRunningCount,
                    done = state.hideEnvCardDone,
                    onClick = actions.onOpenHideEnv,
                )
            }
            StatsRow(state = state, actions = actions)
            InfoCard(systemInfo = state.systemInfo, showFullStatus = state.showFullStatus)
            SupportLinks(onOpenUrl = actions.onOpenUrl)
            Spacer(
                Modifier.height(
                    bottomInnerPadding + if (!Natives.isFullFeatured())
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
                )
            )
        }
    }
}

@Composable
private fun UpdateCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val newVersion = state.latestVersionInfo
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = state.hasUpdate,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { actions.onReleaseUpdateClick() })
        WarningCard(
            message = customText(CustomTextKey.HOME_UPDATE_NEW_VERSION, newVersion.versionCode),
            level = WarningLevel.Notice
        ) {
            if (newVersion.changelog.isEmpty()) {
                actions.onReleaseUpdateClick()
            } else {
                updateDialog.showConfirm(
                    title = title,
                    content = newVersion.changelog,
                    markdown = true,
                    confirm = updateText
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val displayTitle = customText(CustomTextKey.HOME_TITLE)
    LargeFlexibleTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = title.ifBlank { displayTitle },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ModuleEcoBadge()
            }
        },
        actions = { RebootListPopup() },
        colors = expressiveTopAppBarColors(
            containerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = if (hasCustomBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun StatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val ksuActive = state.ksuVersion != null
    val onClick = {
        if (!state.isLateLoadMode) {
            actions.onInstallClick()
        }
    }
    if (ksuActive) {
        ActiveStatusCard(state = state, onClick = onClick)
    } else {
        InactiveStatusCard(
            state = state,
            actions = actions,
            notInstalled = state.kernelVersion.isGKI(),
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveStatusCard(
    state: HomeUiState,
    onClick: () -> Unit,
) {
    val containerColor = backgroundAwareColor(MaterialTheme.colorScheme.primary)
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val workingMode = when (state.lkmMode) {
        null -> ""
        true -> "LKM"
        else -> "Built-in"
    }

    val cardImagePainter = state.homeCardImagePath.takeIf { it.isNotEmpty() }?.let { path ->
        rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(path))
                .crossfade(true)
                .build(),
            contentScale = ContentScale.Crop,
        )
    }

    val hideCardDecoration = state.homeCardImagePath.isNotEmpty() && state.hideHomeCardDecoration
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .then(
                if (cardImagePainter != null) {
                    Modifier.drawPainterCover(cardImagePainter)
                } else {
                    Modifier
                }
            ),
        color = if (cardImagePainter != null) Color.Transparent else containerColor,
        contentColor = contentColor,
        shape = HeroShape,
        onClick = onClick,
    ) {
        BoxWithConstraints(
            modifier = if (hideCardDecoration) {
                Modifier
            } else {
                Modifier.drawBehind { drawStatusRings(contentColor.copy(alpha = 0.18f)) }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 188.dp)
                    .padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (workingMode.isNotEmpty()) {
                        StatusTag(
                            label = workingMode,
                            contentColor = containerColor,
                            backgroundColor = contentColor,
                        )
                    }
                    if (state.showCustomLkmBadge) {
                        StatusTag(
                            label = customText(CustomTextKey.HOME_TAG_LKM),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                    if (state.isSafeMode) {
                        StatusTag(
                            label = customText(CustomTextKey.HOME_TAG_SAFEMODE),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        )
                    }
                    if (state.isLateLoadMode) {
                        StatusTag(
                            label = stringResource(id = R.string.jailbreak_mode),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!(state.homeCardImagePath.isNotEmpty() && state.hideHomeCardStatusText)) {
                        Text(
                            text = customText(CustomTextKey.HOME_STATUS_WORKING),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = stringResource(R.string.home_working_version, "${state.ksuVersion}-${state.kernelUAPIVersion}"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            HomeCardStickerOverlays(
                stickers = state.homeCardStickers,
                cardWidth = maxWidth,
            )
        }
    }
}

@Composable
private fun InactiveStatusCard(
    state: HomeUiState,
    actions: HomeActions,
    notInstalled: Boolean,
    onClick: () -> Unit,
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val accentColor = MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundAwareColor(MaterialTheme.colorScheme.surfaceBright),
        shape = HeroShape,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawDashedBorder(outlineColor) }
                .heightIn(min = 188.dp)
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (notInstalled) Icons.Rounded.Warning else Icons.Rounded.Block,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (notInstalled) {
                        customText(CustomTextKey.HOME_STATUS_INSTALL_HINT)
                    } else {
                        stringResource(R.string.home_unsupported_reason)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                )
            }
            Text(
                text = stringResource(if (notInstalled) R.string.home_not_installed else R.string.home_unsupported),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
            if (notInstalled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.onInstallClick) {
                        Text(customText(CustomTextKey.HOME_BTN_INSTALL))
                    }
                    if (state.isSELinuxPermissive) {
                        OutlinedButton(onClick = actions.onJailbreakClick) {
                            Text(stringResource(R.string.home_jailbreak))
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStatusRings(color: Color) {
    val center = Offset(size.width - 36.dp.toPx(), 32.dp.toPx())
    val stroke = 2.dp.toPx()
    drawCircle(color = color, radius = 112.dp.toPx(), center = center, style = Stroke(stroke))
    drawCircle(
        color = color,
        radius = 84.dp.toPx(),
        center = center,
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 8.dp.toPx()))),
    )
    drawCircle(color = color, radius = 56.dp.toPx(), center = center, style = Stroke(stroke))
    drawCircle(color = color, radius = 18.dp.toPx(), center = center)
}

private fun DrawScope.drawDashedBorder(color: Color) {
    val stroke = 2.dp.toPx()
    val radius = 28.dp.toPx() - stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = size.copy(width = size.width - stroke, height = size.height - stroke),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}

@Composable
private fun StatsRow(
    state: HomeUiState,
    actions: HomeActions,
) {
    val tiles = buildList<Triple<Int, Int, (() -> Unit)?>> {
        state.superuserCount?.let { add(Triple(it, R.string.superuser, actions.onSuperuserClick)) }
        state.moduleCount?.let { add(Triple(it, R.string.module, actions.onModuleClick)) }
        state.kpmCount?.let { add(Triple(it, R.string.kpm_title, null)) }
    }
    if (tiles.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.forEach { (count, label, onClick) ->
            val labelText = when (label) {
                R.string.superuser -> customText(CustomTextKey.HOME_STATS_SUPERUSER)
                R.string.module -> customText(CustomTextKey.HOME_STATS_MODULE)
                else -> stringResource(label)
            }
            StatTile(
                count = count,
                label = labelText,
                onClick = onClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    count: Int,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val content = @Composable {
        Column(
            modifier = Modifier
                .height(88.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = TileShape,
            color = backgroundAwareColor(MaterialTheme.colorScheme.surfaceBright),
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = TileShape,
            color = backgroundAwareColor(MaterialTheme.colorScheme.surfaceBright),
            content = content,
        )
    }
}

@Composable
private fun WarningCard(
    message: String,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null
) {
    val containerColor = when (level) {
        WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
        WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.contentColorFor(containerColor)
            )
        }
    }
    if (onClick != null) {
        TonalCard(containerColor = containerColor, onClick = onClick, content = content)
    } else {
        TonalCard(containerColor = containerColor, content = content)
    }
}

@Composable
private fun SupportLinks(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val learnMoreUrl = stringResource(R.string.home_learn_kernelsu_url)

    SegmentedColumn(modifier = modifier.fillMaxWidth()) {
        item {
            SegmentedListItem(
                onClick = { onOpenUrl("https://patreon.com/weishu") },
                headlineContent = { Text(stringResource(R.string.home_support_title)) },
                supportingContent = { Text(stringResource(R.string.home_support_content)) },
                leadingContent = {
                    IconTile(Icons.Filled.VolunteerActivism, stringResource(R.string.home_support_title))
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
        }
        item {
            SegmentedListItem(
                onClick = { onOpenUrl(learnMoreUrl) },
                headlineContent = { Text(stringResource(R.string.home_learn_kernelsu)) },
                supportingContent = { Text(stringResource(R.string.home_click_to_learn_kernelsu)) },
                leadingContent = {
                    IconTile(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.home_learn_kernelsu))
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
        }
    }
}

@Composable
private fun SpecSheet(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = TileShape,
        color = backgroundAwareColor(MaterialTheme.colorScheme.surfaceBright),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
    showFullStatus: Boolean = true,
) {
    val selinuxDisplay = when (systemInfo.selinuxStatus) {
        "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
        "Permissive" -> stringResource(R.string.selinux_status_permissive)
        "Disabled" -> stringResource(R.string.selinux_status_disabled)
        else -> stringResource(R.string.selinux_status_unknown)
    }
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> stringResource(R.string.seccomp_status_not_supported)
        0 -> stringResource(R.string.seccomp_status_disabled)
        1 -> stringResource(R.string.seccomp_status_strict)
        2 -> stringResource(R.string.seccomp_status_filter)
        else -> stringResource(R.string.seccomp_status_unknown)
    }

    val manualHookText = stringResource(R.string.manual_hook)
    val inlineHookText = stringResource(R.string.inline_hook)
    val tracepointHookText = stringResource(R.string.tracepoint_hook)
    val unknownHookText = stringResource(R.string.selinux_status_unknown)
    val susfsInfo = rememberSusfsInfo(manualHookText, inlineHookText)
    val isSusfsSupported = susfsInfo.status == SusfsStatus.Supported
    val hookTypeLabel = rememberHookTypeLabel(manualHookText, inlineHookText, tracepointHookText, unknownHookText)

    val systemRows = buildList {
        add(customText(CustomTextKey.HOME_INFO_MANAGER_VERSION) to systemInfo.managerVersion)
        add(customText(CustomTextKey.HOME_INFO_KERNEL) to systemInfo.kernelVersion)
        if (showFullStatus) {
            if (!systemInfo.kernelFullVersion.isNullOrBlank()) {
                add(customText(CustomTextKey.HOME_INFO_KERNEL_FULL) to systemInfo.kernelFullVersion)
            }
            if (isSusfsSupported) {
                add(stringResource(R.string.home_susfs_version) to susfsInfo.detail)
            } else if (!hookTypeLabel.isNullOrBlank()) {
                add(customText(CustomTextKey.HOME_INFO_HOOK_TYPE) to hookTypeLabel)
            }
            if (!systemInfo.zygiskImplementation.isNullOrBlank()) {
                add(customText(CustomTextKey.HOME_INFO_ZYGISK) to systemInfo.zygiskImplementation)
            }
        }
        add(customText(CustomTextKey.HOME_INFO_DEVICE_MODEL) to systemInfo.deviceModel)
        add(customText(CustomTextKey.HOME_INFO_FINGERPRINT) to systemInfo.fingerprint)
    }
    val securityRows = listOf(
        customText(CustomTextKey.HOME_INFO_SELINUX) to selinuxDisplay,
        customText(CustomTextKey.HOME_INFO_SECCOMP) to seccompDisplay,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpecSheet(rows = systemRows)
        SpecSheet(rows = securityRows)
    }
}

@Preview(name = "Activated")
@Composable
private fun StatusCardActivatedPreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true),
        actions = HomeActions({}, {})
    )
}

@Preview(name = "Not Activated")
@Composable
private fun StatusCardNotActivatedPreview() {
    StatusCard(state = previewHomeScreenState(ksuVersion = null, lkmMode = null), actions = HomeActions({}, {}))
}

@Preview(name = "Permissive")
@Composable
private fun StatusCardPermissivePreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive"),
        actions = HomeActions({}, {})
    )
}

@Preview(name = "Jailbreak")
@Composable
private fun StatusCardJailbreakPreview() {
    StatusCard(
        state = previewHomeScreenState(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true),
        actions = HomeActions({}, {})
    )
}

private val previewSystemInfo = SystemInfo(
    kernelVersion = "6.1.0-android14-0-g1234567",
    managerVersion = "1.0.0 (10000)",
    deviceModel = "Google Pixel 6 Pro",
    kernelFullVersion = "v4.1.2-abc1234@main",
    fingerprint = "google/raven/raven:14/AP1A.240305.019:user/release-keys",
    selinuxStatus = "Enforcing",
    seccompStatus = 2
)

private val previewUriHandler = object : UriHandler {
    override fun openUri(uri: String) {}
}

@Composable
private fun HomeScreenPreviewContent(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    selinuxStatus: String = "Enforcing",
) {
    CompositionLocalProvider(LocalUriHandler provides previewUriHandler) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val actions = HomeActions({}, {})
            StatusCard(
                state = previewHomeScreenState(
                    ksuVersion = ksuVersion,
                    lkmMode = lkmMode,
                    isSafeMode = isSafeMode,
                    isLateLoadMode = isLateLoadMode,
                    selinuxStatus = selinuxStatus,
                ),
                actions = actions
            )
            InfoCard(previewSystemInfo.copy(selinuxStatus = selinuxStatus), showFullStatus = true)
            SupportLinks(onOpenUrl = {})
        }
    }
}

@Preview(name = "Home Activated", showBackground = true)
@Composable
private fun HomeScreenActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true)
}

@Preview(name = "Home Not Activated", showBackground = true)
@Composable
private fun HomeScreenNotActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null)
}

@Preview(name = "Home Permissive", showBackground = true)
@Composable
private fun HomeScreenPermissivePreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive")
}

@Preview(name = "Home Jailbreak", showBackground = true)
@Composable
private fun HomeScreenJailbreakPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true)
}

private fun previewHomeScreenState(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    selinuxStatus: String = "Enforcing",
) = HomeUiState(
    kernelVersion = KernelVersion(6, 1, 0),
    ksuVersion = ksuVersion,
    lkmMode = lkmMode,
    isLkmBundled = lkmMode == true,
    isManager = true,
    isManagerPrBuild = false,
    requiresNewKernel = false,
    requiresNewManager = false,
    isRootAvailable = ksuVersion != null,
    isSafeMode = isSafeMode,
    isLateLoadMode = isLateLoadMode,
    checkUpdateEnabled = false,
    showFullStatus = true,
    latestVersionInfo = LatestVersionInfo(),
    currentManagerVersionCode = 10000,
    systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
    kernelUAPIVersion = 1,
    managerUAPIVersion = 1,
)

/**
 * 主页的「隐藏环境进行中」卡片。
 *
 * 只做一件事：告诉用户「上次做到第几步、下一步是什么」，点一下回到向导接着做。
 * 数据来自 ColumnProgressStore 的本地汇总，不进 root、不触发任何检测。
 */
@Composable
fun HideEnvCard(
    entry: ColumnProgressEntry,
    runningCount: Int,
    done: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = (entry.settledSteps.toFloat() / entry.totalSteps.coerceAtLeast(1)).coerceIn(0f, 1f)
    TonalCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (done) R.string.home_hide_env_done_title else R.string.home_hide_env_card_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (done) {
                            stringResource(R.string.home_hide_env_done_summary)
                        } else {
                            entry.topicTitle
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringResource(
                            if (done) R.string.home_hide_env_done_title else R.string.home_hide_env_card_continue,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!done) LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.nextStepTitle.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.home_hide_env_card_next, entry.nextStepTitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (runningCount > 1) {
                    Text(
                        text = stringResource(R.string.home_hide_env_card_more, runningCount - 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
