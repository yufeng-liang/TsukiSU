package com.sukisu.ultra.ai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 联网搜索配置迁移与故障转移顺序的回归测试。 */
class AiSearchSettingsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test
    fun legacyNoneProviderFallsBackAndMigratesKey() {
        // 旧版单选 "NONE"（内置 DuckDuckGo）在 coerceInputValues 下应回落到默认的 FIRECRAWL。
        val decoded = json.decodeFromString<AiSearchConfig>(
            """{"provider":"NONE","apiKey":" sk-legacy "}""",
        )
        assertEquals(AiSearchProvider.FIRECRAWL, decoded.provider)

        val migrated = AiSearchSettings.migrateLegacyConfig(decoded)
        assertEquals(mapOf("FIRECRAWL" to "sk-legacy"), migrated.keys)
        assertEquals(setOf(AiSearchProvider.FIRECRAWL), migrated.enabled)
        assertEquals(AiSearchProvider.FIRECRAWL, migrated.order.first())
        // 旧字段在迁移后必须清空，防止下次加载再次当成旧配置。
        assertEquals("", migrated.apiKey)
        assertEquals("", migrated.endpoint)
    }

    @Test
    fun legacyAnySearchWithoutKeyStillMigrates() {
        // AnySearch 支持匿名，旧配置即使没填 Key 也要迁移到「只启用 AnySearch」。
        val config = AiSearchConfig(provider = AiSearchProvider.ANYSEARCH, apiKey = "")
        val migrated = AiSearchSettings.migrateLegacyConfig(config)
        assertEquals(setOf(AiSearchProvider.ANYSEARCH), migrated.enabled)
        assertTrue(migrated.keys.isEmpty())
        assertEquals(AiSearchProvider.ANYSEARCH, migrated.order.first())
    }

    @Test
    fun failoverCandidatesKeepOrderAndDropDuplicates() {
        val config = AiSearchConfig(
            keys = mapOf("TAVILY" to "t"),
            enabled = setOf(
                AiSearchProvider.ANYSEARCH,
                AiSearchProvider.TAVILY,
                AiSearchProvider.FIRECRAWL,
            ),
            order = listOf(
                AiSearchProvider.TAVILY,
                AiSearchProvider.FIRECRAWL,
                AiSearchProvider.TAVILY,
                AiSearchProvider.ANYSEARCH,
            ),
        )
        assertEquals(
            listOf(
                AiSearchProvider.TAVILY,
                AiSearchProvider.FIRECRAWL,
                AiSearchProvider.ANYSEARCH,
            ),
            config.failoverCandidates(),
        )
    }

    @Test
    fun failoverCandidatesSkipDisabledAndUnconfiguredProviders() {
        val config = AiSearchConfig(
            keys = emptyMap(),
            enabled = setOf(AiSearchProvider.TAVILY, AiSearchProvider.FIRECRAWL),
            order = listOf(AiSearchProvider.TAVILY, AiSearchProvider.FIRECRAWL),
        )
        // Tavily 必须填 Key，缺 Key 应跳过；Firecrawl 允许匿名。
        assertEquals(listOf(AiSearchProvider.FIRECRAWL), config.failoverCandidates())
    }
}
