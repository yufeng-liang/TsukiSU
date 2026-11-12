package com.sukisu.ultra.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
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
                AiAvatarImage(size = 42.dp)
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
                                .padding(5.dp)
                                .size(28.dp),
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
    var showDeleteProviderDialog by remember { mutableStateOf(false) }
    var showProviderEditor by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProvider?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detected = if (draft.resolvedEndpoint().isNotBlank()) draft.effectiveFormat() else null

    /**
     * 计算切换后应保留的推理档位：模型明确不支持推理时清回 AUTO，
     * 避免旧模型的 HIGH/MEDIUM 残留到下一次请求里。
     */
    fun thinkingModeFor(provider: AiProvider?, model: AiModelInfo?): AiThinkingMode {
        if (model != null && model.supportedReasoningModes().isEmpty()) return AiThinkingMode.AUTO
        return provider?.thinkingMode ?: AiThinkingMode.AUTO
    }

    fun deleteCurrentProvider() {
        val existing = currentProvider ?: return
        val remaining = draft.providers.filterNot { it.id == existing.id }
        val first = remaining.firstOrNull() ?: return
        val firstModel = first.models.firstOrNull { it.id == first.modelId } ?: first.models.firstOrNull()
        onDraftChange(
            draft.copy(
                providers = remaining,
                currentProviderId = first.id,
                currentModelId = first.modelId.ifBlank { firstModel?.id.orEmpty() },
                // 删除当前供应商后同步切到剩余供应商的推理档位，避免沿用被删供应商的旧档位。
                thinkingMode = first.thinkingMode,
            ),
        )
    }

    fun saveProvider(updated: AiProvider) {
        val exists = draft.providers.any { it.id == updated.id }
        val providers = if (exists) {
            draft.providers.map { if (it.id == updated.id) updated else it }
        } else {
            draft.providers + updated
        }
        val updatedModel = updated.models.firstOrNull { it.id == updated.modelId }
            ?: updated.models.firstOrNull()
        onDraftChange(
            draft.copy(
                providers = providers,
                currentProviderId = updated.id,
                currentModelId = updated.modelId.ifBlank {
                    updatedModel?.id.orEmpty()
                },
                thinkingMode = thinkingModeFor(updated, updatedModel),
            ),
        )
    }

    val providerModels = currentProvider?.models.orEmpty()
    val textModels = providerModels.filterNot { it.looksLikeImageModel() }
    val imageModels = providerModels.filter { it.looksLikeImageModel() }
    val selectedModel = AiSettings.activeModel(draft)

    fun selectModel(model: AiModelInfo) {
        val provider = currentProvider
        // 切换模型时把推理档位重置为默认 AUTO，避免旧模型的高档位残留到新模型请求里。
        val nextThinking = AiThinkingMode.AUTO
        if (provider != null && draft.providers.any { it.id == provider.id }) {
            onDraftChange(
                draft.copy(
                    providers = draft.providers.map {
                        if (it.id == provider.id) {
                            it.copy(modelId = model.id, thinkingMode = nextThinking)
                        } else {
                            it
                        }
                    },
                    currentModelId = model.id,
                    thinkingMode = nextThinking,
                ),
            )
        } else {
            onDraftChange(
                draft.copy(model = model.id, currentModelId = model.id, thinkingMode = nextThinking),
            )
        }
    }

    // 配好地址与 Key 后自动拉一次模型目录；已有目录就不重复请求。
    LaunchedEffect(currentProvider?.id, currentProvider?.endpoint, currentProvider?.apiKey) {
        val provider = currentProvider ?: return@LaunchedEffect
        if (provider.models.isEmpty() &&
            provider.endpoint.isNotBlank() &&
            (
                provider.apiKey.isNotBlank() ||
                    provider.format == AiProviderFormat.OLLAMA ||
                    provider.looksLikeLocalEndpoint()
                )
        ) {
            onFetchModels(false)
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
            // 供应商选择用下拉框：不用横向滑动，一眼能看到全部；新增/编辑单独一行，不会被遮住。
            AiDropdownField(
                label = stringResource(R.string.ai_provider_select),
                selectedLabel = currentProvider?.name ?: defaultProviderName,
                options = draft.providers.map { it.name.ifBlank { it.id } },
                onSelect = { index ->
                    draft.providers.getOrNull(index)?.let { provider ->
                        val modelId = provider.modelId.ifBlank {
                            provider.models.firstOrNull()?.id.orEmpty()
                        }
                        val model = provider.models.firstOrNull { it.id == modelId }
                            ?: provider.models.firstOrNull()
                        onDraftChange(
                            draft.copy(
                                currentProviderId = provider.id,
                                currentModelId = modelId,
                                // 切换供应商时按新供应商的模型能力校验推理档位。
                                thinkingMode = thinkingModeFor(provider, model),
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // 添加按钮独占一行，避免被页签/内容遮住。
            OutlinedButton(
                onClick = {
                    editingProvider = null
                    showProviderEditor = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.ai_provider_add),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    editingProvider = currentProvider
                    showProviderEditor = true
                }) {
                    Text(stringResource(R.string.ai_provider_edit))
                }
                if (draft.providers.size > 1) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showDeleteProviderDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.ai_provider_delete),
                        )
                    }
                }
            }
            // 主模型：候选就是「获取模型」拉到的目录，选中后记在当前供应商上。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiDropdownField(
                    label = stringResource(R.string.ai_main_model),
                    selectedLabel = selectedModel?.let { it.name.ifBlank { it.id } }
                        ?: stringResource(R.string.ai_model_not_selected),
                    // 只列文本模型，避免把生图模型设成聊天主模型。
                    options = textModels.map { it.name.ifBlank { it.id } },
                    onSelect = { index ->
                        textModels.getOrNull(index)?.let { selectModel(it) }
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onFetchModels(false) },
                    enabled = !fetchingModels,
                ) {
                    if (fetchingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = stringResource(R.string.ai_fetch_models),
                        )
                    }
                }
            }

            // 生图模型：候选只保留可生图的模型（按模型名/ID 识别），空 = 不启用。
            // 主模型推理强度：紧贴主模型下拉框，刻度按当前模型支持的档位裁剪。
            ReasoningEffortSection(
                model = selectedModel,
                selected = draft.thinkingMode,
                onSelect = { mode ->
                    val provider = currentProvider
                    if (provider != null && draft.providers.any { it.id == provider.id }) {
                        onDraftChange(
                            draft.copy(
                                thinkingMode = mode,
                                providers = draft.providers.map {
                                    if (it.id == provider.id) it.copy(thinkingMode = mode) else it
                                },
                            ),
                        )
                    } else {
                        onDraftChange(draft.copy(thinkingMode = mode))
                    }
                },
            )

            AiDropdownField(
                label = stringResource(R.string.ai_image_model),
                selectedLabel = draft.imageModel.ifBlank {
                    stringResource(R.string.ai_image_model_none)
                },
                options = listOf(stringResource(R.string.ai_image_model_none)) +
                    imageModels.map { it.name.ifBlank { it.id } },
                onSelect = { index ->
                    val picked = imageModels.getOrNull(index - 1)
                    onDraftChange(draft.copy(imageModel = picked?.id.orEmpty()))
                },
                modifier = Modifier.fillMaxWidth(),
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
            AiDropdownField(
                label = stringResource(R.string.ai_title_model),
                selectedLabel = draft.titleModel.ifBlank {
                    stringResource(R.string.ai_title_model_follow_main)
                },
                options = listOf(stringResource(R.string.ai_title_model_follow_main)) +
                    textModels.map { it.name.ifBlank { it.id } },
                onSelect = { index ->
                    val picked = textModels.getOrNull(index - 1)
                    onDraftChange(draft.copy(titleModel = picked?.id.orEmpty()))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.ai_title_model_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    if (showProviderEditor) {
        AiProviderEditDialog(
            provider = editingProvider,
            suggestedName = nextProviderName(
                draft.providers,
                context.getString(R.string.ai_provider_default_name),
            ),
            onDismiss = { showProviderEditor = false },
            onConfirm = { provider ->
                saveProvider(provider)
                showProviderEditor = false
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
internal fun AiDropdownField(
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

/** 粗略识别「可生图」的模型：只看模型 id/展示名里的关键词，文本模型不会命中。 */
private fun AiModelInfo.looksLikeImageModel(): Boolean {
    val key = (id + " " + name).lowercase()
    return listOf(
        "dall-e", "dalle", "gpt-image", "image-preview", "flux", "stable-diffusion", "sdxl",
        "imagen", "seedream", "cogview", "kolors", "qwen-image", "wanx", "midjourney",
        "ideogram", "recraft", "seededit",
    ).any { key.contains(it) }
}

/** 判断供应商地址是否指向本机/局域网，无 Key 的 Ollama 也应允许自动拉模型目录。 */
private fun AiProvider.looksLikeLocalEndpoint(): Boolean {
    val raw = endpoint.trim()
    if (raw.isEmpty()) return false
    val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
        raw
    } else {
        "http://$raw"
    }
    val host = runCatching { java.net.URI(withScheme).host }.getOrNull()
        ?.lowercase()
        ?: return false
    if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
    val parts = host.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return false
    return parts[0] == 10 ||
        (parts[0] == 192 && parts[1] == 168) ||
        (parts[0] == 172 && parts[1] in 16..31)
}

/** 推理强度滑杆：档位随模型能力变化，每跨一档给一次轻触感。 */
@Composable
private fun ReasoningEffortSection(
    model: AiModelInfo?,
    selected: AiThinkingMode,
    onSelect: (AiThinkingMode) -> Unit,
) {
    val modes = model.supportedReasoningModes()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.ai_main_effort_title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 档位校验必须放在空档位分支之前：模型明确不支持推理时要把历史残留的档位清回 AUTO。
        LaunchedEffect(model?.id) {
            if (modes.isEmpty()) {
                if (selected != AiThinkingMode.AUTO) onSelect(AiThinkingMode.AUTO)
            } else if (selected !in modes) {
                val nearest = modes.minByOrNull { kotlin.math.abs(it.ordinal - selected.ordinal) }
                nearest?.let(onSelect)
            }
        }
        if (modes.isEmpty()) {
            Text(
                text = stringResource(R.string.ai_reasoning_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val haptic = LocalHapticFeedback.current
        val index = modes.indexOf(selected).takeIf { it >= 0 } ?: 0
        Slider(
            value = index.toFloat(),
            onValueChange = { value ->
                val target = value.roundToInt().coerceIn(0, modes.lastIndex)
                if (modes[target] != selected) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(modes[target])
                }
            },
            valueRange = 0f..modes.lastIndex.toFloat(),
            steps = (modes.size - 2).coerceAtLeast(0),
        )
        // Slider 的轨道两端各内缩一个 thumb 半径（M3 默认 10dp），刻度圆点也按这个范围均分；
        // 这里用同样的几何把每个文字的中心放到对应刻度圆点上（等宽单元格会把首尾拉偏）。
        Layout(
            content = {
                modes.forEach { mode ->
                    Text(
                        text = stringResource(mode.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mode == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(Constraints()) }
            val width = constraints.maxWidth
            val pad = 10.dp.roundToPx()
            val step = if (placeables.size > 1) {
                (width - pad * 2).toFloat() / (placeables.size - 1)
            } else {
                0f
            }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    val centerX = pad + step * index
                    placeable.place(
                        x = (centerX - placeable.width / 2f).roundToInt()
                            .coerceIn(0, (width - placeable.width).coerceAtLeast(0)),
                        y = 0,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.ai_thinking_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
