package com.sukisu.ultra.ui.screen.pkgrename

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * miuix 分支：结构按 manager/AGENTS.md 第 2 节（Scaffold + TopAppBar + LazyColumn，卡片间距靠行内 padding）。
 * 内容区只讲「换成什么包名、会有什么后果」，所有可执行动作按状态收在底部操作区。
 */
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
        bottomBar = {
            // 动作吸底：换名过程中的「重试登记 / 撤销 / 重新开始」不需要滚回页面中间找。
            PackageActionBar(state, actions)
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
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            // 当前包名（弱化）→ 裸箭头 → 新包名（强调）：两张独立卡靠层级差表达「替换」。
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .rowSpacing(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CurrentPackageCard(state.currentPackage)
                    RenameArrow()
                    TargetPackageCard(state, actions)
                }
            }
            item {
                // 换名不迁移数据 + 认主说明，都必须出现在按按钮之前。
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

            // 装好了但没登记上：独立状态，绝不能显示成「换名失败」；处置按钮在底部。
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
                        Text(
                            text = stringResource(R.string.pkg_rename_register_failed_after_install, pkg),
                            fontSize = 14.sp,
                            color = colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            // 已完成：纯信息卡，处置按钮在底部。
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
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** 底部操作区：按状态给出唯一的动作组，内容区不再放任何按钮。 */
@Composable
private fun PackageActionBar(
    state: PackageRenameUiState,
    actions: PackageRenameActions,
) {
    if (state.busy) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        // 堆叠按钮之间留 12 dp：与 miuix 卡片间距同一节奏，6 dp 会挤成一坨
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.unregisteredPackage != null -> {
                    // 主操作固定在最底部：那是拇指最容易够到的位置。
                    TextButton(
                        text = stringResource(R.string.pkg_rename_revoke_register),
                        onClick = actions.onRevokeRegister,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.pkg_rename_retry_register),
                        onClick = actions.onRetryRegister,
                    )
                }

                state.finishedPackage != null -> {
                    TextButton(
                        text = stringResource(R.string.pkg_rename_registered_managers),
                        onClick = actions.onOpenManagers,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    )
                    state.previousPackage?.takeIf { state.unregisteredPackage == null }?.let { old ->
                        // 卸载不可逆：用错误色明确标出来。
                        TextButton(
                            text = stringResource(R.string.pkg_rename_uninstall_old) + "（" + old + "）",
                            onClick = actions.onUninstallOld,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.textButtonColors(textColor = colorScheme.error),
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.pkg_rename_open_new),
                        onClick = actions.onOpenNew,
                    )
                }

                else -> PrimaryButton(
                    text = stringResource(R.string.pkg_rename_start),
                    onClick = actions.onStart,
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        onClick = onClick,
        // 换名是主操作，必须用 miuix 主色按钮（默认是次要色）。
        colors = ButtonDefaults.buttonColorsPrimary(),
        cornerRadius = 16.dp,
    ) {
        Text(text = text)
    }
}

/** 卡片统一的行间距：水平 12dp、下 12dp（手册第 4 条，不用 Arrangement.spacedBy 凑卡片间距）。 */
private fun Modifier.rowSpacing(): Modifier = this
    .padding(horizontal = 12.dp)
    .padding(bottom = 12.dp)

/** 当前包名（弱化）：默认卡片 + 弱化文字，把强调留给下方的新包名。 */
@Composable
private fun CurrentPackageCard(currentPackage: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = backgroundAwareCardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.pkg_rename_current),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            PackageNameText(
                name = currentPackage,
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 14,
            )
        }
    }
}

/**
 * 两卡之间只留一个箭头。miuix 图标集里的 ExpandMore 只是个小折角，放大后会散成两个角标，
 * 这里手绘一支带杆的 24dp 向下箭头（形状不是图标，不占 miuix 图标位）。
 */
@Composable
private fun RenameArrow() {
    val tint = colorScheme.onSurfaceVariantSummary
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.dp.toPx()
            drawLine(tint, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.86f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.24f, h * 0.6f), Offset(w * 0.5f, h * 0.86f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.76f, h * 0.6f), Offset(w * 0.5f, h * 0.86f), stroke, StrokeCap.Round)
        }
    }
}

/** 新包名（强调）：主色容器 + 右侧换一个按钮。 */
@Composable
private fun TargetPackageCard(
    state: PackageRenameUiState,
    actions: PackageRenameActions,
) {
    // 已装好未登记 / 已完成时显示真正落到设备上的包名，避免与结果卡对不上。
    val displayTarget = state.unregisteredPackage
        ?: state.finishedPackage
        ?: state.targetPackage
    val canReroll = displayTarget == state.targetPackage && !state.busy
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
                    name = displayTarget,
                    color = colorScheme.onPrimaryContainer,
                    fontSize = 15,
                )
            }
            if (canReroll) {
                IconButton(onClick = actions.onReroll) {
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
