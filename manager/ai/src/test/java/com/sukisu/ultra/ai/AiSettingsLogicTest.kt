package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun nextNumberedNameAlwaysCarriesAnIndexAndFillsTheFirstFreeOne() {
        assertEquals("提供商 1", nextNumberedName(emptyList(), "提供商 %1\$d"))
        assertEquals("提供商 2", nextNumberedName(listOf("提供商 1", "提供商 3"), "提供商 %1\$d"))
        assertEquals("会话 1", nextNumberedName(listOf("", "标题"), "会话 %1\$d"))
    }

    @Test
    fun visibleSessionsHidesBlankOnesButKeepsTheActiveOne() {
        val filled = AiSession(id = "s1", title = "会话 1", turns = listOf(AiTurn("user", "hi")))
        val blankOld = AiSession(id = "s2", title = "会话 2")
        val activeBlank = AiSession(id = "s3", title = "会话 3")
        val all = listOf(filled, blankOld, activeBlank)

        // 历史空会话不展示，当前这个空的要留着
        assertEquals(listOf("s1", "s3"), visibleSessions(all, "s3").map { it.id })
        // 全部有内容时一个不少
        val allFilled = all.map { it.copy(turns = listOf(AiTurn("user", "hi"))) }
        assertEquals(listOf("s1", "s2", "s3"), visibleSessions(allFilled, "s3").map { it.id })
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

    @Test
    fun togglePromptPresetAddsThenRemovesTheSameText() {
        val preset = "回答用中文。"
        val added = togglePromptPreset("保持简短。", preset)
        assertEquals("保持简短。\n$preset", added)
        assertTrue(hasPromptPreset(added, preset))
        // 再点一下：连模板带回的换行一并撤掉，不留下空行。
        assertEquals("保持简短。", togglePromptPreset(added, preset))
        assertFalse(hasPromptPreset("保持简短。", preset))
    }

    @Test
    fun removePromptPresetKeepsTheRestIntact() {
        val first = "回答用中文。"
        val second = "多用例子。"
        val both = mergePromptPreset(mergePromptPreset("", first), second)
        assertEquals(second, removePromptPreset(both, first))
        assertEquals("", removePromptPreset(first, first))
        // 用户把模板改过之后，删不掉也不能炸，原样返回。
        assertEquals("回答用中文", removePromptPreset("回答用中文", first))
    }
}
