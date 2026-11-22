package com.sukisu.ultra.ui.screen.pkgrename

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.WarningLevel
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.component.miuix.WarningCard
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.theme.backgroundAwareCardColors
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** miuix 分支：结构按 manager/AGENTS.md 第 2 节（Scaffold + TopAppBar + LazyColumn，卡片间距靠行内 padding）。 */
@Composable
internal fun PackageRenameMiuix(
    state: PackageRenameUiState,
    actions: PackageRenameActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val blurActive = backdrop != null
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val barColor = if (blurActive || hasCustomBackground) Color.Transparent else colorScheme.surface
    val title = stringResource(R.string.pkg_rename_title)
    val idle = !state.busy && state.finishedPackage == null && state.unregisteredPackage == null

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                tint = colorScheme.onSurface,
                                contentDescription = stringResource(R.string.pkg_rename_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 12.dp),
        ) {
            // 换名不迁移数据 + 认主说明，都必须出现在按按钮之前。
            item {
                PackageSwapCard(state, actions)
            }
            item {
                WarningCard(
                    message = stringResource(R.string.pkg_rename_data_warning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .rowSpacing(),
                    level = WarningLevel.Notice,
                )
            }
            item {
                WarningCard(
                    message = stringResource(R.string.pkg_rename_warning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .rowSpacing(),
                    level = WarningLevel.Notice,
                )
            }

            if (idle) {
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rowSpacing()
                            .height(50.dp),
                        onClick = actions.onStart,
                        // 换名是本页唯一的主操作，必须用 miuix 主色按钮。
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        cornerRadius = 16.dp,
                    ) {
                        Text(text = stringResource(R.string.pkg_rename_start))
                    }
                }
            }

            if (state.busy) {
                item {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rowSpacing()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        colors = backgroundAwareCardColors(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            state.stage?.let {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = renameStageLabel(it),
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            state.error?.let { message ->
                item {
                    WarningCard(
                        message = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .rowSpacing()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        level = WarningLevel.Error,
                    )
                }
            }

            // 装好了但没登记上：独立状态，绝不能显示成「换名失败」。
            state.unregisteredPackage?.let { pkg ->
                item {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rowSpacing(),
                        colors = CardDefaults.defaultColors(
                            color = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.pkg_rename_register_failed_after_install, pkg),
                                fontSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    onClick = actions.onRetryRegister,
                                    enabled = !state.busy,
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    cornerRadius = 12.dp,
                                ) {
                                    Text(text = stringResource(R.string.pkg_rename_retry_register))
                                }
                                TextButton(
                                    text = stringResource(R.string.pkg_rename_revoke_register),
                                    onClick = actions.onRevokeRegister,
                                    modifier = Modifier.weight(1f),
                                    enabled = !state.busy,
                                )
                            }
                        }
                    }
                }
            }

            state.finishedPackage?.let { newPackage ->
                item {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .rowSpacing(),
                        colors = CardDefaults.defaultColors(
                            color = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = MiuixIcons.Ok,
                                    contentDescription = null,
                                    tint = colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(22.dp),
                                )
                                Text(
                                    text = stringResource(R.string.pkg_rename_done_title, newPackage),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight(550),
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.pkg_rename_done_body,
                                    state.previousPackage.orEmpty(),
                                ),
                                fontSize = 14.sp,
                                color = colorScheme.onPrimaryContainer,
                            )
                            if (state.dynamicManagerEnabledByUs) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.pkg_rename_enabled_by_us),
                                    fontSize = 13.sp,
                                    color = colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                onClick = actions.onOpenNew,
                                cornerRadius = 14.dp,
                            ) {
                                Text(text = stringResource(R.string.pkg_rename_open_new))
                            }
                            TextButton(
                                text = stringResource(R.string.pkg_rename_registered_managers),
                                onClick = actions.onOpenManagers,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            state.previousPackage?.takeIf { state.unregisteredPackage == null }?.let { old ->
                                // 卸载不可逆：用错误色明确标出来。
                                TextButton(
                                    text = stringResource(R.string.pkg_rename_uninstall_old) + "（" + old + "）",
                                    onClick = actions.onUninstallOld,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColors(textColor = colorScheme.error),
                                )
                            }
                        }
                    }
                }
            }

            if (!state.busy && (state.finishedPackage != null || state.unregisteredPackage != null)) {
                item {
                    // 一次换名只允许一条结果；重新开始是两步：先回初始态，再显式开始。
                    TextButton(
                        text = stringResource(R.string.pkg_rename_restart),
                        onClick = actions.onRestart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .rowSpacing(),
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** 卡片统一的行间距：水平 12dp、下 12dp（手册第 4 条，不用 Arrangement.spacedBy 凑卡片间距）。 */
private fun Modifier.rowSpacing(): Modifier = this
    .padding(horizontal = 12.dp)
    .padding(bottom = 12.dp)

/**
 * 换名过程卡：当前包名在上，向下箭头表示「换成」，新包名在下并用主色容器强调，
 * 右侧刷新按钮直接换一个候选包名。
 */
@Composable
private fun PackageSwapCard(
    state: PackageRenameUiState,
    actions: PackageRenameActions,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .rowSpacing(),
        colors = backgroundAwareCardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.pkg_rename_current),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            PackageNameText(
                name = state.currentPackage,
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 14,
            )

            // 箭头把「当前 → 新」的变化方向画出来，视觉上是一条替换链。
            Icon(
                imageVector = MiuixIcons.ExpandMore,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariantSummary,
                // miuix 图标保持原生 28dp，压小会糊成一条细线。
                modifier = Modifier.padding(vertical = 2.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                colors = CardDefaults.defaultColors(
                    color = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pkg_rename_target),
                            fontSize = 13.sp,
                            color = colorScheme.onPrimaryContainer,
                        )
                        PackageNameText(
                            name = state.targetPackage,
                            color = colorScheme.onPrimaryContainer,
                            fontSize = 15,
                        )
                    }
                    IconButton(
                        onClick = actions.onReroll,
                        enabled = !state.busy,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            tint = colorScheme.onPrimaryContainer,
                            contentDescription = stringResource(R.string.pkg_rename_reroll),
                        )
                    }
                }
            }
        }
    }
}

/** 包名可能长达 128 字符，给它两行并允许复制。 */
@Composable
private fun PackageNameText(
    name: String,
    color: Color,
    fontSize: Int,
) {
    SelectionContainer {
        Text(
            text = name,
            color = color,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
