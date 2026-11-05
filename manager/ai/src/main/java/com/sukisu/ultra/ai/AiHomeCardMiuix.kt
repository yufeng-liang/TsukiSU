package com.sukisu.ultra.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukisu.ultra.ai.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
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

    AiAppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    AiAvatarImage(
                        size = 30.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_assistant_title),
                        style = MiuixTheme.textStyles.headline1,
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

            Button(
                onClick = onOpenChat,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(R.string.ai_open_chat))
                }
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AiToggleRowMiuix(
            title = stringResource(R.string.ai_master_switch),
            summary = stringResource(R.string.ai_master_switch_summary),
            checked = draft.enabled,
            onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
        )
        Text(
            text = stringResource(R.string.ai_connection_section),
            style = MiuixTheme.textStyles.title4,
            color = colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.ai_connection_trust),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(R.string.ai_provider_section),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = currentProvider?.name ?: defaultProviderName,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.ai_provider_switch),
                onClick = { showProviderDialog = true },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.ai_provider_add),
                onClick = { addProvider() },
            )
            if (draft.providers.size > 1) {
                TextButton(
                    text = stringResource(R.string.ai_provider_delete),
                    onClick = { showDeleteProviderDialog = true },
                )
            }
        }
        TextField(
            value = currentProvider?.name ?: "",
            onValueChange = { value -> updateCurrentProvider { it.copy(name = value) } },
            label = stringResource(R.string.ai_provider_name),
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.ai_format),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProviderFormat.entries.forEach { format ->
                MiuixFormatChip(
                    selected = (currentProvider?.format ?: draft.format) == format,
                    text = stringResource(format.labelRes()),
                    onClick = { updateCurrentProvider { it.copy(format = format) } },
                )
            }
        }
        TextField(
            value = currentProvider?.endpoint ?: draft.endpoint,
            onValueChange = { value -> updateCurrentProvider { it.copy(endpoint = value) } },
            label = stringResource(R.string.ai_endpoint),
            useLabelAsPlaceholder = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        if (detected != null) {
            Text(
                text = stringResource(
                    R.string.ai_format_detected,
                    stringResource(detected.labelRes()),
                ) + " · " + draft.resolvedEndpoint(),
                style = MiuixTheme.textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        TextField(
            value = currentProvider?.apiKey ?: draft.apiKey,
            onValueChange = { value -> updateCurrentProvider { it.copy(apiKey = value) } },
            label = stringResource(R.string.ai_api_key),
            useLabelAsPlaceholder = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (showKey) R.string.ai_hide_key else R.string.ai_show_key
                        ),
                        tint = colorScheme.onSurfaceVariantActions,
                    )
                }
            },
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
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = AiSettings.activeModel(draft)?.name ?: draft.model,
                    style = MiuixTheme.textStyles.body1,
                )
            }
            TextButton(
                text = stringResource(
                    if (fetchingModels || fetchingProviderModels) {
                        R.string.ai_fetching_models
                    } else {
                        R.string.ai_fetch_models
                    }
                ),
                onClick = {
                    onFetchModels(false)
                    fetchProviderModels()
                },
                enabled = !fetchingModels && !fetchingProviderModels,
            )
        }
        TextField(
            value = draft.imageModel,
            onValueChange = { onDraftChange(draft.copy(imageModel = it)) },
            label = stringResource(R.string.ai_image_model),
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                text = stringResource(R.string.ai_fetch_image_models),
                onClick = { onFetchModels(true) },
                enabled = !fetchingModels,
            )
        }

        Text(
            text = stringResource(R.string.ai_thinking_mode),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        TextField(
            value = draft.systemPrompt,
            onValueChange = { onDraftChange(draft.copy(systemPrompt = it)) },
            label = stringResource(R.string.ai_system_prompt),
            useLabelAsPlaceholder = true,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.ai_provider_test_hint,
                currentProvider?.name ?: defaultProviderName,
            ),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        TextButton(
            text = if (testState.testing) stringResource(R.string.ai_testing)
            else stringResource(R.string.ai_test_connection),
            onClick = onTest,
            enabled = !testState.testing,
            modifier = Modifier.fillMaxWidth(),
        )
        if (testState.testing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
        }

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

        TextField(
            value = draft.titleModel,
            onValueChange = { onDraftChange(draft.copy(titleModel = it)) },
            label = stringResource(R.string.ai_title_model),
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.ai_title_model_hint),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(R.string.ai_title_effort),
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiThinkingMode.entries.forEach { mode ->
                MiuixFormatChip(
                    selected = draft.titleEffort == mode,
                    text = stringResource(mode.titleEffortLabelRes()),
                    onClick = { onDraftChange(draft.copy(titleEffort = mode)) },
                )
            }
        }

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

        Text(
            text = stringResource(R.string.ai_scope_section),
            style = MiuixTheme.textStyles.title4,
            color = colorScheme.primary,
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

        Text(
            text = stringResource(R.string.ai_superuser_section),
            style = MiuixTheme.textStyles.title4,
            color = colorScheme.primary,
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

        Text(
            text = stringResource(R.string.ai_write_section),
            style = MiuixTheme.textStyles.title4,
            color = colorScheme.primary,
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
        Text(
            text = stringResource(R.string.ai_behavior_section),
            style = MiuixTheme.textStyles.title4,
            color = colorScheme.primary,
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

    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.ai_provider_switch_title),
                    style = MiuixTheme.textStyles.title4,
                )
            },
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
                                    style = MiuixTheme.textStyles.body1,
                                )
                                Text(
                                    text = stringResource(provider.format.labelRes()) + " · " +
                                        stringResource(
                                            R.string.ai_provider_models_count,
                                            provider.models.size,
                                        ),
                                    style = MiuixTheme.textStyles.body2,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    text = stringResource(R.string.ai_provider_current_mark),
                                    style = MiuixTheme.textStyles.body2,
                                    color = colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.ai_close),
                    onClick = { showProviderDialog = false },
                )
            },
        )
    }

    if (showDeleteProviderDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProviderDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.ai_provider_delete),
                    style = MiuixTheme.textStyles.title4,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.ai_provider_delete_message,
                        currentProvider?.name ?: defaultProviderName,
                    ),
                    style = MiuixTheme.textStyles.body2,
                )
            },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.ai_delete_message_confirm),
                    onClick = {
                        deleteCurrentProvider()
                        showDeleteProviderDialog = false
                    },
                )
            },
            dismissButton = {
                TextButton(
                    text = stringResource(R.string.ai_close),
                    onClick = { showDeleteProviderDialog = false },
                )
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
    AiToolsSettingsSection()
}

@Composable
private fun MiuixFormatChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    val background = if (selected) colorScheme.primary else colorScheme.secondaryContainer
    val content = if (selected) colorScheme.onPrimary else colorScheme.onSecondaryContainer
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text(text = title, style = MiuixTheme.textStyles.body1)
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
