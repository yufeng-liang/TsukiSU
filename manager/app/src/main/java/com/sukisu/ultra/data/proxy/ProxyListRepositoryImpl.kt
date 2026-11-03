package com.sukisu.ultra.data.proxy

import android.content.Context
import android.content.SharedPreferences
import com.sukisu.ultra.data.registry.RegistryUrls
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.hasAnyNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** 缓存单独一份 prefs：清设置页那份主 prefs 时不该连带把代理列表也清掉。 */
private const val CACHE_PREFS_NAME = "proxy_cache"
private const val KEY_CACHED_JSON = "proxies_json"
private const val KEY_CACHED_AT = "proxies_fetched_at"

/**
 * 缓存有效期。远程清单是运营手改的 JSON，一天拉一次足够；过期时先给内置列表、后台再补，
 * 用户打开页面永远不会卡在一个网络请求上。
 */
private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000

/**
 * 响应体上限。正常文档只有几 KB，留 1 MiB 是几十倍余量：没有上限的话，一个被换掉的地址
 * 就能让 `body.string()` 把一整个大文件读进内存。
 */
private const val MAX_PROXIES_BODY_BYTES = 1L * 1024 * 1024

/**
 * 整次请求（连接 + 重定向 + 读体）的上限。全局客户端只设了连接与读的单项超时，一个每十几秒
 * 吐一个字节的地址永远碰不到它们；只有整次调用封顶才拦得住。
 */
private const val CALL_TIMEOUT_SECONDS = 15L

/**
 * 代理列表仓库：远程清单 + 内置兜底。
 *
 * 这里的取舍是「永远有得用，新鲜度可以晚一点」：
 * - 有 24h 内的缓存就先给缓存，不碰网络；
 * - 没缓存或过期就先给 [BUILTIN_PROXIES]，同时在后台拉一次远程（失败静默，下次进页面再试）。
 *
 * 结果是网络再差也不会看到一个空列表，而远程新增的代理最迟一天后出现。
 */
class ProxyListRepositoryImpl : ProxyListRepository {

    /**
     * 懒初始化：不在构造时碰 [ksuApp]（ViewModel / 设置页可能在预览或测试环境里先被构造）。
     * `newBuilder` 复制的是共享的连接池与线程池，不会额外建连接资源。
     */
    private val client: OkHttpClient by lazy {
        ksuApp.okhttpClient.newBuilder()
            .callTimeout(Duration.ofSeconds(CALL_TIMEOUT_SECONDS))
            .build()
    }

    /**
     * 后台刷新的闸门：同一时刻只允许一次。
     *
     * 列表页 / 设置页各调一次 [load] 时，若缓存已过期，没有这道闸就会同时发出好几份同样的
     * 请求；对同一份静态 JSON 来说纯属浪费流量。
     */
    private val refreshing = AtomicBoolean(false)

    /** 后台刷新用的作用域：不跟随任何界面生命周期，页面关掉也让它把缓存写完。 */
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun load(): List<ProxyEntry> = withContext(Dispatchers.IO) {
        val cached = cachedEntries()
        if (cached != null) return@withContext cached
        triggerBackgroundRefresh()
        BUILTIN_PROXIES
    }

    override suspend fun refresh(): Result<List<ProxyEntry>> = withContext(Dispatchers.IO) {
        try {
            // 软检查（有没有网）而不是要求 NET_CAPABILITY_VALIDATED：有些 ROM 从不把网络标成
            // 已验证，硬检查会把明明能上网的机器判成离线。
            if (!hasAnyNetwork(ksuApp)) throw IOException("no network available")

            // jsDelivr 对 @main 回 max-age=604800：走共享 OkHttp 的磁盘缓存会把旧列表钉住最长 7 天，
            // 「远程更新」就等于失效。noCache 只是不走本地缓存，仍带 ETag/If-None-Match，
            // 内容没变时服务端只回 304，代价很小。
            val request = Request.Builder()
                .url(PROXIES_JSON_URL)
                .cacheControl(CacheControl.Builder().noCache().build())
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                readLimitedBody(response)
            }

            // 解析不出 proxies 数组说明这份文档不是代理列表（地址被换掉、CDN 返回了别的内容）。
            val remote = parseProxyList(body)
                ?: throw IOException("proxies document has no proxy list")

            // 只有整份文档确实可用时才写缓存：写进去一份坏文档，接下来 24 小时都会拿它当缓存。
            writeCache(body)
            Result.success(mergeProxyList(remote))
        } catch (e: CancellationException) {
            // 取消不是失败：吞掉它会把这层变成一个「正常的失败结果」，上层就再也取消不掉了。
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 新鲜缓存里的条目；没有缓存、缓存过期或缓存坏掉都返回 null，由调用方回落内置列表。 */
    private fun cachedEntries(): List<ProxyEntry>? {
        val prefs = prefs() ?: return null
        val body = prefs.getString(KEY_CACHED_JSON, null)
        if (body.isNullOrBlank()) return null
        val fetchedAt = prefs.getLong(KEY_CACHED_AT, 0L)
        if (fetchedAt <= 0L) return null
        // 时钟被往回拨时 `now - fetchedAt` 会变成负数，按「刚拉过」处理比按过期处理更合理：
        // 反过来会让每次进页面都白拉一遍。
        if (System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS) return null
        val remote = parseProxyList(body) ?: return null
        return mergeProxyList(remote)
    }

    /**
     * 后台拉一次远程列表，不阻塞调用方。
     *
     * 失败完全静默：[refresh] 自己把异常收进 `Result`，这里只是不等它，下次进入页面还会再试。
     */
    private fun triggerBackgroundRefresh() {
        if (!refreshing.compareAndSet(false, true)) return
        refreshScope.launch {
            try {
                refresh()
            } finally {
                // 放闸必须在 finally 里：中途被取消也得让下一次 load 有机会再试。
                refreshing.set(false)
            }
        }
    }

    /** [Response] 只能读一次，先 peek 出「上限 + 1 字节」判断有没有超限，再整份转成文本。 */
    private fun readLimitedBody(response: Response): String {
        val bytes = response.peekBody(MAX_PROXIES_BODY_BYTES + 1).bytes()
        if (bytes.size > MAX_PROXIES_BODY_BYTES) {
            throw IOException("proxies document exceeds $MAX_PROXIES_BODY_BYTES bytes")
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun writeCache(body: String) {
        prefs()?.edit()
            ?.putString(KEY_CACHED_JSON, body)
            ?.putLong(KEY_CACHED_AT, System.currentTimeMillis())
            ?.apply()
    }

    /** 取不到 prefs（几乎只会在单元测试里）时让缓存静默失效，不要为一份缓存把调用方拖崩。 */
    private fun prefs(): SharedPreferences? = runCatching {
        ksuApp.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()
}

/**
 * 解析 `{"schemaVersion":1,"proxies":[{"id","name","baseUrl","note"}]}`。
 *
 * 文档形状不对（不是 JSON 对象、没有 proxies 数组）返回 null，调用方据此回落内置列表。
 * 单条坏掉只丢它自己：地址不合法、没有 id、id 或地址与前面重复的条目都跳过——运营手改的
 * 清单里有一个 typo，不该让整份列表消失。
 *
 * 不校验 `schemaVersion`：将来加字段时旧客户端要能忽略不认识的键继续用，这里只认 proxies。
 */
internal fun parseProxyList(body: String): List<ProxyEntry>? {
    val root = runCatching {
        JSONObject(body.trimStart('\uFEFF').trimStart())
    }.getOrNull() ?: return null
    val proxies = root.optJSONArray("proxies") ?: return null

    val out = ArrayList<ProxyEntry>(proxies.length())
    val seenIds = HashSet<String>(proxies.length())
    val seenUrls = HashSet<String>(proxies.length())
    for (index in 0 until proxies.length()) {
        val item = proxies.optJSONObject(index) ?: continue
        val id = item.text("id")
        val rawUrl = item.text("baseUrl")
        if (id.isEmpty()) continue
        // `direct` 是「不走代理」的保留 id，远程清单写它等于凭空造一个假代理条目。
        if (id == DIRECT_ID) continue
        if (!ProxyUrl.isValidBaseUrl(rawUrl)) continue
        // 统一去掉尾斜杠再比较：`https://a.com` 与 `https://a.com/` 是同一个代理。
        val baseUrl = rawUrl.trimEnd('/')
        // 代理主机与 GitHub 自己的下载主机重名时只会套娃（把请求再发给 GitHub），不可能是真代理。
        if (hostOf(baseUrl) in RegistryUrls.DOWNLOAD_HOSTS) continue
        if (!seenIds.add(id)) continue
        if (!seenUrls.add(baseUrl)) continue
        out += ProxyEntry(
            id = id,
            // 没写名字时用主机名兜底：列表里出现一个空标题比显示地址更让人困惑。
            name = item.text("name").ifEmpty { hostOf(baseUrl) },
            baseUrl = baseUrl,
            note = item.text("note"),
            builtin = false,
        )
    }
    return out
}

/**
 * 远程清单生效时**以远程为准**，内置只在远程拿不到东西时兜底。
 *
 * 早先的写法是「远程在前、内置补齐」，后果是远程删掉某条代理也不生效——被删的那条会被内置
 * 原地顶回来，维护者改 json 像在跟一个看不见的对手拔河。现在：远程非空就照单全收，
 * 想删就删；远程为空（或文档坏到没有条目）才回退内置，免得一次改错让所有人没代理可用。
 *
 * 条目一律 `builtin = false`：界面靠这个标记区分「远程下发的」与「随包内置的」。
 */
internal fun mergeProxyList(remote: List<ProxyEntry>): List<ProxyEntry> =
    if (remote.isEmpty()) BUILTIN_PROXIES else remote

/**
 * 取一个字符串字段并去掉首尾空白；缺失或显式 `null` 都当空串。
 *
 * 不能直接用 `optString(key, "")`：org.json 对显式 `null` 返回的是字面量 `"null"`，
 * 那会把「没填」变成一句看得见的废话。
 */
private fun JSONObject.text(key: String): String =
    if (isNull(key)) "" else optString(key, "").trim()

/** 地址已通过 [ProxyUrl.isValidBaseUrl]，主机名理论上一定拿得到；拿不到就退回空串。 */
private fun hostOf(baseUrl: String): String =
    runCatching { java.net.URI(baseUrl).host }.getOrNull().orEmpty()
