package com.sukisu.ultra.ai

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukisu.ultra.ai.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    miuix: Boolean = false,
) {
    val model = rememberAiChatViewModel()
    val state by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val localNetworkPermissionDenied = stringResource(R.string.ai_local_network_permission_denied)
    val scope = rememberCoroutineScope()
    var draft by remember(state.config) { mutableStateOf(state.config) }
    var testState by remember { mutableStateOf(AiTestState()) }
    var fetchingModels by remember { mutableStateOf(false) }
    var pendingNetworkAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val networkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingNetworkAction?.invoke()
        } else {
            testState = AiTestState(
                message = localNetworkPermissionDenied,
                success = false,
            )
            Toast.makeText(context, localNetworkPermissionDenied, Toast.LENGTH_LONG).show()
        }
        pendingNetworkAction = null
    }
    val runWithLocalNetwork: (() -> Unit) -> Unit = { action ->
        if (AiLocalNetwork.needsPermission(context, draft)) {
            pendingNetworkAction = action
            networkPermissionLauncher.launch(AiLocalNetwork.PERMISSION)
        } else {
            action()
        }
    }

    fun advancedOptionsError(config: AiConfig = draft): Throwable? =
        if (config.advancedMode && config.advancedOptionsJson.isNotBlank()) {
            AiAdvancedOptions.parse(config.advancedOptionsJson).exceptionOrNull()
        } else {
            null
        }

    fun reportAdvancedOptionsError(error: Throwable) {
        Toast.makeText(
            context,
            context.getString(
                R.string.ai_advanced_options_invalid,
                error.message ?: error.javaClass.simpleName,
            ),
            Toast.LENGTH_LONG,
        ).show()
    }

    val fetchModels: (Boolean) -> Unit = fetch@ { forImage ->
        advancedOptionsError()?.let {
            reportAdvancedOptionsError(it)
            return@fetch
        }
        val resolvedDraft = draft.copy(
            endpoint = AiSettings.resolveEndpoint(draft),
            apiKey = AiSettings.resolveApiKey(draft),
            model = AiSettings.resolveModel(draft),
        )
        if (!fetchingModels) {
            if (resolvedDraft.endpoint.isBlank()) {
                Toast.makeText(context, R.string.ai_address_required, Toast.LENGTH_SHORT).show()
            } else {
                runWithLocalNetwork {
                    fetchingModels = true
                    scope.launch {
                        try {
                            val models = AiApiClient.fetchModels(resolvedDraft)
                            if (models.isEmpty()) {
                                Toast.makeText(context, R.string.ai_no_models, Toast.LENGTH_SHORT).show()
                            } else {
                                // 拉到的目录直接写回当前供应商，主模型/生图/标题三个下拉框共用。
                                val enriched = ModelCatalog.enrich(models.map { AiModelInfo(id = it) })
                                val provider = AiSettings.activeProvider(draft)
                                val format = provider?.format ?: draft.format
                                val resolvedFormat = if (format == AiProviderFormat.AUTO) {
                                    AiProviderFormat.detect(AiSettings.resolveEndpoint(draft))
                                } else {
                                    format
                                }
                                val firstId = enriched.firstOrNull()?.id.orEmpty()
                                val updated = (provider ?: AiProvider(id = "p" + System.currentTimeMillis()))
                                    .copy(
                                        format = resolvedFormat,
                                        models = enriched,
                                        modelId = provider?.modelId?.takeIf { id -> enriched.any { it.id == id } }
                                            ?: firstId,
                                    )
                                draft = draft.copy(
                                    providers = if (provider != null && draft.providers.any { it.id == provider.id }) {
                                        draft.providers.map { if (it.id == provider.id) updated else it }
                                    } else {
                                        listOf(updated)
                                    },
                                    currentProviderId = updated.id,
                                    currentModelId = updated.modelId,
                                )
                                model.updateConfig(draft)
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                error.message ?: context.getString(R.string.ai_fetch_models_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            fetchingModels = false
                        }
                    }
                }
            }
        }
    }

    val testConnection: () -> Unit = test@{
        advancedOptionsError()?.let {
            reportAdvancedOptionsError(it)
            return@test
        }
        if (!testState.testing) {
            val resolvedDraft = draft.copy(
                endpoint = AiSettings.resolveEndpoint(draft),
                apiKey = AiSettings.resolveApiKey(draft),
                model = AiSettings.resolveModel(draft),
            )
            runWithLocalNetwork {
                testState = AiTestState(testing = true)
                scope.launch { testState = runAiConnectionTest(resolvedDraft) }
            }
        }
    }

    val updateDraft: (AiConfig) -> Unit = { updated ->
        draft = updated
        // Advanced JSON is expert input; keep the last valid stored config
        // while the editor text is invalid instead of persisting a broken one.
        if (advancedOptionsError(updated) == null) {
            model.updateConfig(updated)
        }
    }

    // 聊天页快捷条过来的落点：滚到对应分组并亮一下，别把用户丢在页面顶部。
    val focusTarget = remember { AiSettingsFocus.consume() }
    var focusHighlight by remember { mutableStateOf(focusTarget) }
    val cardAnchor = remember { BringIntoViewRequester() }
    val searchAnchor = remember { BringIntoViewRequester() }
    LaunchedEffect(focusTarget) {
        if (focusTarget == null) return@LaunchedEffect
        // 目标卡要先完成一次布局，滚过去才有落点。
        delay(80)
        when (focusTarget) {
            AiSettingsTarget.Model -> cardAnchor.bringIntoView()
            AiSettingsTarget.Search -> searchAnchor.bringIntoView()
        }
        // 亮 1.4 秒就撤，别一直描着边影响阅读。
        delay(1400)
        focusHighlight = null
    }

    if (miuix) {
        AiSettingsMiuix(
            draft = draft,
            onDraftChange = updateDraft,
            testState = testState,
            fetchingModels = fetchingModels,
            onTest = testConnection,
            onFetchModels = fetchModels,
            focusCard = Modifier.bringIntoViewRequester(cardAnchor),
            focusSearch = Modifier.bringIntoViewRequester(searchAnchor),
            highlight = focusHighlight,
            onBack = {
                advancedOptionsError()?.let { reportAdvancedOptionsError(it) }
                onBack()
            },
        )
    } else {
        AiSettingsMaterial(
            draft = draft,
            onDraftChange = updateDraft,
            testState = testState,
            fetchingModels = fetchingModels,
            onTest = testConnection,
            onFetchModels = fetchModels,
            focusCard = Modifier.bringIntoViewRequester(cardAnchor),
            focusSearch = Modifier.bringIntoViewRequester(searchAnchor),
            highlight = focusHighlight,
            onBack = {
                advancedOptionsError()?.let { reportAdvancedOptionsError(it) }
                onBack()
            },
        )
    }
}

@Composable
private fun AiSettingsMaterial(
    draft: AiConfig,
    onDraftChange: (AiConfig) -> Unit,
    testState: AiTestState,
    fetchingModels: Boolean,
    onTest: () -> Unit,
    onFetchModels: (Boolean) -> Unit,
    focusCard: Modifier = Modifier,
    focusSearch: Modifier = Modifier,
    highlight: AiSettingsTarget? = null,
    onBack: () -> Unit,
) {
    AiExpressiveScaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            // 紧凑顶栏：设置页用固定顶栏即可；启用状态交给页面里的总开关，顶栏不再重复提示。
            TopAppBar(
                title = { Text(stringResource(R.string.ai_settings)) },
                navigationIcon = { AiTopBarBackButton(onClick = onBack) },
                colors = aiExpressiveTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        ) {
            if (AiLocalNetwork.isSupported() && AiLocalNetwork.isLanEndpoint(draft)) {
                Text(
                    text = stringResource(R.string.ai_local_network_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            // 字段级必填直接用星号标在标签上，不再加顶部说明行。
            AiConfigEditorMaterial(
                draft = draft,
                onDraftChange = onDraftChange,
                testState = testState,
                onTest = onTest,
                fetchingModels = fetchingModels,
                onFetchModels = onFetchModels,
                focusCard = focusCard,
                focusSearch = focusSearch,
                highlight = highlight,
            )
        }
    }
}

@Composable
private fun AiSettingsMiuix(
    draft: AiConfig,
    onDraftChange: (AiConfig) -> Unit,
    testState: AiTestState,
    fetchingModels: Boolean,
    onTest: () -> Unit,
    onFetchModels: (Boolean) -> Unit,
    focusCard: Modifier = Modifier,
    focusSearch: Modifier = Modifier,
    highlight: AiSettingsTarget? = null,
    onBack: () -> Unit,
) {
    val scroll = MiuixScrollBehavior()
    AiAppScaffold(
        containerColor = colorScheme.surface,
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.ai_settings),
                color = colorScheme.surface,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
        popupHost = { },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.surface)
                .padding(padding)
                .nestedScroll(scroll.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .imePadding()
                // 对齐应用设置页：页面左右留 12dp，卡片自己再给 16dp 内边距。
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (AiLocalNetwork.isSupported() && AiLocalNetwork.isLanEndpoint(draft)) {
                MiuixText(
                    text = stringResource(R.string.ai_local_network_hint),
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            // 编辑器自己按分组输出卡片，这里不要再套一层，否则卡片叠卡片看不出分组。
            // 字段级必填直接用星号标在标签上，不再加顶部说明行。
            AiConfigEditorMiuix(
                draft = draft,
                onDraftChange = onDraftChange,
                testState = testState,
                onTest = onTest,
                fetchingModels = fetchingModels,
                onFetchModels = onFetchModels,
                focusCard = focusCard,
                highlight = highlight,
                beforeSystemPrompt = {
                    AiSettingsSectionMiuix(
                        title = stringResource(R.string.ai_search_section),
                        subtitle = stringResource(R.string.ai_search_section_summary),
                        modifier = focusSearch,
                        highlighted = highlight == AiSettingsTarget.Search,
                    ) {
                        AiSearchSettingsSectionMiuix()
                    }
                    AiSettingsSectionMiuix(title = stringResource(R.string.ai_mcp_section)) {
                        AiMcpSettingsSectionMiuix()
                    }
                },
            )

        }
    }
}

@Composable
private fun AiModelListDialogMiuix(
    models: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.ai_select_model),
        onDismissRequest = onDismiss,
        content = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(models) { model ->
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = model,
                        onClick = { onSelect(model) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
