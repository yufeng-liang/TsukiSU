package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KeywordMatch 纯逻辑回归：权重层级、同分稳定性、分组去重与 limit。
 * 不依赖 Android，也不读资产文件。
 */
class KeywordMatchTest {

    private data class Item(
        val id: String,
        val title: String,
        val aliases: List<String> = emptyList(),
        val body: String = "",
        val group: String = "g",
    )

    private fun rank(query: String, items: List<Item>, limit: Int = 10): List<String> =
        KeywordMatch.rank(
            query = query,
            items = items,
            limit = limit,
            title = { it.title },
            aliases = { it.aliases },
            body = { it.body },
            group = { it.group },
            id = { it.id },
        ).map { it.id }

    @Test
    fun scoreOrderingIsTitleExactThenContainThenAliasThenBody() {
        val query = "主题颜色"
        assertEquals(200, KeywordMatch.score(query, "主题颜色"))
        assertEquals(100, KeywordMatch.score("怎么改" + query, "主题颜色"))
        assertEquals(20, KeywordMatch.score("怎么改" + query, "别的标题", listOf("主题颜色")))
        assertEquals(2, KeywordMatch.score(query, "别的标题", emptyList(), "这里讲主题颜色"))
        assertTrue(
            KeywordMatch.score(query, "主题颜色") >
                KeywordMatch.score("怎么改" + query, "主题颜色"),
        )
        assertTrue(
            KeywordMatch.score("怎么改" + query, "主题颜色") >
                KeywordMatch.score("怎么改" + query, "别的标题", listOf("主题颜色")),
        )
        assertTrue(
            KeywordMatch.score("怎么改" + query, "别的标题", listOf("主题颜色")) >
                KeywordMatch.score(query, "别的标题", emptyList(), "这里讲主题颜色"),
        )
    }

    @Test
    fun rankKeepsInputOrderForEqualScores() {
        val first = Item("a", "甲标题", aliases = listOf("主题颜色"))
        val second = Item("b", "乙标题", aliases = listOf("主题颜色"))
        assertEquals(listOf("a", "b"), rank("主题颜色", listOf(first, second)))
        assertEquals(listOf("b", "a"), rank("主题颜色", listOf(second, first)))
    }

    @Test
    fun rankDropsSimilarTitlesWithinSameGroup() {
        val first = Item("a", "这是一个测试条目（1）", aliases = listOf("测试条目"))
        val duplicate = Item("b", "这是一个测试条目（2）", aliases = listOf("测试条目"))
        val other = Item("c", "另一个完全不同的条目（1）", aliases = listOf("测试条目"))
        assertEquals(listOf("a", "c"), rank("测试条目", listOf(first, duplicate, other)))
    }

    @Test
    fun rankCapsGroupAndRespectsLimit() {
        val items = listOf(
            Item("a", "甲", aliases = listOf("测试条目")),
            Item("b", "乙", aliases = listOf("测试条目")),
            Item("c", "丙", aliases = listOf("测试条目")),
        )
        assertEquals(listOf("a", "b"), rank("测试条目", items))
        assertEquals(listOf("a"), rank("测试条目", items, limit = 1))
    }
}
