package com.sukisu.ultra.ai

import android.content.Context
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 一条双语文本：en / zh 各一份，按设备语言取用。
 */
internal data class GuideText(val en: String, val zh: String) {
    fun resolve(zh: Boolean): String = if (zh) this.zh else this.en
}

/**
 * 内置助手指南的一条条目。
 *
 * 资产结构见 manager/ai/src/main/assets/ai/assistant-guide.json：title / aliases / body 都是 en + zh 双语，
 * covers 声明这条条目覆盖了哪些工具名与 AiConfig 字段，供单测做覆盖率回归。
 */
internal data class AssistantGuideEntry(
    val id: String,
    val topic: String,
    val title: GuideText,
    val aliasesEn: List<String>,
    val aliasesZh: List<String>,
    val body: GuideText,
    val related: List<String>,
    val coversTools: List<String>,
    val coversSettings: List<String>,
)

/**
 * 「助手用户指南」：随 App 打包的内置知识资产 + 关键词检索。
 *
 * 资产是自家只读文档，不联网、不包 <untrusted>；[search] 的返回值就是给模型看的文本，格式固定：
 *
 * <guide version="2" topic="all" hits="2">
 * 1. [capabilities-overview] 助手能做什么 (topic: capabilities)
 * …
 * </guide>
 *
 * 匹配复用 [KeywordMatch]：中英两套文本各打一次分取较高者，同分按 id 升序保证稳定。
 */
internal object AssistantGuide {
    const val ASSET_NAME = "ai/assistant-guide.json"
    const val VERSION = 2
    val TOPICS: List<String> = listOf("overview", "capabilities", "settings", "tools", "troubleshooting")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 进程内缓存：资产只读，解析一次就够。 */
    @Volatile
    private var cached: List<AssistantGuideEntry>? = null

    /** 读 assets；带进程内缓存。 */
    fun load(context: Context = aiContext): List<AssistantGuideEntry> {
        cached?.takeIf { it.isNotEmpty() }?.let { return it }
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        return parse(text).also { if (it.isNotEmpty()) cached = it }
    }

    /** 纯解析，供单测直接喂字符串。 */
    internal fun parse(body: String): List<AssistantGuideEntry> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val array = runCatching { root["entries"]?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching {
                val item = element.jsonObject
                val id = item.string("id")
                val topic = item.string("topic")
                val title = item.guideText("title")
                val text = item.guideText("body")
                if (id.isBlank() || topic !in TOPICS) return@runCatching null
                if (title.en.isBlank() || title.zh.isBlank() || text.en.isBlank() || text.zh.isBlank()) {
                    return@runCatching null
                }
                val aliases = item.objectOrNull("aliases")
                val covers = item.objectOrNull("covers")
                AssistantGuideEntry(
                    id = id,
                    topic = topic,
                    title = title,
                    aliasesEn = aliases?.stringList("en").orEmpty(),
                    aliasesZh = aliases?.stringList("zh").orEmpty(),
                    body = text,
                    related = item.stringList("related"),
                    coversTools = covers?.stringList("tools").orEmpty(),
                    coversSettings = covers?.stringList("settings").orEmpty(),
                )
            }.getOrNull()
        }
    }

    /** 纯匹配，供单测直接喂条目。topic 为空 = 全部。 */
    internal fun matchIn(
        query: String,
        entries: List<AssistantGuideEntry>,
        topic: String? = null,
        limit: Int = 4,
    ): List<AssistantGuideEntry> {
        val wanted = requireTopic(topic)
        val normalized = KeywordMatch.normalize(query)
        if (normalized.isEmpty() || limit <= 0) return emptyList()
        val weights = KeywordWeights()
        val scored = entries
            .filter { wanted == null || it.topic == wanted }
            .map { entry -> entry to scoreOf(normalized, entry, weights) }
            .filter { (_, score) -> score >= weights.minScore }
        return scored
            .sortedWith(
                compareByDescending<Pair<AssistantGuideEntry, Int>> { it.second }
                    .thenBy { it.first.id },
            )
            .take(limit)
            .map { it.first }
    }

    fun match(query: String, topic: String? = null, limit: Int = 4): List<AssistantGuideEntry> =
        matchIn(query, load(), topic, limit)

    /** 零命中兜底：按分数取最接近的标题（不设阈值），给模型一点换词线索。 */
    internal fun nearest(
        query: String,
        entries: List<AssistantGuideEntry>,
        topic: String? = null,
        count: Int = 2,
    ): List<AssistantGuideEntry> {
        val wanted = requireTopic(topic)
        val normalized = KeywordMatch.normalize(query)
        if (normalized.isEmpty()) return emptyList()
        val weights = KeywordWeights()
        return entries
            .filter { wanted == null || it.topic == wanted }
            .map { entry -> entry to scoreOf(normalized, entry, weights) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<AssistantGuideEntry, Int>> { it.second }
                    .thenBy { it.first.id },
            )
            .take(count)
            .map { it.first }
    }

    /** 给模型的文本。limit 由工具层限制在 1..8。 */
    fun search(query: String, topic: String? = null, limit: Int = 4): String =
        searchIn(query, load(), topic, limit)

    /** [search] 的纯函数内核：单测喂条目即可验证格式，不必构造 Context。 */
    internal fun searchIn(
        query: String,
        entries: List<AssistantGuideEntry>,
        topic: String? = null,
        limit: Int = 4,
        zh: Boolean = preferChineseFor(query),
    ): String {
        val hits = matchIn(query, entries, topic, limit)
        val closest = if (hits.isEmpty()) nearest(query, entries, topic) else emptyList()
        return render(hits, requireTopic(topic), zh, closest)
    }

    /** 严格遵循契约的输出格式；0 命中时给最近标题提示。 */
    internal fun render(
        hits: List<AssistantGuideEntry>,
        topic: String?,
        zh: Boolean,
        closest: List<AssistantGuideEntry> = emptyList(),
    ): String {
        val header = "<guide version=\"" + VERSION + "\" topic=\"" + (topic ?: "all") + "\" hits=\"" + hits.size + "\">"
        if (hits.isEmpty()) {
            val hint = if (closest.isEmpty()) {
                ""
            } else {
                " Closest: " + closest.joinToString("; ") { "[" + it.id + "] " + it.title.resolve(zh) } + "."
            }
            return header + "No matching entry." + hint + " Known topics: " + TOPICS.joinToString(", ") + ".</guide>"
        }
        return buildString {
            appendLine(header)
            hits.forEachIndexed { index, entry ->
                append(index + 1)
                append(". [")
                append(entry.id)
                append("] ")
                append(entry.title.resolve(zh))
                append(" (topic: ")
                append(entry.topic)
                append(")\n")
                appendLine(entry.body.resolve(zh))
                if (entry.coversTools.isNotEmpty()) {
                    appendLine("tools: " + entry.coversTools.joinToString(", "))
                }
                if (entry.related.isNotEmpty()) {
                    appendLine("related: " + entry.related.joinToString(", "))
                }
            }
            append("</guide>")
        }
    }

    internal fun preferChinese(): Boolean = Locale.getDefault().language == "zh"

    /**
     * 指南输出语种优先看 query 本身：假名说明用户在写日文，项目没有日文文案就退回英文（默认语言）；
     * 汉字占字母数 ≥15% 视为中文；其余退回系统语言。
     */
    internal fun preferChineseFor(query: String): Boolean {
        if (query.any { it in '\u3040'..'\u30FF' || it in '\uFF66'..'\uFF9D' }) return false
        val letters = query.count { it.isLetter() }
        if (letters == 0) return preferChinese()
        val han = query.count { it in '\u4E00'..'\u9FFF' }
        return if (han * 100 >= letters * 15) true else preferChinese()
    }

    /** 校验 topic：空 = 全部；非法值直接抛，避免模型把拼错的 topic 当成「没结果」。 */
    internal fun requireTopic(topic: String?): String? {
        val wanted = topic?.trim().orEmpty()
        if (wanted.isEmpty()) return null
        if (wanted !in TOPICS) {
            throw IllegalArgumentException(
                "Unknown topic \"" + wanted + "\". Use one of: " + TOPICS.joinToString(", "),
            )
        }
        return wanted
    }

    /** 中英两套文本各打一次分，取较高者：任一语言命中都算命中。 */
    private fun scoreOf(query: String, entry: AssistantGuideEntry, weights: KeywordWeights): Int = maxOf(
        KeywordMatch.score(query, entry.title.en, entry.aliasesEn, entry.body.en, weights),
        KeywordMatch.score(query, entry.title.zh, entry.aliasesZh, entry.body.zh, weights),
    )

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        this[key]?.let { runCatching { it.jsonObject }.getOrNull() }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty().trim()

    private fun JsonObject.guideText(key: String): GuideText {
        val node = objectOrNull(key) ?: return GuideText("", "")
        return GuideText(node.string("en"), node.string("zh"))
    }

    private fun JsonObject.stringList(key: String): List<String> {
        val array = this[key]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
        return array
            .mapNotNull { item -> runCatching { item.jsonPrimitive.contentOrNull }.getOrNull() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
