package com.sukisu.ultra.ai

import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AI 助手的联网搜索入口。
 *
 * 根据 [AiSearchSettings] 里的配置选择服务：
 * - [AiSearchProvider.FIRECRAWL]：调用 Firecrawl 官方 /v1/search；
 * - [AiSearchProvider.ANYSEARCH]：调用内置的 AnySearch 官方端点（Key 可选，匿名额度更低）。
 *
 * [search] 永远返回给模型看的纯文本，不向调用方抛业务异常；协程取消除外。
 */
object AiWebSearch {

    private const val FIRECRAWL_ENDPOINT = "https://api.firecrawl.dev/v1/search"

    /** AnySearch 官方 REST 端点：用户不需要填写，随版本内置。 */
    private const val ANYSEARCH_ENDPOINT = "https://api.anysearch.com/v1/search"
    private const val MAX_RESULTS = 5
    private const val MAX_SUMMARY_CHARS = 500
    private const val MAX_OUTPUT_CHARS = 4000
    private const val MAX_ERROR_BODY_CHARS = 300

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    // 宿主 app 已开启 core library desugaring（见 manager/app/build.gradle.kts），
    // java.time.Duration 在 API 24/25 上会被脱糖；这里抑制库模块单独 lint 的 NewApi 误报。
    @Suppress("NewApi")
    private val client = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build()

    private val whitespace = Regex("\\s+")

    /**
     * 执行一次联网搜索，返回给模型看的纯文本（标题 + URL + 摘要，最多 5 条）。
     * 配置为 NONE / Firecrawl 未填 Key -> 返回提示文案而不是抛异常。
     */
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            val cleaned = query.trim()
            if (cleaned.isEmpty()) {
                return@withContext "搜索关键词为空，无法联网搜索。"
            }

            val config = AiSearchSettings.load()
            if (config.provider == AiSearchProvider.NONE) {
                return@withContext "联网搜索未启用：请在 AI 设置中选择搜索服务。"
            }
            // Firecrawl 必须鉴权；AnySearch 支持匿名访问（额度更低），Key 可留空。
            if (config.provider == AiSearchProvider.FIRECRAWL && config.apiKey.isBlank()) {
                return@withContext "Firecrawl 需要 API Key：请在 AI 设置中填写。"
            }

            val result = when (config.provider) {
                AiSearchProvider.NONE ->
                    "联网搜索未启用：请在 AI 设置中选择搜索服务。"

                AiSearchProvider.FIRECRAWL -> searchFirecrawl(cleaned, config)

                AiSearchProvider.ANYSEARCH -> searchAnySearch(cleaned, config)
            }
            result.take(MAX_OUTPUT_CHARS)
        } catch (e: CancellationException) {
            // 协程取消必须原样抛出，不能被当成普通搜索失败吞掉。
            throw e
        } catch (e: Exception) {
            ("搜索失败：${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}")
                .take(MAX_OUTPUT_CHARS)
        }
    }

    /** Firecrawl：POST /v1/search，解析 data[].title/url/description（或 markdown）。 */
    private fun searchFirecrawl(query: String, config: AiSearchConfig): String {
        val endpoint = config.endpoint.trim().ifEmpty { FIRECRAWL_ENDPOINT }
        val payload = buildJsonObject {
            put("query", query)
            put("limit", MAX_RESULTS)
        }
        val (code, responseBody) = postJson(endpoint, config.apiKey.trim(), payload.toString())
        if (code !in 200..299) {
            return httpError(code, responseBody)
        }

        val root = runCatching { json.parseToJsonElement(responseBody) as? JsonObject }
            .getOrNull()
            ?: return "没有找到相关搜索结果。"

        val data = root["data"] as? JsonArray ?: JsonArray(emptyList())
        val results = data.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item.string("url").orEmpty()
            if (url.isBlank()) return@mapNotNull null
            val markdown = item.string("markdown").orEmpty().take(MAX_SUMMARY_CHARS)
            SearchResult(
                title = item.string("title").orEmpty().ifBlank { url },
                url = url,
                snippet = item.string("description").orEmpty().ifBlank { markdown },
            )
        }
        return formatResults(results)
    }

    /**
     * AnySearch：使用内置官方端点，POST `{query, max_results}`，Key 可选。
     * 响应是 `{code, message, data: {results: [{title, url, content|snippet}]}}`；
     * 仍然兼容 `results[]` / `data[]` 等变体，解析失败按「空结果」处理而不是抛异常。
     */
    private fun searchAnySearch(query: String, config: AiSearchConfig): String {
        val payload = buildJsonObject {
            put("query", query)
            put("max_results", MAX_RESULTS)
        }
        val (code, responseBody) = postJson(ANYSEARCH_ENDPOINT, config.apiKey.trim(), payload.toString())
        if (code !in 200..299) {
            return httpError(code, responseBody)
        }

        val root = runCatching { json.parseToJsonElement(responseBody) as? JsonObject }
            .getOrNull()
            ?: return "AnySearch 返回的数据无法解析，未获得搜索结果。"

        // 业务错误码：code 非 0 时 message 是服务端给的原因（额度不足、Key 无效等）。
        val bizCode = (root["code"] as? JsonPrimitive)?.contentOrNull
        if (bizCode != null && bizCode != "0") {
            val message = root.string("message") ?: "错误码 $bizCode"
            return "AnySearch 搜索失败：$message".take(MAX_OUTPUT_CHARS)
        }

        val resultArray: JsonArray? =
            ((root["data"] as? JsonObject)?.get("results") as? JsonArray)
                ?: (root["results"] as? JsonArray)
                ?: (root["data"] as? JsonArray)
        if (resultArray == null) {
            return "AnySearch 没有返回可用的搜索结果。"
        }

        val results = resultArray.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item.string("url").orEmpty().ifBlank { item.string("link").orEmpty() }
            if (url.isBlank()) return@mapNotNull null
            SearchResult(
                title = item.string("title").orEmpty().ifBlank { url },
                url = url,
                snippet = item.string("content").orEmpty()
                    .ifBlank { item.string("snippet").orEmpty() }
                    .ifBlank { item.string("description").orEmpty() },
            )
        }
        if (results.isEmpty()) {
            return "AnySearch 没有返回可用的搜索结果。"
        }
        return formatResults(results)
    }

    /** 发一次 JSON POST；返回 HTTP 状态码与响应体原文。apiKey 为空时不带鉴权头（AnySearch 匿名）。 */
    private fun postJson(url: String, apiKey: String, body: String): Pair<Int, String> {
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        val request = builder.post(body.toRequestBody(jsonMediaType)).build()
        client.newCall(request).execute().use { response ->
            return response.code to response.body.string()
        }
    }

    private fun httpError(code: Int, body: String): String =
        "搜索失败（HTTP $code）：${body.trim().take(MAX_ERROR_BODY_CHARS)}"

    /** 统一拼成「1. 标题\nURL\n摘要」，条数不超过 [MAX_RESULTS]。 */
    private fun formatResults(results: List<SearchResult>): String {
        val valid = results.filter { it.url.isNotBlank() }
        if (valid.isEmpty()) return "没有找到相关搜索结果。"
        return valid.take(MAX_RESULTS).mapIndexed { index, item ->
            buildString {
                append(index + 1).append(". ").append(item.title.ifBlank { item.url })
                append('\n')
                append(item.url)
                val snippet = item.snippet.replace(whitespace, " ").trim()
                if (snippet.isNotEmpty()) {
                    append('\n')
                    append(snippet.take(MAX_SUMMARY_CHARS))
                }
            }
        }.joinToString("\n\n")
    }

    private data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String,
    )

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
