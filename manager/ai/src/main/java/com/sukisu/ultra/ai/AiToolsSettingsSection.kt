package com.sukisu.ultra.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * AI 设置页里的「联网搜索」与「MCP 服务器」两个独立分组：
 *
 * 这里刻意只用 Material3 控件：设置页的 Material / Miuix 两套外壳都调用同一个 composable，
 * 避免再维护两份几乎相同的配置界面。两套主题的颜色由外层 theme 决定。
 */
@Composable
internal fun AiSearchSettingsSection(modifier: Modifier = Modifier) {
    var searchConfig by remember { mutableStateOf(AiSearchSettings.load()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AiSearchProvider.entries.forEach { provider ->
            SearchProviderRow(
                provider = provider,
                selected = searchConfig.provider == provider,
                config = searchConfig,
                onSelect = {
                    searchConfig = searchConfig.copy(provider = provider)
                    AiSearchSettings.save(searchConfig)
                },
                onApiKey = { value ->
                    searchConfig = searchConfig.copy(apiKey = value)
                    AiSearchSettings.save(searchConfig)
                },
            )
        }
    }
}

@Composable
internal fun AiMcpSettingsSection(modifier: Modifier = Modifier) {
    var servers by remember { mutableStateOf(AiMcpSettings.list()) }
    var showAddServer by remember { mutableStateOf(false) }
    var serverDetail by remember { mutableStateOf<AiMcpServer?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (servers.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_mcp_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { server ->
                    McpServerRow(
                        server = server,
                        onToggle = { checked ->
                            AiMcpSettings.update(server.copy(enabled = checked))
                            servers = AiMcpSettings.list()
                        },
                        onRemove = {
                            AiMcpSettings.remove(server.id)
                            servers = AiMcpSettings.list()
                        },
                        onOpen = { serverDetail = server },
                    )
                }
            }
        }
        TextButton(onClick = { showAddServer = true }) {
            Text(stringResource(R.string.ai_mcp_add))
        }
    }

    if (showAddServer) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddServer = false },
            title = { Text(stringResource(R.string.ai_mcp_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.ai_mcp_name)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.ai_mcp_url)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && url.isNotBlank(),
                    onClick = {
                        AiMcpSettings.add(
                            AiMcpServer(
                                id = "mcp_" + System.currentTimeMillis(),
                                name = name.trim(),
                                url = url.trim(),
                            ),
                        )
                        servers = AiMcpSettings.list()
                        showAddServer = false
                    },
                ) { Text(stringResource(R.string.ai_mcp_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddServer = false }) {
                    Text(stringResource(R.string.ai_mcp_cancel))
                }
            },
        )
    }

    serverDetail?.let { server ->
        McpServerDetailDialog(
            server = server,
            onDismiss = { serverDetail = null },
            onUpdated = { servers = AiMcpSettings.list() },
        )
    }
}

/**
 * 服务器列表行：整行可点进详情，右侧开关直接控制启用。
 */
@Composable
private fun McpServerRow(
    server: AiMcpServer,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(server.name, style = MaterialTheme.typography.titleSmall)
                if (!server.enabled) {
                    Text(
                        text = stringResource(R.string.ai_mcp_disabled_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Text(
                server.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = server.enabled, onCheckedChange = onToggle)
    }
}

/**
 * 服务器详情：可编辑名称 / 地址 / 自定义请求头，刷新工具列表，删除服务器。
 * 工具列表用 AiMcpClient.listTools 实时拉取，展示名称 + 描述。
 */
@Composable
private fun McpServerDetailDialog(
    server: AiMcpServer,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit,
) {
    var name by remember { mutableStateOf(server.name) }
    var url by remember { mutableStateOf(server.url) }
    var headersText by remember {
        mutableStateOf(
            server.headers.entries.joinToString("\n") { "${'$'}{it.key}: ${'$'}{it.value}" },
        )
    }
    var tools by remember { mutableStateOf<List<AiMcpTool>?>(null) }
    var toolsError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_mcp_detail_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ai_mcp_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.ai_mcp_url)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = headersText,
                    onValueChange = { headersText = it },
                    label = { Text(stringResource(R.string.ai_mcp_headers)) },
                    placeholder = { Text(stringResource(R.string.ai_mcp_headers_hint)) },
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !loading,
                        onClick = {
                            loading = true
                            toolsError = null
                            scope.launch {
                                val target = AiMcpServer(
                                    id = server.id,
                                    name = name,
                                    url = url,
                                    enabled = server.enabled,
                                    headers = parseHeaders(headersText),
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
                    ) { Text(stringResource(if (loading) R.string.ai_mcp_loading else R.string.ai_mcp_refresh)) }
                    TextButton(
                        onClick = { showRemove = true },
                    ) { Text(stringResource(R.string.ai_mcp_remove)) }
                }
                toolsError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                tools?.let { list ->
                    if (list.isEmpty()) {
                        Text(
                            stringResource(R.string.ai_mcp_tools_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(R.string.ai_mcp_tools_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        list.forEach { tool ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        tool.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    AiMcpSettings.update(
                        server.copy(
                            name = name.trim(),
                            url = url.trim(),
                            headers = parseHeaders(headersText),
                        ),
                    )
                    onUpdated()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.ai_mcp_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_mcp_cancel)) }
        },
    )

    if (showRemove) {
        AlertDialog(
            onDismissRequest = { showRemove = false },
            title = { Text(stringResource(R.string.ai_mcp_remove)) },
            text = { Text(stringResource(R.string.ai_mcp_remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    AiMcpSettings.remove(server.id)
                    onUpdated()
                    showRemove = false
                    onDismiss()
                }) { Text(stringResource(R.string.ai_mcp_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemove = false }) { Text(stringResource(R.string.ai_mcp_cancel)) }
            },
        )
    }
}

/** "Key: Value" 每行一条，解析成 Map；解析不出冒号的行忽略。 */
internal fun parseHeaders(raw: String): Map<String, String> = raw
    .lines()
    .mapNotNull { line ->
        val idx = line.indexOf(':')
        if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
    }
    .filter { it.first.isNotBlank() }
    .toMap()

/**
 * 搜索服务商行：整行可点选（单选）。
 * 选中态展开 API Key 输入框与申请入口；AnySearch 端点是内置的、Key 可留空匿名试用，
 * 所以不再让用户手填端点。
 */
@Composable
private fun SearchProviderRow(
    provider: AiSearchProvider,
    selected: Boolean,
    config: AiSearchConfig,
    onSelect: () -> Unit,
    onApiKey: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val keyUrl = when (provider) {
        AiSearchProvider.FIRECRAWL -> FIRECRAWL_KEY_URL
        AiSearchProvider.ANYSEARCH -> ANYSEARCH_KEY_URL
        AiSearchProvider.NONE -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSelect)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = providerIcon(provider),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = providerTitle(provider),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = providerDescription(provider),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
        if (selected && provider != AiSearchProvider.NONE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = config.apiKey,
                    onValueChange = onApiKey,
                    label = { Text(stringResource(R.string.ai_search_api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (provider == AiSearchProvider.ANYSEARCH) {
                        { Text(stringResource(R.string.ai_search_key_optional)) }
                    } else {
                        null
                    },
                )
                if (keyUrl != null) {
                    TextButton(
                        onClick = { uriHandler.openUri(keyUrl) },
                        modifier = Modifier.align(Alignment.End),
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
        }
    }
}

/** 两家服务的 Key 申请页；点按钮直接跳浏览器，不用用户自己找。 */
private const val FIRECRAWL_KEY_URL = "https://www.firecrawl.dev/app/api-keys"
private const val ANYSEARCH_KEY_URL = "https://www.anysearch.com/console/api-keys"

private fun providerIcon(provider: AiSearchProvider) = when (provider) {
    AiSearchProvider.NONE -> Icons.Rounded.Block
    AiSearchProvider.FIRECRAWL -> Icons.Rounded.Language
    AiSearchProvider.ANYSEARCH -> Icons.Rounded.Search
}

@Composable
private fun providerTitle(provider: AiSearchProvider): String = when (provider) {
    AiSearchProvider.NONE -> stringResource(R.string.ai_search_provider_none)
    AiSearchProvider.FIRECRAWL -> stringResource(R.string.ai_search_provider_firecrawl)
    AiSearchProvider.ANYSEARCH -> stringResource(R.string.ai_search_provider_anysearch)
}

@Composable
private fun providerDescription(provider: AiSearchProvider): String = when (provider) {
    AiSearchProvider.NONE -> stringResource(R.string.ai_search_desc_none)
    AiSearchProvider.FIRECRAWL -> stringResource(R.string.ai_search_desc_firecrawl)
    AiSearchProvider.ANYSEARCH -> stringResource(R.string.ai_search_desc_anysearch)
}
