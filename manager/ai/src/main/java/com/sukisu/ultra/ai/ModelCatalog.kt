package com.sukisu.ultra.ai

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 模型目录：先拉 provider 自己的模型列表，再按 models.dev 补全上下文体量、
 * 最大输出、推理与图片能力。
 *
 * 设计取舍：
 * - 网络请求都是「尽力而为」：任何失败都退回空列表 / 原列表，不阻塞设置页；
 * - models.dev 目录约 6 MB，本地缓存 24 小时，下载成功后原子写缓存文件；
 * - `:ai` 是独立 library，不依赖 `:app`，Context 统一从 [aiContext] 取。
 */
internal object ModelCatalog {

    private const val TAG = "ModelCatalog"

    private const val MODELS_DEV_URL = "https://models.dev/api.json"
    private const val CACHE_FILE_NAME = "models_dev_catalog.json"

    /** models.dev 目录缓存有效期：24 小时。 */
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000

    private const val LIST_TIMEOUT_SECONDS = 20L
    private const val CATALOG_TIMEOUT_SECONDS = 60L
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 模块内共享的 OkHttp 客户端。`newBuilder()` 会复用连接池和线程池，
     * 每次请求只覆盖 callTimeout，不额外创建连接资源。
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** 避免多个页面同时补全时重复下载 6 MB 目录。 */
    private val catalogMutex = Mutex()

    /**
     * 从 provider 自己的模型接口拉模型列表；失败返回空列表（不抛异常）。
     *
     * 支持 OpenAI 兼容 `/v1/models`、Ollama `/api/tags`、Google
     * `/v1beta/models`；Anthropic 没有标准模型列表接口，直接返回空。
     */
    suspend fun listModels(provider: AiProvider): List<AiModelInfo> = withContext(Dispatchers.IO) {
        try {
            val endpoint = provider.endpoint.trim()
            if (endpoint.isEmpty()) return@withContext emptyList()
            when {
                // GOOGLE 枚举值可能由设置层按版本引入，这里同时兼容显式值。
                isGoogleProvider(provider) -> fetchGoogleModels(endpoint, provider.apiKey)
                provider.format == AiProviderFormat.ANTHROPIC -> emptyList()
                provider.format == AiProviderFormat.OLLAMA -> fetchOllamaModels(endpoint)
                else -> fetchOpenAiModels(endpoint, provider.apiKey)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "拉取模型列表失败：${provider.id}", e)
            emptyList()
        }
    }

    /**
     * 对拿到的模型按 models.dev 补全上下文 / 最大输出 / 推理 / 图片能力；
     * 已有非零值不覆盖。失败返回原列表，不抛异常。
     */
    suspend fun enrich(models: List<AiModelInfo>): List<AiModelInfo> {
        if (models.isEmpty()) return models
        return withContext(Dispatchers.IO) {
            try {
                val catalog = loadCatalog()
                if (catalog.isEmpty()) return@withContext models
                models.map { model -> fillFromCatalog(model, catalog) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "models.dev 补全失败", e)
                models
            }
        }
    }

    // ---------------------------------------------------------------------
    // listModels：各格式的请求与解析
    // ---------------------------------------------------------------------

    private fun isGoogleProvider(provider: AiProvider): Boolean {
        if (provider.format.name == "GOOGLE") return true
        if (provider.format != AiProviderFormat.AUTO) return false
        return isGoogleEndpoint(provider.endpoint)
    }

    private fun isGoogleEndpoint(endpoint: String): Boolean {
        val lower = endpoint.lowercase()
        return "googleapis" in lower || "generativelanguage" in lower
    }

    /** OpenAI 兼容：`GET {base}/models`，base 已带 `/v1` 时不再重复补。 */
    private fun fetchOpenAiModels(endpoint: String, apiKey: String): List<AiModelInfo> {
        // 设置页允许直接填完整的 chat 地址，这里兼容两种写法。
        val base = normalizeEndpoint(endpoint).removeSuffixIgnoreCase("/chat/completions")
        val url = if (base.endsWith("/v1", ignoreCase = true)) {
            "$base/models"
        } else {
            "$base/v1/models"
        }
        val builder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        val body = executeForBody(builder.build(), LIST_TIMEOUT_SECONDS) ?: return emptyList()
        return parseOpenAiModels(body)
    }

    /** Ollama：`GET {base}/api/tags`，返回的 name 即为模型 id。 */
    private fun fetchOllamaModels(endpoint: String): List<AiModelInfo> {
        var base = normalizeEndpoint(endpoint)
            .removeSuffixIgnoreCase("/api/chat")
            .removeSuffixIgnoreCase("/api/generate")
        // 用户也可能只填到 Ollama 的 API 根（.../api）。
        if (base.endsWith("/api", ignoreCase = true)) {
            base = base.dropLast("/api".length)
        }
        val url = "$base/api/tags"
        val body = executeForBody(Request.Builder().url(url).get().build(), LIST_TIMEOUT_SECONDS)
            ?: return emptyList()
        return parseOllamaModels(body)
    }

    /** Google：`GET {base}/v1beta/models?key={apiKey}`。 */
    private fun fetchGoogleModels(endpoint: String, apiKey: String): List<AiModelInfo> {
        val base = googleApiBase(endpoint)
        val url = "$base/v1beta/models".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("key", apiKey)
            ?.build()
            ?: return emptyList()
        val body = executeForBody(Request.Builder().url(url).get().build(), LIST_TIMEOUT_SECONDS)
            ?: return emptyList()
        return parseGoogleModels(body)
    }

    private fun parseOpenAiModels(body: String): List<AiModelInfo> {
        val root = parseJsonObject(body) ?: return emptyList()
        // 标准 OpenAI 是 data；部分兼容实现直接返回 models。
        val array = (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray)
            ?: return emptyList()
        return array.mapNotNull { element ->
            val id = when (element) {
                is JsonObject -> element.string("id")
                    ?: element.string("name")
                    ?: element.string("model")

                is JsonPrimitive -> element.contentOrNull
                else -> null
            }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val displayName = (element as? JsonObject)?.string("displayName")
                ?: (element as? JsonObject)?.string("name")
                ?: id
            AiModelInfo(id = id, name = displayName)
        }.distinctBy { it.id }
    }

    private fun parseOllamaModels(body: String): List<AiModelInfo> {
        val root = parseJsonObject(body) ?: return emptyList()
        val array = root["models"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = (obj.string("name") ?: obj.string("model"))?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            AiModelInfo(id = id, name = obj.string("displayName") ?: id)
        }.distinctBy { it.id }
    }

    private fun parseGoogleModels(body: String): List<AiModelInfo> {
        val root = parseJsonObject(body) ?: return emptyList()
        val array = root["models"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val rawName = obj.string("name") ?: return@mapNotNull null
            val id = rawName.removePrefix("models/").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            AiModelInfo(
                id = id,
                name = obj.string("displayName") ?: id,
                contextWindow = obj.int("inputTokenLimit"),
                maxOutput = obj.int("outputTokenLimit"),
            )
        }.distinctBy { it.id }
    }

    // ---------------------------------------------------------------------
    // enrich：models.dev 缓存、解析与字段填充
    // ---------------------------------------------------------------------

    private fun fillFromCatalog(
        model: AiModelInfo,
        catalog: Map<String, ModelCapabilities>,
    ): AiModelInfo {
        val capabilities = catalog[model.id]
            ?: model.id.substringAfter('/', "")
                .takeIf { it.isNotEmpty() && it != model.id }
                ?.let(catalog::get)
            ?: return model
        return model.copy(
            contextWindow = if (model.contextWindow != 0) {
                model.contextWindow
            } else {
                capabilities.contextWindow
            },
            maxOutput = if (model.maxOutput != 0) model.maxOutput else capabilities.maxOutput,
            reasoning = model.reasoning || capabilities.reasoning,
            supportsImages = model.supportsImages || capabilities.supportsImages,
        )
    }

    private suspend fun loadCatalog(): Map<String, ModelCapabilities> = catalogMutex.withLock {
        val cacheFile = File(aiContext.cacheDir, CACHE_FILE_NAME)
        val age = System.currentTimeMillis() - cacheFile.lastModified()
        if (cacheFile.isFile && age in 0L until CACHE_TTL_MS) {
            val cached = runCatching { parseCatalog(cacheFile.readText()) }.getOrNull()
            if (cached != null) return@withLock cached
            // 文件损坏：删掉后按未命中处理，重新下载。
            cacheFile.delete()
        }

        val body = downloadCatalog() ?: return@withLock emptyMap()
        val parsed = runCatching { parseCatalog(body) }.getOrNull()
            ?: return@withLock emptyMap()
        runCatching { writeCacheAtomically(cacheFile, body) }
            .onFailure { Log.w(TAG, "写入 models.dev 缓存失败", it) }
        parsed
    }

    private fun downloadCatalog(): String? {
        val request = Request.Builder().url(MODELS_DEV_URL).get().build()
        return executeForBody(request, CATALOG_TIMEOUT_SECONDS)
    }

    /**
     * 解析 models.dev 顶层结构：
     * `{ "<provider>": { "models": { "<modelId>": { reasoning, modalities, limit } } } }`。
     *
     * 同一个 model id 会出现在多个 provider 下，取能力字段的并集：
     * context/output 取最大值，布尔值取或。
     */
    private fun parseCatalog(body: String): Map<String, ModelCapabilities> {
        val providers = json.decodeFromString<Map<String, ModelsDevProvider>>(body)
        val index = HashMap<String, ModelCapabilities>(4096)
        for (provider in providers.values) {
            for ((modelId, model) in provider.models) {
                val capabilities = ModelCapabilities(
                    contextWindow = model.limit?.context.toTokenCount(),
                    maxOutput = model.limit?.output.toTokenCount(),
                    reasoning = model.reasoning == true,
                    supportsImages = model.modalities?.input
                        ?.any { it.equals("image", ignoreCase = true) } == true,
                )
                val existing = index[modelId]
                index[modelId] = if (existing == null) {
                    capabilities
                } else {
                    existing.merge(capabilities)
                }
            }
        }
        return index
    }

    private fun writeCacheAtomically(target: File, content: String) {
        val dir = target.parentFile ?: return
        if (!dir.exists() && !dir.mkdirs()) return
        val temp = File.createTempFile("models_dev_catalog", ".tmp", dir)
        try {
            temp.writeText(content)
            if (!temp.renameTo(target)) {
                // Windows/部分文件系统不允许 rename 覆盖已有文件。
                if (target.exists() && !target.delete()) {
                    Log.w(TAG, "无法删除旧的 models.dev 缓存")
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                }
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    // ---------------------------------------------------------------------
    // 通用 HTTP / JSON 工具
    // ---------------------------------------------------------------------

    private fun executeForBody(request: Request, timeoutSeconds: Long): String? {
        val client = httpClient.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "模型接口返回 HTTP ${response.code}：${request.url}")
                return null
            }
            return response.body.string()
        }
    }

    /** endpoint 允许省略 scheme：本地 Ollama 常见 `192.168.x.x:11434` 写法。 */
    private fun normalizeEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim().trimEnd('/')
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        return if (isGoogleEndpoint(trimmed)) "https://$trimmed" else "http://$trimmed"
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

    /**
     * Google 的 endpoint 既可能是 API 根地址，也可能是完整的
     * `/v1beta/models/{model}:generateContent` 地址；统一截到版本段之前。
     */
    private fun googleApiBase(endpoint: String): String {
        val normalized = normalizeEndpoint(endpoint)
            .removeSuffixIgnoreCase("/v1beta")
            .removeSuffixIgnoreCase("/v1")
        val lower = normalized.lowercase()
        val versionIndex = listOf("/v1beta/", "/v1/")
            .map { lower.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
        return if (versionIndex != null) normalized.substring(0, versionIndex) else normalized
    }

    private fun parseJsonObject(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0) ?: 0

    private fun Long?.toTokenCount(): Int =
        this?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0
}

/** models.dev 的 provider 对象；只声明用到的字段，其余由 ignoreUnknownKeys 跳过。 */
@Serializable
private data class ModelsDevProvider(
    val models: Map<String, ModelsDevModel> = emptyMap(),
)

@Serializable
private data class ModelsDevModel(
    val reasoning: Boolean? = null,
    val modalities: ModelsDevModalities? = null,
    val limit: ModelsDevLimit? = null,
)

@Serializable
private data class ModelsDevModalities(
    val input: List<String> = emptyList(),
)

@Serializable
private data class ModelsDevLimit(
    val context: Long? = null,
    val output: Long? = null,
)

/** 一个 model id 在 models.dev 里的能力摘要（跨 provider 合并后）。 */
private data class ModelCapabilities(
    val contextWindow: Int = 0,
    val maxOutput: Int = 0,
    val reasoning: Boolean = false,
    val supportsImages: Boolean = false,
) {
    fun merge(other: ModelCapabilities): ModelCapabilities = ModelCapabilities(
        contextWindow = maxOf(contextWindow, other.contextWindow),
        maxOutput = maxOf(maxOutput, other.maxOutput),
        reasoning = reasoning || other.reasoning,
        supportsImages = supportsImages || other.supportsImages,
    )
}
