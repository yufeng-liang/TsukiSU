package com.sukisu.ultra.ui.screen.pkgrename

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.theme.backgroundAwareCardColors
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = backgroundAwareCardColors(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                        Text(
                            text = stringResource(R.string.pkg_rename_current),
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        PackageNameText(state.currentPackage)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.pkg_rename_target),
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        PackageNameText(state.targetPackage)
                    }
                }

                // 换名是全新安装，不迁移数据：这件事必须在按按钮之前讲清楚。
                Text(
                    text = stringResource(R.string.pkg_rename_data_warning),
                    modifier = Modifier.padding(horizontal = 6.dp),
                    color = colorScheme.onSurfaceVariantSummary,
                )

                // 认主说明放在动作项上方，小屏首屏就能看见。
                Text(
                    text = stringResource(R.string.pkg_rename_warning),
                    modifier = Modifier.padding(horizontal = 6.dp),
                    color = colorScheme.onSurfaceVariantSummary,
                )

                if (idle) {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_reroll),
                            enabled = !state.busy,
                            onClick = actions.onReroll,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_start),
                            enabled = !state.busy,
                            onClick = actions.onStart,
                        )
                    }
                }

                if (state.busy) {
                    Column(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        state.stage?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = renameStageLabel(it),
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                        }
                    }
                }

                state.error?.let { message ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            color = colorScheme.error,
                        )
                    }
                }

                state.unregisteredPackage?.let { pkg ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        Text(
                            text = stringResource(R.string.pkg_rename_register_failed_after_install, pkg),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            color = colorScheme.error,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_retry_register),
                            enabled = !state.busy,
                            onClick = actions.onRetryRegister,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_revoke_register),
                            enabled = !state.busy,
                            onClick = actions.onRevokeRegister,
                        )
                    }
                }

                state.finishedPackage?.let { newPackage ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text(text = stringResource(R.string.pkg_rename_done_title, newPackage))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.pkg_rename_done_body,
                                    state.previousPackage.orEmpty(),
                                ),
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                            if (state.dynamicManagerEnabledByUs) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.pkg_rename_enabled_by_us),
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_open_new),
                            onClick = actions.onOpenNew,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_registered_managers),
                            onClick = actions.onOpenManagers,
                        )
                        state.previousPackage?.takeIf { state.unregisteredPackage == null }?.let { old ->
                            // 卸载不可逆，用错误色标出来。ArrowPreference 的 titleColor 要库内
                            // 的 BasicComponentColors 类型，这里换成红字按钮，效果一致且不用猜类型。
                            TextButton(
                                text = stringResource(R.string.pkg_rename_uninstall_old) + "（" + old + "）",
                                onClick = actions.onUninstallOld,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(textColor = colorScheme.error),
                            )
                        }
                    }
                }

                if (!state.busy && (state.finishedPackage != null || state.unregisteredPackage != null)) {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        // 一次换名只允许一条结果；重新开始是两步：先回初始态，再显式开始。
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_restart),
                            onClick = actions.onRestart,
                        )
                    }
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
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
