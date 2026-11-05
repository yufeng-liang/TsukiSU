package com.sukisu.ultra.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.proxy.ProxyMode
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.SegmentedRadioItem
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.component.material.IconTile

/**
 * 「GitHub 下载加速」子页：从设置页的一行入口点进来。
 *
 * 设置页不再铺开模式选择 + 每条代理 + 测速按钮那一大块，只留一行入口；
 * 交互细节（模式三选一、指定代理、测速）全部收在这里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProxySettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_proxy_title)) },
                navigationIcon = { TopBarBackButton(onBack) },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            ProxySettingsContent(uiState = uiState, actions = actions)
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

/**
 * 模式三选一 + 代理列表 + 测速，与原设置页内嵌版本逻辑一致；
 * 抽成内部函数以便两套外壳（设置页内嵌 / 独立子页）复用。
 */
@Composable
internal fun ProxySettingsContent(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
) {
    val proxyMode = ProxyMode.from(uiState.proxyMode)
    val proxyPinned = proxyMode == ProxyMode.PINNED
    val proxyHealthById = uiState.proxyHealth.associateBy { it.id }
    // 存下来的 id 可能已经不在列表里（下发源下线或改了 id）。这时下载会悄悄退回直连，
    // 界面却还停在「指定代理」上，所以要和「还没选」分开提示。
    // 列表本身为空时判断不了是 id 失效还是列表没拉到，这种情况交给下面的空态文案。
    val proxyPinnedMissing = proxyPinned &&
        uiState.proxyPinnedId.isNotBlank() &&
        uiState.proxyEntries.isNotEmpty() &&
        uiState.proxyEntries.none { it.id == uiState.proxyPinnedId }
    SegmentedColumn(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        content = buildList {
            add {
                SegmentedRadioItem(
                    title = stringResource(R.string.settings_proxy_mode_official),
                    selected = proxyMode == ProxyMode.OFFICIAL,
                    onClick = { actions.onSetProxyMode(ProxyMode.OFFICIAL.value) }
                )
            }
            add {
                SegmentedRadioItem(
                    title = stringResource(R.string.settings_proxy_mode_auto),
                    selected = proxyMode == ProxyMode.AUTO,
                    onClick = { actions.onSetProxyMode(ProxyMode.AUTO.value) }
                )
            }
            add {
                SegmentedRadioItem(
                    title = stringResource(R.string.settings_proxy_mode_pinned),
                    selected = proxyPinned,
                    onClick = { actions.onSetProxyMode(ProxyMode.PINNED.value) }
                )
            }
            // 选了「指定代理」但还没选具体哪一条、或选的那条已经失效时，
            // 明确告诉用户现在实际走的是直连，别让人以为还在用某个代理。
            if (proxyPinned && (uiState.proxyPinnedId.isBlank() || proxyPinnedMissing)) {
                add {
                    SegmentedListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (proxyPinnedMissing) {
                                        R.string.settings_proxy_pinned_missing
                                    } else {
                                        R.string.settings_proxy_pinned_hint
                                    }
                                )
                            )
                        }
                    )
                }
            }
            if (uiState.proxyEntries.isEmpty()) {
                add {
                    SegmentedListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_proxy_empty))
                        }
                    )
                }
            }
            uiState.proxyEntries.forEach { entry ->
                add {
                    val health = proxyHealthById[entry.id]
                    SegmentedListItem(
                        // 只有「指定代理」模式下这些行才是可点的
                        onClick = if (proxyPinned) {
                            { actions.onSetProxyPinnedId(entry.id) }
                        } else {
                            null
                        },
                        enabled = proxyPinned,
                        headlineContent = { Text(entry.name) },
                        supportingContent = {
                            if (entry.note.isNotBlank()) Text(entry.note)
                        },
                        leadingContent = {
                            RadioButton(
                                selected = entry.id == uiState.proxyPinnedId,
                                onClick = null,
                                enabled = proxyPinned
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(proxyLatencyLabel(health))
                                Spacer(Modifier.width(8.dp))
                                // 行的可交互性跟着「指定代理」模式走，状态点同样变灰
                                ProxyStatusDot(health, enabled = proxyPinned)
                            }
                        }
                    )
                }
            }
            add {
                SegmentedListItem(
                    // 测速中禁用整行，避免重复点出好几轮并发探测
                    onClick = if (uiState.proxyTesting) null else actions.onTestProxies,
                    enabled = !uiState.proxyTesting,
                    headlineContent = {
                        Text(
                            stringResource(
                                if (uiState.proxyTesting) {
                                    R.string.settings_proxy_testing
                                } else {
                                    R.string.settings_proxy_test_all
                                }
                            )
                        )
                    },
                    leadingContent = {
                        IconTile(
                            Icons.Filled.Refresh,
                            stringResource(R.string.settings_proxy_test_all),
                            // 测速中整行禁用，图标也跟着灰掉，否则看着像还能再点一次
                            enabled = !uiState.proxyTesting
                        )
                    },
                    trailingContent = {
                        if (uiState.proxyTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )
            }
        }
    )
}
