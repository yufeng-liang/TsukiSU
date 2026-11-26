package com.sukisu.ultra.ai.host

import com.sukisu.ultra.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设置页深链与页面标题的纯逻辑回归：section 归一化和标题分段翻译。
 * 不依赖 Compose，也不启动 Activity。
 */
class AiFeatureRoutingTest {

    @Test
    fun normalizeSectionAcceptsCamelCaseAndSeparators() {
        assertEquals("read_scope", AiFeatureCatalog.normalizeSection(" readScope "))
        assertEquals("write_scope", AiFeatureCatalog.normalizeSection("write-scope"))
        assertEquals("write_scope", AiFeatureCatalog.normalizeSection("write scope"))
        assertEquals("behavior", AiFeatureCatalog.normalizeSection("behavior"))
    }

    @Test
    fun displayTitleUsesSegmentResourcesAndKeepsUnknownText() {
        val ids = mutableListOf<Int>()
        val resolved = AiFeatureTitles.displayTitle("Appearance · appearance") { id ->
            ids += id
            "T${ids.size}"
        }
        assertEquals("T1 · T2", resolved)
        assertEquals(listOf(R.string.ai_page_appearance, R.string.ai_page_icon_text), ids)
        assertEquals("Unknown page", AiFeatureTitles.displayTitle("Unknown page") { "X" })
    }

    @Test
    fun displayTitleHandlesSuSfsPrefix() {
        assertEquals("SuSFS X", AiFeatureTitles.displayTitle("SuSFS basic settings") { "X" })
    }
}