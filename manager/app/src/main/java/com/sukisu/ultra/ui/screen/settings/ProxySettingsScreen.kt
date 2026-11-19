package com.sukisu.ultra.ui.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.proxy.ProxyEntry
import com.sukisu.ultra.data.proxy.ProxyHealth
import com.sukisu.ultra.data.proxy.ProxyMode
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.SegmentedRadioItem
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton as MiuixRadioButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「GitHub 下载加速」子页：从设置页的一行入口点进来。
 *
 * 设置页不再铺开模式选择 + 每条代理 + 测速按钮那一大块，只留一行入口；
 * 交互细节（模式三选一、指定代理、测速）全部收在这里。
 *
 * 这里按 [LocalUiMode] 分派两套实现：Miuix 主题下必须用 Miuix 组件，
 * 否则从 Miuix 设置页点进来会突然变成 Material 观感。
 */
@Composable
internal fun ProxySettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    onBack: () -> Unit,
) {
    // 进入页面自动测一次；ViewModel 内部有 3 分钟缓存，短时间重复进入不会重复请求。
    LaunchedEffect(Unit) {
        actions.onEnsureProxyTested()
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> ProxySettingsScreenMiuix(uiState = uiState, actions = actions, onBack = onBack)
        UiMode.Material -> ProxySettingsScreenMaterial(uiState = uiState, actions = actions, onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxySettingsScreenMaterial(
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
            ProxySettingsContentMaterial(uiState = uiState, actions = actions)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 模式三选一 + 代理列表 + 测速（Material 版）。
 *
 * 说明段固定放在标题下面；每行只保留域名和测速结果，不再显示索引里的小字备注。
 */
@Composable
private fun ProxySettingsContentMaterial(
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_proxy_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        )
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
                            leadingContent = {
                                RadioButton(
                                    selected = entry.id == uiState.proxyPinnedId,
                                    onClick = null,
                                    enabled = proxyPinned
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (uiState.proxyTesting) {
                                        ProxyTestingWave(MaterialTheme.colorScheme.primary)
                                    } else {
                                        Text(proxyLatencyLabel(health))
                                        Spacer(Modifier.width(8.dp))
                                        // 行的可交互性跟着「指定代理」模式走，状态点同样变灰
                                        ProxyStatusDot(health, enabled = proxyPinned)
                                    }
                                }
                            }
                        )
                    }
                }
                add {
                    // 测速按钮改成胶囊：底色随状态过渡（平时淡、测速时加深），
                    // 刷新图标匀速旋转，右侧再跟一段脉冲——一眼就能看出「正在后台跑」。
                    val container by animateColorAsState(
                        targetValue = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (uiState.proxyTesting) 0.18f else 0.10f
                        ),
                        label = "proxy-test-container",
                    )
                    val spinAngle = rememberTestingSpin(uiState.proxyTesting)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // clip 放在 clickable 前面：水波纹才会被裁成胶囊，而不是方块
                            .clip(RoundedCornerShape(999.dp))
                            .background(container)
                            // 测速中禁用点击，避免叠出好几轮并发探测
                            .clickable(enabled = !uiState.proxyTesting) { actions.onTestProxies() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.settings_proxy_test_all),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = spinAngle },
                            )
                        }
                        Text(
                            text = stringResource(
                                if (uiState.proxyTesting) {
                                    R.string.settings_proxy_testing
                                } else {
                                    R.string.settings_proxy_test_all
                                }
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (uiState.proxyTesting) {
                            ProxyTestingWave(MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ProxySettingsScreenMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_proxy_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MiuixIcon(imageVector = MiuixIcons.Back, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProxySettingsContentMiuix(uiState = uiState, actions = actions)
        }
    }
}

/**
 * 模式三选一 + 代理列表 + 测速（Miuix 版）。
 *
 * 卡片的标题行和说明文字用 MiuixText，单选和状态点用 Miuix 自己的组件，
 * 不借用 Material 的 SegmentedColumn，保证和 Miuix 设置页同一套观感。
 */
@Composable
private fun ProxySettingsContentMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
) {
    val proxyMode = ProxyMode.from(uiState.proxyMode)
    val proxyPinned = proxyMode == ProxyMode.PINNED
    val proxyHealthById = uiState.proxyHealth.associateBy { it.id }
    val proxyPinnedMissing = proxyPinned &&
        uiState.proxyPinnedId.isNotBlank() &&
        uiState.proxyEntries.isNotEmpty() &&
        uiState.proxyEntries.none { it.id == uiState.proxyPinnedId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiuixText(
            text = stringResource(R.string.settings_proxy_intro),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
        )
        AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ProxyModeRowMiuix(
                    title = stringResource(R.string.settings_proxy_mode_official),
                    selected = proxyMode == ProxyMode.OFFICIAL,
                    onClick = { actions.onSetProxyMode(ProxyMode.OFFICIAL.value) },
                )
                ProxyModeRowMiuix(
                    title = stringResource(R.string.settings_proxy_mode_auto),
                    selected = proxyMode == ProxyMode.AUTO,
                    onClick = { actions.onSetProxyMode(ProxyMode.AUTO.value) },
                )
                ProxyModeRowMiuix(
                    title = stringResource(R.string.settings_proxy_mode_pinned),
                    selected = proxyPinned,
                    onClick = { actions.onSetProxyMode(ProxyMode.PINNED.value) },
                )
            }
        }
        if (proxyPinned && (uiState.proxyPinnedId.isBlank() || proxyPinnedMissing)) {
            AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                MiuixText(
                    text = stringResource(
                        if (proxyPinnedMissing) {
                            R.string.settings_proxy_pinned_missing
                        } else {
                            R.string.settings_proxy_pinned_hint
                        }
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
        }
        if (uiState.proxyEntries.isEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                MiuixText(
                    text = stringResource(R.string.settings_proxy_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
            }
        } else {
            AppCard(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(8.dp)) {
                Column {
                    uiState.proxyEntries.forEach { entry ->
                        ProxyEntryRowMiuix(
                            entry = entry,
                            health = proxyHealthById[entry.id],
                            selected = entry.id == uiState.proxyPinnedId,
                            enabled = proxyPinned,
                            testing = uiState.proxyTesting,
                            onClick = { actions.onSetProxyPinnedId(entry.id) },
                        )
                    }
                }
            }
        }
        // 不再套 AppCard：按钮自己就是一颗胶囊，外层再包矩形卡片会多出一层边，显得脏。
        val testing = uiState.proxyTesting
        val spinAngle = rememberTestingSpin(testing)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 与 Material 版保持一致：同款胶囊，只是换成 Miuix 的配色与图标
                .clip(RoundedCornerShape(999.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = if (testing) 0.18f else 0.10f))
                .clickable(enabled = !testing) { actions.onTestProxies() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                // 旋转放在外层 Box：不依赖 Miuix 图标是否暴露 modifier，包一层最稳
                Box(modifier = Modifier.graphicsLayer { rotationZ = spinAngle }) {
                    MiuixIcon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            MiuixText(
                text = stringResource(
                    if (testing) {
                        R.string.settings_proxy_testing
                    } else {
                        R.string.settings_proxy_test_all
                    }
                ),
                color = MiuixTheme.colorScheme.primary,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            if (testing) {
                ProxyTestingWave(MiuixTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 测速进行中时返回一个 0→360 循环角度；不在测速时直接返回 0，连动画对象都不创建。
 *
 * 旋转只写进 graphicsLayer（绘制阶段），不触发重新布局，滚动时几乎不额外耗性能。
 */
@Composable
private fun rememberTestingSpin(rotating: Boolean): Float {
    if (!rotating) return 0f
    val transition = rememberInfiniteTransition(label = "proxy-testing-spin")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "proxy-testing-spin-angle",
    ).value
}

@Composable
private fun ProxyModeRowMiuix(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiuixRadioButton(selected = selected, onClick = null)
        MiuixText(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun ProxyEntryRowMiuix(
    entry: ProxyEntry,
    health: ProxyHealth?,
    selected: Boolean,
    enabled: Boolean,
    testing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiuixRadioButton(selected = selected, onClick = null, enabled = enabled)
        MiuixText(
            text = entry.name,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        // 只给「这一轮还没测到」的行画波浪线：先测完的先显示毫秒，别让全表陪着最慢的那条等。
        if (testing && health == null) {
            ProxyTestingWave(MiuixTheme.colorScheme.primary)
        } else {
            MiuixText(
                text = miuixProxyLatencyLabel(health),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(8.dp))
            ProxyStatusDotMiuix(health = health, enabled = enabled)
        }
    }
}

/**
 * 测速中的波浪线：三个小点依次起伏，比转圈更能表达「按域名逐个探测」的过程。
 */
@Composable
private fun ProxyTestingWave(color: Color) {
    val transition = rememberInfiniteTransition(label = "proxy-testing-wave")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 120),
                ),
                label = "proxy-testing-dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        scaleY = 0.4f + 0.6f * phase
                        alpha = 0.4f + 0.6f * phase
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun miuixProxyLatencyLabel(health: ProxyHealth?): String = when {
    health == null -> stringResource(R.string.settings_proxy_untested)
    !health.reachable -> stringResource(R.string.settings_proxy_unavailable)
    else -> stringResource(R.string.settings_proxy_latency, health.latencyMs)
}

@Composable
private fun ProxyStatusDotMiuix(health: ProxyHealth?, enabled: Boolean = true) {
    val color = when {
        health == null -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
        health.reachable -> ProxyDotGreen
        else -> ProxyDotRed
    }
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.38f)),
    )
}

/** Miuix 配色里没有稳定的成功/错误语义色，测速结果又与主题无关，直接用固定色。 */
private val ProxyDotGreen = Color(0xFF2E7D32)
private val ProxyDotRed = Color(0xFFB3261E)
