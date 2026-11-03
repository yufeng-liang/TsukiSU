package com.sukisu.ultra.data.proxy

import android.util.Log
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * 代理探测：一次并发体检 + 一次通道竞速。
 *
 * 两件事分开做是有意的。体检只需要知道「连不连得上」，用 HEAD 最省流量；HEAD 成功却不代表
 * 下得动东西（有的代理对 HEAD 放行、对 GET 限速），所以真正排序走 [race]：谁先吐出第一个
 * 字节谁靠前，用户感知的快慢本来就取决于这个。
 */
class ProxyProberImpl(
    /**
     * 竞速排序要拿当前代理列表当选手，[ranking] 还得回写缓存，所以这里把两个协作者显式接进来。
     * 默认值指向 [ProxyRuntime] 而不是各业务自己 new，保证设置页与下载器读到同一份状态
     * （懒初始化，构造这个类不会提前把别的单例拉起来）。
     */
    private val proxies: ProxyListRepository = ProxyRuntime.list,
    private val prefs: ProxyPreference = ProxyRuntime.prefs,
) : ProxyProber {

    /** 懒初始化：不在构造时碰 [ksuApp]。`newBuilder` 复制的是共享的连接池与线程池。 */
    private val client: OkHttpClient by lazy { ksuApp.okhttpClient }

    override suspend fun probeAll(
        entries: List<ProxyEntry>,
        sampleUrl: String?,
        timeoutMs: Long,
    ): List<ProxyHealth> {
        if (entries.isEmpty()) return emptyList()
        val budget = timeoutMs.coerceAtLeast(MIN_TIMEOUT_MS)
        return coroutineScope {
            // 一个代理卡住不该拖住其他代理的结果：每个探测自己带超时，失败也只是它自己那条
            // 变成 unreachable。
            entries.map { entry ->
                async(Dispatchers.IO) { probe(entry, sampleUrl, budget) }
            }.awaitAll()
        }
    }

    override suspend fun race(
        originalUrl: String,
        entries: List<ProxyEntry>,
        sampleBytes: Int,
        timeoutMs: Long,
    ): List<String> {
        val budget = timeoutMs.coerceAtLeast(MIN_TIMEOUT_MS)
        val bytes = sampleBytes.coerceAtLeast(1)
        val contestants = buildList {
            // 直连也是选手：开了系统梯子的用户直连常常比任何代理都快，写死「代理优先」只会
            // 白绕一圈；没开梯子的用户直连基本连不上，只有实测分得出这两种情况。
            add(DIRECT_ID to originalUrl)
            for (entry in entries) add(entry.id to ProxyUrl.build(entry, originalUrl))
        }.distinctBy { it.first }

        // 每个选手的请求都有自己的 callTimeout，所以在这里等齐是一次**有限**等待（上限就是
        // budget）。拿完整顺序比「谁先赢就先返回」有用：竞速结果会被路由器当成整条降级链的
        // 次序，缺了中间几档，第二次下载就得重新摸黑试。
        val measured = coroutineScope {
            contestants.map { (id, url) ->
                async(Dispatchers.IO) { id to timeToFirstByte(url, bytes, budget) }
            }.awaitAll()
        }
        return measured
            .mapNotNull { (id, latency) -> latency?.let { id to it } }
            // 稳定排序：一样快的时候保持选手顺序（直连在前），结果可复现。
            .sortedBy { it.second }
            .map { it.first }
    }

    override suspend fun ranking(originalUrl: String, force: Boolean): List<String> {
        if (!force && prefs.isRankingFresh()) {
            val cached = prefs.ranking()
            // 缓存的 id 可能指向已被下架的代理；路由器会跳过不认识的 id，这里原样交出去即可。
            if (cached.isNotEmpty()) return cached
        }
        val measured = race(originalUrl, proxies.load())
        if (measured.isNotEmpty()) {
            prefs.setRanking(measured)
            return measured
        }
        // 全部失败（断网、样本地址失效）时不覆盖缓存：留一份旧排序，比留一份空的更有用。
        // 返回空列表会让调用方以为「从没测过」，于是一次下载里对每个 origin 各跑一遍竞速。
        val stale = prefs.ranking()
        Log.w("ProxyProber", "竞速全部失败，沿用上次排序（${stale.size} 条）")
        return stale
    }

    /**
     * 探测单个代理。
     *
     * 没有 [sampleUrl] 时只探代理根路径，用来回答「这个地址还活着吗」；有样本时探的是
     * 「代理 + 一个真实 release 地址」，能顺带验证它真的会转发 GitHub 的内容。
     */
    private suspend fun probe(
        entry: ProxyEntry,
        sampleUrl: String?,
        timeoutMs: Long,
    ): ProxyHealth {
        val startedAt = System.currentTimeMillis()
        val target = if (sampleUrl.isNullOrBlank()) entry.baseUrl else ProxyUrl.build(entry, sampleUrl)
        var reachable = false
        var message = ""
        try {
            val call = client.newCall(Request.Builder().url(target).head().build())
            val code = withTimeout(timeoutMs) { execute(call, timeoutMs).use { it.code } }
            // 2xx/3xx 都算活着：3xx 只是把人往源站或别的节点带，连通性本身没问题。
            reachable = code in 200..399
            if (!reachable) message = "HTTP $code"
        } catch (e: TimeoutCancellationException) {
            message = "timeout after $timeoutMs ms"
        } catch (e: CancellationException) {
            // 调用方取消（页面关了）要照常往外传，不能记成「这个代理不行」。
            throw e
        } catch (e: Exception) {
            message = e.message ?: e.javaClass.simpleName
        }
        val finishedAt = System.currentTimeMillis()
        return ProxyHealth(
            id = entry.id,
            reachable = reachable,
            latencyMs = finishedAt - startedAt,
            checkedAt = finishedAt,
            message = message,
        )
    }

    /**
     * 发 Range 请求，只要读到 1 字节就记下耗时（毫秒），失败或超时返回 null。
     *
     * 不按 [sampleBytes] 读满样本：读满只会让慢代理一直占着线程，而「能不能开始吐数据」才是
     * 用户真正感知的快慢。响应体在 `use` 里关掉，连接能及时还回连接池。
     *
     * 这里不用协程的 `withTimeout`：它打断不了阻塞中的 socket 读，反而会在临界点上把一次
     * 正常的成功读判成超时，让这个选手从排序里凭空消失。真正的上限交给 OkHttp 的 callTimeout，
     * 它连 DNS、连接和读响应都算在里面。
     */
    private fun timeToFirstByte(url: String, sampleBytes: Int, timeoutMs: Long): Long? {
        val startedAt = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${sampleBytes - 1}")
                .build()
            val call = client.newCall(request)
            runCatching { call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS) }
            val readable = call.execute().use { response ->
                response.isSuccessful && response.body.byteStream().read() >= 0
            }
            if (readable) System.currentTimeMillis() - startedAt else null
        } catch (e: Exception) {
            // 连不上、被限流、4xx/5xx 都只是「这个选手这次没跑出来」，不影响别人的名次。
            null
        }
    }

    /**
     * 阻塞执行，并把整次调用的上限压到 [timeoutMs]。
     *
     * `call.execute()` 不响应协程取消，光靠外面的 `withTimeout` 拦不住卡在 socket 上的线程；
     * 这里再给 OkHttp 自己的 callTimeout 设同一个值，两层同时到点，线程才真的能回来——
     * 否则一批探测里只要有一个黑洞地址，Dispatchers.IO 的线程就会被它占着。
     */
    private fun execute(call: Call, timeoutMs: Long): Response {
        runCatching { call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS) }
        return call.execute()
    }

    private companion object {
        /** 0 或负数的超时在 `withTimeout` 里等于「立刻失败」，没有诊断价值，兜到 1ms 起。 */
        const val MIN_TIMEOUT_MS = 1L
    }
}
