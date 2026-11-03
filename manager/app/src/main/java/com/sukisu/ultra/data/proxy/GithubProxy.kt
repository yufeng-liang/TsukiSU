package com.sukisu.ultra.data.proxy

import com.sukisu.ultra.data.registry.RegistryUrls

/**
 * GitHub 下载加速：契约层。
 *
 * 本文件只放类型、常量与纯函数，不放网络/存储实现，保证多个并行任务能同时引用它。
 * 实现分别由下列文件提供（见各自 KDoc）：
 * - [ProxyListRepository] → `ProxyListRepositoryImpl.kt`
 * - [ProxyProber] → `ProxyProberImpl.kt`
 * - [ProxyPreference] → `ProxyPreferenceImpl.kt`
 *
 * 安全底线：代理是 TLS 终点，能看见也能改写下载内容。因此
 * 1. 只有 [RegistryUrls.isAllowedReleaseDownloadUrl] 认可的原始 GitHub Release 地址才允许套代理；
 * 2. 校验用的哈希必须来自直连（见 `checkLatestRelease` 与各下载器的 `expected`）；
 * 3. 代理地址本身必须是 https 且只含 host（不带路径/查询/片段）。
 */
enum class ProxyMode(val value: String) {
    /** 官方直连，完全不用代理。 */
    OFFICIAL("official"),

    /** 自动选优：粘性上次成功的代理，失败按顺序降级，最后回退直连。 */
    AUTO("auto"),

    /** 固定使用某一个代理。 */
    PINNED("pinned"),
    ;

    companion object {
        fun from(value: String?): ProxyMode = entries.firstOrNull { it.value == value } ?: OFFICIAL
    }
}

/** 一个 GitHub 下载代理。[baseUrl] 形如 `https://gh-proxy.org`，不带尾斜杠。 */
data class ProxyEntry(
    val id: String,
    val name: String,
    val baseUrl: String,
    val note: String = "",
    val builtin: Boolean = true,
)

/** 代理健康检查结果。 */
data class ProxyHealth(
    val id: String,
    val reachable: Boolean,
    val latencyMs: Long,
    val checkedAt: Long,
    val message: String = "",
)

/** 内置代理列表（实测可用的一批；其余由远程 proxies.json 增补）。 */
val BUILTIN_PROXIES: List<ProxyEntry> = listOf(
    ProxyEntry("gh-proxy-org", "gh-proxy.org", "https://gh-proxy.org", "实测最快"),
    ProxyEntry("gh-proxy-com", "gh-proxy.com", "https://gh-proxy.com", "稳定"),
    ProxyEntry("ghproxy-net", "ghproxy.net", "https://ghproxy.net", "速度波动较大"),
    ProxyEntry("hk-gh-proxy", "hk.gh-proxy.com", "https://hk.gh-proxy.com", "境外节点，偏慢"),
)

/**
 * 代理地址与 URL 的互相转换。
 *
 * 代理前缀规则是把原始 URL 整体拼在后面：`https://gh-proxy.org/https://github.com/...`。
 */
object ProxyUrl {

    private const val MAX_PROXY_BASE_LENGTH = 128

    /** 代理地址必须是 https、无路径/查询/片段、无 userinfo，且长度合理。 */
    fun isValidBaseUrl(baseUrl: String): Boolean {
        if (baseUrl.isEmpty() || baseUrl.length > MAX_PROXY_BASE_LENGTH) return false
        val uri = runCatching { java.net.URI(baseUrl) }.getOrNull() ?: return false
        if (!uri.isAbsolute || uri.isOpaque) return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.host.isNullOrEmpty()) return false
        if (uri.rawUserInfo != null) return false
        if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") return false
        if (uri.rawQuery != null || uri.rawFragment != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        return true
    }

    /** 把原始 GitHub 地址套到某个代理上。 */
    fun build(entry: ProxyEntry, originalUrl: String): String =
        entry.baseUrl.trimEnd('/') + "/" + originalUrl

    /** 如果 [url] 是某个已知代理包出来的，返回内层原始地址；否则返回 null。 */
    fun unwrap(url: String, entries: List<ProxyEntry>): String? {
        for (entry in entries) {
            val prefix = entry.baseUrl.trimEnd('/') + "/"
            if (url.startsWith(prefix, ignoreCase = true)) {
                val inner = url.substring(prefix.length)
                if (RegistryUrls.isAllowedReleaseDownloadUrl(inner)) return inner
            }
        }
        return null
    }

    /** 找到 [url] 对应的代理条目。 */
    fun entryOf(url: String, entries: List<ProxyEntry>): ProxyEntry? = entries.firstOrNull { entry ->
        url.startsWith(entry.baseUrl.trimEnd('/') + "/", ignoreCase = true)
    }

    /**
     * 只看形状：路径里是否还套着另一个绝对 URL（`https://代理/<https://github.com/...>`）。
     *
     * 给「没有哈希、因而必须直连」的下载做纵深防御——这类地址不该经过任何代理。
     * 不依赖代理列表，所以服务层拿不到 entries 时也能判。
     */
    fun looksLikeProxied(url: String): Boolean {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return false
        val afterScheme = url.substring(schemeEnd + 3)
        val pathStart = afterScheme.indexOf('/')
        if (pathStart < 0) return false
        return afterScheme.substring(pathStart + 1).contains("://")
    }
}

/** 竞速排序里代表「直连」的通道 id。 */
const val DIRECT_ID: String = "direct"

/**
 * 纯函数路由：把一次下载展开成按优先级排列的候选 URL 列表。
 *
 * 调用方（下载器）按顺序尝试，第一个成功的即用。
 *
 * [ranking] 是竞速实测出来的通道顺序（含 [DIRECT_ID]）：开了梯子的用户直连往往最快，
 * 没开梯子的用户直连几乎连不上，所以**不能写死「代理优先」或「直连优先」**，
 * 有实测排序就照它走，没有才退回保守默认。
 */
object GithubProxyRouter {

    /**
     * @param originalUrl 原始 GitHub Release 地址；不是 release 地址时永远只返回它自己。
     * @param mode 用户选择的模式。
     * @param entries 当前可用代理（已过滤启用状态）。
     * @param pinnedId [ProxyMode.PINNED] 时用户指定的代理 id。
     * @param lastGoodId [ProxyMode.AUTO] 时上次成功的代理 id（粘性）。
     * @param ranking 竞速实测顺序（通道 id，含 [DIRECT_ID]）；空表示还没测过。
     */
    fun candidates(
        originalUrl: String,
        mode: ProxyMode,
        entries: List<ProxyEntry>,
        pinnedId: String? = null,
        lastGoodId: String? = null,
        ranking: List<String> = emptyList(),
    ): List<String> {
        if (!RegistryUrls.isAllowedReleaseDownloadUrl(originalUrl)) return listOf(originalUrl)
        if (entries.isEmpty()) return listOf(originalUrl)

        val usable = entries.filter { ProxyUrl.isValidBaseUrl(it.baseUrl) }
        if (usable.isEmpty()) return listOf(originalUrl)

        return when (mode) {
            ProxyMode.OFFICIAL -> listOf(originalUrl)

            ProxyMode.PINNED -> {
                // 指定的代理被下架或改名了：退回 AUTO 的保守顺序，而不是只剩直连。
                // 用户选某个代理的动机通常正是直连不通，纯直连等于把这个设置废掉。
                val pinned = usable.firstOrNull { it.id == pinnedId }
                    ?: return autoOrder(originalUrl, usable, ranking, lastGoodId)
                listOfDistinct(ProxyUrl.build(pinned, originalUrl), originalUrl)
            }

            ProxyMode.AUTO -> autoOrder(originalUrl, usable, ranking, lastGoodId)
        }
    }

    /** AUTO 与「PINNED 失效」共用的保守顺序：竞速排名 → 上次成功的 → 列表序 → 直连兜底。 */
    private fun autoOrder(
        originalUrl: String,
        usable: List<ProxyEntry>,
        ranking: List<String>,
        lastGoodId: String?,
    ): List<String> {
        val byId = usable.associateBy { it.id }
        val orderedUrls = buildList {
            // 1. 竞速排序优先：直连也在里面，谁实测快谁在前面。
            for (id in ranking) {
                if (id == DIRECT_ID) {
                    add(originalUrl)
                } else {
                    byId[id]?.let { add(ProxyUrl.build(it, originalUrl)) }
                }
            }
            // 2. 没测过 / 没排进来的：上次成功的先上，其余按列表顺序。
            val rankedIds = ranking.toSet()
            val rest = buildList {
                usable.firstOrNull { it.id == lastGoodId && it.id !in rankedIds }?.let { add(it) }
                usable.forEach { if (it.id != lastGoodId && it.id !in rankedIds) add(it) }
            }
            rest.forEach { add(ProxyUrl.build(it, originalUrl)) }
            // 3. 直连永远兜底。
            add(originalUrl)
        }
        return orderedUrls.distinct()
    }

    private fun listOfDistinct(vararg items: String): List<String> = items.toList().distinct()
}

/** 代理列表来源：内置 [BUILTIN_PROXIES] + 远程 `json/proxies.json`（缓存后用）。 */
interface ProxyListRepository {
    suspend fun load(): List<ProxyEntry>

    suspend fun refresh(): Result<List<ProxyEntry>>
}

/** 健康检查与首包竞速。 */
interface ProxyProber {
    /**
     * 并发探测每个代理的可用性与延迟。
     * @param sampleUrl 用于探测的原始 GitHub Release 地址；为空时只看连通性（探测代理根路径）。
     */
    suspend fun probeAll(
        entries: List<ProxyEntry>,
        sampleUrl: String? = null,
        timeoutMs: Long = 8_000,
    ): List<ProxyHealth>

    /**
     * 首包竞速：把「直连 + 每个代理」都当成选手，并发请求前 [sampleBytes] 字节，
     * 按「拿到可读数据的时间」排序。**直连与代理同台竞争**——开了系统梯子的用户
     * 直连通常最快，没开梯子的用户直连基本连不上，只有实测才能区分。
     *
     * @return 按快慢排序的通道 id 列表（含 [DIRECT_ID]）；全部失败时为空列表。
     */
    suspend fun race(
        originalUrl: String,
        entries: List<ProxyEntry>,
        sampleBytes: Int = 64 * 1024,
        timeoutMs: Long = 8_000,
    ): List<String>

    /**
     * 取「当前网络下该走哪条通道」的排序结果：缓存（1 小时 + 同一网络指纹）新鲜就直接用，
     * 否则跑一次 [race] 并写回 [ProxyPreference.setRanking]。
     *
     * 下载器**只需调用这个方法拿排序**，不要自己决定先试代理还是先试直连。
     */
    suspend fun ranking(originalUrl: String, force: Boolean = false): List<String>
}

/** 远程代理列表地址（与 modules.json / columns.json 同一发布路径）。 */
const val PROXIES_JSON_URL: String =
    "https://cdn.jsdelivr.net/gh/SukiSU-Community/modules-repo@main/json/proxies.json"

/**
 * 生产环境默认实例（懒加载单例）。
 *
 * 实现类由 [ProxyListRepositoryImpl] / [ProxyProberImpl] / [ProxyPreferenceImpl] 提供，
 * 调用方一律通过这里取实例，不要在业务代码里各自 new，避免设置页与下载器读到两份状态。
 */
object ProxyRuntime {
    val list: ProxyListRepository by lazy { ProxyListRepositoryImpl() }
    val prober: ProxyProber by lazy { ProxyProberImpl() }
    val prefs: ProxyPreference by lazy { ProxyPreferenceImpl() }
}

/** 用户选择与粘性记忆（SharedPreferences 持久化）。 */
interface ProxyPreference {
    fun mode(): ProxyMode

    fun setMode(mode: ProxyMode)

    fun pinnedId(): String?

    fun setPinnedId(id: String?)

    fun lastGoodId(): String?

    /** 记录一次成功下载，[speedBytesPerSec] 用于判断是否值得继续粘着。 */
    fun recordSuccess(entry: ProxyEntry, speedBytesPerSec: Long)

    fun recordFailure(entryId: String)

    /** 当前网络指纹（如 `WIFI`、`MOBILE`、`ETHERNET`），换网时变化。 */
    fun networkKey(): String

    /** 上次竞速排序（通道 id，含 [DIRECT_ID]）。 */
    fun ranking(): List<String>

    /** 排序是否仍然可信：同一网络指纹且不超过 1 小时。 */
    fun isRankingFresh(): Boolean

    /** 写入一次竞速结果（同时写时间戳与当时的网络指纹）。 */
    fun setRanking(order: List<String>)
}
