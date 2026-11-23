package com.sukisu.ultra.ai

/*
 * 设置页的纯逻辑层：Miuix 与 Material3 两套外壳共用同一份实现。
 *
 * 这里只放「不碰 Composable」的决定性代码：字符串/配置的解析与拼装、列表增删改、
 * 开关语义。这样两套外壳的界面可以长得不一样，行为却不会各改一套各自跑偏。
 */

// ---------------------------------------------------------------- MCP 认证

/**
 * MCP 服务器的认证方式。绝大多数远程 MCP 服务只要一个密钥：
 * Bearer 走 `Authorization: Bearer <key>`，ApiKey 走 `X-API-Key: <key>`；
 * 两者都不合适时才退回「自定义」，让用户手写完整的请求头。
 */
internal enum class McpAuthMode {
    Bearer,
    ApiKey,
    Custom,
}

internal fun McpAuthMode.labelRes(): Int = when (this) {
    McpAuthMode.Bearer -> R.string.ai_mcp_auth_bearer
    McpAuthMode.ApiKey -> R.string.ai_mcp_auth_api_key
    McpAuthMode.Custom -> R.string.ai_mcp_auth_custom
}

internal fun McpAuthMode.keyHintRes(): Int = when (this) {
    McpAuthMode.Bearer -> R.string.ai_mcp_key_hint_bearer
    McpAuthMode.ApiKey -> R.string.ai_mcp_key_hint_api_key
    McpAuthMode.Custom -> R.string.ai_mcp_custom_hint
}

/** 弹窗里的认证输入：认证方式 + 密钥 + 其他请求头（原样文本，每行一条）。 */
internal data class McpAuthInput(
    val mode: McpAuthMode,
    val key: String,
    val extraHeaders: String,
)

/**
 * 从已保存的请求头反推出认证方式：能认出 Bearer / X-API-Key 就把密钥单独拿出来，
 * 让用户在详情里还能「只改密钥」；认不出（或没有认证头）就整段落进自定义文本。
 */
internal fun detectMcpAuth(headers: Map<String, String>): McpAuthInput {
    val auth = headers.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
    val bearer = auth?.value?.trim()?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
    if (auth != null && bearer != null) {
        return McpAuthInput(
            mode = McpAuthMode.Bearer,
            key = bearer.substring("Bearer ".length).trim(),
            extraHeaders = headersWithout(headers, auth.key),
        )
    }
    val apiKey = headers.entries.firstOrNull { it.key.equals("X-API-Key", ignoreCase = true) }
    if (apiKey != null) {
        return McpAuthInput(
            mode = McpAuthMode.ApiKey,
            key = apiKey.value.trim(),
            extraHeaders = headersWithout(headers, apiKey.key),
        )
    }
    return McpAuthInput(
        mode = McpAuthMode.Custom,
        key = "",
        extraHeaders = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
    )
}

private fun headersWithout(headers: Map<String, String>, name: String): String = headers.entries
    .filterNot { it.key.equals(name, ignoreCase = true) }
    .joinToString("\n") { "${it.key}: ${it.value}" }

/** 把「认证方式 + 密钥 + 其他请求头」拼回请求头表；密钥为空时不写认证头。 */
internal fun buildMcpHeaders(mode: McpAuthMode, key: String, extraHeaders: String): Map<String, String> {
    if (mode == McpAuthMode.Custom) return parseHeaders(extraHeaders)
    val result = parseHeaders(extraHeaders).toMutableMap()
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return result
    when (mode) {
        McpAuthMode.Bearer -> {
            result["Authorization"] = if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
                trimmed
            } else {
                "Bearer $trimmed"
            }
        }
        McpAuthMode.ApiKey -> result["X-API-Key"] = trimmed
        McpAuthMode.Custom -> Unit
    }
    return result
}

// ------------------------------------------------------------ 联网搜索开关

/**
 * 点某个搜索服务开关后的新配置。
 *
 * 三条语义都是同一件事：总闸（[AiSearchConfig.searchEnabled]）不该在没有服务时还开着，
 * 也不该在用户明确点开某个服务时继续关着——
 * - 关掉最后一个服务 → 总闸跟随关闭；
 * - 总闸关着时点开某个服务 → 总闸自动打开；
 * - 其余情况保持总闸原样。
 */
internal fun toggleSearchProvider(
    config: AiSearchConfig,
    provider: AiSearchProvider,
    enabled: Boolean,
): AiSearchConfig {
    val nextEnabled = if (enabled) config.enabled + provider else config.enabled - provider
    val nextSearchEnabled = when {
        nextEnabled.isEmpty() -> false
        !config.searchEnabled && enabled -> true
        else -> config.searchEnabled
    }
    return config.copy(enabled = nextEnabled, searchEnabled = nextSearchEnabled)
}

/** 拖拽排序后的新顺序；下标越界时原样返回。 */
internal fun reorderSearchProviders(
    config: AiSearchConfig,
    from: Int,
    to: Int,
): AiSearchConfig {
    val next = config.order.toMutableList()
    if (from !in next.indices || to !in next.indices) return config
    next.add(to, next.removeAt(from))
    return config.copy(order = next)
}

// ------------------------------------------------------------- 提供商列表

/**
 * 新增条目统一带序号：把 template 里的 %1$d 换成第一个没被占用的序号。
 * 提供商「提供商 1/2/3」和会话「会话 1/2/3」共用这一条规则，用户一眼能分清谁是谁。
 */
internal fun nextNumberedName(existing: Collection<String>, template: String): String {
    val used = existing.map { it.trim() }.toSet()
    var index = 1
    while (true) {
        val candidate = String.format(template, index)
        if (candidate !in used) return candidate
        index++
    }
}

/** 保存编辑结果：已存在就原地更新，新增则追加到末尾。 */
internal fun upsertProvider(providers: List<AiProvider>, provider: AiProvider): List<AiProvider> =
    if (providers.any { it.id == provider.id }) {
        providers.map { if (it.id == provider.id) provider else it }
    } else {
        providers + provider
    }

/** 按 id 删除；列表只剩一个时不动（至少要留一个提供商）。 */
internal fun removeProvider(providers: List<AiProvider>, id: String): List<AiProvider> {
    val remaining = providers.filterNot { it.id == id }
    return if (remaining.isEmpty()) providers else remaining
}

// ------------------------------------------------------------- 会话列表

/**
 * 会话列表该展示哪些。
 *
 * 只列有内容的会话，免得空会话在弹窗里堆成一片「新对话」；当前会话即使还是空
 * 也留着，否则用户找不到自己正待着的那一个。
 */
internal fun visibleSessions(all: List<AiSession>, activeId: String): List<AiSession> =
    all.filter { it.turns.isNotEmpty() || it.id == activeId }

/** 选中的提供商没了之后该回落到谁：优先第一个剩下的。 */
internal fun fallbackProvider(providers: List<AiProvider>, currentId: String?): AiProvider? =
    providers.firstOrNull { it.id == currentId } ?: providers.firstOrNull()

// ------------------------------------------------------------ 自定义指令

/** 自定义指令的预设模板；填入后用户仍可继续修改。 */
internal enum class AiSystemPromptPreset(val labelRes: Int, val textRes: Int) {
    Humorous(R.string.ai_prompt_preset_humorous, R.string.ai_prompt_preset_humorous_text),
    Friendly(R.string.ai_prompt_preset_friendly, R.string.ai_prompt_preset_friendly_text),
    Plain(R.string.ai_prompt_preset_plain, R.string.ai_prompt_preset_plain_text),
    Teacher(R.string.ai_prompt_preset_teacher, R.string.ai_prompt_preset_teacher_text),
    Rigorous(R.string.ai_prompt_preset_rigorous, R.string.ai_prompt_preset_rigorous_text),
    Concise(R.string.ai_prompt_preset_concise, R.string.ai_prompt_preset_concise_text),
}

/** 把模板并入已有指令：已有内容就换行追加，重复的模板不重复添加。 */
internal fun mergePromptPreset(current: String, preset: String): String {
    val trimmed = current.trimEnd()
    if (trimmed.isBlank()) return preset
    if (trimmed.contains(preset)) return current
    return trimmed + "\n" + preset
}

/** 模板当前是否已填入指令：芯片据此显示选中态。 */
internal fun hasPromptPreset(current: String, preset: String): Boolean = current.contains(preset)

/** 从指令里摘掉模板：连同它带进来的那个换行一起删，避免留下空行。 */
internal fun removePromptPreset(current: String, preset: String): String {
    val index = current.indexOf(preset)
    if (index < 0) return current
    val before = current.substring(0, index).trimEnd()
    val after = current.substring(index + preset.length).trimStart()
    return when {
        before.isEmpty() -> after
        after.isEmpty() -> before
        else -> before + "\n" + after
    }
}

/** 点一下填入模板，再点一下连内容一起撤掉——芯片的选中态与输入框内容始终一致。 */
internal fun togglePromptPreset(current: String, preset: String): String =
    if (hasPromptPreset(current, preset)) removePromptPreset(current, preset) else mergePromptPreset(current, preset)
