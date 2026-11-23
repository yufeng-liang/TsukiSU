package com.sukisu.ultra.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableColumn

/**
 * AI 设置页里的「联网搜索」分组。
 *
 * 结构（自上而下）：
 * 1. 总开关「启用联网搜索」；
 * 2. 四家搜索服务，列表只显示名称与启用开关，左侧拖柄可拖拽排序；
 *    点行进入二级弹窗，里面放服务说明、API Key、申请入口与测试。
 *
 * 设计取舍：
 * - 只用 Material3 控件，Material / Miuix 两套外壳共用同一个 composable；
 * - 所有改动立即写进 [AiSearchSettings]，不设「保存」按钮，避免用户改完忘了保存；
 * - 列表不堆说明与输入框，避免四家服务把设置页拉得很长；
 * - 拖拽排序交给 reorderable 库（模块已有该依赖），拖动时行跟手、其余行自动让位；
 * - 总开关关闭只是暂停：整组压暗但开关仍可点，点开任一服务即视为明确意图、顺手恢复总闸。
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

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ① 总开关：关掉只是暂停，服务选择与顺序都保留，下面仍可操作。
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
            AiExpressiveSwitch(
                checked = config.searchEnabled,
                onCheckedChange = { persist(config.copy(searchEnabled = it)) },
            )
        }

        if (!config.searchEnabled) {
            Text(
                text = stringResource(R.string.ai_search_paused),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // ② 四家搜索服务：拖拽排序用 reorderable 库，行内只留名称与开关。
        ReorderableColumn(
            list = order,
            // 总闸关闭时整组压暗：明确「现在不参与搜索」，但开关仍可点。
            modifier = Modifier.alpha(if (config.searchEnabled) 1f else 0.55f),
            onSettle = { from, to -> persist(reorderSearchProviders(config, from, to)) },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { _, provider, isDragging ->
            ReorderableItem(Modifier.fillMaxWidth()) {
                SearchProviderRow(
                    provider = provider,
                    checked = config.isEnabled(provider),
                    keyMissing = provider.requiresKey && config.keyOf(provider).isBlank(),
                    dragging = isDragging,
                    // 排序只改配置，与总闸无关，永远可拖。
                    handleModifier = Modifier.draggableHandle(),
                    onOpen = { detailProvider = provider },
                    onToggle = { enabled ->
                        persist(toggleSearchProvider(config, provider, enabled))
                    },
                )
            }
        }

        // ③ 空链与缺 Key 都要明说：否则用户以为搜索在工作，其实一次请求都不会发。
        if (config.enabled.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_search_none_enabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val missingKeys = config.enabled.count { it.requiresKey && config.keyOf(it).isBlank() }
            if (missingKeys > 0) {
                Text(
                    text = stringResource(R.string.ai_search_missing_key_summary, missingKeys),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    detailProvider?.let { provider ->
        SearchProviderDetailDialog(
            provider = provider,
            config = config,
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
 * 布局：[拖柄][服务名称（缺 Key 时带提示）][启用开关]，整行可点进二级弹窗。
 */
@Composable
private fun SearchProviderRow(
    provider: AiSearchProvider,
    checked: Boolean,
    keyMissing: Boolean,
    dragging: Boolean,
    handleModifier: Modifier,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SearchRowShape)
            .clickable(onClick = onOpen)
            .background(
                if (dragging) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = providerTitle(provider),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (keyMissing) {
                Text(
                    text = stringResource(R.string.ai_search_missing_key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val toggleDescription = stringResource(
            R.string.ai_search_provider_enabled,
            providerTitle(provider),
        )
        AiExpressiveSwitch(
            checked = checked,
            onCheckedChange = onToggle,
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
    // Key 输入只改本地草稿，停止输入 500ms 后才落盘，避免每敲一个字符都写 SharedPreferences。
    var draftKey by remember(provider) { mutableStateOf(config.keyOf(provider)) }
    LaunchedEffect(draftKey) {
        if (draftKey != config.keyOf(provider)) {
            delay(500)
            onKeyChange(draftKey)
        }
    }

    AlertDialog(
        onDismissRequest = {
            // 关闭弹窗时把尚未到 debounce 的输入补一次落盘，避免丢字。
            if (draftKey != config.keyOf(provider)) onKeyChange(draftKey)
            onDismiss()
        },
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
                    value = draftKey,
                    onValueChange = { draftKey = it },
                    label = {
                        Text(
                            text = stringResource(R.string.ai_search_api_key) +
                                if (provider.requiresKey) {
                                    stringResource(R.string.ai_field_required)
                                } else {
                                    stringResource(R.string.ai_field_optional)
                                },
                        )
                    },
                    singleLine = true,
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
                            shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        // 停用的服务也允许测试：用户经常是先测通再打开；只按「是否有测试在跑」禁用。
                        enabled = testing == null,
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

/** 设置行圆角：与 MCP 行保持一致，避免同页出现多种圆角。 */
internal val SearchRowShape = RoundedCornerShape(12.dp)

/** 拖柄的触碰区：图标 20dp，外面留出余量，手指更容易抓到。 */
internal val DragHandleTouchSize = 32.dp

/** 四家服务的默认顺序：老配置里没有 order 或只有部分服务时用来补全。 */
private val ProviderFallbackOrder = listOf(
    AiSearchProvider.FIRECRAWL,
    AiSearchProvider.ANYSEARCH,
    AiSearchProvider.TAVILY,
    AiSearchProvider.YUANBAO,
)

/**
 * 界面上展示的顺序：去重，再把缺失的服务补到末尾。
 * 这样旧版本配置（或以后新增服务）不会让某一行凭空消失。
 */
internal fun AiSearchConfig.uiOrder(): List<AiSearchProvider> =
    (order + ProviderFallbackOrder).distinct()

/** 各家服务的 Key 申请页；点按钮直接跳浏览器，不用用户自己找。 */
private const val FIRECRAWL_KEY_URL = "https://www.firecrawl.dev/app/api-keys"
private const val ANYSEARCH_KEY_URL = "https://www.anysearch.com/console/api-keys"
private const val TAVILY_KEY_URL = "https://app.tavily.com/home"
private const val YUANBAO_KEY_URL = "https://console.cloud.tencent.com/wsapi/index"

internal fun keyUrlOf(provider: AiSearchProvider): String? = when (provider) {
    AiSearchProvider.FIRECRAWL -> FIRECRAWL_KEY_URL
    AiSearchProvider.ANYSEARCH -> ANYSEARCH_KEY_URL
    AiSearchProvider.TAVILY -> TAVILY_KEY_URL
    AiSearchProvider.YUANBAO -> YUANBAO_KEY_URL
}

@Composable
internal fun providerTitle(provider: AiSearchProvider): String = when (provider) {
    AiSearchProvider.FIRECRAWL -> stringResource(R.string.ai_search_provider_firecrawl)
    AiSearchProvider.ANYSEARCH -> stringResource(R.string.ai_search_provider_anysearch)
    AiSearchProvider.TAVILY -> stringResource(R.string.ai_search_provider_tavily)
    AiSearchProvider.YUANBAO -> stringResource(R.string.ai_search_provider_yuanbao)
}

@Composable
internal fun providerDescription(provider: AiSearchProvider): String = when (provider) {
    AiSearchProvider.FIRECRAWL -> stringResource(R.string.ai_search_desc_firecrawl)
    AiSearchProvider.ANYSEARCH -> stringResource(R.string.ai_search_desc_anysearch)
    AiSearchProvider.TAVILY -> stringResource(R.string.ai_search_desc_tavily)
    AiSearchProvider.YUANBAO -> stringResource(R.string.ai_search_desc_yuanbao)
}
