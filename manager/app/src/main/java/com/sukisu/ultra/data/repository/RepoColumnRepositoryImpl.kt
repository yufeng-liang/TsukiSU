package com.sukisu.ultra.data.repository

import com.sukisu.ultra.data.model.RepoColumn
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.hasAnyNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

/**
 * 专栏文档是运营手写的清单，正常几十 KB。给 1 MiB 留了几十倍余量：没有上限的话，一个被换掉的
 * 地址就能让 `body.string()` 把一整个大文件读进内存。
 */
internal const val MAX_COLUMNS_RESPONSE_BYTES = 1L * 1024 * 1024

/**
 * 整次专栏请求（连接 + 重定向 + 读体）的上限。全局客户端只设了连接与读的单项超时，一个每十几秒
 * 吐一个字节的地址永远碰不到它们；只有整次调用的封顶拦得住。
 */
internal const val COLUMNS_CALL_TIMEOUT_SECONDS = 15L

/**
 * 备用地址：jsDelivr 在国内直连经常不通（用户没开代理时专栏区就是空的），
 * raw.githubusercontent 在不同网络下也不稳定。多试几个 jsDelivr 的接入点再判定失败。
 */
internal val OFFICIAL_COLUMNS_FALLBACK_URLS = listOf(
    "https://fastly.jsdelivr.net/gh/SukiSU-Community/modules-repo@main/json/columns.json",
    "https://gcore.jsdelivr.net/gh/SukiSU-Community/modules-repo@main/json/columns.json",
    "https://raw.githubusercontent.com/SukiSU-Community/modules-repo/main/json/columns.json",
)

/**
 * 从官方索引仓拉专栏。
 *
 * 只读：不发任何写请求、不执行文档里的步骤。装不装由用户自己在模块页里一步步操作。
 */
class RepoColumnRepositoryImpl(
    /** 索引地址；留成参数是为了让测试能把这一层指向本地假数据。 */
    private val url: String = OFFICIAL_COLUMNS_URL,
    /** 主地址失败后依次尝试的备用地址；测试可以传空表只测主地址。 */
    private val fallbackUrls: List<String> = OFFICIAL_COLUMNS_FALLBACK_URLS,
) : RepoColumnRepository {

    /**
     * 懒初始化：不在构造时碰 [ksuApp]（ViewModel 可能在某些预览/测试环境里先被构造）。
     * `newBuilder` 复制的是共享的连接池与线程池，不会额外建连接资源。
     */
    companion object {
        /**
         * 进程级内存缓存：专栏文档很小（几十 KB），但每次进详情页都会新建一个 ViewModel，
         * 没有缓存就要重新等一次网络。只在这里留一份，不做失效判断——调用方拿到缓存先渲染，
         * 再自己决定要不要刷新。
         */
        @Volatile
        private var cachedColumns: List<RepoColumn>? = null

        /** 上次成功拉到的原始 JSON；断网时拿它兜底，至少让专栏区有东西可点。 */
        private const val CACHE_FILE_NAME = "columns-cache.json"

        private fun cacheFile(): File = File(ksuApp.cacheDir, CACHE_FILE_NAME)

        /**
         * 供上层同步取用：拿到就先渲染，网络回来再覆盖。
         *
         * 内存没有就退到磁盘缓存——用户上次联网看过的专栏，这次断网进来仍然看得到。
         */
        fun peekCached(): List<RepoColumn>? = cachedColumns ?: runCatching {
            val file = cacheFile()
            if (!file.isFile) return@runCatching null
            val columns = RepoColumnParser.parse(file.readText()) ?: return@runCatching null
            cachedColumns = columns
            columns
        }.getOrNull()
    }

    private val client: OkHttpClient by lazy {
        ksuApp.okhttpClient.newBuilder()
            .callTimeout(Duration.ofSeconds(COLUMNS_CALL_TIMEOUT_SECONDS))
            .build()
    }

    override suspend fun fetchColumns(): Result<List<RepoColumn>> = withContext(Dispatchers.IO) {
        // 用软检查（hasAnyNetwork）而不是要求 NET_CAPABILITY_VALIDATED 的硬检查：有些 ROM
        // 从不把网络标记成已验证，硬检查会把明明能上网的机器判成离线。
        if (!hasAnyNetwork(ksuApp)) return@withContext Result.failure(IOException("no network available"))

        var lastError: Exception? = null
        for (candidate in (listOf(url) + fallbackUrls).distinct()) {
            try {
                val body = fetchBody(candidate)
                // 解析不出 columns 数组说明这份文档不是专栏（地址被换掉、CDN 返回了别的内容）。
                val columns = RepoColumnParser.parse(body)
                    ?: throw IOException("columns document has no column list")
                cachedColumns = columns
                // 落盘：下次断网/不开代理时 peekCached() 能从磁盘捞回来。
                runCatching { cacheFile().writeText(body) }
                return@withContext Result.success(columns)
            } catch (e: CancellationException) {
                // 取消不是失败：吞掉它会把这层变成一个「正常的失败结果」，上层就再也取消不掉了。
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IOException("columns endpoint unreachable"))
    }

    private fun fetchBody(target: String): String {
        // 不读 okhttp 磁盘缓存：jsDelivr 给这类 JSON 的 max-age 是 7 天，走共享客户端的缓存会
        // 让运营改过的专栏文案要等一周才生效。noCache 只禁「读缓存」，仍会正常发请求。
        val request = Request.Builder()
            .url(target)
            .cacheControl(CacheControl.Builder().noCache().build())
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.readIndexBody(MAX_COLUMNS_RESPONSE_BYTES)
        }
    }
}
