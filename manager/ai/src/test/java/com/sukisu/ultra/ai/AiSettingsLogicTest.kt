package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享逻辑层的回归测试：两套外壳都调这些函数，语义只在这里定一次。
 */
class AiSettingsLogicTest {

    private fun config(
        searchEnabled: Boolean = true,
        enabled: Set<AiSearchProvider> = setOf(AiSearchProvider.FIRECRAWL),
        order: List<AiSearchProvider> = AiSearchProvider.orderDefault,
    ) = AiSearchConfig(searchEnabled = searchEnabled, enabled = enabled, order = order)

    // ------------------------------------------------------------ 搜索开关

    @Test
    fun turningOffTheLastProviderAlsoTurnsOffTheMasterSwitch() {
        val next = toggleSearchProvider(
            config(searchEnabled = true, enabled = setOf(AiSearchProvider.FIRECRAWL)),
            AiSearchProvider.FIRECRAWL,
            false,
        )
        assertTrue(next.enabled.isEmpty())
        assertEquals(false, next.searchEnabled)
    }

    @Test
    fun turningOnAProviderTurnsOnTheMasterSwitch() {
        val next = toggleSearchProvider(
            config(searchEnabled = false, enabled = emptySet()),
            AiSearchProvider.TAVILY,
            true,
        )
        assertEquals(setOf(AiSearchProvider.TAVILY), next.enabled)
        assertTrue(next.searchEnabled)
    }

    @Test
    fun otherCasesKeepTheMasterSwitchAsItWas() {
        val on = toggleSearchProvider(
            config(searchEnabled = true, enabled = setOf(AiSearchProvider.FIRECRAWL)),
            AiSearchProvider.TAVILY,
            true,
        )
        assertTrue(on.searchEnabled)
        assertEquals(setOf(AiSearchProvider.FIRECRAWL, AiSearchProvider.TAVILY), on.enabled)

        val off = toggleSearchProvider(
            config(searchEnabled = true, enabled = setOf(AiSearchProvider.FIRECRAWL)),
            AiSearchProvider.FIRECRAWL,
            false,
        )
        assertEquals(false, off.searchEnabled)
    }

    @Test
    fun reorderMovesTheProviderAndIgnoresOutOfRangeIndexes() {
        val order = listOf(AiSearchProvider.FIRECRAWL, AiSearchProvider.ANYSEARCH, AiSearchProvider.TAVILY)
        val moved = reorderSearchProviders(config(order = order), 0, 2)
        assertEquals(
            listOf(AiSearchProvider.ANYSEARCH, AiSearchProvider.TAVILY, AiSearchProvider.FIRECRAWL),
            moved.order,
        )
        val untouched = config(order = order)
        assertEquals(untouched.order, reorderSearchProviders(untouched, -1, 2).order)
        assertEquals(untouched.order, reorderSearchProviders(untouched, 0, 9).order)
    }

    // ------------------------------------------------------------ 提供商

    @Test
    fun uniqueProviderNameAppendsAnIndexOnlyWhenNeeded() {
        val existing = listOf("新提供商", "新提供商 2")
        assertEquals("新提供商", uniqueProviderName(emptyList(), "新提供商"))
        assertEquals("新提供商 3", uniqueProviderName(existing, "新提供商"))
        assertEquals("别的名字", uniqueProviderName(existing, "别的名字"))
    }

    @Test
    fun upsertUpdatesInPlaceAndAppendsNewOnes() {
        val first = AiProvider(id = "p1", name = "A", endpoint = "https://a.example.com")
        val second = AiProvider(id = "p2", name = "B", endpoint = "https://b.example.com")
        val providers = listOf(first)

        val updated = upsertProvider(providers, first.copy(name = "A2"))
        assertEquals(1, updated.size)
        assertEquals("A2", updated[0].name)

        val appended = upsertProvider(providers, second)
        assertEquals(listOf("p1", "p2"), appended.map { it.id })
    }

    @Test
    fun removeKeepsTheLastProvider() {
        val first = AiProvider(id = "p1", name = "A", endpoint = "https://a.example.com")
        val second = AiProvider(id = "p2", name = "B", endpoint = "https://b.example.com")

        assertEquals(listOf("p2"), removeProvider(listOf(first, second), "p1").map { it.id })
        // 只剩一个时不动，保证任何时候都有可用提供商
        assertEquals(listOf("p1"), removeProvider(listOf(first), "p1").map { it.id })
    }

    @Test
    fun fallbackPrefersTheCurrentProviderThenTheFirstOne() {
        val first = AiProvider(id = "p1", name = "A", endpoint = "https://a.example.com")
        val second = AiProvider(id = "p2", name = "B", endpoint = "https://b.example.com")
        val providers = listOf(first, second)

        assertEquals("p2", fallbackProvider(providers, "p2")?.id)
        assertEquals("p1", fallbackProvider(providers, "missing")?.id)
        assertEquals("p1", fallbackProvider(providers, null)?.id)
        assertNull(fallbackProvider(emptyList(), "p1"))
    }

    // ------------------------------------------------------------ 指令预设

    @Test
    fun mergePromptPresetFillsAppendsAndIsIdempotent() {
        val preset = "回答用中文。"
        assertEquals(preset, mergePromptPreset("", preset))
        assertEquals("保持简短。\n$preset", mergePromptPreset("保持简短。", preset))
        assertEquals("保持简短。\n$preset", mergePromptPreset("保持简短。\n$preset", preset))
    }
}
