package com.sukisu.ultra.ai

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资产 + 匹配 + 输出格式的回归。纯 JVM：只碰 [AssistantGuide.parse] / matchIn / searchIn，
 * 不构造 Context（load() 需要 assets，留给真机与构建期校验）。
 */
class AssistantGuideTest {

    private val projectDir: File by lazy { findProjectDir() }
    private val assetPath: File by lazy { projectDir.resolve("src/main/assets/ai/assistant-guide.json") }
    private val questionPath: File by lazy { projectDir.resolve("src/test/resources/guide-questions.json") }

    private val entries by lazy { AssistantGuide.parse(assetPath.readText()) }

    /**
     * Gradle 单测的工作目录可能是模块目录，也可能被 IDE 或聚合任务改到仓库根；
     * 从 user.dir 向上最多找 4 层，定位包含指南资产的模块根目录。
     */
    private fun findProjectDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var depth = 0
        while (dir != null && depth <= 4) {
            if (File(dir, "src/main/assets/ai/assistant-guide.json").isFile) return dir
            dir = dir.parentFile
            depth++
        }
        error(
            "Cannot find manager/ai from user.dir=" + System.getProperty("user.dir") +
                "; expected src/main/assets/ai/assistant-guide.json within 4 parent levels",
        )
    }

    /** specsFor 之外、由执行层直接接线的常量工具名。 */
    private val constantTools = setOf(
        "ask_user",
        "run_shell",
        "search_detector_answers",
        "mcp_list",
        "mcp_call",
        "column_wizard",
        "column_wizard_run",
        "search_assistant_guide",
        "suggest_setting",
    )

    private val specTools = AiTools.specsFor(AiConfig()).map { it.name }.toSet()

    private val configFields = AiConfig::class.java.declaredFields.map { it.name }.toSet()

    private val booleanFields = AiConfig::class.java.declaredFields
        .filter { it.type == java.lang.Boolean.TYPE }
        .map { it.name }
        .toSet()

    // 1. 资产能解析、version == 2、条目数 ≥ 26、id 唯一、topic ∈ 五类、双语齐备。
    @Test
    fun assetParsesAndStaysWellFormed() {
        val root = Json.parseToJsonElement(assetPath.readText()).jsonObject
        assertEquals(AssistantGuide.VERSION, root["version"]?.jsonPrimitive?.int ?: -1)

        assertTrue("expected at least 26 entries, got " + entries.size, entries.size >= 26)
        assertEquals("duplicate ids", entries.size, entries.map { it.id }.toSet().size)

        val allowedTopics = AssistantGuide.TOPICS
        assertEquals(allowedTopics.sorted(), entries.map { it.topic }.distinct().sorted())
        assertTrue(entries.all { it.id.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")) })
        assertTrue(entries.all { it.title.en.isNotBlank() && it.title.zh.isNotBlank() })
        assertTrue(entries.all { it.body.en.isNotBlank() && it.body.zh.isNotBlank() })

        assertEquals("zh body shorter than 200 chars", emptyList<String>(), entries.filter { it.body.zh.length < 200 }.map { it.id })
        assertEquals("zh body longer than 400 chars", emptyList<String>(), entries.filter { it.body.zh.length > 400 }.map { it.id })
    }

    // 2. related 里的 id 全部存在。
    @Test
    fun relatedIdsResolve() {
        val ids = entries.map { it.id }.toSet()
        val dangling = entries.flatMap { entry -> entry.related.filter { it !in ids }.map { entry.id + " -> " + it } }
        assertEquals("dangling related ids: " + dangling, emptyList<String>(), dangling)
    }

    // 3. covers.tools ⊆ 工具表；且工具表里每个名字都被至少一条覆盖。
    @Test
    fun toolCoversMatchTheToolTable() {
        val allowed = specTools + constantTools
        val unknown = entries.flatMap { entry -> entry.coversTools.filter { it !in allowed }.map { entry.id + " -> " + it } }
        assertEquals("unknown tool names: " + unknown, emptyList<String>(), unknown)

        val covered = entries.flatMap { it.coversTools }.toSet()
        val uncovered = specTools.filter { it !in covered }.sorted()
        assertEquals("specsFor tools with no guide entry: " + uncovered, emptyList<String>(), uncovered)
    }

    // 4. covers.settings ⊆ AiConfig 真实字段；且每个布尔开关都被至少一条覆盖。
    @Test
    fun settingCoversMatchConfigFields() {
        val unknown = entries.flatMap { entry -> entry.coversSettings.filter { it !in configFields }.map { entry.id + " -> " + it } }
        assertEquals("unknown AiConfig fields: " + unknown, emptyList<String>(), unknown)

        assertTrue("no boolean field detected on AiConfig", booleanFields.isNotEmpty())
        val covered = entries.flatMap { it.coversSettings }.toSet()
        val uncovered = booleanFields.filter { it !in covered }.sorted()
        assertEquals("boolean switches with no guide entry: " + uncovered, emptyList<String>(), uncovered)
    }

    // 5. covers 里点名的工具 / 设置键必须真的在正文里出现，防止「声明覆盖但正文没写」。
    @Test
    fun coversNamesAppearInBodyText() {
        val missing = entries.flatMap { entry ->
            (entry.coversTools + entry.coversSettings)
                .filter { name -> !entry.body.en.contains(name) && !entry.body.zh.contains(name) }
                .map { entry.id + " -> " + it }
        }
        assertEquals("covers names missing from body text: " + missing, emptyList<String>(), missing)
    }

    // 6a. 中文 query 命中中文别名条目；topic 过滤与 limit 生效。
    @Test
    fun matchingPrefersLocalizedAliases() {
        val hits = AssistantGuide.matchIn("怎么改主题颜色", entries, limit = 4)
        assertTrue("expected the appearance entry in " + hits, hits.map { it.id }.contains("capabilities-appearance"))
        assertEquals("capabilities-appearance", hits.first().id)

        val all = AssistantGuide.matchIn("root 权限开关", entries, limit = 20)
        val settingsOnly = AssistantGuide.matchIn("root 权限开关", entries, topic = "settings", limit = 20)
        assertTrue("topic filter must keep settings entries", settingsOnly.isNotEmpty())
        assertTrue("topic filter leaked: " + settingsOnly, settingsOnly.all { it.topic == "settings" })
        assertTrue("topic filter must narrow results", all.size > settingsOnly.size)

        assertEquals(1, AssistantGuide.matchIn("root 权限开关", entries, limit = 1).size)
    }

    // 6b. 空 query 返回 0 命中文本；非法 topic 抛 IllegalArgumentException。
    @Test
    fun emptyQueryAndUnknownTopic() {
        val text = AssistantGuide.searchIn("   ", entries, zh = true)
        assertEquals(
            "<guide version=\"2\" topic=\"all\" hits=\"0\">No matching entry. " +
                "Known topics: overview, capabilities, settings, tools, troubleshooting.</guide>",
            text,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            AssistantGuide.matchIn("主题", entries, topic = "bogus")
        }
        assertTrue(error.message.orEmpty().contains("Unknown topic"))
        assertTrue(error.message.orEmpty().contains("Use one of:"))
    }

    // 6c. 输出格式：<guide version="2" …、条目行含 [id] 与 tools 行、topic 透传到属性。
    @Test
    fun searchOutputIsWrapped() {
        val zh = AssistantGuide.searchIn("主题颜色", entries, zh = true)
        assertTrue(zh.startsWith("<guide version=\"2\" topic=\"all\" hits=\""))
        assertTrue(zh.trimEnd().endsWith("</guide>"))
        assertTrue(zh.contains("] 外观、主题与自定义文案 (topic: capabilities)"))
        assertTrue(zh.contains("related: "))

        val en = AssistantGuide.searchIn("theme colors", entries, zh = false)
        assertTrue(en.startsWith("<guide version=\"2\""))
        assertTrue(en.contains("] Appearance, theme and custom text (topic: capabilities)"))

        val scoped = AssistantGuide.searchIn("root 权限开关", entries, topic = "settings", zh = true)
        assertTrue(scoped.startsWith("<guide version=\"2\" topic=\"settings\" hits=\""))
    }

    // 6d. 每条命中都带 tools 行（仅当 covers.tools 非空）；零命中时给最近标题。
    @Test
    fun toolsLineAndClosestFallback() {
        val text = AssistantGuide.searchIn("run shell command", entries, topic = "capabilities", limit = 1, zh = false)
        assertTrue("expected a tools line in " + text, text.contains("tools: "))

        val fallback = AssistantGuide.render(emptyList(), null, false, listOf(entries.first()))
        assertTrue(fallback.contains("No matching entry."))
        assertTrue(fallback.contains("Closest: ["))
    }

    // 6e. limit 真的截断条数。
    @Test
    fun limitCapsHowManyEntriesComeBack() {
        val one = AssistantGuide.searchIn("主题颜色", entries, limit = 1, zh = true)
        assertEquals(1, Regex("\\n\\d+\\. \\[").findAll(one).count())
    }

    // 6f. 语种判断：假名 → 英文；汉字 → 中文。
    @Test
    fun outputLanguageFollowsTheQuery() {
        assertFalse(AssistantGuide.preferChineseFor("こんにちは、AIで何ができる？"))
        assertTrue(AssistantGuide.preferChineseFor("你能干什么"))
    }

    // 7. 每条条目的中英标题都能把自己检索出来：top-1 ≥90%，top-3 100%。
    @Test
    fun selfTitlesRetrieveTheirOwnEntry() {
        val zhTop1 = entries.count { entry -> AssistantGuide.matchIn(entry.title.zh, entries, limit = 3).firstOrNull()?.id == entry.id }
        val enTop1 = entries.count { entry -> AssistantGuide.matchIn(entry.title.en, entries, limit = 3).firstOrNull()?.id == entry.id }
        val zhTop3 = entries.count { entry -> AssistantGuide.matchIn(entry.title.zh, entries, limit = 3).any { it.id == entry.id } }
        val enTop3 = entries.count { entry -> AssistantGuide.matchIn(entry.title.en, entries, limit = 3).any { it.id == entry.id } }
        assertTrue("zh title top-1 " + zhTop1 + "/" + entries.size, zhTop1 * 100 >= entries.size * 90)
        assertTrue("en title top-1 " + enTop1 + "/" + entries.size, enTop1 * 100 >= entries.size * 90)
        assertEquals("zh title top-3", entries.size, zhTop3)
        assertEquals("en title top-3", entries.size, enTop3)
    }

    // 8. 30 条真实问法语料：top-1 ≥90%，top-3 100%。
    @Test
    fun realQuestionsLandInTheRightEntry() {
        val root = Json.parseToJsonElement(questionPath.readText()).jsonObject
        val questions = root["questions"]?.jsonArray ?: error("guide-questions.json has no questions array")
        var top1 = 0
        val top3Misses = mutableListOf<String>()
        val top1Misses = mutableListOf<String>()
        questions.forEach { element ->
            val item = element.jsonObject
            val query = item["query"]?.jsonPrimitive?.content.orEmpty()
            val expected = item["expect"]?.jsonPrimitive?.content.orEmpty()
            val hits = AssistantGuide.matchIn(query, entries, limit = 3).map { it.id }
            if (hits.firstOrNull() == expected) {
                top1++
            } else {
                top1Misses += query + " -> " + hits + " expected " + expected
            }
            if (expected !in hits) top3Misses += query + " -> " + hits + " expected " + expected
        }
        assertTrue("top-3 misses: " + top3Misses, top3Misses.isEmpty())
        assertTrue(
            "top-1 " + top1 + "/" + questions.size + ", misses: " + top1Misses,
            top1 * 100 >= questions.size * 90,
        )
    }
}
