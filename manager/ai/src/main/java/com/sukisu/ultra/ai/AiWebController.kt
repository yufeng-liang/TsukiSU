package com.sukisu.ultra.ai

import com.sukisu.ultra.ai.api.AiGithubUrls
import com.sukisu.ultra.ai.api.GithubModuleAsset

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

data class GithubRepository(
    val fullName: String,
    val url: String,
    val description: String,
    val stars: Int,
    val updatedAt: String,
    val archived: Boolean,
    val topics: List<String>,
)

data class GithubReleaseAsset(
    val name: String,
    val size: Long,
    val url: String,
    val sha256: String?,
)

data class GithubRelease(
    val tag: String,
    val name: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val publishedAt: String,
    val assets: List<GithubReleaseAsset>,
)

/**
 * Web and GitHub discovery used by the assistant. Only read-only HTTP calls
 * are made here; installation is handed to [AiModuleService] after the user
 * approves the resolved release asset.
 */
object AiWebController {

    private const val MAX_BODY = 2 * 1024 * 1024

    /** 自动故障转移链的总预算：所有服务商尝试共享。 */
    private const val SEARCH_CHAIN_BUDGET_MS = 20_000L

    /** 剩余预算低于这个值就不再尝试下一家，避免最后一家必然超时。 */
    private const val MIN_ATTEMPT_BUDGET_MS = 1_000L

    /** 聚合错误里每家失败原因最多展示 120 字。 */
    private const val MAX_PROVIDER_ERROR_CHARS = 120

    /** searchWeb 聚合错误文本总长上限。 */
    private const val MAX_SEARCH_OUTPUT_CHARS = 4000

    /** 单次搜索最多回给模型多少条（对应 web_search 工具的 limit 参数上限）。 */
    private const val MAX_RESULTS_PER_QUERY = 10
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Only direct GitHub release downloads are installable. */
    fun isGithubReleaseUrl(url: String): Boolean = AiGithubUrls.isReleaseDownloadUrl(url)

    /**
     * 联网搜索：按设置页的顺序逐个尝试已启用的服务商（单次最多 8 秒、整链 20 秒），
     * 全部失败时返回聚合错误；成功时在末尾标注搜索来源。
     *
     * 切换条件：只有网络异常 / 非 2xx / 解析失败才换下一家；「0 条结果」算这家服务可用，不切换。
     */
    suspend fun searchWeb(query: String, limit: Int = 8): String = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        require(cleaned.isNotEmpty()) { "query is required" }
        require(cleaned.length <= 200) { "query is too long" }

        val config = AiSearchSettings.load()
        if (!config.searchEnabled) {
            return@withContext "联网搜索已关闭：请在 AI 助手的联网搜索设置中开启。"
        }

        val startedAt = System.nanoTime()
        val errors = mutableListOf<String>()
        var skipped: List<AiSearchProvider> = emptyList()
        // 候选 = 用户启用且已配置的服务商，按拖拽顺序；Firecrawl / AnySearch 匿名访问也算已配置。
        val candidates = config.failoverCandidates()
        for ((index, provider) in candidates.withIndex()) {
            val remainingMs = SEARCH_CHAIN_BUDGET_MS - elapsedMs(startedAt)
            // 剩余预算不够一次有意义的请求时直接收尾，避免总耗时失控。
            if (remainingMs < MIN_ATTEMPT_BUDGET_MS) {
                skipped = candidates.drop(index)
                break
            }
            val timeoutMs = minOf(PROVIDER_TIMEOUT_MS, remainingMs)
            val outcome = AiWebSearch.searchProvider(
                query = cleaned,
                provider = provider,
                apiKey = config.keyOf(provider),
                timeoutMs = timeoutMs,
            )
            when (outcome) {
                // 0 条结果也算成功：说明这家服务可用，没必要再切下一家。
                is ProviderOutcome.Success -> return@withContext withSearchSource(
                    AiWebSearch.formatResults(outcome.results, limit.coerceIn(1, MAX_RESULTS_PER_QUERY)),
                    provider.displayName(),
                )

                is ProviderOutcome.Failure ->
                    errors += "${provider.displayName()}：${outcome.message}"
            }
        }

        // 内置 DuckDuckGo 兜底已移除：一个可用服务都没有时直接给出可操作的提示。
        if (candidates.isEmpty()) {
            return@withContext "联网搜索未配置：请在联网搜索设置中启用并配置至少一个搜索服务。"
        }

        if (skipped.isNotEmpty()) {
            errors += "总耗时接近上限，已跳过：${skipped.joinToString("、") { it.displayName() }}"
        }
        buildAggregateSearchError(errors)
    }

    /**
     * 抓取一页网页正文，供模型在搜索之后深挖具体页面。
     *
     * 按设置页顺序优先用有抓取 API 的服务商（Firecrawl / Tavily / AnySearch），
     * 元宝没有抓取接口，最后统一用通用 GET + 正文抽取兜底。
     */
    suspend fun fetchPage(url: String): String = withContext(Dispatchers.IO) {
        val cleaned = url.trim()
        require(cleaned.isNotEmpty()) { "url is required" }
        require(cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            "url must start with http:// or https://"
        }

        val config = AiSearchSettings.load()
        if (!config.searchEnabled) {
            return@withContext "联网搜索已关闭：请在 AI 助手的联网搜索设置中开启。"
        }

        val startedAt = System.nanoTime()
        val errors = mutableListOf<String>()
        for (provider in config.failoverCandidates()) {
            // 这两家没有抓取 API，留给最后的通用抓取。
            if (provider == AiSearchProvider.YUANBAO) continue
            val remainingMs = SEARCH_CHAIN_BUDGET_MS - elapsedMs(startedAt)
            if (remainingMs < MIN_ATTEMPT_BUDGET_MS) break
            val timeoutMs = minOf(PROVIDER_TIMEOUT_MS, remainingMs)
            when (
                val outcome = AiWebSearch.fetchPage(
                    url = cleaned,
                    provider = provider,
                    apiKey = config.keyOf(provider),
                    timeoutMs = timeoutMs,
                )
            ) {
                is PageOutcome.Success ->
                    if (outcome.text.isNotBlank()) {
                        return@withContext withSearchSource(
                            outcome.text,
                            "${provider.displayName()} 正文",
                        )
                    }

                is PageOutcome.Failure ->
                    errors += "${provider.displayName()}：${outcome.message}"
            }
        }

        // 通用抓取兜底：直接 GET 网页再抽正文。
        when (val fallback = AiWebSearch.fetchHtmlPage(cleaned)) {
            is PageOutcome.Success -> withSearchSource(fallback.text, "直接抓取")
            is PageOutcome.Failure -> {
                errors += "直接抓取：${fallback.message}"
                buildAggregateFetchError(errors)
            }
        }
    }

    /** 搜索结果末尾统一标注来源，方便模型/用户判断可信度。 */
    private fun withSearchSource(text: String, source: String): String =
        "$text\n\n搜索来源：$source"

    /** 从开始时间算已用毫秒。 */
    private fun elapsedMs(startedAtNanos: Long): Long =
        (System.nanoTime() - startedAtNanos) / 1_000_000

    /** 抓取失败时的聚合文本。 */
    private fun buildAggregateFetchError(errors: List<String>): String =
        "抓取网页失败：" + errors.joinToString("；") { it.take(MAX_PROVIDER_ERROR_CHARS) }

    /** 所有服务都失败时的聚合文本：每家一行（各截断 120 字），整体不超过 4000 字。 */
    private fun buildAggregateSearchError(errors: List<String>): String = buildString {
        append("所有搜索服务均失败：")
        errors.forEach { error ->
            append('\n').append("· ").append(error.take(MAX_PROVIDER_ERROR_CHARS))
        }
    }.take(MAX_SEARCH_OUTPUT_CHARS)

    suspend fun searchGithub(query: String, limit: Int = 8): String = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        require(cleaned.isNotEmpty()) { "query is required" }
        require(cleaned.length <= 200) { "query is too long" }
        val encoded = URLEncoder.encode(cleaned, StandardCharsets.UTF_8.toString())
        val url = "https://api.github.com/search/repositories" +
            "?q=$encoded&sort=stars&order=desc&per_page=${limit.coerceIn(1, 20)}"
        val body = fetch(url, "application/vnd.github+json")
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw IllegalStateException("GitHub returned an unexpected response")
        root["message"]?.let { throw IllegalStateException("GitHub: ${it.text()}") }
        val items = root["items"] as? JsonArray ?: JsonArray(emptyList())
        val repos = items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            GithubRepository(
                fullName = item.str("full_name").orEmpty(),
                url = item.str("html_url").orEmpty(),
                description = stripHtml(item.str("description").orEmpty()),
                stars = item.int("stargazers_count") ?: 0,
                updatedAt = item.str("updated_at").orEmpty(),
                archived = item.bool("archived") ?: false,
                topics = (item["topics"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty(),
            )
        }.filter { it.fullName.isNotBlank() }
        buildJsonObject {
            put("query", cleaned)
            put("results", buildJsonArray {
                repos.forEach { repo ->
                    add(buildJsonObject {
                        put("repo", repo.fullName)
                        put("url", repo.url)
                        put("description", repo.description.take(300))
                        put("stars", repo.stars)
                        put("updated_at", repo.updatedAt)
                        put("archived", repo.archived)
                        put("topics", buildJsonArray { repo.topics.take(12).forEach { add(it) } })
                    })
                }
            })
        }.toString()
    }

    suspend fun githubReleases(repo: String, limit: Int = 5): String = withContext(Dispatchers.IO) {
        val releases = fetchGithubReleases(repo)
        buildJsonObject {
            put("repo", repo)
            put("releases", buildJsonArray {
                releases.take(limit.coerceIn(1, 10)).forEach { release ->
                    add(buildJsonObject {
                        put("tag", release.tag)
                        put("name", release.name)
                        put("prerelease", release.prerelease)
                        put("draft", release.draft)
                        put("published_at", release.publishedAt)
                        put("assets", buildJsonArray {
                            release.assets.take(12).forEach { asset ->
                                add(buildJsonObject {
                                    put("name", asset.name)
                                    put("size", asset.size)
                                    put("url", asset.url)
                                    put("sha256", asset.sha256 ?: "")
                                })
                            }
                        })
                    })
                }
            })
        }.toString()
    }

    /**
     * Resolves a repository release to one installable ZIP asset. Prefers the
     * latest stable release unless [version] names a tag.
     */
    suspend fun resolveGithubModule(
        repo: String,
        version: String? = null,
        assetName: String? = null,
    ): GithubModuleAsset = withContext(Dispatchers.IO) {
        val normalizedRepo = normalizeRepo(repo)
        val releases = fetchGithubReleases(normalizedRepo)
        val requested = version?.trim().orEmpty()
        val release = if (requested.isEmpty()) {
            releases.firstOrNull { !it.draft && !it.prerelease }
        } else {
            releases.firstOrNull {
                !it.draft && (it.tag.equals(requested, true) || it.name.equals(requested, true))
            }
        } ?: throw IllegalArgumentException(
            "No stable release '${requested.ifEmpty { "latest" }}' in $normalizedRepo. " +
                "Available: ${releases.filter { !it.draft }.joinToString { it.tag }}"
        )

        val zips = release.assets.filter {
            it.name.endsWith(".zip", true) &&
                !it.name.contains(".sha256", true) &&
                !it.name.contains(".asc", true) &&
                !it.name.contains("source", true)
        }
        val requestedAsset = assetName?.trim().orEmpty()
        val asset = if (requestedAsset.isNotEmpty()) {
            zips.firstOrNull { it.name.equals(requestedAsset, true) }
                ?: zips.firstOrNull { it.name.contains(requestedAsset, true) }
                ?: throw IllegalArgumentException(
                    "No ZIP asset '$requestedAsset' in $normalizedRepo ${release.tag}. " +
                        "Assets: ${zips.joinToString { it.name }}"
                )
        } else {
            zips.firstOrNull { it.name.contains("module", true) }
                ?: zips.firstOrNull { !it.name.contains("source", true) }
                ?: zips.firstOrNull()
                ?: throw IllegalArgumentException(
                    "No ZIP asset in $normalizedRepo ${release.tag}; this release has no installable module."
                )
        }
        check(isGithubReleaseUrl(asset.url)) {
            "Release asset is not a GitHub release download URL"
        }
        GithubModuleAsset(
            repo = normalizedRepo,
            tag = release.tag,
            name = asset.name,
            size = asset.size,
            url = asset.url,
            sha256 = asset.sha256,
        )
    }

    private suspend fun fetchGithubReleases(repo: String): List<GithubRelease> = withContext(Dispatchers.IO) {
        val normalized = normalizeRepo(repo)
        val body = fetch(
            "https://api.github.com/repos/$normalized/releases?per_page=10",
            "application/vnd.github+json",
        )
        val root = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull()
            ?: throw IllegalStateException("GitHub returned an unexpected releases response")
        root.mapNotNull { element ->
            val release = element as? JsonObject ?: return@mapNotNull null
            val assets = (release["assets"] as? JsonArray).orEmpty().mapNotNull { assetElement ->
                val asset = assetElement as? JsonObject ?: return@mapNotNull null
                val name = asset.str("name").orEmpty()
                val url = asset.str("browser_download_url").orEmpty()
                if (name.isBlank() || url.isBlank()) return@mapNotNull null
                GithubReleaseAsset(
                    name = name,
                    size = asset.long("size") ?: 0L,
                    url = url,
                    sha256 = asset.str("digest")
                        ?.substringAfter("sha256:", "")
                        ?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
                        ?.lowercase(),
                )
            }
            GithubRelease(
                tag = release.str("tag_name").orEmpty(),
                name = release.str("name").orEmpty().ifBlank { release.str("tag_name").orEmpty() },
                prerelease = release.bool("prerelease") ?: false,
                draft = release.bool("draft") ?: false,
                publishedAt = release.str("published_at").orEmpty(),
                assets = assets,
            )
        }.filter { it.tag.isNotBlank() }
    }

    private fun normalizeRepo(repo: String): String {
        val cleaned = repo.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trimEnd('/')
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        require(parts.size >= 2) {
            "repo must be owner/name, e.g. owner/repository"
        }
        val owner = parts[0]
        val name = parts[1]
        require(owner.length <= 100 && name.length <= 100) { "repo is too long" }
        require((owner + name).all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
            "repo contains invalid characters"
        }
        return "$owner/$name"
    }

    private fun fetch(url: String, accept: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = runCatching { response.body.string() }.getOrDefault("")
                throw IllegalStateException(
                    "HTTP ${response.code} from ${URI(url).host}" +
                        if (body.isNotBlank()) ": ${stripHtml(body).take(300)}" else ""
                )
            }
            val body = response.body.string()
            return body.take(MAX_BODY)
        }
    }

    private fun stripHtml(value: String): String {
        val withoutBlocks = value
            .replace(Regex("""<(script|style)[\s\S]*?</\1>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), " ")
        return unescapeHtml(withoutBlocks)
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun unescapeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

    private fun kotlinx.serialization.json.JsonElement.text(): String =
        (this as? JsonPrimitive)?.contentOrNull ?: toString()
}
