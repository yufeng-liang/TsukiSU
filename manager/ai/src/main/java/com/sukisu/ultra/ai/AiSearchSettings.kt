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
    /** 关闭联网搜索。 */
    NONE,

    /** Firecrawl 官方搜索 API（https://api.firecrawl.dev/v1/search）。 */
    FIRECRAWL,

    /** AnySearch 官方搜索 API（https://api.anysearch.com/v1/search）；端点内置，Key 可选（匿名额度更低）。 */
    ANYSEARCH,
}

/**
 * 联网搜索配置。
 *
 * @property provider 搜索服务；[AiSearchProvider.NONE] 表示不启用。
 * @property apiKey 服务鉴权 Key；Firecrawl 必填，AnySearch 可留空走匿名额度。
 * @property endpoint Firecrawl 的自定义端点（留空用官方地址）；AnySearch 恒用内置端点，此字段忽略。
 */
@Serializable
data class AiSearchConfig(
    val provider: AiSearchProvider = AiSearchProvider.NONE,
    val apiKey: String = "",
    val endpoint: String = "",
)

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

    /** 读取配置；未配置或数据损坏时返回默认配置（NONE）。 */
    fun load(): AiSearchConfig {
        val raw = prefs().getString(KEY_CONFIG, null) ?: return AiSearchConfig()
        return runCatching { json.decodeFromString<AiSearchConfig>(raw) }
            .getOrDefault(AiSearchConfig())
    }

    /** 保存配置；Key 与端点去掉首尾空白，避免脏字符进入 HTTP 头或 URL。 */
    fun save(config: AiSearchConfig) {
        val normalized = config.copy(
            apiKey = config.apiKey.trim(),
            endpoint = config.endpoint.trim(),
        )
        prefs().edit { putString(KEY_CONFIG, json.encodeToString(normalized)) }
    }
}
