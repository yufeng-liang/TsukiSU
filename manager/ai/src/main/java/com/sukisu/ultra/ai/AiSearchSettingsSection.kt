package com.sukisu.ultra.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * AI 设置页里的「联网搜索」分组。
 *
 * 结构（自上而下）：
 * 1. 总开关「启用联网搜索」；
 * 2. 四家搜索服务，列表只显示名称与启用开关，左侧拖柄可拖拽排序；
 *    点行进入二级弹窗，里面放服务说明、API Key、申请入口与测试；
 * 3. 固定在最末的「内置 DuckDuckGo 兜底」开关，不参与拖拽。
 *
 * 设计取舍：
 * - 只用 Material3 控件，Material / Miuix 两套外壳共用同一个 composable；
 * - 所有改动立即写进 [AiSearchSettings]，不设「保存」按钮，避免用户改完忘了保存；
 * - 列表不堆说明与输入框，避免四家服务把设置页拉得很长；
 * - 拖拽不引入 reorderable 依赖（本模块没有），用 [detectDragGestures] + 累计位移交换相邻行。
 */
@Composable
internal fun AiSearchSettingsSection(modifier: Modifier = Modifier) {
    var config by remember { mutableStateOf(AiSearchSettings.load()) }
    // 同一时刻只允许一个测试请求在跑；null 表示空闲。
    var testing by remember { mutableStateOf<AiSearchProvider?>(null) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    val testResults = remember { mutableStateMapOf<AiSearchProvider, AiSearchTestResult>() }
    // 测试搜索用的查询词，弹窗内可改；四家服务共用同一个输入，免得住户各自维护。
    var testQuery by remember { mutableStateOf("") }
    // 二级弹窗当前对应的服务；null 表示关闭。
    var detailProvider by remember { mutableStateOf<AiSearchProvider?>(null) }
    val scope = rememberCoroutineScope()

    // 离开设置页就取消未完成的测试，避免回调写进已经销毁的组合。
    DisposableEffect(Unit) {
        onDispose {
            testJob?.cancel()
            testJob = null
        }
    }

    /** 先更新内存状态再落盘，保证开关/输入框立即响应，不依赖下一次重组读盘。 */
    fun persist(next: AiSearchConfig) {
        config = next
        AiSearchSettings.save(next)
    }

    /** 与相邻行交换位置；[direction] 为 +1 下移、-1 上移。越界时什么都不做。 */
    fun move(provider: AiSearchProvider, direction: Int) {
        val order = config.uiOrder().toMutableList()
        val index = order.indexOf(provider)
        val target = index + direction
        if (index < 0 || target !in order.indices) return
        val neighbor = order[target]
        order[target] = order[index]
        order[index] = neighbor
        persist(config.copy(order = order))
    }

    /** 发起一次真实搜索测试：消耗一次调用，结果写回对应服务，在弹窗里展示。 */
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
                // 测试是辅助功能，网络异常不应该让页面崩，转成弹窗内的失败提示。
                AiSearchTestResult.Failure(provider, error.message ?: error.javaClass.simpleName)
            }
            testResults[provider] = result
            testing = null
        }
    }

    val order = config.uiOrder()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ① 总开关：关掉后下面的服务行全部禁用（内容仍可见，方便先填 Key 再打开）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_search_enable),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.ai_search_enable_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = config.searchEnabled,
                onCheckedChange = { persist(config.copy(searchEnabled = it)) },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // ② 四家搜索服务：列表只留名称与开关，更多设置进二级弹窗。
        order.forEach { provider ->
            key(provider) {
                SearchProviderRow(
                    provider = provider,
                    checked = config.isEnabled(provider),
                    sectionEnabled = config.searchEnabled,
                    onOpen = { detailProvider = provider },
                    onToggle = { enabled ->
                        val next = if (enabled) config.enabled + provider
                        else config.enabled - provider
                        persist(config.copy(enabled = next))
                    },
                    onMove = { direction -> move(provider, direction) },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // ③ 兜底：所有服务都失败时直接抓 DuckDuckGo，不参与排序。
        DuckDuckGoFallbackRow(
            checked = config.duckDuckGoFallback,
            sectionEnabled = config.searchEnabled,
            onCheckedChange = { persist(config.copy(duckDuckGoFallback = it)) },
        )
    }

    detailProvider?.let { provider ->
        SearchProviderDetailDialog(
            provider = provider,
            config = config,
            sectionEnabled = config.searchEnabled,
            testing = testing,
            result = testResults[provider],
            testQuery = testQuery,
            onQueryChange = { testQuery = it },
            onDismiss = { detailProvider = null },
            onKeyChange = { value ->
                // key 以枚举名做索引，避免枚举改名后和旧 Key 串味。
                persist(config.copy(keys = config.keys + (provider.name to value)))
            },
            onTest = { runTest(provider, testQuery) },
        )
    }
}

/**
 * 单个搜索服务行（列表态）。
 *
 * 布局： [拖柄][服务名称][启用开关]，整行可点进二级弹窗。
 *
 * @param sectionEnabled 总开关状态；关闭时本行控件禁用（名称仍可见）。
 * @param onOpen 点行打开该服务的详细设置弹窗。
 * @param onMove 请求与相邻行交换位置（+1 下移 / -1 上移）。
 */
@Composable
private fun SearchProviderRow(
    provider: AiSearchProvider,
    checked: Boolean,
    sectionEnabled: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    // 禁用时把文字压暗；控件交给 Material3 自己的 disabled 配色。
    val contentAlpha = if (sectionEnabled) 1f else 0.5f

    // 拖拽：以整行高度为参考，累计位移超过约一行就与相邻行交换，然后扣掉阈值继续累计。
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val minSwapPx = with(LocalDensity.current) { DragSwapMin.toPx() }
    val swapThresholdPx = if (rowHeightPx > 0) {
        maxOf(rowHeightPx * 0.85f, minSwapPx)
    } else {
        minSwapPx
    }
    val latestThreshold by rememberUpdatedState(swapThresholdPx)
    val latestOnMove by rememberUpdatedState(onMove)
    var dragging by remember { mutableStateOf(false) }

    val handleModifier = if (sectionEnabled) {
        Modifier.pointerInput(provider) {
            var accumulated = 0f
            detectDragGestures(
                onDragStart = {
                    dragging = true
                    accumulated = 0f
                },
                onDragEnd = {
                    dragging = false
                    accumulated = 0f
                },
                onDragCancel = {
                    dragging = false
                    accumulated = 0f
                },
                onDrag = { change, dragAmount ->
                    // 吃掉事件，避免拖柄上的滑动被外层设置页的滚动容器抢走。
                    change.consume()
                    accumulated += dragAmount.y
                    // 用 while 处理一次事件里跨过多行的情况；guard 防止极端值下死循环。
                    var guard = 0
                    while (accumulated >= latestThreshold && guard < MaxSwapsPerEvent) {
                        latestOnMove(1)
                        accumulated -= latestThreshold
                        guard++
                    }
                    while (accumulated <= -latestThreshold && guard < MaxSwapsPerEvent) {
                        latestOnMove(-1)
                        accumulated += latestThreshold
                        guard++
                    }
                },
            )
        }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowHeightPx = it.size.height }
            .clip(SearchRowShape)
            .clickable(onClick = onOpen)
            .background(
                if (dragging) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                else Color.Transparent,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(DragHandleTouchSize)
                .then(handleModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.ai_search_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = providerTitle(provider),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            modifier = Modifier.weight(1f),
        )
        val toggleDescription = stringResource(
            R.string.ai_search_provider_enabled,
            providerTitle(provider),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = sectionEnabled,
            modifier = Modifier.semantics { contentDescription = toggleDescription },
        )
    }
}

/**
 * 单个搜索服务的二级设置弹窗：说明、API Key、申请入口、测试与最近一次测试结果。
 *
 * 列表页因此只留名称与开关，四家服务不会把设置页撑长。
 */
@Composable
private fun SearchProviderDetailDialog(
    provider: AiSearchProvider,
    config: AiSearchConfig,
    sectionEnabled: Boolean,
    testing: AiSearchProvider?,
    result: AiSearchTestResult?,
    testQuery: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onKeyChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val keyUrl = keyUrlOf(provider)
    val isTesting = testing == provider

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(providerTitle(provider)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = providerDescription(provider),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = config.keyOf(provider),
                    onValueChange = onKeyChange,
                    label = { Text(stringResource(R.string.ai_search_api_key)) },
                    singleLine = true,
                    enabled = sectionEnabled,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (
                        provider == AiSearchProvider.ANYSEARCH ||
                        provider == AiSearchProvider.FIRECRAWL
                    ) {
                        { Text(stringResource(R.string.ai_search_key_optional)) }
                    } else {
                        null
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (keyUrl != null) {
                        TextButton(
                            enabled = sectionEnabled,
                            onClick = { uriHandler.openUri(keyUrl) },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.ai_search_get_key),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.ai_search_test_query),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = testQuery,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.ai_search_test_query_hint)) },
                        singleLine = true,
                        enabled = sectionEnabled,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        // 停用的服务也允许测试：用户经常是先测通再打开；测试进行中全部禁点。
                        enabled = sectionEnabled && testing == null,
                        onClick = onTest,
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.ai_search_test_run),
                            )
                        }
                    }
                }
                when {
                    result is AiSearchTestResult.Success && result.count == 0 -> Text(
                        text = stringResource(R.string.ai_search_test_no_result),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    result is AiSearchTestResult.Success -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.ai_search_test_success,
                                result.latencyMs,
                                result.count,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        // 只展示前三条，弹窗里能确认真实返回即可。
                        result.results.take(3).forEach { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.shapes.large,
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = item.title.ifBlank { item.url },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.snippet.isNotBlank()) {
                                    Text(
                                        text = item.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    result is AiSearchTestResult.Failure -> Text(
                        text = stringResource(R.string.ai_search_test_failure, result.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.ai_search_test_cost),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_close))
            }
        },
    )
}

/**
 * 兜底行：内置 DuckDuckGo 抓取，不参与拖拽（用等宽占位保持名称左对齐）。
 */
@Composable
private fun DuckDuckGoFallbackRow(
    checked: Boolean,
    sectionEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val contentAlpha = if (sectionEnabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SearchRowShape)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 占位：与上方服务行的拖柄触碰区同宽，保证名称左边缘对齐。
        Spacer(modifier = Modifier.size(DragHandleTouchSize))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ai_search_duckduckgo_fallback),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Text(
                text = stringResource(R.string.ai_search_duckduckgo_fallback_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = sectionEnabled,
        )
    }
}

/** 设置行圆角：与 MCP 行保持一致，避免同页出现多种圆角。 */
private val SearchRowShape = RoundedCornerShape(12.dp)

/** 拖拽交换的最小位移阈值：实测行高后取更大者，手指拖动一小段不会误交换。 */
private val DragSwapMin = 48.dp

/** 拖柄的触碰区：图标 20dp，外面留出余量，手指更容易抓到。 */
private val DragHandleTouchSize = 32.dp

/** 单次拖动事件里最多连续交换几行，防止累计值异常时疯狂重排。 */
private const val MaxSwapsPerEvent = 4

/** 四家服务的默认顺序：老配置里没有 order 或只有部分服务时用来补全。 */
private val ProviderFallbackOrder = listOf(
    AiSearchProvider.FIRECRAWL,
    AiSearchProvider.ANYSEARCH,
    AiSearchProvider.TAVILY,
    AiSearchProvider.YUANBAO,
)

/**
 * 界面上展示的顺序：去掉 NONE，去重，再把缺失的服务补到末尾。
 * 这样旧版本配置（或以后新增服务）不会让某一行凭空消失。
 */
private fun AiSearchConfig.uiOrder(): List<AiSearchProvider> =
    (order.filter { it != AiSearchProvider.NONE } + ProviderFallbackOrder).distinct()

/** 各家服务的 Key 申请页；点按钮直接跳浏览器，不用用户自己找。 */
private const val FIRECRAWL_KEY_URL = "https://www.firecrawl.dev/app/api-keys"
private const val ANYSEARCH_KEY_URL = "https://www.anysearch.com/console/api-keys"
private const val TAVILY_KEY_URL = "https://app.tavily.com/home"
private const val YUANBAO_KEY_URL = "https://console.cloud.tencent.com/wsapi/index"

private fun keyUrlOf(provider: AiSearchProvider): String? = when (provider) {
    AiSearchProvider.FIRECRAWL -> FIRECRAWL_KEY_URL
    AiSearchProvider.ANYSEARCH -> ANYSEARCH_KEY_URL
    AiSearchProvider.TAVILY -> TAVILY_KEY_URL
    AiSearchProvider.YUANBAO -> YUANBAO_KEY_URL
    AiSearchProvider.NONE -> null
}

@Composable
private fun providerTitle(provider: AiSearchProvider): String = when (provider) {
    AiSearchProvider.NONE -> stringResource(R.string.ai_search_provider_none)
    AiSearchProvider.FIRECRAWL -> stringResource(R.string.ai_search_provider_firecrawl)
    AiSearchProvider.ANYSEARCH -> stringResource(R.string.ai_search_provider_anysearch)
    AiSearchProvider.TAVILY -> stringResource(R.string.ai_search_provider_tavily)
    AiSearchProvider.YUANBAO -> stringResource(R.string.ai_search_provider_yuanbao)
}

@Composable
private fun providerDescription(provider: AiSearchProvider): String = when (provider) {
    AiSearchProvider.NONE -> stringResource(R.string.ai_search_desc_none)
    AiSearchProvider.FIRECRAWL -> stringResource(R.string.ai_search_desc_firecrawl)
    AiSearchProvider.ANYSEARCH -> stringResource(R.string.ai_search_desc_anysearch)
    AiSearchProvider.TAVILY -> stringResource(R.string.ai_search_desc_tavily)
    AiSearchProvider.YUANBAO -> stringResource(R.string.ai_search_desc_yuanbao)
}
