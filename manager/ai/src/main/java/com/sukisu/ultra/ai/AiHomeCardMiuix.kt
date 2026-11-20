package com.sukisu.ultra.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukisu.ultra.ai.R
import com.sukisu.ultra.ai.api.AiHomeCardBounds
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun AiHomeCardMiuix(onOpenChat: () -> Unit) {
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
        else -> stringResource(
            R.string.ai_status_ready,
            AiSettings.activeModel(config)?.name ?: config.model,
        )
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
    AiAppCard(
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
                        style = MiuixTheme.textStyles.headline1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    // 整行按钮太占地方，右侧小圆箭头表示「进入」，点击整卡即可。
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = stringResource(R.string.ai_open_chat),
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            if (!snippet.isNullOrBlank()) {
                Text(
                    text = snippet,
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun AiConfigEditorMiuix(
    draft: AiConfig,
    onDraftChange: (AiConfig) -> Unit,
    testState: AiTestState,
    onTest: () -> Unit,
    fetchingModels: Boolean = false,
    onFetchModels: (Boolean) -> Unit = {},
    // 联网搜索与 MCP 分组插在「连接」之后、「自定义指令」之前，由设置页从外部传入。
    beforeSystemPrompt: @Composable () -> Unit = {},
) {
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
    var providerPendingDelete by remember { mutableStateOf<AiProvider?>(null) }
    var editingProvider by remember { mutableStateOf<AiProvider?>(null) }
    var showProviderEditor by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<MiuixModelPickerTarget?>(null) }
    var fetchingProviderModels by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val providerModels = currentProvider?.models.orEmpty()
    // 生图模型的能力标记写在模型元数据里，这里只按标记分池，不猜名字。
    val textModels = providerModels.filterNot { it.looksLikeImageModel() }
    val imageModels = providerModels.filter { it.looksLikeImageModel() }
    val selectedModel = AiSettings.activeModel(draft)
    // 模板文案要在组合里取，回调里不能再调 stringResource。
    val presetTexts = AiSystemPromptPreset.entries.map { stringResource(it.textRes) }

    /** 选定模型：同时写提供商与全局字段，并把推理档位重置为默认，避免旧档位残留到新模型。 */
    fun selectModel(model: AiModelInfo) {
        val provider = currentProvider
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

    /** 名称优先显示人类可读的名字，手填 ID 时就显示 ID 本身。 */
    fun modelLabelOf(model: AiModelInfo?, fallbackId: String): String =
        model?.name?.ifBlank { null } ?: fallbackId

    /** 手填的模型 ID 直接落库，不要求它出现在拉取回来的列表里。 */
    fun applyModelId(id: String) {
        val provider = currentProvider
        if (provider != null && draft.providers.any { it.id == provider.id }) {
            onDraftChange(
                draft.copy(
                    providers = draft.providers.map {
                        if (it.id == provider.id) it.copy(modelId = id) else it
                    },
                    currentModelId = id,
                ),
            )
        } else {
            onDraftChange(draft.copy(model = id, currentModelId = id))
        }
    }

    /** 新增提供商时避免重名：重名就依次加序号（共享逻辑，两套外壳同一规则）。 */
    fun uniqueNewProviderName(): String =
        uniqueProviderName(draft.providers.map { it.name }, newProviderName)

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

    /** 保存编辑结果：已存在就原地更新，新增则追加并立即切成当前提供商。 */
    fun saveProvider(provider: AiProvider) {
        val exists = draft.providers.any { it.id == provider.id }
        val providers = upsertProvider(draft.providers, provider)
        val becomesCurrent = !exists || provider.id == currentProvider?.id
        if (becomesCurrent) {
            onDraftChange(
                draft.copy(
                    providers = providers,
                    currentProviderId = provider.id,
                    currentModelId = provider.modelId.ifBlank {
                        provider.models.firstOrNull()?.id.orEmpty()
                    },
                ),
            )
        } else {
            onDraftChange(draft.copy(providers = providers))
        }
    }

    /** 按 id 删除；删掉的正好是当前提供商时，回落到第一个剩下的。 */
    /** 按 id 删除；删掉的正好是当前提供商时，回落到第一个剩下的。共享逻辑，两套外壳同一规则。 */
    fun deleteProvider(id: String) {
        val remaining = removeProvider(draft.providers, id)
        if (remaining.size == draft.providers.size) return
        if (currentProvider?.id == id) {
            val next = fallbackProvider(remaining, null) ?: return
            onDraftChange(
                draft.copy(
                    providers = remaining,
                    currentProviderId = next.id,
                    currentModelId = next.models.firstOrNull()?.id ?: "",
                ),
            )
        } else {
            onDraftChange(draft.copy(providers = remaining))
        }
    }

    /** 拉取当前提供商的模型列表；force 用于每次打开下拉时刷新，但不会改掉用户已经选好的模型。 */
    fun fetchProviderModels(force: Boolean = false) {
        val provider = currentProvider ?: return
        if (provider.endpoint.isBlank()) return
        if (!force && provider.models.isNotEmpty()) return
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
                    val keepId = if (force) {
                        provider.modelId.ifBlank { draft.currentModelId }
                    } else {
                        ""
                    }
                    onDraftChange(
                        draft.copy(
                            providers = providers,
                            currentProviderId = provider.id,
                            currentModelId = keepId.ifBlank { enriched.firstOrNull()?.id ?: "" },
                        ),
                    )
                }
            } catch (_: Exception) {
            } finally {
                fetchingProviderModels = false
            }
        }
    }

    // 对齐应用设置页：总开关单独一张卡，其余按分组各一张卡，组标题用中性色。
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AiSettingsCardMiuix {
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_master_switch),
            summary = stringResource(R.string.ai_master_switch_summary),
            checked = draft.enabled,
            onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
        )
        }
        AiSettingsCardMiuix {
        Text(
            text = stringResource(R.string.ai_connection_section),
            style = MiuixTheme.textStyles.title4,
        )
        Text(
            text = stringResource(R.string.ai_connection_trust),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        // 提供商平铺在卡片里：点一行切换当前提供商，右侧直接编辑或删除，新增复用同一个编辑弹窗。
        Text(
            text = stringResource(R.string.ai_provider_section),
            style = MiuixTheme.textStyles.body1,
            color = colorScheme.onBackground,
        )
        draft.providers.forEach { provider ->
            AiProviderRowMiuix(
                provider = provider,
                isCurrent = provider.id == currentProvider?.id,
                fallbackName = defaultProviderName,
                canDelete = draft.providers.size > 1,
                onSelect = {
                    onDraftChange(
                        draft.copy(
                            currentProviderId = provider.id,
                            currentModelId = provider.modelId.ifBlank {
                                provider.models.firstOrNull()?.id.orEmpty()
                            },
                        ),
                    )
                },
                onEdit = {
                    editingProvider = provider
                    showProviderEditor = true
                },
                onDelete = { providerPendingDelete = provider },
            )
        }
        Button(
            onClick = {
                editingProvider = null
                showProviderEditor = true
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                text = stringResource(R.string.ai_provider_add),
                style = MiuixTheme.textStyles.button,
            )
        }
        // 不再单独放获取按钮：点开下拉就自动拉取，列表里还能手填模型 ID。
        AiSelectFieldMiuix(
            label = stringResource(R.string.ai_main_model),
            value = modelLabelOf(selectedModel, draft.model),
            placeholder = stringResource(R.string.ai_model_not_selected),
            onClick = { pickerTarget = MiuixModelPickerTarget.Main },
            modifier = Modifier.fillMaxWidth(),
        )

        // 测试直接跑上面下拉框选中的模型，按钮跟在模型选择下面更直观。
        Text(
            text = stringResource(
                R.string.ai_provider_test_hint,
                currentProvider?.name ?: defaultProviderName,
                modelLabelOf(selectedModel, draft.model).ifBlank {
                    stringResource(R.string.ai_model_not_selected)
                },
            ),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        // 测试连接是这一组的结束动作，用填充按钮占满一行，比左对齐的文字按钮更像按钮。
        Button(
            onClick = onTest,
            enabled = !testState.testing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(
                text = if (testState.testing) stringResource(R.string.ai_testing)
                else stringResource(R.string.ai_test_connection),
                style = MiuixTheme.textStyles.button,
            )
        }
        if (testState.testing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
        }

        Text(
            text = stringResource(R.string.ai_thinking_mode),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiThinkingMode.entries.forEach { mode ->
                MiuixFormatChip(
                    selected = draft.thinkingMode == mode,
                    text = stringResource(mode.labelRes()),
                    onClick = { onDraftChange(draft.copy(thinkingMode = mode)) },
                )
            }
        }
        Text(
            text = stringResource(R.string.ai_thinking_summary),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )

        testState.message?.let { message ->
            val container = if (testState.success) colorScheme.primaryContainer else colorScheme.errorContainer
            val content = if (testState.success) colorScheme.onPrimaryContainer else colorScheme.onErrorContainer
            AiAppCard(insideMargin = androidx.compose.foundation.layout.PaddingValues(12.dp),
                colors = CardDefaults.defaultColors(color = container)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (testState.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = content,
                    )
                    Text(
                        text = message,
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body2,
                        fontFamily = FontFamily.Monospace,
                        color = content,
                    )
                }
            }
        }

        // 标题模型只选模型：推理强度固定关闭，不再让用户选。
        AiSelectFieldMiuix(
            label = stringResource(R.string.ai_title_model),
            value = draft.titleModel,
            placeholder = stringResource(R.string.ai_title_model_follow_main),
            onClick = { pickerTarget = MiuixModelPickerTarget.Title },
            modifier = Modifier.fillMaxWidth(),
        )
        AiSelectFieldMiuix(
            label = stringResource(R.string.ai_image_model),
            value = draft.imageModel,
            placeholder = stringResource(R.string.ai_image_model_none),
            onClick = { pickerTarget = MiuixModelPickerTarget.Image },
            modifier = Modifier.fillMaxWidth(),
        )
        AiToggleRowMiuix(
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
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.error,
            )
            TextField(
                value = draft.advancedOptionsJson,
                onValueChange = { onDraftChange(draft.copy(advancedOptionsJson = it)) },
                label = stringResource(R.string.ai_advanced_options),
                useLabelAsPlaceholder = true,
                maxLines = 10,
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
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.ai_advanced_options_help),
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.ai_advanced_fill_example),
                    onClick = {
                        onDraftChange(
                            draft.copy(advancedOptionsJson = AiAdvancedOptions.exampleJson())
                        )
                    },
                )
                TextButton(
                    text = stringResource(R.string.ai_advanced_reset),
                    onClick = {
                        onDraftChange(draft.copy(advancedOptionsJson = ""))
                    },
                )
            }
        }

        }
        beforeSystemPrompt()
        AiSettingsCardMiuix {
        Text(
            text = stringResource(R.string.ai_system_prompt),
            style = MiuixTheme.textStyles.title4,
        )
        Text(
            text = stringResource(R.string.ai_system_prompt_help),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(R.string.ai_system_prompt_presets),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiSystemPromptPreset.entries.forEachIndexed { index, preset ->
                MiuixFormatChip(
                    selected = false,
                    text = stringResource(preset.labelRes),
                    onClick = {
                        onDraftChange(
                            draft.copy(
                                systemPrompt = mergePromptPreset(
                                    draft.systemPrompt,
                                    presetTexts[index],
                                ),
                            ),
                        )
                    },
                )
            }
        }
        // 清空和放大收进输入框内部，不再单独占一行；放大后编辑的仍是同一份草稿。
        var promptExpanded by remember { mutableStateOf(false) }
        val promptHint = stringResource(R.string.ai_system_prompt_hint)
        val clearLabel = stringResource(R.string.ai_system_prompt_clear)
        TextField(
            value = draft.systemPrompt,
            onValueChange = { onDraftChange(draft.copy(systemPrompt = it)) },
            label = stringResource(R.string.ai_system_prompt),
            useLabelAsPlaceholder = true,
            minLines = 2,
            maxLines = 6,
            trailingIcon = {
                // 图标贴在输入框右下角，不占正文行宽。
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End,
                ) {
                    if (draft.systemPrompt.isNotBlank()) {
                        IconButton(
                            onClick = { onDraftChange(draft.copy(systemPrompt = "")) },
                            modifier = Modifier.semantics { contentDescription = clearLabel },
                        ) {
                            Text(
                                text = "×",
                                style = MiuixTheme.textStyles.title3,
                                color = colorScheme.onSurfaceVariantActions,
                            )
                        }
                    }
                    IconButton(onClick = { promptExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.AspectRatio,
                            contentDescription = stringResource(R.string.ai_system_prompt_expand),
                            tint = colorScheme.onSurfaceVariantActions,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = promptHint,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        if (promptExpanded) {
            OverlayDialog(
                show = true,
                title = stringResource(R.string.ai_system_prompt),
                onDismissRequest = { promptExpanded = false },
                content = {
                    TextField(
                        value = draft.systemPrompt,
                        onValueChange = { onDraftChange(draft.copy(systemPrompt = it)) },
                        label = stringResource(R.string.ai_system_prompt),
                        useLabelAsPlaceholder = true,
                        minLines = 8,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    )
                    Text(
                        text = promptHint,
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = clearLabel,
                            onClick = { onDraftChange(draft.copy(systemPrompt = "")) },
                            enabled = draft.systemPrompt.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.ai_done),
                            onClick = { promptExpanded = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                },
            )
        }
        }
        AiSettingsCardMiuix {
        Text(
            text = stringResource(R.string.ai_scope_section),
            style = MiuixTheme.textStyles.title4,
        )
        Text(
            text = stringResource(R.string.ai_scope_summary),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        AiToggleRowMiuix(
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
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_allow_superuser),
            summary = stringResource(R.string.ai_allow_superuser_summary),
            checked = draft.allowSuperuserAccess,
            onCheckedChange = { onDraftChange(draft.copy(allowSuperuserAccess = it)) },
        )
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_allow_module_data),
            summary = stringResource(R.string.ai_allow_module_data_summary),
            checked = draft.allowModuleAccess,
            onCheckedChange = { onDraftChange(draft.copy(allowModuleAccess = it)) },
        )
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_allow_extension_data),
            summary = stringResource(R.string.ai_allow_extension_data_summary),
            checked = draft.allowExtensionAccess,
            onCheckedChange = { onDraftChange(draft.copy(allowExtensionAccess = it)) },
        )
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_allow_web),
            summary = stringResource(R.string.ai_allow_web_summary),
            checked = draft.allowWebAccess,
            onCheckedChange = { onDraftChange(draft.copy(allowWebAccess = it)) },
        )

        AiToggleRowMiuix(
            title = stringResource(R.string.ai_allow_theme_read),
            summary = stringResource(R.string.ai_allow_theme_read_summary),
            checked = draft.allowThemeRead,
            onCheckedChange = { onDraftChange(draft.copy(allowThemeRead = it)) },
        )

        }
        AiSettingsCardMiuix {
        Text(
            text = stringResource(R.string.ai_superuser_section),
            style = MiuixTheme.textStyles.title4,
        )
        Text(
            text = stringResource(R.string.ai_superuser_section_summary),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        AiToggleRowMiuix(
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
        AiToggleRowMiuix(
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
        AiToggleRowMiuix(
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
        AiSettingsCardMiuix {
        Text(
            text = stringResource(R.string.ai_write_section),
            style = MiuixTheme.textStyles.title4,
        )
        AiToggleRowMiuix(
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
            AiThemeFeatureChipsMiuix(
                selected = draft.themeWriteFeatures,
                onToggle = { feature, enabled ->
                    val updated = draft.themeWriteFeatures.toMutableSet()
                    if (enabled) updated += feature.id else updated -= feature.id
                    onDraftChange(draft.copy(themeWriteFeatures = updated))
                },
            )
        }
        AiToggleRowMiuix(
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
            AiExtensionFeatureChipsMiuix(
                selected = draft.extensionWriteFeatures,
                onToggle = { feature, enabled ->
                    val updated = draft.extensionWriteFeatures.toMutableSet()
                    if (enabled) updated += feature.id else updated -= feature.id
                    onDraftChange(draft.copy(extensionWriteFeatures = updated))
                },
            )
        }
        AiToggleRowMiuix(
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
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_confirm_actions),
            summary = stringResource(R.string.ai_confirm_actions_summary),
            checked = draft.requireConfirmation,
            onCheckedChange = { onDraftChange(draft.copy(requireConfirmation = it)) },
        )
        }
        AiSettingsCardMiuix {
        Text(
            text = stringResource(R.string.ai_behavior_section),
            style = MiuixTheme.textStyles.title4,
        )
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_include_context),
            summary = stringResource(R.string.ai_include_context_summary),
            checked = draft.includeContext,
            onCheckedChange = { onDraftChange(draft.copy(includeContext = it)) },
        )
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_streaming),
            summary = stringResource(R.string.ai_streaming_summary),
            checked = draft.streaming,
            onCheckedChange = { onDraftChange(draft.copy(streaming = it)) },
        )
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_tool_calling),
            summary = stringResource(R.string.ai_tool_calling_summary),
            checked = draft.toolCalling,
            onCheckedChange = { onDraftChange(draft.copy(toolCalling = it)) },
        )
        AiToggleRowMiuix(
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
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_keep_history),
            summary = stringResource(R.string.ai_keep_history_summary),
            checked = draft.keepHistory,
            onCheckedChange = { onDraftChange(draft.copy(keepHistory = it)) },
        )
        }
    }

    if (showProviderEditor) {
        AiProviderEditDialogMiuix(
            provider = editingProvider,
            suggestedName = editingProvider?.name?.ifBlank { null } ?: uniqueNewProviderName(),
            onDismiss = { showProviderEditor = false },
            onConfirm = { saved ->
                saveProvider(saved)
                showProviderEditor = false
            },
        )
    }

    providerPendingDelete?.let { provider ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.ai_provider_delete_title),
            onDismissRequest = { providerPendingDelete = null },
            content = {
                Text(
                    text = stringResource(
                        R.string.ai_provider_delete_message,
                        provider.name.ifBlank { defaultProviderName },
                    ),
                    style = MiuixTheme.textStyles.body2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.ai_close),
                        onClick = { providerPendingDelete = null },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.ai_delete_message_confirm),
                        onClick = {
                            deleteProvider(provider.id)
                            providerPendingDelete = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(color = colorScheme.error),
                    )
                }
            },
        )
    }

    pickerTarget?.let { target ->
        // 打开下拉就自动拉一次模型列表，用户不用再找获取按钮。
        LaunchedEffect(target, currentProvider?.id) {
            when (target) {
                MiuixModelPickerTarget.Image -> onFetchModels(true)
                else -> onFetchModels(false)
            }
            fetchProviderModels(force = true)
        }
        val pickerOptions = when (target) {
            MiuixModelPickerTarget.Main -> textModels
            MiuixModelPickerTarget.Image -> imageModels
            MiuixModelPickerTarget.Title -> textModels
        }
        val currentValue = when (target) {
            MiuixModelPickerTarget.Main -> selectedModel?.id ?: draft.model
            MiuixModelPickerTarget.Image -> draft.imageModel
            MiuixModelPickerTarget.Title -> draft.titleModel
        }
        AiModelPickerDialogMiuix(
            title = stringResource(
                when (target) {
                    MiuixModelPickerTarget.Main -> R.string.ai_main_model
                    MiuixModelPickerTarget.Image -> R.string.ai_image_model
                    MiuixModelPickerTarget.Title -> R.string.ai_title_model
                },
            ),
            options = pickerOptions,
            selectedId = currentValue,
            emptyText = stringResource(R.string.ai_models_empty),
            noneLabel = stringResource(
                when (target) {
                    MiuixModelPickerTarget.Main -> R.string.ai_model_not_selected
                    MiuixModelPickerTarget.Image -> R.string.ai_image_model_none
                    MiuixModelPickerTarget.Title -> R.string.ai_title_model_follow_main
                },
            ),
            showNone = target != MiuixModelPickerTarget.Main,
            fetching = fetchingModels || fetchingProviderModels,
            onDismiss = { pickerTarget = null },
            onPick = { id ->
                when (target) {
                    MiuixModelPickerTarget.Main -> {
                        val known = pickerOptions.firstOrNull { it.id == id || it.name == id }
                        if (known != null) {
                            selectModel(known)
                        } else if (id.isNotBlank()) {
                            // 手填的模型 ID 不在列表里也要能用。
                            applyModelId(id)
                        }
                    }
                    MiuixModelPickerTarget.Image -> onDraftChange(draft.copy(imageModel = id))
                    MiuixModelPickerTarget.Title -> onDraftChange(draft.copy(titleModel = id))
                }
                pickerTarget = null
            },
        )
    }

    pendingRiskKind?.let { kind ->
        AiRiskDialogMiuix(
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
    // 联网搜索与 MCP 由设置页以独立分组卡片承载（见 AiSettingsScreen）。
}

@Composable
internal fun MiuixFormatChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) colorScheme.primary else colorScheme.secondaryContainer
    val content = if (selected) colorScheme.onPrimary else colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MiuixTheme.textStyles.body2, color = content)
    }
}

@Composable
private fun AiThemeFeatureChipsMiuix(
    selected: Set<String>,
    onToggle: (AiThemeFeature, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.ai_theme_features),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiThemeFeature.entries.forEach { feature ->
                MiuixFormatChip(
                    selected = feature.id in selected,
                    text = stringResource(feature.labelRes),
                    onClick = { onToggle(feature, feature.id !in selected) },
                )
            }
        }
    }
}

@Composable
private fun AiExtensionFeatureChipsMiuix(
    selected: Set<String>,
    onToggle: (AiExtensionFeature, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.ai_extension_features),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiExtensionFeature.entries.forEach { feature ->
                MiuixFormatChip(
                    selected = feature.id in selected,
                    text = stringResource(feature.labelRes),
                    onClick = { onToggle(feature, feature.id !in selected) },
                )
            }
        }
    }
}

@Composable
private fun AiToggleRowMiuix(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Miuix Text 默认用 onSurfaceVariantSummary，标题会显得发灰；行标题要 onBackground 才和应用设置页一致。
            Text(text = title, style = MiuixTheme.textStyles.body1, color = colorScheme.onBackground)
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun AiThinkingMode.titleEffortLabelRes(): Int = when (this) {
    AiThinkingMode.AUTO -> R.string.ai_title_effort_auto
    AiThinkingMode.OFF -> R.string.ai_title_effort_off
    AiThinkingMode.LOW -> R.string.ai_title_effort_low
    AiThinkingMode.MEDIUM -> R.string.ai_title_effort_medium
    AiThinkingMode.HIGH -> R.string.ai_title_effort_high
}

/** 下拉选择行：左边标签 + 当前值，右边展开箭头，整行点击打开选项弹窗。 */
@Composable
internal fun AiSelectFieldMiuix(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasValue = value.isNotBlank()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainerHigh)
            // 描边和输入框对齐：不描边时整块和卡片底色糊在一起，看不出这是可点的控件。
            .border(1.dp, colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = if (hasValue) value else placeholder,
                style = MiuixTheme.textStyles.body1,
                color = if (hasValue) colorScheme.onBackground else colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 三个下拉入口共用一套弹窗，只是标题与候选不同。 */
private enum class MiuixModelPickerTarget { Main, Image, Title }

@Composable
private fun AiModelPickerDialogMiuix(
    title: String,
    options: List<AiModelInfo>,
    selectedId: String,
    emptyText: String,
    noneLabel: String,
    showNone: Boolean,
    fetching: Boolean = false,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        content = {
            // 列表之外再给一个手填入口：有些自建服务返回的 id 不在 /models 里。
            var manualId by remember(selectedId) { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = manualId,
                    onValueChange = { manualId = it },
                    label = stringResource(R.string.ai_model_id_manual),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.ai_model_use),
                    onClick = { onPick(manualId.trim()) },
                    enabled = manualId.isNotBlank(),
                )
            }
            if (fetching) {
                Text(
                    text = stringResource(R.string.ai_fetching_models),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showNone) {
                    AiModelOptionRow(
                        text = noneLabel,
                        selected = selectedId.isBlank(),
                        onClick = { onPick("") },
                    )
                }
                options.forEach { model ->
                    AiModelOptionRow(
                        text = model.name.ifBlank { model.id },
                        selected = model.id == selectedId,
                        onClick = { onPick(model.id) },
                    )
                }
                if (options.isEmpty()) {
                    Text(
                        text = emptyText,
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.ai_close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun AiModelOptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
            color = if (selected) colorScheme.primary else colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 设置页里平铺的提供商行：整行点击切换，右侧编辑与删除，视觉与管理弹窗一致。 */
@Composable
private fun AiProviderRowMiuix(
    provider: AiProvider,
    isCurrent: Boolean,
    fallbackName: String,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowBackground = if (isCurrent) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    val rowContent = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onBackground
    // 每行都描一圈边：非当前项与卡片底色区分开，当前项用主题色描边保持整体一致。
    val rowBorder = if (isCurrent) colorScheme.primary else colorScheme.outline.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBackground)
            .border(1.dp, rowBorder, RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSelect)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = provider.name.ifBlank { fallbackName },
                style = MiuixTheme.textStyles.body1,
                color = rowContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(provider.format.labelRes()) + " · " +
                    stringResource(R.string.ai_provider_models_count, provider.models.size),
                style = MiuixTheme.textStyles.body2,
                color = if (isCurrent) {
                    colorScheme.onPrimaryContainer
                } else {
                    colorScheme.onSurfaceVariantSummary
                },
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.ai_provider_current_mark),
                tint = rowContent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(R.string.ai_provider_edit),
            style = MiuixTheme.textStyles.body2,
            color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onEdit)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.ai_provider_delete_title),
                    tint = colorScheme.error,
                )
            }
        }
    }
}
