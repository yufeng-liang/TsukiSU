package com.sukisu.ultra.ai

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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

/** 单次服务商请求的默认超时；自动故障转移链会用剩余总预算把它收紧。 */
internal const val PROVIDER_TIMEOUT_MS = 8_000L

/** 解析成功但一条结果都没有时的提示文案，供 Controller 复用。 */
internal const val NO_WEB_RESULT_TEXT = "没有找到相关搜索结果。"

/** 各服务商解析后的统一搜索结果。 */
internal data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

/** 单个服务商的一次搜索尝试结果。 */
internal sealed interface ProviderOutcome {

    /** 请求成功。[results] 可能为空（解析成功但确实没有结果），这也算成功、不切换下一家。 */
    data class Success(val results: List<SearchResult>) : ProviderOutcome

    /** [message] 是不带服务商前缀的可读中文原因，由调用方决定是否拼上服务商名字。 */
    data class Failure(val message: String) : ProviderOutcome
}

/** 抓取一页网页的结果：成功给正文纯文本，失败给可读原因。 */
internal sealed interface PageOutcome {

    data class Success(val text: String) : PageOutcome

    data class Failure(val message: String) : PageOutcome
}

/** 抓回来的正文最多回给模型多少字，避免一次把上下文塞满。 */
internal const val MAX_PAGE_CHARS = 12_000

/** 设置页「测试」按钮的结果：成功带耗时与条数，失败带可读原因（已截断）。 */
internal sealed interface AiSearchTestResult {

    data class Success(
        val provider: AiSearchProvider,
        val latencyMs: Long,
        val results: List<SearchResult>,
    ) : AiSearchTestResult {
        val count: Int get() = results.size
    }

    data class Failure(
        val provider: AiSearchProvider,
        val message: String,
    ) : AiSearchTestResult
}

/**
 * AI 助手的单个搜索服务商执行入口。
 *
 * 只负责「用这一家发一次请求并解析」，不做开关判断、不做故障转移；编排在
 * [AiWebController.searchWeb] 里。所有失败都以 [ProviderOutcome.Failure] 返回，
 * 不向调用方抛业务异常（协程取消除外）。
 */
object AiWebSearch {

    private const val FIRECRAWL_ENDPOINT = "https://api.firecrawl.dev/v1/search"

    /** AnySearch 官方 REST 端点：用户不需要填写，随版本内置。 */
    private const val ANYSEARCH_ENDPOINT = "https://api.anysearch.com/v1/search"

    /** Tavily 官方搜索端点。 */
    private const val TAVILY_ENDPOINT = "https://api.tavily.com/search"

    /** 腾讯云元宝搜索端点（服务 API KEY 方式）。 */
    private const val YUANBAO_ENDPOINT = "https://api.wsa.cloud.tencent.com/SearchPro"

    /** Firecrawl 单页抓取端点。 */
    private const val FIRECRAWL_SCRAPE_ENDPOINT = "https://api.firecrawl.dev/v1/scrape"

    /** Tavily 正文提取端点。 */
    private const val TAVILY_EXTRACT_ENDPOINT = "https://api.tavily.com/extract"

    /** AnySearch 正文提取端点。 */
    private const val ANYSEARCH_EXTRACT_ENDPOINT = "https://api.anysearch.com/v1/extract"
    private const val MAX_RESULTS = 5
    private const val MAX_SUMMARY_CHARS = 900
    private const val MAX_OUTPUT_CHARS = 5000
    private const val MAX_ERROR_BODY_CHARS = 300
    private const val MIN_PROVIDER_TIMEOUT_MS = 1_000L

    /** 抓取/接口响应的原始字节上限：防止超大页面把内存打满。 */
    private const val MAX_RESPONSE_BYTES = 512 * 1024

    /** HTML 抓取最多跟随的重定向次数；每一跳都要重新做 SSRF 校验。 */
    private const val MAX_HTML_REDIRECTS = 5

    /** 连通性测试用的固定查询词。 */
    private const val TEST_QUERY = "今天有什么新闻"

    /** 测试失败原因给设置页展示，过长会撑变形，截断到 160 字。 */
    private const val MAX_TEST_ERROR_CHARS = 160

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    private const val PAGE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    private val HTML_COMMENT = Regex("(?s)<!--.*?-->")
    private val HTML_SCRIPT = Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>")
    private val HTML_BLOCK_TAG = Regex(
        "(?i)</?(p|div|br|li|tr|h[1-6]|section|article|header|footer|nav|ul|ol|table|blockquote|pre)[^>]*>",
    )
    private val HTML_TAG = Regex("(?s)<[^>]+>")

    // 宿主 app 已开启 core library desugaring（见 manager/app/build.gradle.kts），
    // java.time.Duration 在 API 24/25 上会被脱糖；这里抑制库模块单独 lint 的 NewApi 误报。
    @Suppress("NewApi")
    private val client = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(30))
        .build()

    private val whitespace = Regex("\\s+")

    /**
     * 用指定服务商执行一次搜索。
     *
     * @param timeoutMs 本次调用的超时预算；自动链会传入「剩余总预算」把它压到 8 秒以内，
     *   最终生效值被夹在 1 秒 ~ [PROVIDER_TIMEOUT_MS] 之间。
     */
    internal suspend fun searchProvider(
        query: String,
        provider: AiSearchProvider,
        apiKey: String,
        timeoutMs: Long = PROVIDER_TIMEOUT_MS,
    ): ProviderOutcome = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            return@withContext ProviderOutcome.Failure("搜索关键词为空")
        }
        val key = apiKey.trim()
        if (provider.requiresApiKey() && key.isEmpty()) {
            return@withContext ProviderOutcome.Failure("${provider.displayName()} 需要 API Key")
        }
        val effectiveTimeout = timeoutMs.coerceIn(MIN_PROVIDER_TIMEOUT_MS, PROVIDER_TIMEOUT_MS)
        try {
            when (provider) {
                AiSearchProvider.FIRECRAWL -> searchFirecrawl(cleaned, key, effectiveTimeout)
                AiSearchProvider.ANYSEARCH -> searchAnySearch(cleaned, key, effectiveTimeout)
                AiSearchProvider.TAVILY -> searchTavily(cleaned, key, effectiveTimeout)
                AiSearchProvider.YUANBAO -> searchYuanbao(cleaned, key, effectiveTimeout)
            }
        } catch (cancelled: CancellationException) {
            // 协程取消必须原样抛出，不能被当成普通搜索失败吞掉。
            throw cancelled
        } catch (error: Exception) {
            ProviderOutcome.Failure(error.readableSearchError())
        }
    }

    /**
     * 设置页「测试」：用固定查询词真实发一次请求，返回耗时与结果条数。
     *
     * 不受总开关/启用状态影响，Key 用调用方传入的那一份（不读配置），方便边填边测。
     */
    internal suspend fun test(
        provider: AiSearchProvider,
        apiKey: String,
        query: String = TEST_QUERY,
    ): AiSearchTestResult {
        val startedAt = System.nanoTime()
        val outcome = searchProvider(query.ifBlank { TEST_QUERY }, provider, apiKey)
        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
        return when (outcome) {
            is ProviderOutcome.Success ->
                AiSearchTestResult.Success(provider, latencyMs, outcome.results)

            is ProviderOutcome.Failure ->
                AiSearchTestResult.Failure(provider, outcome.message.take(MAX_TEST_ERROR_CHARS))
        }
    }

    /** Firecrawl：POST /v1/search，解析 data[].title/url/description（或 markdown）。 */
    private fun searchFirecrawl(query: String, apiKey: String, timeoutMs: Long): ProviderOutcome {
        val payload = buildJsonObject {
            put("query", query)
            put("limit", MAX_RESULTS)
        }
        val (code, responseBody) = postJson(FIRECRAWL_ENDPOINT, apiKey, payload.toString(), timeoutMs)
        if (code !in 200..299) {
            return ProviderOutcome.Failure(firecrawlError(code, responseBody))
        }
        val root = responseBody.toJsonObjectOrNull()
            ?: return ProviderOutcome.Failure("响应无法解析为 JSON")
        val data = root["data"] as? JsonArray
            ?: return ProviderOutcome.Failure("响应格式异常：未找到结果列表")
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
        return ProviderOutcome.Success(results)
    }

    /**
     * AnySearch：使用内置官方端点，POST `{query, max_results}`，Key 可选。
     * 响应是 `{code, message, data: {results: [{title, url, content|snippet}]}}`；
     * 仍然兼容 `results[]` / `data[]` 等变体。
     */
    private fun searchAnySearch(query: String, apiKey: String, timeoutMs: Long): ProviderOutcome {
        val payload = buildJsonObject {
            put("query", query)
            put("max_results", MAX_RESULTS)
        }
        val (code, responseBody) = postJson(ANYSEARCH_ENDPOINT, apiKey, payload.toString(), timeoutMs)
        if (code !in 200..299) {
            return ProviderOutcome.Failure(httpError(code, responseBody))
        }
        val root = responseBody.toJsonObjectOrNull()
            ?: return ProviderOutcome.Failure("响应无法解析为 JSON")

        // 业务错误码：code 非 0 时 message 是服务端给的原因（额度不足、Key 无效等）。
        val bizCode = (root["code"] as? JsonPrimitive)?.contentOrNull
        if (bizCode != null && bizCode != "0") {
            val message = root.string("message") ?: "错误码 $bizCode"
            return ProviderOutcome.Failure("服务返回错误：$message")
        }

        val resultArray: JsonArray? =
            ((root["data"] as? JsonObject)?.get("results") as? JsonArray)
                ?: (root["results"] as? JsonArray)
                ?: (root["data"] as? JsonArray)
        if (resultArray == null) {
            return ProviderOutcome.Failure("响应格式异常：未找到结果列表")
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
        return ProviderOutcome.Success(results)
    }

    /** Tavily：POST /search，鉴权走 Bearer；响应 results[].title/url/content。 */
    private fun searchTavily(query: String, apiKey: String, timeoutMs: Long): ProviderOutcome {
        val payload = buildJsonObject {
            put("query", query)
            put("max_results", MAX_RESULTS)
            put("search_depth", "basic")
        }
        val (code, responseBody) = postJson(TAVILY_ENDPOINT, apiKey, payload.toString(), timeoutMs)
        if (code !in 200..299) {
            return ProviderOutcome.Failure(httpError(code, responseBody))
        }
        val root = responseBody.toJsonObjectOrNull()
            ?: return ProviderOutcome.Failure("响应无法解析为 JSON")
        val data = root["results"] as? JsonArray
            ?: return ProviderOutcome.Failure("响应格式异常：未找到结果列表")
        val results = data.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item.string("url").orEmpty()
            if (url.isBlank()) return@mapNotNull null
            SearchResult(
                title = item.string("title").orEmpty().ifBlank { url },
                url = url,
                snippet = item.string("content").orEmpty().ifBlank { item.string("description").orEmpty() },
            )
        }
        return ProviderOutcome.Success(results)
    }

    /**
     * 元宝搜索：POST /SearchPro，请求体 `{Query, Cnt}`。
     * 响应 `{Response:{Pages:["{...json 字符串...}", ...]}}`，Pages 元素需要二次解析；
     * 单条字段 passage / content 都是正文摘录，优先用更短的 passage。
     */
    private fun searchYuanbao(query: String, apiKey: String, timeoutMs: Long): ProviderOutcome {
        val payload = buildJsonObject {
            put("Query", query)
            put("Cnt", MAX_RESULTS)
        }
        val (code, responseBody) = postJson(YUANBAO_ENDPOINT, apiKey, payload.toString(), timeoutMs)
        if (code !in 200..299) {
            return ProviderOutcome.Failure(httpError(code, responseBody))
        }
        val root = responseBody.toJsonObjectOrNull()
            ?: return ProviderOutcome.Failure("响应无法解析为 JSON")
        val response = root["Response"] as? JsonObject
            ?: return ProviderOutcome.Failure("响应格式异常：缺少 Response 字段")
        val pages = response["Pages"] as? JsonArray
        if (pages == null) {
            val message = (response["Error"] as? JsonObject)?.string("Message")
                ?: response.string("Message")
            return ProviderOutcome.Failure(
                if (message != null) "服务返回错误：$message" else "响应格式异常：未找到结果列表",
            )
        }
        val results = pages.mapNotNull { element ->
            val item = when (element) {
                is JsonObject -> element
                is JsonPrimitive -> runCatching {
                    json.parseToJsonElement(element.contentOrNull.orEmpty()) as? JsonObject
                }.getOrNull()

                else -> null
            } ?: return@mapNotNull null
            val url = item.string("url").orEmpty()
            if (url.isBlank()) return@mapNotNull null
            SearchResult(
                title = item.string("title").orEmpty().ifBlank { url },
                url = url,
                // content 比 passage 更完整；搜索阶段多带一点正文，模型常常不用再抓页面。
                snippet = item.string("content").orEmpty()
                    .ifBlank { item.string("passage").orEmpty() }
                    .ifBlank { item.string("snippet").orEmpty() },
            )
        }
        return ProviderOutcome.Success(results)
    }

    /**
     * 抓取一页网页正文：优先用当前服务商的抓取 API，退化成通用 HTML 抓取。
     * 与搜索一样只负责「发一次请求并解析」，编排在 [AiWebController.fetchPage]。
     */
    internal suspend fun fetchPage(
        url: String,
        provider: AiSearchProvider,
        apiKey: String,
        timeoutMs: Long = PROVIDER_TIMEOUT_MS,
    ): PageOutcome = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                AiSearchProvider.FIRECRAWL -> fetchFirecrawl(url, apiKey, timeoutMs)
                AiSearchProvider.TAVILY -> fetchTavily(url, apiKey, timeoutMs)
                AiSearchProvider.ANYSEARCH -> fetchAnySearch(url, apiKey, timeoutMs)
                AiSearchProvider.YUANBAO -> fetchHtml(url, timeoutMs)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PageOutcome.Failure(error.readableSearchError())
        }
    }

    /**
     * 通用 HTML 抓取：不依赖任何服务商的抓取接口（元宝也没有抓取 API），
     * 供 [AiWebController.fetchPage] 在服务商候选链全部失败后直接兜底调用。
     */
    internal suspend fun fetchHtmlPage(
        url: String,
        timeoutMs: Long = PROVIDER_TIMEOUT_MS,
    ): PageOutcome = withContext(Dispatchers.IO) {
        try {
            fetchHtml(url, timeoutMs)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PageOutcome.Failure(error.readableSearchError())
        }
    }

    /** Firecrawl：POST /v1/scrape，只取主内容 markdown。 */
    private fun fetchFirecrawl(url: String, apiKey: String, timeoutMs: Long): PageOutcome {
        val payload = buildJsonObject {
            put("url", url)
            put("formats", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("markdown")) })
            put("onlyMainContent", true)
        }
        val (code, body) = postJson(FIRECRAWL_SCRAPE_ENDPOINT, apiKey, payload.toString(), timeoutMs)
        if (code !in 200..299) return PageOutcome.Failure(firecrawlError(code, body))
        val root = body.toJsonObjectOrNull() ?: return PageOutcome.Failure("响应无法解析为 JSON")
        val data = root["data"] as? JsonObject ?: return PageOutcome.Failure("响应格式异常：缺少 data")
        val text = data.string("markdown").orEmpty().ifBlank { data.string("content").orEmpty() }
        return text.takeIf { it.isNotBlank() }
            ?.let { PageOutcome.Success(it.take(MAX_PAGE_CHARS)) }
            ?: PageOutcome.Failure("页面没有可读正文")
    }

    /** Tavily：POST /extract，取 results[0].raw_content。 */
    private fun fetchTavily(url: String, apiKey: String, timeoutMs: Long): PageOutcome {
        val payload = buildJsonObject {
            put("urls", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(url)) })
        }
        val (code, body) = postJson(TAVILY_EXTRACT_ENDPOINT, apiKey, payload.toString(), timeoutMs)
        if (code !in 200..299) return PageOutcome.Failure(httpError(code, body))
        val root = body.toJsonObjectOrNull() ?: return PageOutcome.Failure("响应无法解析为 JSON")
        val item = (root["results"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return PageOutcome.Failure("响应格式异常：未找到结果")
        val text = item.string("raw_content").orEmpty().ifBlank { item.string("content").orEmpty() }
        return text.takeIf { it.isNotBlank() }
            ?.let { PageOutcome.Success(it.take(MAX_PAGE_CHARS)) }
            ?: PageOutcome.Failure("页面没有可读正文")
    }

    /** AnySearch：POST /v1/extract，响应结构兼容多种字段。 */
    private fun fetchAnySearch(url: String, apiKey: String, timeoutMs: Long): PageOutcome {
        val payload = buildJsonObject { put("url", url) }
        val (code, body) = postJson(ANYSEARCH_EXTRACT_ENDPOINT, apiKey, payload.toString(), timeoutMs)
        if (code !in 200..299) return PageOutcome.Failure(httpError(code, body))
        val root = body.toJsonObjectOrNull() ?: return PageOutcome.Failure("响应无法解析为 JSON")
        val bizCode = (root["code"] as? JsonPrimitive)?.contentOrNull
        if (bizCode != null && bizCode != "0") {
            return PageOutcome.Failure("服务返回错误：${root.string("message") ?: "错误码 $bizCode"}")
        }
        val data = root["data"] as? JsonObject
        val text = listOfNotNull(
            data?.string("content"),
            data?.string("markdown"),
            data?.string("text"),
            root.string("content"),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        return text.takeIf { it.isNotBlank() }
            ?.let { PageOutcome.Success(it.take(MAX_PAGE_CHARS)) }
            ?: PageOutcome.Failure("页面没有可读正文")
    }

    /** 通用兜底：直接 GET 网页并抽正文（元宝搜索没有抓取 API）。 */
    private fun fetchHtml(url: String, timeoutMs: Long): PageOutcome {
        var currentUrl = AiWebUrlGuard.requireSafeUrl(url)
        // 禁止 OkHttp 自动跟随重定向，改为逐跳校验，防止 302 到内网地址绕过入口检查。
        val htmlClient = clientWithTimeout(timeoutMs).newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        var redirects = 0
        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", PAGE_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
            val response = htmlClient.newCall(request).execute()
            if (response.isRedirect) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) return PageOutcome.Failure("服务返回重定向但缺少 Location")
                if (redirects++ >= MAX_HTML_REDIRECTS) return PageOutcome.Failure("重定向次数过多")
                currentUrl = AiWebUrlGuard.resolveRedirect(currentUrl, location)
                continue
            }
            response.use {
                if (!it.isSuccessful) return PageOutcome.Failure("服务返回 HTTP ${it.code}")
                val body = it.readBodyLimited(MAX_RESPONSE_BYTES)
                val text = htmlToText(body).take(MAX_PAGE_CHARS)
                return text.takeIf { it.isNotBlank() }
                    ?.let { PageOutcome.Success(it) }
                    ?: PageOutcome.Failure("页面没有可读正文")
            }
        }
    }

    /** 极简正文抽取：去脚本/样式/注释，块级标签转换行，去标签后压缩空白。 */
    private fun htmlToText(html: String): String {
        var text = html.replace(HTML_COMMENT, " ")
        text = text.replace(HTML_SCRIPT, " ")
        text = text.replace(HTML_BLOCK_TAG, "\n")
        text = text.replace(HTML_TAG, " ")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return text.lines()
            .map { it.replace(whitespace, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** 发一次 JSON POST；返回 HTTP 状态码与响应体原文。apiKey 为空时不带鉴权头（AnySearch 匿名）。 */
    private fun postJson(url: String, apiKey: String, body: String, timeoutMs: Long): Pair<Int, String> {
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        val request = builder.post(body.toRequestBody(jsonMediaType)).build()
        clientWithTimeout(timeoutMs).newCall(request).execute().use { response ->
            return response.code to response.readBodyLimited(MAX_RESPONSE_BYTES)
        }
    }

    /** 在共享连接池/线程池的基础上只改单次 callTimeout，避免每次请求都新建一整套资源。 */
    @Suppress("NewApi")
    private fun clientWithTimeout(timeoutMs: Long): OkHttpClient =
        client.newBuilder()
            .callTimeout(Duration.ofMillis(timeoutMs))
            .build()

    /** Firecrawl 在机房/代理 IP 上会拒绝匿名请求，单独给一句可操作的提示。 */
    private fun firecrawlError(code: Int, body: String): String =
        if (code == 403 && body.contains("VPN", ignoreCase = true)) {
            "当前网络被判定为 VPN/代理，匿名无法使用：请填 API Key 或关闭代理"
        } else {
            httpError(code, body)
        }

    private fun httpError(code: Int, body: String): String =
        "服务返回 HTTP $code：${body.trim().take(MAX_ERROR_BODY_CHARS)}"

    /** 响应体转 JSON 对象；解析失败返回 null。 */
    private fun String.toJsonObjectOrNull(): JsonObject? =
        runCatching { json.parseToJsonElement(this) as? JsonObject }.getOrNull()

    /**
     * 统一拼成「1. 标题\nURL\n摘要」，条数不超过 [max]，整体不超过 4000 字；
     * 没有任何结果时返回 [NO_WEB_RESULT_TEXT]。
     */
    internal fun formatResults(results: List<SearchResult>, max: Int = MAX_RESULTS): String {
        val valid = results.filter { it.url.isNotBlank() }
        if (valid.isEmpty()) return NO_WEB_RESULT_TEXT
        return valid.take(max).mapIndexed { index, item ->
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
        }.joinToString("\n\n").take(MAX_OUTPUT_CHARS)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}

/** 是否必须填 Key：Tavily / 元宝必须填写；Firecrawl / AnySearch 允许匿名（额度更低）。 */
internal fun AiSearchProvider.requiresApiKey(): Boolean = when (this) {
    // Firecrawl / AnySearch 都允许匿名访问（额度与频率更低，Firecrawl 在代理 IP 上会被拒绝）。
    AiSearchProvider.ANYSEARCH, AiSearchProvider.FIRECRAWL -> false
    AiSearchProvider.TAVILY, AiSearchProvider.YUANBAO -> true
}

/** 错误提示与来源标注里给用户看的服务名。 */
internal fun AiSearchProvider.displayName(): String = when (this) {
    AiSearchProvider.FIRECRAWL -> "Firecrawl"
    AiSearchProvider.ANYSEARCH -> "AnySearch"
    AiSearchProvider.TAVILY -> "Tavily"
    AiSearchProvider.YUANBAO -> "元宝搜索"
}

/** 把网络异常翻译成给模型/用户看的中文原因。 */
internal fun Throwable.readableSearchError(): String = when (this) {
    is SocketTimeoutException -> "请求超时"
    is UnknownHostException -> "网络不可用：无法解析服务地址"
    is ConnectException -> "网络不可用：连接失败"
    is InterruptedIOException ->
        if (message?.contains("timeout", ignoreCase = true) == true) "请求超时" else (message ?: "请求被中断")

    else -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
