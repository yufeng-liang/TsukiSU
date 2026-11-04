package com.sukisu.ultra.ai

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** One red-flag entry of the community detector troubleshooting reference. */
internal data class DetectorAnswer(
    val id: String,
    val title: String,
    val aliases: List<String> = emptyList(),
    val chapter: String = "",
    val detect: String = "",
    val cause: String = "",
    val fix: List<String> = emptyList(),
    val deviceTypes: List<String> = emptyList(),
    val link: String = "",
)

/**
 * Fetches and caches the "detector red-flag solutions" reference published by the
 * module index, then matches the wording of a detector's red flag against entry
 * titles.
 *
 * The reference is third-party documentation: entries can lag behind the latest
 * detector build, so every result carries its source link and the model is told
 * to cite it instead of inventing an entry.
 */
internal object DetectorAnswers {

    private const val TAG = "DetectorAnswers"

    /** Same CDN mirror as the module index; no credentials, no per-user data. */
    private const val ANSWERS_URL =
        "https://cdn.jsdelivr.net/gh/SukiSU-Community/modules-repo@main/json/chunqiu-answers.json"

    private const val MAX_BODY_BYTES = 4L * 1024 * 1024
    private const val CACHE_TTL_MS = 10L * 60 * 1000
    private const val MAX_RESULTS = 5
    private const val MAX_FIELD_CHARS = 400

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: List<DetectorAnswer>? = null

    @Volatile
    private var cachedAt: Long = 0L

    /** Returns a model-facing summary of the best matching entries. */
    suspend fun search(query: String): String {
        val entries = load()
        if (entries.isEmpty()) {
            return "The detector reference is unavailable right now (network or CDN failure). " +
                "Say so plainly and answer from general knowledge, clearly marked as not " +
                "coming from the reference."
        }
        val hits = match(query, entries)
        if (hits.isEmpty()) {
            return "No entry matches \"$query\". Ask the user for the detector's exact red-flag " +
                "wording, or answer from general knowledge and say the reference had no entry."
        }
        return buildString {
            appendLine("Reference matches for \"$query\":")
            hits.forEachIndexed { index, entry ->
                appendLine()
                appendLine("${index + 1}. ${entry.title}${chapterSuffix(entry)}")
                if (entry.detect.isNotBlank()) appendLine("   detection: ${short(entry.detect)}")
                if (entry.cause.isNotBlank()) appendLine("   cause: ${short(entry.cause)}")
                val fixText = entry.fix.filter { it.isNotBlank() }.joinToString(" / ")
                if (fixText.isNotBlank()) appendLine("   fix: ${short(fixText)}")
                if (entry.deviceTypes.isNotEmpty()) {
                    appendLine("   applies to: ${entry.deviceTypes.joinToString(", ")}")
                }
                if (entry.link.isNotBlank()) appendLine("   source: ${entry.link}")
            }
            append("Cite these entries by title and source. Never invent an entry that is not listed.")
        }
    }

    private fun chapterSuffix(entry: DetectorAnswer): String =
        if (entry.chapter.isBlank()) "" else "  [${entry.chapter}]"

    private fun short(value: String): String =
        if (value.length <= MAX_FIELD_CHARS) value else value.take(MAX_FIELD_CHARS) + "…"

    private suspend fun load(): List<DetectorAnswer> {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < CACHE_TTL_MS) return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(ANSWERS_URL).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "answers request failed: HTTP ${response.code}")
                        return@use emptyList()
                    }
                    val body = response.peekBody(MAX_BODY_BYTES).string()
                    parse(body)
                }
            }.onFailure { error ->
                Log.w(TAG, "answers request failed: ${error.message}")
            }.getOrDefault(emptyList()).also { fresh ->
                if (fresh.isNotEmpty()) {
                    cached = fresh
                    cachedAt = now
                }
            }
        }
    }

    private fun parse(body: String): List<DetectorAnswer> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        val array = root["entries"]?.jsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching {
                val item = element.jsonObject
                val id = item.string("id")
                val title = item.string("title")
                if (id.isBlank() || title.isBlank()) return@runCatching null
                DetectorAnswer(
                    id = id,
                    title = title,
                    aliases = item.stringList("aliases"),
                    chapter = item.string("chapter"),
                    detect = item.string("detect"),
                    cause = item.string("cause"),
                    fix = item.stringList("fix"),
                    deviceTypes = item.stringList("deviceTypes"),
                    link = item.string("link"),
                )
            }.getOrNull()
        }
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty().trim()

    private fun JsonObject.stringList(key: String): List<String> {
        val array = this[key]?.let { element -> runCatching { element.jsonArray }.getOrNull() }
            ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
        }
    }

    /**
     * Scores entries against the detector's own wording.
     *
     * Titles are the detector's red-flag names, so an exact containment match is
     * the strongest signal; aliases carry the attribute/package names, and body
     * keywords are a weak fallback. Entries below the threshold are dropped rather
     * than padded out - five plausible entries are worse than one correct one.
     */
    fun match(query: String, entries: List<DetectorAnswer>, limit: Int = MAX_RESULTS): List<DetectorAnswer> {
        val normalized = query.lowercase().replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        return entries.map { entry -> entry to score(normalized, entry) }
            .filter { it.second >= 20 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun score(query: String, entry: DetectorAnswer): Int {
        var total = 0
        val title = entry.title.lowercase()
        if (title.isNotBlank() && query.contains(title)) total += 100
        for (alias in entry.aliases) {
            val key = alias.lowercase().trim()
            if (key.length >= 2 && query.contains(key)) total += 30
        }
        // Fragment match for titles that are longer than what the user pasted.
        if (title.length >= 2 && !query.contains(title)) {
            var fragments = 0
            var index = 0
            while (index + 2 <= title.length && fragments < 8) {
                if (query.contains(title.substring(index, index + 2))) fragments++
                index += 2
            }
            total += fragments * 5
        }
        val body = (entry.detect + " " + entry.cause).lowercase()
        if (body.isNotBlank()) {
            var hits = 0
            for (word in query.split(' ', '，', '。', ',').filter { it.length >= 3 }) {
                if (body.contains(word)) hits++
            }
            total += (hits * 2).coerceAtMost(10)
        }
        return total
    }
}
