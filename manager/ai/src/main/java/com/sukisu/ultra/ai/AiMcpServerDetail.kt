package com.sukisu.ultra.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 拉取某个服务器的工具列表并写回配置缓存。
 *
 * 失败返回 null（[AiMcpClient.listTools] 自己吞掉了网络异常，只有取消会抛出来），
 * 成功时把工具名和描述存进 [AiMcpServer.tools]，下次离线打开详情页也能先画一份。
 */
internal suspend fun refreshMcpTools(server: AiMcpServer): List<AiMcpToolInfo>? =
    runCatching { AiMcpClient.listTools(server) }
        .getOrNull()
        ?.map { AiMcpToolInfo(name = it.name, description = it.description) }
        ?.also { AiMcpSettings.updateTools(server.id, it) }

/**
 * MCP 服务器详情页：全屏弹出，带自己的顶栏（返回 + 删除），
 * 中间是可二次编辑的名称 / 地址 / 认证，下面是工具列表和每个工具的总开关。
 *
 * 用全屏 Dialog 而不是新建导航路由：设置页本身就是一个屏，详情页只是它的下一层，
 * 这样两套外壳都不用改宿主导航，返回手势和系统返回键天然可用。
 */
@Composable
internal fun McpServerDetailPage(
    serverId: String,
    miuix: Boolean = false,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 铺满整屏并铺到状态栏、导航栏下面，系统栏区域由页面自己上色。
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        // 对话框默认会给背景窗口压暗，状态栏那一条就成了一道灰带；去掉压暗并把窗口撑满。
        val dialogView = LocalView.current
        // 对话框窗口默认按深色状态栏出图标，浅色页面上会看不清；跟随当前背景明暗切换。
        val lightBackground = (if (miuix) {
            MiuixTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }).luminance() > 0.5f
        LaunchedEffect(dialogView, lightBackground) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            window.setDimAmount(0f)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            WindowCompat.getInsetsController(window, dialogView).apply {
                isAppearanceLightStatusBars = lightBackground
                isAppearanceLightNavigationBars = lightBackground
            }
        }
        if (miuix) {
            McpServerDetailPageMiuix(serverId = serverId, onDismiss = onDismiss)
        } else {
            McpServerDetailPageMaterial(serverId = serverId, onDismiss = onDismiss)
        }
    }
}

/** 从配置里按 id 取服务器；取不到时返回 null，调用方直接关页。 */
@Composable
private fun rememberMcpServer(serverId: String): AiMcpServer? =
    remember(serverId) { AiMcpSettings.list().firstOrNull { it.id == serverId } }

/** 工具开关只改本机记录里的禁用名单，不动其它字段（先读一遍最新值，避免覆盖刚刷新的工具）。 */
private fun setMcpToolEnabled(serverId: String, toolName: String, enabled: Boolean) {
    val current = AiMcpSettings.list().firstOrNull { it.id == serverId } ?: return
    val next = if (enabled) current.disabledTools - toolName else current.disabledTools + toolName
    AiMcpSettings.update(current.copy(disabledTools = next))
}

/** 详情页三组卡片共用的圆角，和设置页一致。 */
private val McpCardShape = RoundedCornerShape(20.dp)

/**
 * 详情页分组卡片（Material3）：沿用设置页的平面卡片——无阴影、无描边、弱底色，
 * 标题用主色 titleMedium，右侧可挂一个轻量动作（工具组的「获取工具列表」）。
 */
@Composable
private fun McpSectionMaterial(
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    contentSpacing: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = McpCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            if (title != null || action != null) Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                action?.invoke()
            }
            content()
        }
    }
}

// 详情页是全屏 Dialog，Miuix 的 OverlayDialog / M3 的 AlertDialog 在这种窗口里弹不出来（没有宿主 popup host，
// 也不会新建窗口），所以确认框直接画在页面最上层：一层压暗遮罩加一张卡片，两套外壳共用同一套结构。
@Composable
private fun McpConfirmOverlay(
    miuix: Boolean,
    title: String,
    message: String,
    cancelText: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val swallow = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = swallow, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .clip(McpCardShape)
                .background(
                    if (miuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            if (miuix) {
                MiuixText(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                MiuixText(
                    text = message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiuixTextButton(text = cancelText, onClick = onDismiss, modifier = Modifier.weight(1f))
                    MiuixTextButton(
                        text = confirmText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error,
                        ),
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(cancelText) }
                    TextButton(
                        onClick = onConfirm,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(confirmText) }
                }
            }
        }
    }
}

@Composable
private fun McpServerDetailPageMaterial(serverId: String, onDismiss: () -> Unit) {
    val loaded = rememberMcpServer(serverId)
    if (loaded == null) {
        LaunchedEffect(serverId) { onDismiss() }
        return
    }
    val initialAuth = remember(serverId) { detectMcpAuth(loaded.headers) }
    var name by remember(serverId) { mutableStateOf(loaded.name) }
    var url by remember(serverId) { mutableStateOf(loaded.url) }
    var authMode by remember(serverId) { mutableStateOf(initialAuth.mode) }
    var apiKey by remember(serverId) { mutableStateOf(initialAuth.key) }
    var extraHeaders by remember(serverId) { mutableStateOf(initialAuth.extraHeaders) }
    var tools by remember(serverId) { mutableStateOf(loaded.tools) }
    var disabled by remember(serverId) { mutableStateOf(loaded.disabledTools) }
    var loading by remember(serverId) { mutableStateOf(false) }
    var error by remember(serverId) { mutableStateOf<String?>(null) }
    var showRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 打开就自动拉一次：新增服务器后也是这样拿到工具清单，用户不用手点。
    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            val target = loaded.copy(
                name = name.trim().ifBlank { loaded.name },
                url = url.trim().ifBlank { loaded.url },
                headers = buildMcpHeaders(authMode, apiKey, extraHeaders),
            )
            val result = runCatching { AiMcpClient.listTools(target) }
            loading = false
            result.fold(
                onSuccess = { list ->
                    val infos = list.map { AiMcpToolInfo(name = it.name, description = it.description) }
                    // 拉不到（离线、超时）时 listTools 会返回空表，这时保留上一次的缓存，别把清单清空。
                    if (infos.isNotEmpty()) {
                        AiMcpSettings.updateTools(serverId, infos)
                        tools = infos
                    }
                },
                onFailure = { error = it.message },
            )
        }
    }

    LaunchedEffect(serverId) { refresh() }

    // 保存即改配置并关页；两套外壳共用同一份写法。
    val save = {
        AiMcpSettings.update(
            loaded.copy(
                name = name.trim(),
                url = url.trim(),
                headers = buildMcpHeaders(authMode, apiKey, extraHeaders),
            ),
        )
        onDismiss()
    }

    val titleText = name.trim().ifBlank { stringResource(R.string.ai_mcp_detail_title) }
    Box(modifier = Modifier.fillMaxSize()) {
    AiExpressiveScaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        bottomBar = {
            // 主操作固定在底栏：详情页内容可能很长，保存不能藏在滚动末尾。
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    enabled = name.isNotBlank() && isValidMcpUrl(url) && !loading,
                    onClick = { save() },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                ) {
                    Text(stringResource(R.string.ai_mcp_save))
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = titleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { AiTopBarBackButton(onClick = onDismiss) },
                actions = {
                    IconButton(onClick = { showRemove = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.ai_mcp_detail_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors = aiExpressiveTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            McpSectionMaterial(title = stringResource(R.string.ai_mcp_detail_basic)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ai_mcp_name) + stringResource(R.string.ai_field_required)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.ai_mcp_url_label) + stringResource(R.string.ai_field_required)) },
                    singleLine = true,
                    isError = url.isNotBlank() && !isValidMcpUrl(url),
                    supportingText = {
                        if (url.isNotBlank() && !isValidMcpUrl(url)) {
                            Text(stringResource(R.string.ai_mcp_url_invalid))
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            McpSectionMaterial {
                McpAuthFieldsMaterial(
                    mode = authMode,
                    onModeChange = { authMode = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    extraHeaders = extraHeaders,
                    onExtraHeadersChange = { extraHeaders = it },
                )
            }
            McpSectionMaterial(
                title = stringResource(R.string.ai_mcp_tools_title),
                subtitle = if (tools.isEmpty()) null else stringResource(R.string.ai_mcp_tool_count, tools.size),
                action = {
                    TextButton(
                        enabled = !loading && isValidMcpUrl(url),
                        onClick = { refresh() },
                    ) {
                        Text(
                            text = stringResource(if (loading) R.string.ai_mcp_loading else R.string.ai_mcp_refresh),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
                contentSpacing = 12.dp,
            ) {
                if (tools.isEmpty()) {
                    Text(
                        text = stringResource(if (loading) R.string.ai_mcp_loading else R.string.ai_mcp_tools_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    tools.forEach { tool ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        text = tool.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            AiExpressiveSwitch(
                                checked = tool.name !in disabled,
                                onCheckedChange = { checked ->
                                    disabled = if (checked) disabled - tool.name else disabled + tool.name
                                    setMcpToolEnabled(serverId, tool.name, checked)
                                },
                            )
                        }
                    }
                }
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

        if (showRemove) {
            McpConfirmOverlay(
                miuix = false,
                title = stringResource(R.string.ai_mcp_remove),
                message = stringResource(R.string.ai_mcp_remove_confirm),
                cancelText = stringResource(R.string.ai_mcp_cancel),
                confirmText = stringResource(R.string.ai_mcp_remove),
                onDismiss = { showRemove = false },
                onConfirm = {
                    AiMcpSettings.remove(serverId)
                    showRemove = false
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun McpServerDetailPageMiuix(serverId: String, onDismiss: () -> Unit) {
    val loaded = rememberMcpServer(serverId)
    if (loaded == null) {
        LaunchedEffect(serverId) { onDismiss() }
        return
    }
    val initialAuth = remember(serverId) { detectMcpAuth(loaded.headers) }
    var name by remember(serverId) { mutableStateOf(loaded.name) }
    var url by remember(serverId) { mutableStateOf(loaded.url) }
    var authMode by remember(serverId) { mutableStateOf(initialAuth.mode) }
    var apiKey by remember(serverId) { mutableStateOf(initialAuth.key) }
    var extraHeaders by remember(serverId) { mutableStateOf(initialAuth.extraHeaders) }
    var tools by remember(serverId) { mutableStateOf(loaded.tools) }
    var disabled by remember(serverId) { mutableStateOf(loaded.disabledTools) }
    var loading by remember(serverId) { mutableStateOf(false) }
    var error by remember(serverId) { mutableStateOf<String?>(null) }
    var showRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            val target = loaded.copy(
                name = name.trim().ifBlank { loaded.name },
                url = url.trim().ifBlank { loaded.url },
                headers = buildMcpHeaders(authMode, apiKey, extraHeaders),
            )
            val result = runCatching { AiMcpClient.listTools(target) }
            loading = false
            result.fold(
                onSuccess = { list ->
                    val infos = list.map { AiMcpToolInfo(name = it.name, description = it.description) }
                    // 拉不到（离线、超时）时 listTools 会返回空表，这时保留上一次的缓存，别把清单清空。
                    if (infos.isNotEmpty()) {
                        AiMcpSettings.updateTools(serverId, infos)
                        tools = infos
                    }
                },
                onFailure = { error = it.message },
            )
        }
    }

    LaunchedEffect(serverId) { refresh() }

    // 保存即改配置并关页；两套外壳共用同一份写法。
    val save = {
        AiMcpSettings.update(
            loaded.copy(
                name = name.trim(),
                url = url.trim(),
                headers = buildMcpHeaders(authMode, apiKey, extraHeaders),
            ),
        )
        onDismiss()
    }

    val titleText = name.trim().ifBlank { stringResource(R.string.ai_mcp_detail_title) }
    Box(modifier = Modifier.fillMaxSize()) {
    AiAppScaffold(
        containerColor = colorScheme.surface,
        bottomBar = {
            // 主操作固定在底栏，滚动多长都随手可点。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                MiuixTextButton(
                    text = stringResource(R.string.ai_mcp_save),
                    onClick = { save() },
                    enabled = name.isNotBlank() && isValidMcpUrl(url) && !loading,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        topBar = {
            MiuixTopAppBar(
                title = titleText,
                color = colorScheme.surface,
                navigationIcon = {
                    MiuixIconButton(onClick = onDismiss) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    MiuixIconButton(onClick = { showRemove = true }) {
                        MiuixIcon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.ai_mcp_detail_delete),
                            tint = colorScheme.error,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.surface)
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AiSettingsSectionMiuix(title = stringResource(R.string.ai_mcp_detail_basic)) {
                MiuixTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.ai_mcp_name) + stringResource(R.string.ai_field_required),
                    useLabelAsPlaceholder = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                MiuixTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = stringResource(R.string.ai_mcp_url_label) + stringResource(R.string.ai_field_required),
                    useLabelAsPlaceholder = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (url.isNotBlank() && !isValidMcpUrl(url)) {
                    MiuixText(
                        text = stringResource(R.string.ai_mcp_url_invalid),
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.error,
                    )
                }
            }
            AiSettingsSectionMiuix {
                McpAuthFieldsMiuix(
                    mode = authMode,
                    onModeChange = { authMode = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    extraHeaders = extraHeaders,
                    onExtraHeadersChange = { extraHeaders = it },
                )
            }
            AiSettingsSectionMiuix(title = stringResource(R.string.ai_mcp_tools_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = if (tools.isEmpty()) "" else stringResource(R.string.ai_mcp_tool_count, tools.size),
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = stringResource(if (loading) R.string.ai_mcp_loading else R.string.ai_mcp_refresh),
                        onClick = { refresh() },
                        enabled = !loading && isValidMcpUrl(url),
                    )
                }
                if (tools.isEmpty()) {
                    MiuixText(
                        text = stringResource(if (loading) R.string.ai_mcp_loading else R.string.ai_mcp_tools_empty),
                        style = MiuixTheme.textStyles.footnote1,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    tools.forEach { tool ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                MiuixText(tool.name, style = MiuixTheme.textStyles.body2)
                                if (tool.description.isNotBlank()) {
                                    MiuixText(
                                        text = tool.description,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            MiuixSwitch(
                                checked = tool.name !in disabled,
                                onCheckedChange = { checked ->
                                    disabled = if (checked) disabled - tool.name else disabled + tool.name
                                    setMcpToolEnabled(serverId, tool.name, checked)
                                },
                            )
                        }
                    }
                }
            }
            error?.let {
                MiuixText(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

        if (showRemove) {
            McpConfirmOverlay(
                miuix = true,
                title = stringResource(R.string.ai_mcp_remove),
                message = stringResource(R.string.ai_mcp_remove_confirm),
                cancelText = stringResource(R.string.ai_mcp_cancel),
                confirmText = stringResource(R.string.ai_mcp_remove),
                onDismiss = { showRemove = false },
                onConfirm = {
                    AiMcpSettings.remove(serverId)
                    showRemove = false
                    onDismiss()
                },
            )
        }
    }
}
