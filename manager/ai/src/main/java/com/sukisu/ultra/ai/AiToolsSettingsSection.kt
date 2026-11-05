package com.sukisu.ultra.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * AI 设置页里的「联网搜索」与「MCP 服务器」两个小组。
 *
 * 这里刻意只用 Material3 控件：设置页的 Material / Miuix 两套外壳都调用同一个 composable，
 * 避免再维护两份几乎相同的配置界面。两套主题的颜色由外层 theme 决定。
 */
@Composable
internal fun AiToolsSettingsSection(modifier: Modifier = Modifier) {
    var searchConfig by remember { mutableStateOf(AiSearchSettings.load()) }
    var servers by remember { mutableStateOf(AiMcpSettings.list()) }
    var showAddServer by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.ai_search_section),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiSearchProvider.entries.forEach { provider ->
                AssistChip(
                    onClick = {
                        searchConfig = searchConfig.copy(provider = provider)
                        AiSearchSettings.save(searchConfig)
                    },
                    label = { Text(providerLabel(provider)) },
                    enabled = true,
                )
            }
        }
        if (searchConfig.provider != AiSearchProvider.NONE) {
            OutlinedTextField(
                value = searchConfig.apiKey,
                onValueChange = {
                    searchConfig = searchConfig.copy(apiKey = it)
                    AiSearchSettings.save(searchConfig)
                },
                label = { Text(stringResource(R.string.ai_search_api_key)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (searchConfig.provider == AiSearchProvider.ANYSEARCH) {
                OutlinedTextField(
                    value = searchConfig.endpoint,
                    onValueChange = {
                        searchConfig = searchConfig.copy(endpoint = it)
                        AiSearchSettings.save(searchConfig)
                    },
                    label = { Text(stringResource(R.string.ai_search_endpoint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(
            text = stringResource(R.string.ai_mcp_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (servers.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_mcp_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(servers, key = { it.id }) { server ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = { checked ->
                                val updated = server.copy(enabled = checked)
                                AiMcpSettings.update(updated)
                                servers = AiMcpSettings.list()
                            },
                        )
                        TextButton(onClick = {
                            AiMcpSettings.remove(server.id)
                            servers = AiMcpSettings.list()
                        }) { Text(stringResource(R.string.ai_mcp_remove)) }
                    }
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
}

@Composable
private fun providerLabel(provider: AiSearchProvider): String = when (provider) {
    AiSearchProvider.NONE -> stringResource(R.string.ai_search_provider_none)
    AiSearchProvider.FIRECRAWL -> stringResource(R.string.ai_search_provider_firecrawl)
    AiSearchProvider.ANYSEARCH -> stringResource(R.string.ai_search_provider_anysearch)
}
