package com.sukisu.ultra.ai

/**
 * 聊天输入框上方快捷控制需要的最小数据：当前模型、可选模型、推理档位、搜索服务商状态。
 *
 * 这些计算两个外壳（Miuix / Material3）都要用，所以放这里，UI 文件只负责画。
 */

/** 可切换的模型条目。 */
internal data class AiQuickModel(
    val id: String,
    val label: String,
)

/** 联网搜索里一个服务商的当前状态。 */
internal data class AiQuickSearchProvider(
    val provider: AiSearchProvider,
    val enabled: Boolean,
    val configured: Boolean,
)

/** 当前供应商下能聊天的模型；图片模型不参与对话，不出现在切换列表里。 */
internal fun AiConfig.quickModels(): List<AiQuickModel> =
    AiSettings.activeProvider(this)?.models.orEmpty()
        .filterNot { it.looksLikeImageModel() }
        .map { AiQuickModel(it.id, it.name.ifBlank { it.id }) }

/** 输入框上显示的模型名：优先展示名，其次 id；都没配就是空串。 */
internal fun AiConfig.quickModelLabel(): String {
    val resolved = AiSettings.resolveActiveProvider(this)
    val name = resolved.model?.name?.takeIf { it.isNotBlank() }
    return name ?: resolved.modelId.ifBlank { resolved.model?.id.orEmpty() }
}

/** 该模型支持的推理档位；模型未知或明确不支持推理时只有「自动」。 */
internal fun AiConfig.quickThinkingModes(): List<AiThinkingMode> {
    val modes = AiSettings.activeModel(this).supportedReasoningModes()
    return modes.ifEmpty { listOf(AiThinkingMode.AUTO) }
}

/** 当前档位：不在支持列表里就显示「自动」，和请求侧的回落口径一致。 */
internal fun AiConfig.quickThinkingMode(): AiThinkingMode {
    val modes = quickThinkingModes()
    return if (thinkingMode in modes) thinkingMode else AiThinkingMode.AUTO
}

/** 联网搜索的服务商状态，按自动链顺序排列。 */
internal fun AiSearchConfig.quickSearchProviders(): List<AiQuickSearchProvider> =
    order.distinct().map {
        AiQuickSearchProvider(
            provider = it,
            enabled = isEnabled(it),
            configured = isConfigured(it),
        )
    }
