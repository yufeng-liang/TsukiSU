package com.sukisu.ultra.ui.util

import android.net.Uri
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import com.sukisu.ultra.data.proxy.GithubProxyRouter
import com.sukisu.ultra.data.proxy.ProxyEntry
import com.sukisu.ultra.data.proxy.ProxyMode
import com.sukisu.ultra.data.proxy.ProxyRuntime
import com.sukisu.ultra.data.proxy.ProxyUrl
import com.sukisu.ultra.data.registry.DownloadVerificationException
import com.sukisu.ultra.data.registry.ExpectedFile
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.module.LatestVersionInfo
import okhttp3.Request

/**
 * @author weishu
 * @date 2023/6/22.
 *
 * [altUrl] is tried once when [url] fails, which lets a repository whose declared asset
 * host has moved still install: the index keeps the old host while the files follow the
 * project to its new one.
 *
 * [expected] is the size + SHA-256 a registry release declared; [onFailed] reports the last
 * failed state (including a verification failure) instead of returning silently.
 *
 * 每个地址都会先按「GitHub 下载加速」展开成候选列表（粘性代理 → 其余代理 → 直连），
 * 逐个交给 [DownloadManager] 尝试。带 [expected] 时哈希仍由服务层兜底；没有哈希也照样走加速，
 * 这是「尽可能快」优先于「可校验」的取舍（updateJson 不保证给 sha256）。
 */
suspend fun download(
    url: String,
    fileName: String,
    altUrl: String? = null,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {},
    expected: ExpectedFile? = null,
    onFailed: (DownloadManager.DownloadState) -> Unit = {},
) {
    onDownloading()

    // 主地址在前、备用地址在后，各自展开；代理全挂才会轮到备用地址，直连是每条链路最后的兜底。
    // 没有哈希也照样展开：模块 updateJson 不保证给 sha256，而用户要的是尽量走加速。
    val origins = listOfNotNull(url, altUrl?.takeIf { it.isNotBlank() && it != url })
    val (candidates, proxies) = expandCandidates(origins)

    var lastState: DownloadManager.DownloadState? = null
    for ((index, candidate) in candidates.withIndex()) {
        val downloadId = DownloadManager.enqueue(
            context = ksuApp,
            url = candidate,
            fileName = fileName,
            onCompleted = onDownloaded,
            expected = expected,
            proxyBase = ProxyUrl.entryOf(candidate, proxies)?.baseUrl,
            // 只有最后一个候选才弹失败通知：串行撞 4 个代理、每个都弹一条，等于把用户埋掉。
            notifyOnFailure = index == candidates.lastIndex,
        )
        // 服务没起来（-1）：换下一个候选，别空等一个永远不会出现的状态。
        if (downloadId < 0) continue

        val state = withTimeoutOrNull(downloadWaitTimeoutMs(expected)) {
            DownloadManager.downloads
                .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
                .first { map ->
                    val status = map[downloadId]?.status
                    status == DownloadManager.Status.COMPLETED ||
                        status == DownloadManager.Status.FAILED
                }[downloadId]
        }

        if (state == null) {
            // 服务被杀 / 再也不会回调：主动取消这一条，别让它继续占着通知栏和网络。
            DownloadService.cancel(ksuApp, downloadId)
            Log.w("Downloader", "download timed out for $candidate")
            continue
        }

        // 只有「确实是代理包出来的」候选才计入代理健康度，直连不写粘性记忆。
        val proxied = ProxyUrl.entryOf(candidate, proxies)
        if (state.status == DownloadManager.Status.COMPLETED) {
            // 速度用于判断下次还要不要粘在这个代理上；拿不到字节/耗时就是 0。
            proxied?.let { ProxyRuntime.prefs.recordSuccess(it, state.speedBytesPerSec) }
            return
        }
        lastState = state
        // 被本地闸门当场拒掉的地址（校验型下载不认某个代理）不代表代理不可用，不能记黑账。
        val rejectedBeforeNetwork =
            state.verificationFailure == DownloadVerificationException.Reason.URL_NOT_ALLOWED
        if (!rejectedBeforeNetwork) {
            proxied?.let { ProxyRuntime.prefs.recordFailure(it.id) }
        }
        if (index < candidates.lastIndex) {
            Log.w("Downloader", "download failed for $candidate, retrying ${candidates[index + 1]}")
        }
    }

    val failed = lastState
    if (failed != null) withContext(Dispatchers.Main) { onFailed(failed) }
}

/**
 * 把 [origins] 展开成按优先级排列的候选地址，并带回代理条目，便于事后记成功/失败。
 *
 * 不同原始地址之间也会去重（主地址与备用地址可能指向同一个资产）。代理配置读不到时
 * 退回「只下原始地址」：加速是锦上添花，不能因为它把正常下载挡死。
 */
private suspend fun expandCandidates(
    origins: List<String>,
): Pair<List<String>, List<ProxyEntry>> {
    val proxies = runCatching { ProxyRuntime.list.load() }.getOrDefault(emptyList())
    val expanded = runCatching {
        val mode = ProxyRuntime.prefs.mode()
        // 官方直连就是直连：不读排序、不竞速，也不产生任何测速流量。
        if (mode == ProxyMode.OFFICIAL) return@runCatching origins
        val pinnedId = ProxyRuntime.prefs.pinnedId()
        val lastGoodId = ProxyRuntime.prefs.lastGoodId()
        buildList {
            for (origin in origins) {
                val ranking = if (mode == ProxyMode.AUTO) rankingFor(origin) else emptyList()
                GithubProxyRouter.candidates(origin, mode, proxies, pinnedId, lastGoodId, ranking)
                    .filterNot { it in this }
                    .forEach { add(it) }
            }
        }
    }.getOrElse {
        Log.w("Downloader", "代理路由不可用，退回直连", it)
        origins
    }
    return (if (expanded.isEmpty()) origins else expanded) to proxies
}

/**
 * 竞速排序，最多软等 [RANKING_SOFT_WAIT_MS]。
 *
 * 首次竞速要跑满 8 秒；让用户盯着「正在准备下载」8 秒不值得。超时就按现有（可能为空的）
 * 顺序开跑，同时让 [ProxyProber.warmRanking] 在后台把结果写完，下一次下载直接命中缓存。
 */
private suspend fun rankingFor(origin: String): List<String> {
    ProxyRuntime.prober.warmRanking(origin)
    val measured = withTimeoutOrNull(RANKING_SOFT_WAIT_MS) {
        runCatching { ProxyRuntime.prober.ranking(origin) }.getOrDefault(emptyList())
    }
    return measured ?: emptyList()
}

/**
 * 单条候选的等待上限。
 *
 * 没有上限时，服务被杀、通知栏任务被清掉都会让 [download] 永远停在 first 等待上，整个更新
 * 流程卡死。按期望大小估一个宽松上限（64 KB/s 的龟速也够用），小文件有固定下限，大文件封顶。
 */
private fun downloadWaitTimeoutMs(expected: ExpectedFile?): Long {
    val size = expected?.size?.takeIf { it > 0 } ?: 0L
    val estimated = size / 64 * 1000 / 1024
    return (estimated + DOWNLOAD_WAIT_MIN_MS).coerceAtMost(DOWNLOAD_WAIT_MAX_MS)
}

private const val RANKING_SOFT_WAIT_MS = 2_000L
private const val DOWNLOAD_WAIT_MIN_MS = 5 * 60 * 1000L
private const val DOWNLOAD_WAIT_MAX_MS = 30 * 60 * 1000L

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ksuApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}

/**
 * 管理器自身更新的检查地址。
 *
 * 上游原实现写死的是官方仓库 SukiSU-Ultra/SukiSU-Ultra，但本机 APK 是 fork 版本：
 * versionCode 走 fork 自己的提交数（manager/build.gradle.kts 的 getVersionCode），
 * 与官方不同源，两者不可比。官方版本号一旦反超，用户会被引导去下载官方 APK——
 * 签名与本机不同，Android 不允许覆盖安装，只能卸载重装（仓库源配置会丢）。
 * 所以这里改成查本 fork 的最新 release。
 */
internal const val SELF_UPDATE_REPO = "yufeng-liang/SukiSU-Ultra"

/**
 * 从 release 资产名里认出管理器 APK 并抽出 versionCode。
 *
 * 本 fork 的 APK 形如 `SukiSU_mod_41151-release.apk`（versionName 是品牌标识 mod，
 * 版本号只在文件名末尾出现一次）；release 里同一批还躺着 8 个 `.ko`，以及可选
 * 的随机包名变体 `…-release-spoofed.apk`（不是给用户常规升级用的，排除掉）。
 * 认不出就返回 null，调用方按“没有更新”处理。
 */
internal fun matchSelfUpdateApk(name: String): Long? {
    if (!name.endsWith("-release.apk")) return null
    if (name.endsWith("-release-spoofed.apk")) return null
    // 上游用 SukiSU_v4.2.1_40994-release.apk 这套命名。本 fork 的 versionName 是
    // mod，绝不会以 v 开头；这里显式挡掉，是防「某次 release 误传了
    // 官方 APK」——那种情况下把用户引导去装它是覆盖安装不了的。
    if (name.startsWith("SukiSU_v")) return null
    val match = Regex("""^SukiSU_.+_(\d+)-release\.apk$""").find(name) ?: return null
    return match.groupValues[1].toLongOrNull()
}

/**
 * 检查管理器更新。
 *
 * 上游曾有一条独立的 CI 通道（manager-ci 滚动 release）与 `checkNewCiVersion`，
 * 但本 fork 不发布 manager-ci —— 两个入口最终都落到同一次 fork release 查询上，
 * 开着 CI 开关只是把同一个 latest 再查一遍，首页会同时弹出两张一模一样的更新卡。
 * 所以本 fork 只保留这一条通道，CI 开关与它的全部接线已一并移除。
 */
fun checkNewVersion(): LatestVersionInfo = checkLatestRelease()

/**
 * 检查更新的接口地址，按顺序尝试：直连优先，失败后走能转发 `api.github.com` 的镜像。
 *
 * 国内直连 api.github.com 常被 RST，只走直连时首页永远看不到更新卡。实测 gh-proxy.org /
 * gh-proxy.com 支持把整个 api 地址拼在路径后面（200），ghproxy.net 会 403 —— 所以只列这两个。
 */
internal fun updateCheckUrls(): List<String> {
    val api = "https://api.github.com/repos/$SELF_UPDATE_REPO/releases/latest"
    return listOf(
        api,
        "https://gh-proxy.org/$api",
        "https://gh-proxy.com/$api",
    )
}

/** 单次检查的超时；三个地址串行试，最坏约 24s，不会把首页刷新卡太久。 */
private const val UPDATE_CHECK_TIMEOUT_MS = 8_000L

/** 检查结果缓存时长：首页与设置页可能连着查，没必要每次都打网络。 */
private const val UPDATE_CHECK_CACHE_MS = 5 * 60 * 1000L

@Volatile
private var updateCheckCache: Pair<Long, LatestVersionInfo>? = null

private fun checkLatestRelease(): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    val now = System.currentTimeMillis()
    updateCheckCache?.takeIf { now - it.first in 0L until UPDATE_CHECK_CACHE_MS }?.let { return it.second }
    for ((index, url) in updateCheckUrls().withIndex()) {
        val info = fetchLatestRelease(url, index > 0) ?: continue
        updateCheckCache = now to info
        return info
    }
    Log.w("UpdateCheck", "latest: 直连与镜像都没能取到 release 信息")
    return LatestVersionInfo()
}

/**
 * 拉一次 latest release；网络或解析失败返回 null，交给调用方继续试下一个地址。
 *
 * 返回空 [LatestVersionInfo] 表示「查到了但目前没有可用的 APK 资产」，属于成功结果，会被缓存。
 */
private fun fetchLatestRelease(url: String, viaMirror: Boolean): LatestVersionInfo? = runCatching {
    val call = ksuApp.okhttpClient.newCall(Request.Builder().url(url).build())
    call.timeout().timeout(UPDATE_CHECK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    call.execute().use { response ->
        if (!response.isSuccessful) {
            Log.w("UpdateCheck", "latest: HTTP ${response.code} via ${if (viaMirror) "mirror" else "direct"}")
            null
        } else {
            parseLatestRelease(response.body.string(), viaMirror)
        }
    }
}.onFailure {
    Log.w("UpdateCheck", "latest: check failed via $url", it)
}.getOrNull()

/** 解析 latest release 的 JSON；认得出版本号就返回更新信息，否则返回空结果。 */
private fun parseLatestRelease(body: String, viaMirror: Boolean): LatestVersionInfo {
    val json = org.json.JSONObject(body)
    val changelog = json.optString("body")
    val assets = json.getJSONArray("assets")
    for (i in 0 until assets.length()) {
        val asset = assets.getJSONObject(i)
        val versionCode = matchSelfUpdateApk(asset.getString("name")) ?: continue
        // 管理器 APK 的哈希只能来自这条查询：走代理下载时它就是唯一的校验依据，
        // 拿不到就留空，下载侧会因此拒绝走代理（宁可直接下，也不能让代理决定装什么）。
        val digest = sha256DigestOf(asset)
        if (digest == null) {
            Log.w("UpdateCheck", "latest: ${asset.getString("name")} 没有 sha256，只能直连下载")
        }
        if (viaMirror) Log.i("UpdateCheck", "latest: 直连不可用，已通过镜像获取")
        return LatestVersionInfo(
            versionCode = versionCode,
            downloadUrl = asset.getString("browser_download_url"),
            changelog = changelog,
            sha256 = digest ?: "",
        )
    }
    if (viaMirror) Log.i("UpdateCheck", "latest: 直连不可用，已通过镜像获取")
    return LatestVersionInfo()
}

/**
 * 从 release 资产里取 sha256 摘要。
 *
 * GitHub 的 release 接口给的是 `"digest": "sha256:<64 位十六进制>"`；老接口没有这个字段，
 * 或者算法不是 sha256，一律返回 null —— 调用方按「没有哈希」处理，绝不能拿未知内容去装。
 */
private fun sha256DigestOf(asset: org.json.JSONObject): String? {
    val digest = asset.optString("digest").trim()
    if (!digest.startsWith("sha256:", ignoreCase = true)) return null
    val hex = digest.substringAfter("sha256:", "").trim()
    if (hex.length != 64 || !hex.all { it in "0123456789abcdefABCDEF" }) return null
    return hex.lowercase()
}
