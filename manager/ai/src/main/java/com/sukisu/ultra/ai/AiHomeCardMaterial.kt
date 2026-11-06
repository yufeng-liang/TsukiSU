package com.sukisu.ultra.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukisu.ultra.ai.R
import com.sukisu.ultra.ai.api.AiHomeCardBounds
import kotlinx.coroutines.launch

/**
 * Compact home entry point. The card only shows live status and a snippet;
 * every setting lives in the chat screen.
 */
@Composable
fun AiHomeCardMaterial(onOpenChat: () -> Unit) {
    val model = rememberAiChatViewModel()
    val state by model.uiState.collectAsStateWithLifecycle()
    val config = state.config

    val status = when {
        !config.enabled -> stringResource(R.string.ai_status_disabled)
        !config.isConfigured -> stringResource(R.string.ai_status_not_configured)
        state.pendingApproval != null -> stringResource(R.string.ai_status_waiting_approval)
        state.pendingQuestion != null -> stringResource(R.string.ai_status_waiting_choice)
        state.isSending -> stringResource(R.string.ai_status_working)
        state.messages.lastOrNull()?.role == AiMessageRole.ERROR ->
            stringResource(R.string.ai_status_error)
        else -> stringResource(R.string.ai_status_ready, config.model)
    }
    val snippet = when {
        state.pendingApproval != null -> state.pendingApproval?.request?.let {
            compactSnippet("${it.title} · ${it.detail}")
        }

        state.pendingQuestion != null -> state.pendingQuestion?.let {
            compactSnippet(it.question)
        }

        state.isSending -> {
            val runningTool = state.messages.lastOrNull {
                it.role == AiMessageRole.TOOL && it.streaming && it.content.isNotBlank()
            }
            val streamingAssistant = state.messages.lastOrNull {
                it.role == AiMessageRole.ASSISTANT && it.streaming && it.content.isNotBlank()
            }
            when {
                runningTool != null -> compactSnippet(runningTool.content)
                streamingAssistant != null -> compactSnippet(streamingAssistant.content)
                else -> stringResource(R.string.ai_thinking)
            }
        }

        else -> state.messages.lastOrNull {
            it.content.isNotBlank() && it.role != AiMessageRole.ERROR
        }?.let { compactSnippet(it.content) }
    }

    val rootView = LocalView.current
    AiTonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                // 记录卡片矩形，供 Container Transform 过渡从这张卡片展开页面。
                AiHomeCardBounds.update(
                    rect = coordinates.boundsInRoot(),
                    rootWidth = rootView.width.toFloat(),
                    rootHeight = rootView.height.toFloat(),
                )
            },
        // 与首页「超级用户」等统计卡同底色（surfaceBright），避免 AI 卡比其它卡深一档。
        containerColor = MaterialTheme.colorScheme.surfaceBright,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenChat)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    AiAvatarImage(
                        modifier = Modifier.padding(7.dp),
                        size = 30.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_assistant_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    // 整行按钮太占地方，右侧小圆箭头表示「进入」，点击整卡即可。
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = stringResource(R.string.ai_open_chat),
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp),
                        )
                    }
                }
            }

            if (!snippet.isNullOrBlank()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun AiConfigEditorMaterial(
    draft: AiConfig,
    onDraftChange: (AiConfig) -> Unit,
    testState: AiTestState,
    onTest: () -> Unit,
    fetchingModels: Boolean = false,
    onFetchModels: (Boolean) -> Unit = {},
) {
    var showKey by remember { mutableStateOf(false) }
    var pendingRiskKind by remember { mutableStateOf<AiRiskKind?>(null) }
    var pendingRiskPhrase by remember { mutableStateOf<String?>(null) }
    var pendingRiskAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun requestRisk(kind: AiRiskKind, phrase: String? = null, action: () -> Unit) {
        pendingRiskKind = kind
        pendingRiskPhrase = phrase
        pendingRiskAction = action
    }
    val currentProvider = AiSettings.activeProvider(draft)
    val defaultProviderName = stringResource(R.string.ai_provider_default)
    val newProviderName = stringResource(R.string.ai_provider_new_name)
    var showProviderDialog by remember { mutableStateOf(false) }
    var showDeleteProviderDialog by remember { mutableStateOf(false) }
    var fetchingProviderModels by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val detected = if (draft.resolvedEndpoint().isNotBlank()) draft.effectiveFormat() else null

    fun updateCurrentProvider(transform: (AiProvider) -> AiProvider) {
        val existing = currentProvider
        if (existing != null && draft.providers.any { it.id == existing.id }) {
            onDraftChange(
                draft.copy(
                    providers = draft.providers.map {
                        if (it.id == existing.id) transform(it) else it
                    },
                ),
            )
            return
        }
        val seed = existing ?: AiProvider(
            id = "p" + System.currentTimeMillis(),
            name = newProviderName,
            format = draft.format,
            endpoint = draft.endpoint,
            apiKey = draft.apiKey,
        )
        val updated = transform(seed)
        onDraftChange(
            draft.copy(
                providers = listOf(updated),
                currentProviderId = updated.id,
                currentModelId = updated.models.firstOrNull()?.id ?: "",
            ),
        )
    }

    fun addProvider() {
        val provider = AiProvider(
            id = "p" + System.currentTimeMillis(),
            name = newProviderName,
            format = draft.format,
            endpoint = draft.endpoint,
            apiKey = draft.apiKey,
        )
        onDraftChange(
            draft.copy(
                providers = draft.providers + provider,
                currentProviderId = provider.id,
                currentModelId = "",
            ),
        )
    }

    fun deleteCurrentProvider() {
        val existing = currentProvider ?: return
        val remaining = draft.providers.filterNot { it.id == existing.id }
        val first = remaining.firstOrNull() ?: return
        onDraftChange(
            draft.copy(
                providers = remaining,
                currentProviderId = first.id,
                currentModelId = first.models.firstOrNull()?.id ?: "",
            ),
        )
    }

    fun fetchProviderModels() {
        val provider = currentProvider ?: return
        if (provider.models.isNotEmpty() || provider.endpoint.isBlank()) return
        scope.launch {
            fetchingProviderModels = true
            try {
                val models = ModelCatalog.listModels(provider)
                if (models.isNotEmpty()) {
                    val enriched = ModelCatalog.enrich(models)
                    val updated = provider.copy(models = enriched)
                    val providers = if (draft.providers.any { it.id == provider.id }) {
                        draft.providers.map { if (it.id == provider.id) updated else it }
                    } else {
                        listOf(updated)
                    }
                    onDraftChange(
                        draft.copy(
                            providers = providers,
                            currentProviderId = provider.id,
                            currentModelId = enriched.firstOrNull()?.id ?: "",
                        ),
                    )
                }
            } catch (_: Exception) {
            } finally {
                fetchingProviderModels = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ① 启用助手：卡片内容自带标题行，不再叠一层分组标题
        SettingsSection {
            AiToggleRow(
                title = stringResource(R.string.ai_master_switch),
                summary = stringResource(R.string.ai_master_switch_summary),
                checked = draft.enabled,
                onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
            )
        }


        // ② AI 提供商：头部一行放切换/新增/删除，下面依次是名称、格式、地址、密钥、模型、图片模型、推理强度、提示、会话标题模型与测试
        SettingsSection(
            title = stringResource(R.string.ai_provider_group),
            subtitle = stringResource(R.string.ai_connection_trust),
            contentSpacing = 8.dp,
        ) {
            // 头部一行放下「当前提供商 + 切换 / 新增 / 删除」，格式与模型做副标题，省掉原来三行独立操作区。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (currentProvider?.name ?: defaultProviderName).take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = currentProvider?.name ?: defaultProviderName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val modelName = AiSettings.activeModel(draft)?.name ?: draft.model
                    Text(
                        text = listOfNotNull(
                            stringResource((currentProvider?.format ?: draft.format).labelRes()),
                            modelName.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 只有一个供应商时「切换」没有意义，隐藏以省出空间、避免弹出空列表。
                if (draft.providers.size > 1) {
                    TextButton(onClick = { showProviderDialog = true }) {
                        Text(stringResource(R.string.ai_provider_switch))
                    }
                }
                IconButton(onClick = { addProvider() }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.ai_provider_add),
                    )
                }
                if (draft.providers.size > 1) {
                    IconButton(onClick = { showDeleteProviderDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.ai_provider_delete),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = currentProvider?.name ?: "",
                    onValueChange = { value -> updateCurrentProvider { it.copy(name = value) } },
                    label = { Text(stringResource(R.string.ai_provider_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                )
                AiDropdownField(
                    label = stringResource(R.string.ai_format),
                    selectedLabel = stringResource((currentProvider?.format ?: draft.format).labelRes()),
                    options = AiProviderFormat.entries.map { stringResource(it.labelRes()) },
                    onSelect = { index ->
                        updateCurrentProvider { it.copy(format = AiProviderFormat.entries[index]) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = currentProvider?.endpoint ?: draft.endpoint,
                onValueChange = { value -> updateCurrentProvider { it.copy(endpoint = value) } },
                label = { Text(stringResource(R.string.ai_endpoint)) },
                placeholder = { Text(stringResource(R.string.ai_endpoint_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                // 识别结果挪进 supportingText，省掉单独一行说明。
                supportingText = if (detected != null) {
                    {
                        Text(
                            text = stringResource(
                                R.string.ai_format_detected,
                                stringResource(detected.labelRes()),
                            ) + " · " + draft.resolvedEndpoint(),
                        )
                    }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = currentProvider?.apiKey ?: draft.apiKey,
                onValueChange = { value -> updateCurrentProvider { it.copy(apiKey = value) } },
                label = { Text(stringResource(R.string.ai_api_key)) },
                placeholder = { Text(stringResource(R.string.ai_api_key_hint)) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (showKey) R.string.ai_hide_key else R.string.ai_show_key
                            ),
                        )
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_model),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = AiSettings.activeModel(draft)?.name ?: draft.model,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(
                    onClick = {
                        onFetchModels(false)
                        fetchProviderModels()
                    },
                    enabled = !fetchingModels && !fetchingProviderModels,
                ) {
                    if (fetchingModels || fetchingProviderModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 6.dp).size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp).size(16.dp),
                        )
                    }
                    Text(
                        stringResource(
                            if (fetchingModels || fetchingProviderModels) {
                                R.string.ai_fetching_models
                            } else {
                                R.string.ai_fetch_models
                            }
                        )
                    )
                }
            }
            // 图片模型与「获取图片模型」并排，少占一行。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.imageModel,
                    onValueChange = { onDraftChange(draft.copy(imageModel = it)) },
                    label = { Text(stringResource(R.string.ai_image_model)) },
                    placeholder = { Text(stringResource(R.string.ai_image_model_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onFetchModels(true) },
                    enabled = !fetchingModels,
                ) {
                    Text(stringResource(R.string.ai_fetch_image_models))
                }
            }

            AiDropdownField(
                label = stringResource(R.string.ai_thinking_mode),
                selectedLabel = stringResource(draft.thinkingMode.labelRes()),
                options = AiThinkingMode.entries.map { stringResource(it.labelRes()) },
                onSelect = { index ->
                    onDraftChange(draft.copy(thinkingMode = AiThinkingMode.entries[index]))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.ai_thinking_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = draft.systemPrompt,
                onValueChange = { onDraftChange(draft.copy(systemPrompt = it)) },
                label = { Text(stringResource(R.string.ai_system_prompt)) },
                placeholder = { Text(stringResource(R.string.ai_system_prompt_hint)) },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )

            // 会话标题模型也放在提供商分组里：属于模型配置，单独占一张卡太浪费空间。
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 2.dp),
            )
            OutlinedTextField(
                value = draft.titleModel,
                onValueChange = { onDraftChange(draft.copy(titleModel = it)) },
                label = { Text(stringResource(R.string.ai_title_model)) },
                placeholder = { Text(stringResource(R.string.ai_title_model_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            AiDropdownField(
                label = stringResource(R.string.ai_title_effort),
                selectedLabel = stringResource(draft.titleEffort.titleEffortLabelRes()),
                options = AiThinkingMode.entries.map { stringResource(it.titleEffortLabelRes()) },
                onSelect = { index ->
                    onDraftChange(draft.copy(titleEffort = AiThinkingMode.entries[index]))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // 测试连接收成紧凑按钮：说明放左边，按钮靠右。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.ai_provider_test_hint,
                        currentProvider?.name ?: defaultProviderName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onTest,
                    enabled = !testState.testing,
                ) {
                    if (testState.testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 6.dp).size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.ai_testing))
                    } else {
                        Text(stringResource(R.string.ai_test_connection))
                    }
                }
            }

            testState.message?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (testState.success) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (testState.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (testState.success) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                        Text(
                            text = message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (testState.success) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                    }
                }
            }
        }


        // ③ 联网搜索：服务商单选与 Key
        SettingsSection(
            title = stringResource(R.string.ai_search_section),
            subtitle = stringResource(R.string.ai_search_section_summary),
        ) {
            AiSearchSettingsSection()
        }

        // ④ MCP 服务器：服务器列表与新增
        SettingsSection(title = stringResource(R.string.ai_mcp_section)) {
            AiMcpSettingsSection()
        }

        // ⑤ 访问范围
        SettingsSection(
            title = stringResource(R.string.ai_scope_section),
            subtitle = stringResource(R.string.ai_scope_summary),
        ) {
            AiToggleRow(
                title = stringResource(R.string.ai_allow_root),
                summary = stringResource(R.string.ai_allow_root_summary),
                checked = draft.allowRootAccess,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onDraftChange(draft.copy(allowRootAccess = false))
                    } else {
                        requestRisk(AiRiskKind.ROOT) {
                            onDraftChange(draft.copy(allowRootAccess = true))
                        }
                    }
                },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_allow_superuser),
                summary = stringResource(R.string.ai_allow_superuser_summary),
                checked = draft.allowSuperuserAccess,
                onCheckedChange = { onDraftChange(draft.copy(allowSuperuserAccess = it)) },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_allow_module_data),
                summary = stringResource(R.string.ai_allow_module_data_summary),
                checked = draft.allowModuleAccess,
                onCheckedChange = { onDraftChange(draft.copy(allowModuleAccess = it)) },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_allow_extension_data),
                summary = stringResource(R.string.ai_allow_extension_data_summary),
                checked = draft.allowExtensionAccess,
                onCheckedChange = { onDraftChange(draft.copy(allowExtensionAccess = it)) },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_allow_web),
                summary = stringResource(R.string.ai_allow_web_summary),
                checked = draft.allowWebAccess,
                onCheckedChange = { onDraftChange(draft.copy(allowWebAccess = it)) },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_allow_theme_read),
                summary = stringResource(R.string.ai_allow_theme_read_summary),
                checked = draft.allowThemeRead,
                onCheckedChange = { onDraftChange(draft.copy(allowThemeRead = it)) },
            )
        }


        // ⑥ 超级用户控制
        SettingsSection(
            title = stringResource(R.string.ai_superuser_section),
            subtitle = stringResource(R.string.ai_superuser_section_summary),
        ) {
            AiToggleRow(
                title = stringResource(R.string.ai_allow_superuser_root),
                summary = stringResource(R.string.ai_allow_superuser_root_summary),
                checked = draft.allowSuperuserRootControl,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onDraftChange(draft.copy(allowSuperuserRootControl = false))
                    } else {
                        requestRisk(AiRiskKind.PERMANENT_ROOT) {
                            onDraftChange(
                                draft.copy(
                                    allowSuperuserRootControl = true,
                                    allowSuperuserAccess = true,
                                    allowRootAccess = true,
                                )
                            )
                        }
                    }
                },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_allow_superuser_temp_root),
                summary = stringResource(R.string.ai_allow_superuser_temp_root_summary),
                checked = draft.allowSuperuserTemporaryRoot,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onDraftChange(draft.copy(allowSuperuserTemporaryRoot = false))
                    } else {
                        requestRisk(AiRiskKind.TEMPORARY_ROOT) {
                            onDraftChange(
                                draft.copy(
                                    allowSuperuserTemporaryRoot = true,
                                    allowSuperuserAccess = true,
                                    allowRootAccess = true,
                                )
                            )
                        }
                    }
                },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_allow_superuser_switch),
                summary = stringResource(R.string.ai_allow_superuser_switch_summary),
                checked = draft.allowSuperuserSwitch,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onDraftChange(draft.copy(allowSuperuserSwitch = false))
                    } else {
                        requestRisk(AiRiskKind.GLOBAL_SU) {
                            onDraftChange(
                                draft.copy(
                                    allowSuperuserSwitch = true,
                                    allowRootAccess = true,
                                )
                            )
                        }
                    }
                },
            )
        }


        // ⑦ 写入权限
        SettingsSection(
            title = stringResource(R.string.ai_write_section),
        ) {
            AiToggleRow(
                title = stringResource(R.string.ai_allow_theme),
                summary = stringResource(R.string.ai_allow_theme_summary),
                checked = draft.allowThemeWrite,
                onCheckedChange = { enabled ->
                    onDraftChange(
                        draft.copy(
                            allowThemeWrite = enabled,
                            allowThemeRead = enabled || draft.allowThemeRead,
                            themeWriteFeatures = if (enabled) AiThemeFeature.ALL_IDS else draft.themeWriteFeatures,
                        )
                    )
                },
            )
            if (draft.allowThemeWrite) {
                AiThemeFeatureChips(
                    selected = draft.themeWriteFeatures,
                    onToggle = { feature, enabled ->
                        val updated = draft.themeWriteFeatures.toMutableSet()
                        if (enabled) updated += feature.id else updated -= feature.id
                        onDraftChange(draft.copy(themeWriteFeatures = updated))
                    },
                )
            }
            AiToggleRow(
                title = stringResource(R.string.ai_allow_extensions),
                summary = stringResource(R.string.ai_allow_extensions_summary),
                checked = draft.allowExtensionWrite,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onDraftChange(draft.copy(allowExtensionWrite = false))
                    } else {
                        requestRisk(AiRiskKind.EXTENSION_WRITE) {
                            onDraftChange(
                                draft.copy(
                                    allowExtensionWrite = true,
                                    allowExtensionAccess = true,
                                    extensionWriteFeatures = AiExtensionFeature.ALL_IDS,
                                )
                            )
                        }
                    }
                },
            )
            if (draft.allowExtensionWrite) {
                AiExtensionFeatureChips(
                    selected = draft.extensionWriteFeatures,
                    onToggle = { feature, enabled ->
                        val updated = draft.extensionWriteFeatures.toMutableSet()
                        if (enabled) updated += feature.id else updated -= feature.id
                        onDraftChange(draft.copy(extensionWriteFeatures = updated))
                    },
                )
            }
            AiToggleRow(
                title = stringResource(R.string.ai_allow_modules),
                summary = stringResource(R.string.ai_allow_modules_summary),
                checked = draft.allowModuleInstall,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onDraftChange(draft.copy(allowModuleInstall = false))
                    } else {
                        requestRisk(AiRiskKind.MODULE_INSTALL) {
                            onDraftChange(
                                draft.copy(
                                    allowModuleInstall = true,
                                    allowModuleAccess = true,
                                )
                            )
                        }
                    }
                },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_confirm_actions),
                summary = stringResource(R.string.ai_confirm_actions_summary),
                checked = draft.requireConfirmation,
                onCheckedChange = { onDraftChange(draft.copy(requireConfirmation = it)) },
            )
        }


        // ⑧ 行为
        SettingsSection(
            title = stringResource(R.string.ai_behavior_section),
        ) {
            AiToggleRow(
                title = stringResource(R.string.ai_include_context),
                summary = stringResource(R.string.ai_include_context_summary),
                checked = draft.includeContext,
                onCheckedChange = { onDraftChange(draft.copy(includeContext = it)) },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_streaming),
                summary = stringResource(R.string.ai_streaming_summary),
                checked = draft.streaming,
                onCheckedChange = { onDraftChange(draft.copy(streaming = it)) },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_tool_calling),
                summary = stringResource(R.string.ai_tool_calling_summary),
                checked = draft.toolCalling,
                onCheckedChange = { onDraftChange(draft.copy(toolCalling = it)) },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_allow_code_execution),
                summary = stringResource(R.string.ai_allow_code_execution_summary),
                checked = draft.allowCodeExecution,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onDraftChange(draft.copy(allowCodeExecution = false))
                    } else {
                        requestRisk(AiRiskKind.CODE_EXECUTION) {
                            onDraftChange(draft.copy(allowCodeExecution = true))
                        }
                    }
                },
            )
            AiToggleRow(
                title = stringResource(R.string.ai_keep_history),
                summary = stringResource(R.string.ai_keep_history_summary),
                checked = draft.keepHistory,
                onCheckedChange = { onDraftChange(draft.copy(keepHistory = it)) },
            )
        }


        // ⑨ 高级 API 模式：开关自带标题与摘要，展开内容跟随开关
        SettingsSection {
            AiToggleRow(
                title = stringResource(R.string.ai_advanced_mode),
                summary = stringResource(R.string.ai_advanced_mode_summary),
                checked = draft.advancedMode,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        onDraftChange(draft.copy(advancedMode = false))
                    } else {
                        requestRisk(AiRiskKind.ADVANCED_API, "ADVANCED") {
                            onDraftChange(draft.copy(advancedMode = true))
                        }
                    }
                },
            )
            if (draft.advancedMode) {
                Text(
                    text = stringResource(R.string.ai_advanced_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = draft.advancedOptionsJson,
                    onValueChange = { onDraftChange(draft.copy(advancedOptionsJson = it)) },
                    label = { Text(stringResource(R.string.ai_advanced_options)) },
                    placeholder = { Text(stringResource(R.string.ai_advanced_options_hint)) },
                    minLines = 4,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                val validationError = remember(draft.advancedOptionsJson) {
                    if (draft.advancedOptionsJson.isBlank()) {
                        null
                    } else {
                        AiAdvancedOptions.parse(draft.advancedOptionsJson).exceptionOrNull()
                    }
                }
                if (validationError != null) {
                    Text(
                        text = stringResource(
                            R.string.ai_advanced_options_invalid,
                            validationError.message ?: validationError.javaClass.simpleName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.ai_advanced_options_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            onDraftChange(
                                draft.copy(advancedOptionsJson = AiAdvancedOptions.exampleJson())
                            )
                        },
                    ) {
                        Text(stringResource(R.string.ai_advanced_fill_example))
                    }
                    TextButton(
                        onClick = {
                            onDraftChange(draft.copy(advancedOptionsJson = ""))
                        },
                    ) {
                        Text(stringResource(R.string.ai_advanced_reset))
                    }
                }
            }
        }



    }

    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = { Text(stringResource(R.string.ai_provider_switch_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    draft.providers.forEach { provider ->
                        val isCurrent = provider.id == currentProvider?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDraftChange(
                                        draft.copy(
                                            currentProviderId = provider.id,
                                            currentModelId = provider.models.firstOrNull()?.id ?: "",
                                        ),
                                    )
                                    showProviderDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.name.ifBlank { defaultProviderName },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(provider.format.labelRes()) + " · " +
                                        stringResource(
                                            R.string.ai_provider_models_count,
                                            provider.models.size,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    text = stringResource(R.string.ai_provider_current_mark),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) {
                    Text(stringResource(R.string.ai_close))
                }
            },
        )
    }

    if (showDeleteProviderDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProviderDialog = false },
            title = { Text(stringResource(R.string.ai_provider_delete)) },
            text = {
                Text(
                    stringResource(
                        R.string.ai_provider_delete_message,
                        currentProvider?.name ?: defaultProviderName,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCurrentProvider()
                        showDeleteProviderDialog = false
                    },
                ) {
                    Text(stringResource(R.string.ai_delete_message_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProviderDialog = false }) {
                    Text(stringResource(R.string.ai_close))
                }
            },
        )
    }

    pendingRiskKind?.let { kind ->
        AiRiskDialogMaterial(
            kind = kind,
            phrase = pendingRiskPhrase,
            onConfirm = {
                pendingRiskAction?.invoke()
                pendingRiskKind = null
                pendingRiskPhrase = null
                pendingRiskAction = null
            },
            onDismiss = {
                pendingRiskKind = null
                pendingRiskPhrase = null
                pendingRiskAction = null
            },
        )
    }
}

/** 分组卡片圆角。 */
private val SettingsCardShape = RoundedCornerShape(20.dp)

/**
 * 设置页分组卡片：统一的圆角、内边距与标题样式，替代原先靠分隔线堆叠的长列表。
 *
 * @param title 分组标题；为 null 时不画标题区（用于内容自带标题行的分组，避免标题重复）
 * @param subtitle 可选的补充说明，使用弱化色
 * @param content 分组内容，内部统一按 12dp 垂直间距排布
 */
@Composable
private fun SettingsSection(
    title: String? = null,
    subtitle: String? = null,
    contentSpacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            if (title != null || subtitle != null) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (title != null) {
                        // 分组标题：左侧短色条 + titleMedium，和卡片内部的 14sp 正文拉开层级。
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
    }
}

/**
 * 只读下拉框：用于设置项里的枚举选择（接口格式、思考强度），比一排 chip 更省空间。
 */
@Composable
private fun AiDropdownField(
    label: String,
    selectedLabel: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun AiThemeFeatureChips(
    selected: Set<String>,
    onToggle: (AiThemeFeature, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.ai_theme_features),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiThemeFeature.entries.forEach { feature ->
                FilterChip(
                    selected = feature.id in selected,
                    onClick = { onToggle(feature, feature.id !in selected) },
                    label = { Text(stringResource(feature.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun AiExtensionFeatureChips(
    selected: Set<String>,
    onToggle: (AiExtensionFeature, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.ai_extension_features),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiExtensionFeature.entries.forEach { feature ->
                FilterChip(
                    selected = feature.id in selected,
                    onClick = { onToggle(feature, feature.id !in selected) },
                    label = { Text(stringResource(feature.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun AiToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AiExpressiveSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun AiThinkingMode.titleEffortLabelRes(): Int = when (this) {
    AiThinkingMode.AUTO -> R.string.ai_title_effort_auto
    AiThinkingMode.OFF -> R.string.ai_title_effort_off
    AiThinkingMode.LOW -> R.string.ai_title_effort_low
    AiThinkingMode.MEDIUM -> R.string.ai_title_effort_medium
    AiThinkingMode.HIGH -> R.string.ai_title_effort_high
}
