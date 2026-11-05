package com.sukisu.ultra.ai

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 联网搜索服务提供方。
 */
@Serializable
enum class AiSearchProvider {
    /** 历史配置里的「关闭」值：新配置不再用它表达开关，是否参与搜索由 [AiSearchConfig.enabled] 决定。 */
    NONE,

    /** Firecrawl 官方搜索 API（https://api.firecrawl.dev/v1/search）。 */
    FIRECRAWL,

    /** AnySearch 官方搜索 API（https://api.anysearch.com/v1/search）；端点内置，Key 可选（匿名额度更低）。 */
    ANYSEARCH,

    /** Tavily 官方搜索 API（https://api.tavily.com/search），面向 AI Agent，需要 API Key。 */
    TAVILY,

    /** 腾讯云元宝搜索（https://api.wsa.cloud.tencent.com/SearchPro），需要服务 API KEY。 */
    YUANBAO;

    companion object {
        /** 默认尝试顺序：除 [NONE] 外的全部服务商，按枚举声明顺序。 */
        val orderDefault: List<AiSearchProvider> = entries.filter { it != NONE }
    }
}

/**
 * 联网搜索配置。
 *
 * @property searchEnabled 联网搜索总开关；关闭后 AI 不发起任何联网搜索。
 * @property duckDuckGoFallback 所有服务商都失败后是否抓取内置 DuckDuckGo 兜底。
 * @property keys 各服务商的独立 Key，key 为 [AiSearchProvider.name]（枚举名做索引，改名不会串味）。
 * @property enabled 参与自动故障转移链的服务商集合。
 * @property order 自动链的尝试顺序（设置页可拖拽排序）。
 * @property provider 仅用于旧配置迁移：旧版单选的服务商；新代码不再读写。
 * @property apiKey 仅用于旧配置迁移：旧版单一 Key；新代码不再读写。
 * @property endpoint 仅用于旧配置迁移：旧版 Firecrawl 自定义端点；新代码不再读写。
 */
@Serializable
data class AiSearchConfig(
    val searchEnabled: Boolean = true,
    val duckDuckGoFallback: Boolean = true,
    val keys: Map<String, String> = emptyMap(),
    val enabled: Set<AiSearchProvider> = emptySet(),
    val order: List<AiSearchProvider> = AiSearchProvider.orderDefault,
    // 以下三个字段只为兼容旧版 JSON 而保留，写回时会被清空（见 AiSearchSettings#normalizeForWrite）。
    val provider: AiSearchProvider = AiSearchProvider.NONE,
    val apiKey: String = "",
    val endpoint: String = "",
) {
    /** 取某服务商的 Key；未配置返回空串。 */
    fun keyOf(provider: AiSearchProvider): String = keys[provider.name].orEmpty()

    /** 是否加入自动故障转移链。 */
    fun isEnabled(provider: AiSearchProvider): Boolean = provider in enabled

    /** AnySearch 允许匿名（额度更低），其余服务必须填 Key 才算可用。 */
    fun isConfigured(provider: AiSearchProvider): Boolean =
        provider == AiSearchProvider.ANYSEARCH || keyOf(provider).isNotBlank()

    /**
     * 自动故障转移的候选服务商：按 [order] 排列，跳过 [AiSearchProvider.NONE]、
     * 未启用的和缺 Key 的；[order] 里重复出现的只保留第一次。
     */
    fun failoverCandidates(): List<AiSearchProvider> = order.asSequence()
        .filter { it != AiSearchProvider.NONE }
        .distinct()
        .filter { isEnabled(it) && isConfigured(it) }
        .toList()
}

/**
 * 联网搜索配置的持久化：整段 JSON 存进独立的 `ai_search` SharedPreferences。
 */
object AiSearchSettings {

    private const val PREFS_NAME = "ai_search"
    private const val KEY_CONFIG = "config"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // 手工改坏枚举值时回落到默认值，而不是让整份配置解析失败。
        coerceInputValues = true
        encodeDefaults = true
    }

    private fun prefs() = aiContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 读取配置；未配置或数据损坏时返回默认配置。
     *
     * 旧版只存单个 `provider` + `apiKey`，这里做一次性迁移：迁成新结构后立刻回写，
     * 写回时旧字段被清空，因此不会出现「下次加载又迁移一遍」的循环（也就没有 load→save→load 递归）。
     */
    fun load(): AiSearchConfig {
        val raw = prefs().getString(KEY_CONFIG, null) ?: return AiSearchConfig()
        val parsed = runCatching { json.decodeFromString<AiSearchConfig>(raw) }
            .getOrDefault(AiSearchConfig())
        return migrateLegacy(parsed)
    }

    /** 保存配置；Key 逐个去空白，空的 Key 直接丢弃，避免把无效字符写进 HTTP 头。 */
    fun save(config: AiSearchConfig) {
        writeConfig(normalizeForWrite(config))
    }

    /** 旧版配置迁移：keys 为空且旧 apiKey 有效时，把单一服务商迁成「一家启用 + 独立 Key」。 */
    private fun migrateLegacy(config: AiSearchConfig): AiSearchConfig {
        if (config.keys.isNotEmpty()) return config
        if (config.provider == AiSearchProvider.NONE) return config
        val provider = config.provider
        val legacyKey = config.apiKey.trim()
        // AnySearch 支持匿名：旧版只要选中它就等于「已配置」，即使没填 Key 也要迁移，
        // 否则升级后会静默退回 DuckDuckGo，与旧行为不一致。
        if (legacyKey.isEmpty() && provider != AiSearchProvider.ANYSEARCH) return config
        val migrated = normalizeForWrite(
            config.copy(
                keys = if (legacyKey.isEmpty()) emptyMap() else mapOf(provider.name to legacyKey),
                enabled = setOf(provider),
                // 旧配置里用户选中的服务商优先，其余保持默认顺序。
                order = listOf(provider) + AiSearchProvider.orderDefault.filter { it != provider },
            ),
        )
        writeConfig(migrated)
        return migrated
    }

    /**
     * 落盘前的统一清洗：
     * - Key 去首尾空白、丢弃空 Key；
     * - order / enabled 里的 [AiSearchProvider.NONE] 无意义，直接剔除，order 去重；
     * - 旧字段只用于迁移，写回时一律清空，防止下次加载又被当成旧配置迁移。
     */
    private fun normalizeForWrite(config: AiSearchConfig): AiSearchConfig = config.copy(
        keys = config.keys
            .mapValues { (_, value) -> value.trim() }
            .filterValues { it.isNotEmpty() },
        enabled = config.enabled.filter { it != AiSearchProvider.NONE }.toSet(),
        order = config.order.filter { it != AiSearchProvider.NONE }.distinct(),
        provider = AiSearchProvider.NONE,
        apiKey = "",
        endpoint = "",
    )

    /** 只做编码与写盘，不再触发迁移，供 [save] 与迁移回写共用。 */
    private fun writeConfig(config: AiSearchConfig) {
        prefs().edit { putString(KEY_CONFIG, json.encodeToString(config)) }
    }
}
