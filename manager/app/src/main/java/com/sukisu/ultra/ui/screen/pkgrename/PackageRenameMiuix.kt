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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
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
                                contentDescription = null,
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
                        Text(
                            text = state.currentPackage,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.pkg_rename_target),
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            text = state.targetPackage,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = backgroundAwareCardColors(),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.pkg_rename_reroll),
                        onClick = actions.onReroll,
                    )
                    val stageText = state.stage?.let { renameStageLabel(it) }
                    if (state.busy && stageText != null) {
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_start),
                            summary = stageText,
                            onClick = actions.onStart,
                        )
                    } else {
                        ArrowPreference(
                            title = stringResource(R.string.pkg_rename_start),
                            onClick = actions.onStart,
                        )
                    }
                }

                state.error?.let { message ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = backgroundAwareCardColors(),
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            color = colorScheme.error,
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
                        }
                        state.previousPackage?.let { old ->
                            ArrowPreference(
                                title = stringResource(R.string.pkg_rename_uninstall_old),
                                summary = old,
                                onClick = actions.onUninstallOld,
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.pkg_rename_warning),
                    modifier = Modifier.padding(horizontal = 6.dp),
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
