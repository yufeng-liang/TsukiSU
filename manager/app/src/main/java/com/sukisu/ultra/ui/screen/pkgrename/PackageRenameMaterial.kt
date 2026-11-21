package com.sukisu.ultra.ui.screen.pkgrename

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors

@Composable
internal fun PackageRenameMaterial(
    state: PackageRenameUiState,
    actions: PackageRenameActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val idle = !state.busy && state.finishedPackage == null && state.unregisteredPackage == null

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.pkg_rename_current),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    PackageNameText(state.currentPackage)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.pkg_rename_target),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    PackageNameText(state.targetPackage)
                }
            }

            // 换名是全新安装，不迁移数据：这件事必须在按按钮之前讲清楚。
            Text(
                text = stringResource(R.string.pkg_rename_data_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 认主说明放在动作按钮上方：小屏首屏就能看见，不会被「卸载旧包」按钮挤到下面。
            Text(
                text = stringResource(R.string.pkg_rename_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (idle) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = actions.onReroll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.pkg_rename_reroll))
                    }
                    Button(
                        onClick = actions.onStart,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.pkg_rename_start))
                    }
                }
            }

            if (state.busy) {
                Column(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.stage?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = renameStageLabel(it),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // 装好了但没登记上：这是独立状态，绝不能显示成「换名失败」。
            state.unregisteredPackage?.let { pkg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.pkg_rename_register_failed_after_install, pkg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = actions.onRetryRegister,
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.pkg_rename_retry_register))
                            }
                            OutlinedButton(
                                onClick = actions.onRevokeRegister,
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.pkg_rename_revoke_register))
                            }
                        }
                    }
                }
            }

            state.finishedPackage?.let { newPackage ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.pkg_rename_done_title, newPackage),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.pkg_rename_done_body,
                                state.previousPackage.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.dynamicManagerEnabledByUs) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.pkg_rename_enabled_by_us),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = actions.onOpenNew,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.pkg_rename_open_new))
                        }
                        TextButton(onClick = actions.onOpenManagers) {
                            Text(stringResource(R.string.pkg_rename_registered_managers))
                        }
                        if (state.previousPackage != null && state.unregisteredPackage == null) {
                            TextButton(onClick = actions.onUninstallOld) {
                                Text(
                                    text = stringResource(R.string.pkg_rename_uninstall_old),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            if (!state.busy && (state.finishedPackage != null || state.unregisteredPackage != null)) {
                // 一次换名只允许一条结果。重新开始是两步：先回到初始态，再显式点「开始换名」。
                OutlinedButton(
                    onClick = actions.onRestart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pkg_rename_restart))
                }
            }
        }
    }
}

/** 包名可能长达 128 字符，给它两行并允许复制。 */
@Composable
private fun PackageNameText(name: String) {
    SelectionContainer {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
