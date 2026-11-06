package com.sukisu.ultra.ai

import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.items

import com.sukisu.ultra.ai.api.AiNavigationTarget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.tween
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sukisu.ultra.ai.R
import java.io.File

@Composable
fun AiChatMaterial(
    state: AiChatUiState,
    actions: AiChatActions,
) {
    val listState = rememberLazyListState()
    val contextLabel = remember(state.messages, state.config) {
        val used = state.messages.sumOf { it.content.length } / 3
        val window = AiSettings.activeModel(state.config)?.contextWindow ?: 0
        if (window > 0 && used > 0) "上下文约 ${used / 1000}k / ${window / 1000}k" else ""
    }

    var showSessions by remember { mutableStateOf(false) }
    BackHandler(enabled = state.pendingQuestion != null) {
        actions.onAnswerQuestion(null)
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.pendingQuestion) {
        if (state.pendingQuestion != null) keyboardController?.hide()
    }
    val clearDialog = rememberAiConfirmDialog(AiUiMode.Material, onConfirm = actions.onClearHistory)
    val clearTitle = stringResource(R.string.ai_clear_history_title)
    val clearContent = stringResource(R.string.ai_clear_history_message)
    val clearConfirm = stringResource(R.string.ai_clear_history_confirm)
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val deleteTitle = stringResource(R.string.ai_delete_message_title)
    val deleteContent = stringResource(R.string.ai_delete_message_message)
    val deleteConfirm = stringResource(R.string.ai_delete_message_confirm)
    val deleteDialog = rememberAiConfirmDialog(
        AiUiMode.Material,
        onConfirm = {
            pendingDeleteId?.let(actions.onDeleteMessage)
            pendingDeleteId = null
        },
        onDismiss = { pendingDeleteId = null },
    )

    LaunchedEffect(
        state.messages.size,
        state.messages.lastOrNull()?.content?.length,
        state.suggestions,
        state.suggestionsLoading,
        state.isSending,
    ) {
        if (state.messages.isNotEmpty()) {
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.scrollToItem(last)
        }
    }

    // 不在列表底部时显示「回到底部」按钮。
    val jumpScope = rememberCoroutineScope()
    val showJumpDown = remember(listState) { derivedStateOf {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        lastVisible < info.totalItemsCount - 1
    } }.value

    val fallbackTitle = remember(state.messages) {
        state.messages.firstOrNull { it.role == AiMessageRole.USER }
            ?.content?.trim()?.replace('\n', ' ')?.take(18).orEmpty()
    }
    // AI 生成的会话标题优先；为空时退回「首条用户消息前 18 字」。
    val sessionTitle = state.sessionTitle.trim().ifBlank { fallbackTitle }
    AiExpressiveScaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (sessionTitle.isEmpty()) stringResource(R.string.ai_chat_title) else sessionTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 模型名作为副标题；未选模型时不占位。
                        if (state.config.model.isNotBlank()) {
                            Text(
                                text = state.config.model,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { AiTopBarBackButton(onClick = actions.onBack) },
                actions = {
                    // 会话列表
                    IconButton(onClick = { showSessions = true }) {
                        Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.ai_sessions))
                    }
                    // 设置
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.ai_settings))
                    }
                    // 清空聊天记录
                    IconButton(onClick = {
                        clearDialog.showConfirm(
                            title = clearTitle,
                            content = clearContent,
                            confirm = clearConfirm,
                        )
                    }) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.ai_clear_history_action),
                        )
                    }
                },
                colors = aiExpressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // 只有欢迎页（没有消息）时整屏刚好放得下，不需要滚动。
                userScrollEnabled = state.messages.isNotEmpty(),
            ) {
                if (!state.config.isConfigured || !state.config.enabled) {
                    item(key = "setup") {
                        AiSetupCardMaterial(
                            enabled = state.config.enabled,
                            onOpenSettings = actions.onOpenSettings,
                        )
                    }
                }
                if (state.messages.isEmpty()) {
                    // 问候 + 能力卡整块在剩余可视区里居中，并再上移 10%（视觉重心更舒服）。
                    item(key = "welcome") {
                        BoxWithConstraints(modifier = Modifier.fillParentMaxHeight().fillMaxWidth()) {
                            val shiftUp = maxHeight * 0.10f
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                AiWelcomeMaterial(
                                    suggestions = state.suggestions,
                                    loading = state.suggestionsLoading,
                                    onSuggestion = { text ->
                                        actions.onInputChange(text)
                                        actions.onSend()
                                    },
                                    onRefresh = actions.onRefreshSuggestions,
                                    modifier = Modifier.offset(y = -shiftUp),
                                )
                            }
                        }
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (!message.streaming) {
                                        pendingDeleteId = message.id
                                        deleteDialog.showConfirm(
                                            title = deleteTitle,
                                            content = deleteContent,
                                            confirm = deleteConfirm,
                                        )
                                    }
                                },
                            )
                    ) {
                        AiMessageBubbleMaterial(
                            message = message,
                            config = state.config,
                            rootGrantedForRequest = state.rootGrantedForRequest,
                            onNavigate = actions.onNavigate,
                            onCapabilityChange = actions.onSetCapability,
                            onGrantRoot = actions.onGrantRoot,
                            onRunCode = actions.onRunCode,
                            isLastAssistant = message.id == state.messages
                                .lastOrNull { it.role == AiMessageRole.ASSISTANT }?.id,
                            onRegenerate = actions.onRegenerate,
                onEditResend = actions.onEditResend,
                        )
                    }
                }
                if (state.messages.isNotEmpty() && !state.isSending) {
                    item(key = "followups") {
                        AiFollowUpSuggestionsMaterial(
                            suggestions = state.suggestions,
                            loading = state.suggestionsLoading,
                            onSuggestion = { text ->
                                actions.onInputChange(text)
                                actions.onSend()
                            },
                            onRefresh = actions.onRefreshSuggestions,
                        )
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(4.dp)) }
            }

            state.pendingQuestion?.let { question ->
                AiQuestionPanelMaterial(
                    question = question,
                    onAnswer = actions.onAnswerQuestion,
                )
            }

                if (showJumpDown) {
                SmallFloatingActionButton(
                    onClick = {
                        jumpScope.launch {
                            val last = listState.layoutInfo.totalItemsCount - 1
                            if (last >= 0) listState.animateScrollToItem(last)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.ai_jump_bottom),
                    )
                }
            }
        }

        AiComposerMaterial(
                contextLabel = contextLabel,
                value = state.input,
                isSending = state.isSending,
                error = state.error,
                onValueChange = actions.onInputChange,
                onSend = actions.onSend,
                onStop = actions.onStop,
pendingImages = state.pendingImages,
onAttachImage = actions.onAttachImage,
onRemoveImage = actions.onRemoveImage,
                onDismissError = actions.onDismissError,
            )
        }
    }

    if (showSessions) {
        ModalBottomSheet(
            onDismissRequest = { showSessions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ai_sessions),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        actions.onNewSession()
                        showSessions = false
                    }) { Text(stringResource(R.string.ai_new_chat)) }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(state.sessions, key = { it.id }) { session ->
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = session.title.ifBlank { stringResource(R.string.ai_new_chat) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            selected = session.id == state.activeSessionId,
                            onClick = {
                                actions.onSwitchSession(session.id)
                                showSessions = false
                            },
                        )
                    }
                }
            }
        }
    }

    state.pendingApproval?.let { pending ->
        AiApprovalDialogMaterial(
            pending = pending,
            onAllow = actions.onApprove,
            onAllowAlways = actions.onApproveAlways,
            onDeny = actions.onDeny,
        )
    }
}

@Composable
private fun AiFollowUpSuggestionsMaterial(
    suggestions: List<String>,
    loading: Boolean,
    onSuggestion: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ai_follow_up_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.ai_suggestions_refresh),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onSuggestion(suggestion) },
                    label = {
                        Text(
                            text = suggestion,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AiSetupCardMaterial(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        // 单行紧凑卡片：提示文字占满剩余宽度，尾部跟「打开设置」，整体约 56dp 高。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    if (enabled) R.string.ai_setup_required else R.string.ai_setup_disabled
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                // tertiaryContainer 上的 TextButton 默认用 primary，会变成浅色低对比；跟随容器内容色。
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.ai_open_settings),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun AiMessageBubbleMaterial(
    message: AiChatMessage,
    config: AiConfig,
    rootGrantedForRequest: Boolean,
    onNavigate: (AiNavigationTarget) -> Unit,
    onCapabilityChange: (AiCapability, Boolean) -> Unit,
    onGrantRoot: () -> Unit,
    onRunCode: (String) -> Unit,
    isLastAssistant: Boolean = false,
    onRegenerate: () -> Unit = {},
    onEditResend: (String, String) -> Unit = { _, _ -> },
) {
    val copy = rememberAiCopyText()
    var pendingRiskCapability by remember { mutableStateOf<AiCapability?>(null) }
    var pendingRootGrant by remember { mutableStateOf(false) }
    when (message.role) {
        AiMessageRole.USER -> {
            var editing by remember(message.id) { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { editing = true }) {
                    Text(
                        text = stringResource(R.string.ai_edit_message),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (editing) {
                var draft by remember(message.id, editing) { mutableStateOf(message.content) }
                AlertDialog(
                    onDismissRequest = { editing = false },
                    title = { Text(stringResource(R.string.ai_edit_message)) },
                    text = {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (draft.isNotBlank()) {
                                editing = false
                                onEditResend(message.id, draft)
                            }
                        }) { Text(stringResource(R.string.ai_edit_send)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { editing = false }) { Text(stringResource(R.string.ai_edit_cancel)) }
                    },
                )
            }
        }

        AiMessageRole.ASSISTANT -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    AiAvatarImage(
                        modifier = Modifier.padding(6.dp),
                        size = 22.dp,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AiMarkdownMaterial(
                            content = message.content,
                            allowCodeExecution = config.allowCodeExecution,
                            onRunCode = onRunCode,
                        )
                        if (message.streaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (message.content.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                if (isLastAssistant) {
                                    TextButton(onClick = onRegenerate) {
                                        Text(
                                            text = stringResource(R.string.ai_regenerate),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { copy(message.content) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.ai_copy),
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AiMessageRole.TOOL -> {
            var toolExpanded by remember(message.id) { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = message.toolName ?: stringResource(R.string.ai_tool),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        if (message.streaming) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (message.content.isNotBlank()) {
                            TextButton(onClick = { toolExpanded = !toolExpanded }) {
                                Text(
                                    text = stringResource(
                                        if (toolExpanded) R.string.ai_tool_collapse else R.string.ai_tool_expand
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            IconButton(
                                onClick = { copy(message.content) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.ai_copy),
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                    if (toolExpanded) {
                        SelectionContainer {
                            Text(
                                text = linkifyAiText(message.content, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    val imagePath = message.imagePath
                    if (imagePath != null) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    val navigation = message.navigation
                    if (navigation != null) {
                        Button(
                            onClick = { onNavigate(navigation) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(
                                    R.string.ai_go_to_page,
                                    message.navigationTitle.orEmpty(),
                                ),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    val capability = message.capability
                    if (capability != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.ai_capability_needed),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = stringResource(R.string.ai_capability_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(capability.labelRes()),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    AiExpressiveSwitch(
                                        checked = config.capabilityEnabled(capability),
                                        onCheckedChange = { enabled ->
                                            if (!enabled) {
                                                onCapabilityChange(capability, false)
                                            } else if (capability.riskKind() != null) {
                                                pendingRiskCapability = capability
                                            } else {
                                                onCapabilityChange(capability, true)
                                            }
                                        },
                                    )
                                }
                                if (capability == AiCapability.ROOT) {
                                    if (rootGrantedForRequest) {
                                        Text(
                                            text = stringResource(R.string.ai_root_granted_for_request),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    } else if (!config.allowRootAccess) {
                                        TextButton(onClick = { pendingRootGrant = true }) {
                                            Text(stringResource(R.string.ai_root_allow_once))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AiMessageRole.ERROR -> {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    pendingRiskCapability?.let { capability ->
        val kind = capability.riskKind()
        if (kind != null) {
            AiRiskDialogMaterial(
                kind = kind,
                onConfirm = {
                    pendingRiskCapability = null
                    onCapabilityChange(capability, true)
                },
                onDismiss = { pendingRiskCapability = null },
            )
        }
    }

    if (pendingRootGrant) {
        AiRiskDialogMaterial(
            kind = AiRiskKind.ROOT,
            onConfirm = {
                pendingRootGrant = false
                onGrantRoot()
            },
            onDismiss = { pendingRootGrant = false },
        )
    }
}

@Composable
private fun AiCapabilityCards(
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    generation: Int = 0,
) {
    // 三套预置候选：即使没配模型，「换一批」也能真的换内容，不依赖网络。
    val pages = listOf(
        listOf(
            Triple(R.string.ai_home_card_device, R.string.ai_home_card_device_prompt, "\uD83D\uDCF1"),
            Triple(R.string.ai_home_card_troubleshoot, R.string.ai_home_card_troubleshoot_prompt, "\uD83D\uDD0D"),
            Triple(R.string.ai_home_card_module_pick, R.string.ai_home_card_module_pick_prompt, "\uD83E\uDDE9"),
            Triple(R.string.ai_home_card_cleanup, R.string.ai_home_card_cleanup_prompt, "\uD83E\uDDF9"),
        ),
        listOf(
            Triple(R.string.ai_cap_status, R.string.ai_suggestion_status2, "\uD83D\uDCCA"),
            Triple(R.string.ai_cap_troubleshoot, R.string.ai_suggestion_troubleshoot2, "\uD83E\uDDE9"),
            Triple(R.string.ai_cap_lkm, R.string.ai_suggestion_lkm, "\u2699\uFE0F"),
            Triple(R.string.ai_cap_modules2, R.string.ai_suggestion_modules2, "\uD83D\uDCE6"),
        ),
        listOf(
            Triple(R.string.ai_cap_clean, R.string.ai_suggestion_cleanup2, "\uD83E\uDDF9"),
            Triple(R.string.ai_cap_detect, R.string.ai_suggestion_detect, "\uD83D\uDEE1\uFE0F"),
            Triple(R.string.ai_cap_backup, R.string.ai_suggestion_backup, "\uD83D\uDCBE"),
            Triple(R.string.ai_cap_beginner, R.string.ai_suggestion_beginner, "\uD83C\uDF31"),
        ),
    )
    val cards = pages[generation.mod(pages.size)].map { (title, prompt, emoji) ->
        Triple(stringResource(title), stringResource(prompt), emoji)
    }

    // 两列错落排列：左列「高+矮」，右列「矮+高」。
    // 滚动容器里高度无界，卡片用 weight 会塌成 0，所以用 heightIn(min) 给不同起跳高度，长文案再自己撑开。
    AnimatedContent(
        targetState = generation,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 6 }) togetherWith
                (fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 8 })
        },
        label = "capability-cards",
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiCapabilityCard(cards[0], onSuggestion, Modifier.heightIn(min = 116.dp))
                AiCapabilityCard(cards[2], onSuggestion, Modifier.heightIn(min = 92.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiCapabilityCard(cards[1], onSuggestion, Modifier.heightIn(min = 96.dp))
                AiCapabilityCard(cards[3], onSuggestion, Modifier.heightIn(min = 112.dp))
            }
        }
    }
}

@Composable
private fun AiCapabilityCard(
    card: Triple<String, String, String>,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, prompt, emoji) = card
    Card(
        onClick = { onSuggestion(prompt) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = emoji, fontSize = 22.sp)
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // 不再截断：卡片高度由文案撑开，短文案也不会显得空。
            )
        }
    }
}

@Composable
private fun AiWelcomeMaterial(
    suggestions: List<String>,
    loading: Boolean,
    onSuggestion: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 每次「换一批」都递增，即使模型不可用、候选集来自本地预置，也有轮换动画。
    var generation by remember { mutableIntStateOf(0) }
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                AiAvatarImage(
                    modifier = Modifier.padding(4.dp),
                    size = 32.dp,
                )
            }
            Text(
                text = stringResource(R.string.ai_welcome_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = stringResource(R.string.ai_welcome_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        AiCapabilityCards(
            onSuggestion = onSuggestion,
            modifier = Modifier.padding(horizontal = 8.dp),
            generation = generation,
        )
        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.ai_suggestions_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 胶囊按钮比裸 TextButton 更像一个可点的动作，也不再单独占很宽的空白。
        FilledTonalButton(
            onClick = {
                generation++
                onRefresh()
            },
            modifier = Modifier.height(38.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.ai_suggestions_refresh),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun AiComposerMaterial(
    value: String,
    isSending: Boolean,
    error: String?,
    pendingImages: List<AiImage>,
    onAttachImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    contextLabel: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (contextLabel.isNotEmpty()) {
            Text(
                text = contextLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        AnimatedVisibility(visible = error != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = error.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    IconButton(onClick = onDismissError) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ai_close),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
        PendingImagesRow(images = pendingImages, onRemove = onRemoveImage)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            // 加号统一相对输入框垂直居中，多行时也不贴底。
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onAttachImage,
                    enabled = !isSending,
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(stringResource(R.string.ai_chat_input_hint)) },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = if (isSending) onStop else onSend,
                    enabled = isSending || value.isNotBlank(),
                    modifier = Modifier.padding(6.dp).size(48.dp),
                ) {
                    Icon(
                        imageVector = if (isSending) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(
                            if (isSending) R.string.ai_stop else R.string.ai_send
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiApprovalDialogMaterial(
    pending: AiPendingApproval,
    onAllow: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    // run_shell 这类 allowRemember=false 的请求必须每一发都真人确认：
    // 「本次请求始终允许」对它们不展示，也不生效。
    val canRemember = pending.request.allowRemember
    var alwaysAllow by remember(pending.request) { mutableStateOf(false) }
    AiExpressiveDialog(
        onDismissRequest = onDeny,
        title = { Text(stringResource(R.string.ai_approval_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pending.request.title, style = MaterialTheme.typography.bodyLarge)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    SelectionContainer {
                        Text(
                            text = pending.request.detail,
                            modifier = Modifier
                                .padding(12.dp)
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                if (canRemember) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = alwaysAllow,
                            onCheckedChange = { alwaysAllow = it },
                        )
                        Text(
                            text = stringResource(R.string.ai_approval_always),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (canRemember && alwaysAllow) onAllowAlways() else onAllow() },
            ) {
                Text(stringResource(R.string.ai_approval_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text(stringResource(R.string.ai_approval_deny)) }
        },
    )
}

@Composable
internal fun AiDisclaimerDialogMaterial(
    onAccept: () -> Unit,
    onExit: () -> Unit,
) {
    var checked by remember { mutableStateOf(false) }
    AiExpressiveDialog(
        onDismissRequest = onExit,
        title = { Text(stringResource(R.string.ai_disclaimer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.ai_disclaimer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                    )
                    Text(
                        text = stringResource(R.string.ai_disclaimer_check),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept, enabled = checked) {
                Text(stringResource(R.string.ai_disclaimer_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.ai_disclaimer_exit))
            }
        },
    )
}

@Composable
internal fun AiRiskDialogMaterial(
    kind: AiRiskKind,
    phrase: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember(kind, phrase) { mutableStateOf(false) }
    var phraseInput by remember(kind, phrase) { mutableStateOf("") }
    val phraseMatches = phrase == null || phraseInput.trim().equals(phrase, ignoreCase = true)
    AiExpressiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_risk_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(kind.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                )
                if (phrase != null) {
                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it.take(32) },
                        label = { Text(stringResource(R.string.ai_advanced_phrase)) },
                        placeholder = { Text(stringResource(R.string.ai_advanced_phrase_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                    )
                    Text(
                        text = stringResource(R.string.ai_risk_check),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = checked && phraseMatches) {
                Text(stringResource(R.string.ai_risk_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_risk_cancel))
            }
        },
    )
}
