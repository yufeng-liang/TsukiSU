package com.sukisu.ultra.ui.screen.pkgrename

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.TonalCard
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors

/**
 * Material 分支：内容区只讲「换成什么包名、会有什么后果」，
 * 所有可执行动作按当前状态统一收在底部操作区，单手够得着、不用滚回页面中间找按钮。
 */
@Composable
internal fun PackageRenameMaterial(
    state: PackageRenameUiState,
    actions: PackageRenameActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.pkg_rename_title)) },
                navigationIcon = {
                    TopBarBackButton(
                        onClick = actions.onBack,
                        contentDescription = stringResource(R.string.pkg_rename_back),
                    )
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 两张独立卡 + 中间裸箭头：弱化当前包名、强调新包名，靠层级差表达「替换」。
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrentPackageCard(state.currentPackage)
                    RenameArrow()
                    TargetPackageCard(state, actions)
                }

                // 两条说明都是「按按钮之前必须知道」的事，用带图标的弱提示而不是纯文字堆叠。
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoticeRow(Icons.Rounded.Info, stringResource(R.string.pkg_rename_data_warning))
                    NoticeRow(Icons.Rounded.VerifiedUser, stringResource(R.string.pkg_rename_warning))
                }

                if (state.busy) {
                    ProgressCard(state.stage?.let { renameStageLabel(it) })
                }

                state.error?.let { message ->
                    MessageCard(
                        icon = Icons.Rounded.ErrorOutline,
                        text = message,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                // 装好了但没登记上：这是独立状态，绝不能显示成「换名失败」。
                state.unregisteredPackage?.let { pkg -> UnregisteredCard(pkg) }

                state.finishedPackage?.let { newPackage -> DoneCard(newPackage, state) }
            }

            PackageActionBar(state, actions)
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.unregisteredPackage != null -> {
                    // 主操作固定在最底部：那是拇指最容易够到的位置。
                    OutlinedButton(
                        onClick = actions.onRevokeRegister,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(stringResource(R.string.pkg_rename_revoke_register))
                    }
                    PrimaryAction(
                        onClick = actions.onRetryRegister,
                        icon = Icons.Rounded.SystemUpdate,
                        label = stringResource(R.string.pkg_rename_retry_register),
                    )
                }

                state.finishedPackage != null -> {
                    TextButton(
                        onClick = actions.onOpenManagers,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(stringResource(R.string.pkg_rename_registered_managers))
                    }
                    // 这个分支只在「没有未登记的新包」时才会走到，所以不必再判 unregisteredPackage
                    if (state.previousPackage != null) {
                        TextButton(
                            onClick = actions.onUninstallOld,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.pkg_rename_uninstall_old),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    PrimaryAction(
                        onClick = actions.onOpenNew,
                        icon = Icons.Rounded.OpenInNew,
                        label = stringResource(R.string.pkg_rename_open_new),
                    )
                }

                else -> PrimaryAction(
                    onClick = actions.onStart,
                    icon = Icons.Rounded.SystemUpdate,
                    label = stringResource(R.string.pkg_rename_start),
                )
            }
        }
    }
}

@Composable
private fun PrimaryAction(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

/** 已装但未登记：纯信息卡，处置按钮在底部。 */
@Composable
private fun UnregisteredCard(pkg: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.pkg_rename_register_failed_after_install, pkg),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 已完成：纯信息卡，处置按钮在底部。 */
@Composable
private fun DoneCard(newPackage: String, state: PackageRenameUiState) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.pkg_rename_done_title, newPackage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.pkg_rename_done_body, state.previousPackage.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.dynamicManagerEnabledByUs) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pkg_rename_enabled_by_us),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** 当前包名（弱化）：透明底 + 1dp 描边，把视觉权重让给下方的新包名。 */
@Composable
private fun CurrentPackageCard(currentPackage: String) {
    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.pkg_rename_current),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PackageNameText(
                name = currentPackage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 两卡之间只留一个裸箭头，不画时间线，靠层级差表达「替换」。
 * 图标库里的 KeyboardArrowDown 只是一道细折角，这里手绘一支带杆的箭头，笔画更清楚。
 */
@Composable
private fun RenameArrow() {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    // 已装好未登记 / 已完成时，卡片必须显示真正落到设备上的那个包名，
    // 不能被控制器里新滚出来的随机包名顶掉，否则两处对不上。
    val displayTarget = state.unregisteredPackage
        ?: state.finishedPackage
        ?: state.targetPackage
    val canReroll = displayTarget == state.targetPackage && !state.busy
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.pkg_rename_target),
                    style = MaterialTheme.typography.labelMedium,
                )
                PackageNameText(
                    name = displayTarget,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (canReroll) {
                IconButton(onClick = actions.onReroll) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.pkg_rename_reroll),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressCard(stageLabel: String?) {
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )
            stageLabel?.let {
                Spacer(Modifier.height(12.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MessageCard(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 包名可能长达 128 字符，给它两行并允许复制。 */
@Composable
private fun PackageNameText(
    name: String,
    color: Color,
    style: TextStyle,
) {
    SelectionContainer {
        Text(
            text = name,
            style = style,
            color = color,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
