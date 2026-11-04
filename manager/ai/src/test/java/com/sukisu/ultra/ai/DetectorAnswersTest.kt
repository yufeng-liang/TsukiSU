package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测纯逻辑的 match/打分：不联网，条目由测试自己构造。
 * 真实的 chunqiu-answers.json 结构见 DetectorAnswer。
 */
class DetectorAnswersTest {

    private fun entry(
        id: String,
        title: String,
        chapter: String = "一、属性与系统状态",
        aliases: List<String> = emptyList(),
        detect: String = "",
        cause: String = "",
    ) = DetectorAnswer(
        id = id,
        title = title,
        aliases = aliases,
        chapter = chapter,
        detect = detect,
        cause = cause,
    )

    @Test
    fun exactTitleOutranksAliasHeavyEntry() {
        val exact = entry("a", "TEE 损坏")
        val aliasHeavy = entry(
            id = "b",
            title = "ZZZZZZ",
            chapter = "七、Keystore 与密钥证明",
            aliases = listOf("tee", "root", "magisk", "xposed", "abcd"),
        )
        // 别名条目只靠 "tee" 拿 20 分过阈值，但必须排在标题精确命中的条目之后。
        val hits = DetectorAnswers.match("TEE 损坏", listOf(aliasHeavy, exact))
        assertEquals(listOf("a", "b"), hits.map { it.id })
    }

    @Test
    fun aliasAccumulationIsCapped() {
        // 5 个 alias 全部命中：未封顶会是 100，封顶 60 后仍低于标题包含的 100。
        val aliasHeavy = entry(
            id = "b",
            title = "ZZZZZZ",
            aliases = listOf("root", "tee", "magisk", "xposed", "abcd"),
        )
        val titleHit = entry("a", "root tee")
        val hits = DetectorAnswers.match("root tee magisk xposed abcd", listOf(aliasHeavy, titleHit))
        assertEquals(listOf("a", "b"), hits.map { it.id })
        // 单靠 alias 也能过阈值，不至于因为压 alias 就完全搜不到。
        assertEquals(
            listOf("b"),
            DetectorAnswers.match("root tee magisk xposed abcd", listOf(aliasHeavy)).map { it.id },
        )
    }

    @Test
    fun aliasOnlyBelowThresholdIsDropped() {
        // 单字符 alias 不计分（数据里也存在这种短别名），标题又不沾边，应低于阈值。
        val noise = entry("a", "ZZZZZZ", aliases = listOf("a"))
        assertTrue(DetectorAnswers.match("abcd", listOf(noise)).isEmpty())
    }

    @Test
    fun blankQueryReturnsNothing() {
        assertTrue(DetectorAnswers.match("   ", listOf(entry("a", "root"))).isEmpty())
    }

    @Test
    fun foldsSameFamilyVariantsInOneChapter() {
        val entries = listOf(
            entry("a", "Mount Detector(2)", chapter = "二、挂载视图与文件系统一致性"),
            entry("b", "Mount Detector(3)", chapter = "二、挂载视图与文件系统一致性"),
            entry("c", "Mount Detector(4)", chapter = "二、挂载视图与文件系统一致性"),
        )
        assertEquals(listOf("a"), DetectorAnswers.match("Mount Detector", entries).map { it.id })
    }

    @Test
    fun keepsSimilarTitlesFromDifferentChapters() {
        val entries = listOf(
            entry("a", "Miscellaneous Check(2)", chapter = "十、其它"),
            entry("b", "Miscellaneous Check(4/5/6/7/8/9)", chapter = "二、挂载视图与文件系统一致性"),
        )
        assertEquals(
            listOf("a", "b"),
            DetectorAnswers.match("Miscellaneous Check", entries).map { it.id },
        )
    }

    @Test
    fun capsResultsPerChapter() {
        val entries = listOf(
            entry("a", "属性异常 A", aliases = listOf("属性异常")),
            entry("b", "属性异常 B", aliases = listOf("属性异常")),
            entry("c", "属性异常 C", aliases = listOf("属性异常")),
        )
        assertEquals(2, DetectorAnswers.match("属性异常", entries).size)
    }

    @Test
    fun respectsLimit() {
        val entries = (0..5).map { index ->
            entry("id$index", "红项$index", chapter = "章节$index", aliases = listOf("红项"))
        }
        assertEquals(3, DetectorAnswers.match("红项", entries, limit = 3).size)
        assertEquals(5, DetectorAnswers.match("红项", entries).size)
    }
}
