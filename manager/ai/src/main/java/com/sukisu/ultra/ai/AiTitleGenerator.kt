package com.sukisu.ultra.ai

/**
 * 会话标题生成器：复用主对话的 endpoint 发一次最小请求，让模型把首轮问答
 * 概括成简短标题。
 *
 * 标题只是锦上添花，任何失败（网络、鉴权、解析、空结果）都返回 null，
 * 绝不能影响主对话。
 */
object AiTitleGenerator {

    /** 固定的中文标题提示词。 */
    private const val SYSTEM_PROMPT =
        "你是标题生成器。用 8 到 14 个汉字概括下面这段对话的主题，只输出标题本身：" +
            "不要引号、不要句号、不要解释、不要换行。"

    /** 首条用户消息与首条回复各自最多截取的字数。 */
    private const val MAX_INPUT_CHARS = 1000

    /** 清洗后的标题最长字数。 */
    private const val MAX_TITLE_CHARS = 24

    /**
     * 标题请求的最大 token 数。必须大于 Anthropic 高推理档的 budget_tokens(3072)，
     * 否则接口会直接报 400；正常标题远用不到这么多。
     */
    private const val MAX_TITLE_TOKENS = 4096

    /** 需要从标题首尾去掉的引号/书名号；空白由 isWhitespace 处理。 */
    private val TITLE_TRIM_CHARS = charArrayOf(
        '"', '\'', '“', '”', '‘', '’', '「', '」', '『', '』', '《', '》',
    )

    /**
     * 生成会话标题。全程不抛异常；失败或无有效结果时返回 null。
     *
     * @param firstUser 本轮首条用户消息
     * @param firstReply 本轮最终 assistant 回复
     */
    suspend fun generate(config: AiConfig, firstUser: String, firstReply: String): String? =
        runCatching {
            if (!config.isConfigured) return@runCatching null
            val userContent = buildString {
                append(firstUser.trim().take(MAX_INPUT_CHARS))
                val reply = firstReply.trim().take(MAX_INPUT_CHARS)
                if (reply.isNotEmpty()) {
                    append("\n---\n")
                    append(reply)
                }
            }
            if (userContent.isBlank()) return@runCatching null

            // 标题用独立模型/温度/推理强度；endpoint 与鉴权沿用主配置。
            // 注意：高级模式下 AiApi 以 advancedOptions.temperature 为准，
            // 这里仍保留高级模式的 headers/extraBody，避免破坏自定义网关。
            val titleConfig = config.copy(
                model = config.titleModel.trim().ifEmpty { config.model },
                temperature = 0.2f,
                // 一次拿到完整结果即可，标题请求不需要流式与工具。
                streaming = false,
                toolCalling = false,
                // AUTO = 不传推理参数；OFF/LOW/MEDIUM/HIGH 原样映射。
                thinkingMode = config.titleEffort,
            )
            val completion = AiApiClient.complete(
                config = titleConfig,
                messages = listOf(
                    AiApiMessage("system", SYSTEM_PROMPT),
                    AiApiMessage("user", userContent),
                ),
                tools = emptyList(),
                useTools = false,
                maxTokens = MAX_TITLE_TOKENS,
            )
            sanitize(completion.text)
        }.getOrNull()

    /** 去掉首尾空白/引号/书名号，只保留第一行，并截断到 [MAX_TITLE_CHARS]。 */
    private fun sanitize(raw: String): String? {
        val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val cleaned = firstLine
            .trim { it.isWhitespace() || it in TITLE_TRIM_CHARS }
            .take(MAX_TITLE_CHARS)
            .trim { it.isWhitespace() || it in TITLE_TRIM_CHARS }
        return cleaned.ifBlank { null }
    }
}
