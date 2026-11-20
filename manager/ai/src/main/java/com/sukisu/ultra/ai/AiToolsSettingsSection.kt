package com.sukisu.ultra.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 设置项圆角：行高亮与输入框外层保持一致，避免同页出现 4/12/16dp 三种圆角。 */
private val SettingsRowShape = RoundedCornerShape(12.dp)

/**
 * 与 [AiMcpClient.validUrl] 保持一致的即时校验：只允许 https；
 * 本机回环地址额外允许 http，方便本地调试。`httpx://` 等非法 scheme 一律拒绝。
 */
internal fun isValidMcpUrl(raw: String): Boolean {
    val text = raw.trim()
    if (text.isEmpty()) return false
    val uri = runCatching { java.net.URI(text) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    val host = uri.host?.lowercase()?.trim('[', ']') ?: return false
    val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
    return scheme == "https" || (scheme == "http" && loopback)
}

/**
 * AI 设置页里的「MCP 服务器」分组（联网搜索分组已拆分到 [AiSearchSettingsSection]）。
 *
 * 这里刻意只用 Material3 控件：设置页的 Material / Miuix 两套外壳都调用同一个 composable，
 * 避免再维护两份几乎相同的配置界面。两套主题的颜色由外层 theme 决定。
 */
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
        OutlinedButton(
            onClick = { showAddServer = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.ai_mcp_add),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }

    if (showAddServer) {
        McpServerAddDialog(
            onDismiss = { showAddServer = false },
            onSave = { server ->
                AiMcpSettings.add(server)
                servers = AiMcpSettings.list()
                showAddServer = false
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
 * 凭据输入：三档认证方式 + 只填密钥，自定义请求头收在可展开入口里。
 *
 * 两种认证方式只是请求头写法的差别（Bearer / X-API-Key），解析与拼装都交给
 * [detectMcpAuth] / [buildMcpHeaders]，M3 与 Miuix 共用同一套规则。
 */
@Composable
private fun McpAuthFieldsMaterial(
    mode: McpAuthMode,
    onModeChange: (McpAuthMode) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    extraHeaders: String,
    onExtraHeadersChange: (String) -> Unit,
) {
    var keyVisible by remember { mutableStateOf(false) }
    var headersExpanded by remember { mutableStateOf(mode == McpAuthMode.Custom) }
    Text(
        text = stringResource(R.string.ai_mcp_auth),
        style = MaterialTheme.typography.labelLarge,
    )
    // 三档换行摆放：窄屏上「自定义」会被挤成两行，交给 FlowRow 断行更稳。
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        McpAuthMode.entries.forEach { entry ->
            FilterChip(
                selected = mode == entry,
                onClick = { onModeChange(entry) },
                label = { Text(stringResource(entry.labelRes()), maxLines = 1) },
            )
        }
    }
    if (mode == McpAuthMode.Custom) {
        OutlinedTextField(
            value = extraHeaders,
            onValueChange = onExtraHeadersChange,
            label = { Text(stringResource(R.string.ai_mcp_headers)) },
            placeholder = { Text(stringResource(R.string.ai_mcp_custom_hint)) },
            minLines = 2,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(R.string.ai_mcp_key)) },
        singleLine = true,
        // 密钥默认打码，右侧眼睛临时明文核对，避免截图或旁人看到。
        visualTransformation = if (keyVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { keyVisible = !keyVisible }) {
                Icon(
                    imageVector = if (keyVisible) {
                        Icons.Rounded.VisibilityOff
                    } else {
                        Icons.Rounded.Visibility
                    },
                    contentDescription = stringResource(R.string.ai_mcp_key),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        supportingText = { Text(stringResource(mode.keyHintRes())) },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    )
    // 其他请求头默认收起：多数服务只认一个密钥，需要时再展开。
    if (!headersExpanded) {
        TextButton(onClick = { headersExpanded = true }) {
            Text(
                text = "+ " + stringResource(R.string.ai_mcp_extra_headers),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        OutlinedTextField(
            value = extraHeaders,
            onValueChange = onExtraHeadersChange,
            label = { Text(stringResource(R.string.ai_mcp_extra_headers)) },
            placeholder = { Text(stringResource(R.string.ai_mcp_custom_hint)) },
            minLines = 2,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 新增 MCP 服务器弹窗（对齐 RikkaHub 的字段顺序，改成轻量弹窗）：
 * 名称 → 地址 → 认证方式与密钥（其他请求头可选） → 保存/取消。
 */
@Composable
private fun McpServerAddDialog(
    onDismiss: () -> Unit,
    onSave: (AiMcpServer) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var transport by remember { mutableStateOf("STREAMABLE_HTTP") }
    var authMode by remember { mutableStateOf(McpAuthMode.Bearer) }
    var apiKey by remember { mutableStateOf("") }
    var extraHeaders by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_mcp_add)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ai_mcp_enabled),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ai_mcp_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.ai_mcp_transport),
                    style = MaterialTheme.typography.labelLarge,
                )
                // SSE（legacy）客户端尚未实现，先只提供 Streamable HTTP，避免选了不生效的选项。
                val transportOptions = listOf("STREAMABLE_HTTP" to "Streamable HTTP")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    transportOptions.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = transport == value,
                            onClick = { transport = value },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = transportOptions.size,
                            ),
                        ) {
                            Text(label)
                        }
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.ai_mcp_url)) },
                    placeholder = { Text("https://example.com/mcp") },
                    singleLine = true,
                    isError = url.isNotBlank() && !isValidMcpUrl(url),
                    supportingText = {
                        if (url.isNotBlank() && !isValidMcpUrl(url)) {
                            Text(stringResource(R.string.ai_mcp_url_invalid))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                McpAuthFieldsMaterial(
                    mode = authMode,
                    onModeChange = { authMode = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    extraHeaders = extraHeaders,
                    onExtraHeadersChange = { extraHeaders = it },
                )            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && isValidMcpUrl(url),
                onClick = {
                    onSave(
                        AiMcpServer(
                            id = "mcp_" + System.currentTimeMillis(),
                            name = name.trim(),
                            url = url.trim(),
                            enabled = enabled,
                            transport = transport,
                            headers = buildMcpHeaders(authMode, apiKey, extraHeaders),
                        )
                    )
                },
            ) { Text(stringResource(R.string.ai_mcp_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_mcp_cancel)) }
        },
    )
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
            .clip(SettingsRowShape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 8.dp),
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
    // 已有配置反推成「方式 + 密钥 + 其他请求头」，用户只看到密钥框。
    val initialAuth = remember(server.id) { detectMcpAuth(server.headers) }
    var authMode by remember(server.id) { mutableStateOf(initialAuth.mode) }
    var apiKey by remember(server.id) { mutableStateOf(initialAuth.key) }
    var extraHeaders by remember(server.id) { mutableStateOf(initialAuth.extraHeaders) }
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
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.ai_mcp_url)) },
                    singleLine = true,
                    isError = url.isNotBlank() && !isValidMcpUrl(url),
                    supportingText = {
                        if (url.isNotBlank() && !isValidMcpUrl(url)) {
                            Text(stringResource(R.string.ai_mcp_url_invalid))
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                )
                McpAuthFieldsMaterial(
                    mode = authMode,
                    onModeChange = { authMode = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    extraHeaders = extraHeaders,
                    onExtraHeadersChange = { extraHeaders = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
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
