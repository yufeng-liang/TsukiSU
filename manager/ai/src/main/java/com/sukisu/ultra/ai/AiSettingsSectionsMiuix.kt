package com.sukisu.ultra.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableColumn
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Miuix 设置页的分组卡片。
 *
 * Material 侧的 [SettingsSection] 用 Material3 Card 承载分组；Miuix 侧不能复用，
 * 否则卡片底色与文字配色都拿不到 Miuix 主题，整段会发灰。这里用 [AiAppCard] 加同构的标题区。
 */
@Composable
internal fun AiSettingsCardMiuix(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 对齐应用设置页：卡片左右 16dp、上下 12dp 内边距，行与行 10dp。之前用卡片默认内边距，
    // 文字几乎贴着卡片边框。
    // highlighted：从聊天页快捷条跳进来时给目标卡描一圈边，滚到位也认得出是哪一组。
    AiAppCard(
        modifier = modifier
            .aiFocusRing(highlighted, RoundedCornerShape(16.dp), colorScheme.primary)
            .fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun AiSettingsSectionMiuix(
    title: String? = null,
    subtitle: String? = null,
    contentSpacing: Dp = 10.dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AiSettingsCardMiuix(modifier = modifier, highlighted = highlighted) {
        if (title != null) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(contentSpacing), content = content)
    }
}

/**
 * Miuix 设置页的「联网搜索」分组。
 *
 * 数据与交互与 Material 版 [AiSearchSettingsSection] 完全一致，只把控件换成 Miuix：
 * Material3 的 Switch / Text 在 Miuix 外壳里拿不到主题配色，整段会变成半透明的灰。
 */
@Composable
internal fun AiSearchSettingsSectionMiuix(modifier: Modifier = Modifier) {
    var config by remember { mutableStateOf(AiSearchSettings.load()) }
    // 同一时刻只允许一个测试请求在跑；null 表示空闲。
    var testing by remember { mutableStateOf<AiSearchProvider?>(null) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    val testResults = remember { mutableStateMapOf<AiSearchProvider, AiSearchTestResult>() }
    var testQuery by remember { mutableStateOf("") }
    var detailProvider by remember { mutableStateOf<AiSearchProvider?>(null) }
    val scope = rememberCoroutineScope()

    // 离开设置页就取消未完成的测试，避免回调写进已经销毁的组合。
    DisposableEffect(Unit) {
        onDispose {
            testJob?.cancel()
            testJob = null
        }
    }

    /** 先更新内存状态再落盘，保证开关立即响应。 */
    fun persist(next: AiSearchConfig) {
        config = next
        AiSearchSettings.save(next)
    }

    /** 发起一次真实搜索测试：消耗一次调用，结果在弹窗里展示。 */
    fun runTest(provider: AiSearchProvider, query: String) {
        if (testing != null) return
        testing = provider
        testResults.remove(provider)
        testJob = scope.launch {
            val result = try {
                AiWebSearch.test(provider, config.keyOf(provider), query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AiSearchTestResult.Failure(provider, error.message ?: error.javaClass.simpleName)
            }
            testResults[provider] = result
            testing = null
        }
    }

    val order = config.uiOrder()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ① 总开关：关掉后下面的服务行全部禁用（内容仍可见，方便先填 Key 再打开）。
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_search_enable),
                    style = MiuixTheme.textStyles.body1,
                )
                Text(
                    text = stringResource(R.string.ai_search_enable_desc),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Switch(
                checked = config.searchEnabled,
                onCheckedChange = { persist(config.copy(searchEnabled = it)) },
            )
        }

        // 总闸关掉只是暂停：服务选择与顺序都保留着，必须让用户看得见这一点。
        if (!config.searchEnabled) {
            Text(
                text = stringResource(R.string.ai_search_paused),
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            thickness = 0.5.dp,
            color = colorScheme.outline.copy(alpha = 0.5f),
        )

        // ② 四家搜索服务：列表只留名称与开关，更多设置进二级弹窗。
        // 拖拽排序交给 reorderable 库（项目已有该依赖），拖动时行会跟着手指走，其余行自动让位。
        ReorderableColumn(
            list = order,
            // 总闸关闭时整组压暗：视觉上明确“这些服务现在不参与搜索”，但开关仍可点（点开关会自动打开总闸）。
            modifier = Modifier.alpha(if (config.searchEnabled) 1f else 0.55f),
            // 库把位移回调叫 onSettle：from/to 是交换前后的下标，直接落盘即可。
            onSettle = { from, to ->
                val next = order.toMutableList()
                if (from in next.indices && to in next.indices) {
                    next.add(to, next.removeAt(from))
                    persist(config.copy(order = next))
                }
            },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { _, provider, isDragging ->
            ReorderableItem(Modifier.fillMaxWidth()) {
            SearchProviderRowMiuix(
                provider = provider,
                checked = config.isEnabled(provider),
                sectionEnabled = config.searchEnabled,
                keyMissing = provider.requiresKey && config.keyOf(provider).isBlank(),
                dragging = isDragging,
                // 排序只是配置，与总闸是否打开无关，永远可拖。
                handleModifier = Modifier.draggableHandle(),
                onOpen = { detailProvider = provider },
                onToggle = { enabled ->
                    val nextEnabled = if (enabled) config.enabled + provider
                    else config.enabled - provider
                    // 关掉最后一个服务时总闸跟随关闭；重新启用不自动打开总闸，避免悄悄恢复付费调用。
                    val nextSearchEnabled = when {
                        nextEnabled.isEmpty() -> false
                        // 总闸原本关着、用户却点了某个服务开关：这是明确意图，顺手把总闸打开。
                        !config.searchEnabled && enabled -> true
                        else -> config.searchEnabled
                    }
                    persist(config.copy(enabled = nextEnabled, searchEnabled = nextSearchEnabled))
                },
            )
            }
        }

        // ③ 空链或缺 Key 都要明说：否则用户会以为搜索在工作，其实一次请求都不会发出。
        if (config.enabled.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_search_none_enabled),
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
            )
        } else {
            val missingKeys = config.enabled.count { it.requiresKey && config.keyOf(it).isBlank() }
            if (missingKeys > 0) {
                Text(
                    text = stringResource(R.string.ai_search_missing_key_summary, missingKeys),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    detailProvider?.let { provider ->
        SearchProviderDetailDialogMiuix(
            provider = provider,
            config = config,
            testing = testing,
            result = testResults[provider],
            testQuery = testQuery,
            onQueryChange = { testQuery = it },
            onKeyChange = { value ->
                persist(config.copy(keys = config.keys + (provider.name to value)))
            },
            onTest = { runTest(provider, testQuery) },
            onDismiss = { detailProvider = null },
        )
    }
}

/** 单个搜索服务行（Miuix）：[拖柄][服务名称][启用开关]，整行可点进二级弹窗。 */
@Composable
private fun SearchProviderRowMiuix(
    provider: AiSearchProvider,
    checked: Boolean,
    sectionEnabled: Boolean,
    keyMissing: Boolean,
    dragging: Boolean,
    handleModifier: Modifier,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val nameColor = if (sectionEnabled) colorScheme.onSurface else colorScheme.onSurfaceVariantSummary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SearchRowShape)
            .clickable(onClick = onOpen)
            .background(if (dragging) colorScheme.surfaceContainerHigh else Color.Transparent)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(DragHandleTouchSize).then(handleModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.ai_search_drag_handle),
                tint = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = providerTitle(provider),
                style = MiuixTheme.textStyles.body1,
                color = nameColor,
            )
            // 必填 Key 的服务在没填 Key 时明确标出来，否则“已启用却永远搜不到”无从解释。
            if (keyMissing) {
                Text(
                    text = stringResource(R.string.ai_search_missing_key),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.error,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(it) },
        )
    }
}

/** 单个搜索服务的二级设置弹窗（Miuix）：说明、API Key、申请入口、测试与最近一次结果。 */
@Composable
private fun SearchProviderDetailDialogMiuix(
    provider: AiSearchProvider,
    config: AiSearchConfig,
    testing: AiSearchProvider?,
    result: AiSearchTestResult?,
    testQuery: String,
    onQueryChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val keyUrl = keyUrlOf(provider)
    val isTesting = testing == provider
    // Key 输入只改本地草稿，停止输入 500ms 后才落盘，避免每敲一个字符都写 SharedPreferences。
    var draftKey by remember(provider) { mutableStateOf(config.keyOf(provider)) }
    LaunchedEffect(draftKey) {
        if (draftKey != config.keyOf(provider)) {
            delay(500)
            onKeyChange(draftKey)
        }
    }

    OverlayDialog(
        show = true,
        title = providerTitle(provider),
        onDismissRequest = {
            // 关闭弹窗时把尚未到 debounce 的输入补一次落盘，避免丢字。
            if (draftKey != config.keyOf(provider)) onKeyChange(draftKey)
            onDismiss()
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = providerDescription(provider),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                TextField(
                    value = draftKey,
                    onValueChange = { draftKey = it },
                    // 免 Key 也能用的服务，占位词直接标“可选”，省得用户以为必须去申请。
                    label = stringResource(R.string.ai_search_api_key) +
                        if (provider.requiresKey) {
                            stringResource(R.string.ai_field_required)
                        } else {
                            stringResource(R.string.ai_field_optional)
                        },
                    useLabelAsPlaceholder = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (keyUrl != null) {
                    // 走浏览器领 Key；填完回来接着写，不打断弹窗。这里只做文字链，不要块状按钮抢主次。
                    Text(
                        text = stringResource(R.string.ai_search_get_key),
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { uriHandler.openUri(keyUrl) }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.ai_search_test_query),
                    style = MiuixTheme.textStyles.body2,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = testQuery,
                        onValueChange = onQueryChange,
                        label = stringResource(R.string.ai_search_test_query_hint),
                        useLabelAsPlaceholder = true,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onTest) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.ai_search_test_run),
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                when (result) {
                    is AiSearchTestResult.Success -> {
                        if (result.count == 0) {
                            Text(
                                text = stringResource(R.string.ai_search_test_no_result),
                                style = MiuixTheme.textStyles.footnote1,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.ai_search_test_success,
                                    result.latencyMs,
                                    result.count,
                                ),
                                style = MiuixTheme.textStyles.footnote1,
                                color = colorScheme.primary,
                            )
                            // 只展示前三条，弹窗里能确认真实返回即可。
                            result.results.take(3).forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = item.title,
                                        style = MiuixTheme.textStyles.footnote1,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.url,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    is AiSearchTestResult.Failure -> {
                        Text(
                            text = stringResource(
                                R.string.ai_search_test_failure,
                                result.message,
                            ),
                            style = MiuixTheme.textStyles.footnote1,
                            color = colorScheme.error,
                        )
                    }

                    null -> Unit
                }
                Text(
                    text = stringResource(R.string.ai_search_test_cost),
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            TextButton(
                text = stringResource(R.string.ai_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        },
    )
}

/**
 * Miuix 设置页的「MCP 服务器」分组：列表 + 新增 + 详情。
 *
 * 与 Material 版 [AiMcpSettingsSection] 同数据同行为，控件换成 Miuix；
 * 请求头在两个弹窗里都用「一行一条 Key: Value」的文本框，比逐行增删输入框更适合窄屏。
 */
@Composable
internal fun AiMcpSettingsSectionMiuix(modifier: Modifier = Modifier) {
    var servers by remember { mutableStateOf(AiMcpSettings.list()) }
    var showAddServer by remember { mutableStateOf(false) }
    var serverDetail by remember { mutableStateOf<AiMcpServer?>(null) }

    fun refresh() {
        servers = AiMcpSettings.list()
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (servers.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_mcp_empty),
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
            )
        } else {
            servers.forEachIndexed { index, server ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        thickness = 0.5.dp,
                        color = colorScheme.outline.copy(alpha = 0.5f),
                    )
                }
                McpServerRowMiuix(
                    server = server,
                    onToggle = { checked ->
                        AiMcpSettings.update(server.copy(enabled = checked))
                        refresh()
                    },
                    onOpen = { serverDetail = server },
                )
            }
        }
        TextButton(
            text = stringResource(R.string.ai_mcp_add),
            onClick = { showAddServer = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }

    if (showAddServer) {
        McpServerAddDialogMiuix(
            onDismiss = { showAddServer = false },
            onSave = { server ->
                AiMcpSettings.add(server)
                refresh()
                showAddServer = false
            },
        )
    }

    serverDetail?.let { server ->
        McpServerDetailDialogMiuix(
            server = server,
            onDismiss = { serverDetail = null },
            onUpdated = { refresh() },
        )
    }
}

/** 服务器列表行（Miuix）：整行可点进详情，右侧开关直接控制启用。 */
@Composable
private fun McpServerRowMiuix(
    server: AiMcpServer,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SearchRowShape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = server.name,
                    style = MiuixTheme.textStyles.body1,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!server.enabled) {
                    Text(
                        text = stringResource(R.string.ai_mcp_disabled_tag),
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            // 地址独占一行并允许换行，长路径不会在主机名之后被切断。
            Text(
                text = server.url,
                style = MiuixTheme.textStyles.footnote1,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = server.enabled, onCheckedChange = onToggle)
    }
}

/** 认证方式选择 + 密钥输入；「自定义」时退回整段请求头文本。 */
@Composable
private fun McpAuthFieldsMiuix(
    mode: McpAuthMode,
    onModeChange: (McpAuthMode) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    extraHeaders: String,
    onExtraHeadersChange: (String) -> Unit,
) {
    var showKey by remember { mutableStateOf(false) }
    var showExtra by remember { mutableStateOf(extraHeaders.isNotBlank()) }

    Text(
        // 有的 MCP 服务器不校验凭据，认证方式和密钥都不标必填。
        text = stringResource(R.string.ai_mcp_auth),
        style = MiuixTheme.textStyles.body1,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        McpAuthMode.entries.forEach { candidate ->
            McpAuthChip(
                selected = candidate == mode,
                text = stringResource(candidate.labelRes()),
                onClick = { onModeChange(candidate) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (mode == McpAuthMode.Custom) {
        TextField(
            value = extraHeaders,
            onValueChange = onExtraHeadersChange,
            label = stringResource(R.string.ai_mcp_headers) + stringResource(R.string.ai_field_optional),
            useLabelAsPlaceholder = true,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        TextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = stringResource(R.string.ai_mcp_key),
            useLabelAsPlaceholder = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (showKey) R.string.ai_hide_key else R.string.ai_show_key,
                        ),
                        tint = colorScheme.onSurfaceVariantActions,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (showExtra) {
            TextField(
                value = extraHeaders,
                onValueChange = onExtraHeadersChange,
                label = stringResource(R.string.ai_mcp_extra_headers),
                useLabelAsPlaceholder = true,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // 次要入口用轻量文字，别做成和「保存」同尺寸的块状按钮。
            Text(
                text = "+ " + stringResource(R.string.ai_mcp_extra_headers),
                style = MiuixTheme.textStyles.footnote1,
                color = colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showExtra = true }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
    }
    Text(
        text = stringResource(mode.keyHintRes()),
        style = MiuixTheme.textStyles.footnote1,
        color = colorScheme.onSurfaceVariantSummary,
    )
}

/** 等宽的三选一 chip，和设置页其它 chip 同一套圆角与配色。 */
@Composable
private fun McpAuthChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colorScheme.primary else colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
        )
    }
}

/** 新增 MCP 服务器（Miuix）：名称 → 地址 → 认证方式/密钥 → 保存。 */
@Composable
private fun McpServerAddDialogMiuix(
    onDismiss: () -> Unit,
    onSave: (AiMcpServer) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(McpAuthMode.Bearer) }
    var apiKey by remember { mutableStateOf("") }
    var extraHeaders by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    val urlValid = url.isBlank() || isValidMcpUrl(url)

    OverlayDialog(
        show = true,
        title = stringResource(R.string.ai_mcp_add),
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.ai_mcp_enabled),
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.ai_mcp_name) + stringResource(R.string.ai_field_required),
                    useLabelAsPlaceholder = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.ai_mcp_url_label) + stringResource(R.string.ai_field_required),
                    useLabelAsPlaceholder = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!urlValid) {
                    Text(
                        text = stringResource(R.string.ai_mcp_url_invalid),
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.error,
                    )
                }
                McpAuthFieldsMiuix(
                    mode = authMode,
                    onModeChange = { authMode = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    extraHeaders = extraHeaders,
                    onExtraHeadersChange = { extraHeaders = it },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.ai_mcp_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.ai_mcp_save),
                    onClick = {
                        onSave(
                            AiMcpServer(
                                id = "mcp_" + System.currentTimeMillis(),
                                name = name.trim(),
                                url = url.trim(),
                                enabled = enabled,
                                headers = buildMcpHeaders(authMode, apiKey, extraHeaders),
                            ),
                        )
                    },
                    enabled = name.isNotBlank() && isValidMcpUrl(url),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

/** 服务器详情（Miuix）：改名、改地址、改请求头、拉取工具列表、删除。 */
@Composable
private fun McpServerDetailDialogMiuix(
    server: AiMcpServer,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit,
) {
    var name by remember { mutableStateOf(server.name) }
    var url by remember { mutableStateOf(server.url) }
    val initialAuth = remember(server.id) { detectMcpAuth(server.headers) }
    var authMode by remember(server.id) { mutableStateOf(initialAuth.mode) }
    var apiKey by remember(server.id) { mutableStateOf(initialAuth.key) }
    var extraHeaders by remember(server.id) { mutableStateOf(initialAuth.extraHeaders) }
    var tools by remember { mutableStateOf<List<AiMcpTool>?>(null) }
    var toolsError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    OverlayDialog(
        show = true,
        title = stringResource(R.string.ai_mcp_detail_title),
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.ai_mcp_name) + stringResource(R.string.ai_field_required),
                    useLabelAsPlaceholder = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.ai_mcp_url_label) + stringResource(R.string.ai_field_required),
                    useLabelAsPlaceholder = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (url.isNotBlank() && !isValidMcpUrl(url)) {
                    Text(
                        text = stringResource(R.string.ai_mcp_url_invalid),
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.error,
                    )
                }
                McpAuthFieldsMiuix(
                    mode = authMode,
                    onModeChange = { authMode = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    extraHeaders = extraHeaders,
                    onExtraHeadersChange = { extraHeaders = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(if (loading) R.string.ai_mcp_loading else R.string.ai_mcp_refresh),
                        enabled = !loading && isValidMcpUrl(url),
                        onClick = {
                            loading = true
                            toolsError = null
                            scope.launch {
                                val target = AiMcpServer(
                                    id = server.id,
                                    name = name,
                                    url = url,
                                    enabled = server.enabled,
                                    transport = server.transport,
                                    headers = buildMcpHeaders(authMode, apiKey, extraHeaders),
                                )
                                val result = runCatching { AiMcpClient.listTools(target) }
                                loading = false
                                result.fold(
                                    onSuccess = { tools = it },
                                    onFailure = {
                                        tools = emptyList()
                                        toolsError = it.message
                                    },
                                )
                            }
                        },
                    )
                    TextButton(
                        text = stringResource(R.string.ai_mcp_remove),
                        onClick = { showRemove = true },
                    )
                }
                toolsError?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.error,
                    )
                }
                tools?.let { list ->
                    if (list.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ai_mcp_tools_empty),
                            style = MiuixTheme.textStyles.footnote1,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.ai_mcp_tools_title),
                            style = MiuixTheme.textStyles.body1,
                        )
                        list.forEach { tool ->
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(tool.name, style = MiuixTheme.textStyles.footnote1)
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        text = tool.description,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = colorScheme.onSurfaceVariantSummary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.ai_mcp_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.ai_mcp_save),
                    enabled = name.isNotBlank() && isValidMcpUrl(url),
                    onClick = {
                        AiMcpSettings.update(
                            server.copy(
                                name = name.trim(),
                                url = url.trim(),
                                headers = buildMcpHeaders(authMode, apiKey, extraHeaders),
                            ),
                        )
                        onUpdated()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )

    if (showRemove) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.ai_mcp_remove),
            onDismissRequest = { showRemove = false },
            content = {
                Text(
                    text = stringResource(R.string.ai_mcp_remove_confirm),
                    style = MiuixTheme.textStyles.body2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.ai_mcp_cancel),
                        onClick = { showRemove = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.ai_mcp_remove),
                        onClick = {
                            AiMcpSettings.remove(server.id)
                            onUpdated()
                            showRemove = false
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    }
}
