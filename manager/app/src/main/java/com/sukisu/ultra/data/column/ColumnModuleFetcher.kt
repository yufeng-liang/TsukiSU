package com.sukisu.ultra.data.column

import android.net.Uri
import android.util.Log
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.proxy.GithubProxyRouter
import com.sukisu.ultra.data.proxy.ProxyEntry
import com.sukisu.ultra.data.proxy.ProxyMode
import com.sukisu.ultra.data.proxy.ProxyRuntime
import com.sukisu.ultra.data.proxy.ProxyUrl
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.hasAnyNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/** 下载的模块 zip 放在缓存目录下的这个子目录里，安装完由 flashModule 删掉源文件。 */
internal const val COLUMN_DOWNLOAD_DIR_NAME = "column_dl"

/** GitHub API 根地址；单独抽出来，方便测试时对着本地假服务解释这一层在干什么。 */
private const val GITHUB_API_BASE = "https://api.github.com"

/** 一次最多看多少个 release。专栏锁定的版本通常就在最近几个里。 */
private const val RELEASES_PER_PAGE = 30

/** release 列表的响应体上限：接口返回的 changelog 可以很长，但 8 MiB 足够覆盖 30 个 release。 */
private const val MAX_RELEASES_BODY_BYTES = 8L * 1024 * 1024

/** release 列表请求的整次调用上限，防止一个不响应的地址把向导卡死。 */
private const val RELEASES_CALL_TIMEOUT_SECONDS = 15L

/** 模块包单个连接的建立/读取超时；下载本身不设整次上限（大包在慢网下可能几分钟）。 */
private const val MODULE_CONNECT_TIMEOUT_SECONDS = 15L
private const val MODULE_READ_TIMEOUT_SECONDS = 30L

/**
 * 模块包体积上限。
 *
 * 正常模块几十 MB，512 MiB 已经非常宽松；但下载是流式写盘的，没有上限的话，一个被换掉的
 * 地址就能把设备缓存分区填满。超限直接中止，已经写入的部分会被删掉。
 */
private const val MAX_MODULE_BYTES = 512L * 1024 * 1024

private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

/** 缓存文件名里允许出现的字符；资产名来自上游，不能直接当路径用。 */
private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")

/** GitHub 的仓库标识只能是 `owner/repo` 两段。 */
private val REPO_PATTERN = Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$")

/**
 * 已经把「用户接下来该怎么办」写进 message 的失败。
 *
 * 单独一个类型是为了让「网络层原始 IOException」和「我们自己的结论」分开：前者还要补一句
 * 「可能被墙、可以手动下载」，后者直接原样展示，不再重复包装。
 */
private class ColumnFetchException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * 为 `installModule` 步骤准备一个可以交给 `ksud` 的 zip。
 *
 * 两种来源：
 * - `upstream`：按 `repo` + `assetPattern` + `pinnedVersion` 去 GitHub release 里找资产，下载并
 *   校验 sha256。专栏不打包、不镜像，拿到的就是上游发布的原始 zip。
 * - `store`：引用商店索引里的模块。P1 阶段还没有「moduleId → 下载地址」的查询接口，所以直接
 *   失败并让用户去模块页装，**不猜 URL**。
 *
 * 失败一律以中文 [Result.failure] 返回；协程取消继续往上抛（取消不是失败，吞掉会让上层取消不掉）。
 */
class ColumnModuleFetcher(
    private val http: OkHttpClient = ksuApp.okhttpClient,
    private val cacheRoot: File = File(ksuApp.cacheDir, COLUMN_DOWNLOAD_DIR_NAME),
) {

    /** release 列表只允许短平快：查不到就报错，别让向导停在「正在检查更新」。 */
    private val apiClient: OkHttpClient by lazy {
        http.newBuilder()
            .callTimeout(RELEASES_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** 下载客户端：不设整次上限，但连接/读取都要有边界。 */
    private val downloadClient: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(MODULE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(MODULE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun resolve(step: RepoColumnStep): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            Result.success(resolveOrThrow(step))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveOrThrow(step: RepoColumnStep): Uri =
        when (val kind = step.source.kind.trim().lowercase()) {
            SOURCE_UPSTREAM -> downloadUpstream(step)
            SOURCE_STORE -> throw ColumnFetchException("商店模块安装请用模块页，专栏暂不支持 store 来源")
            "" -> throw ColumnFetchException("步骤 ${step.id} 没有给出模块来源（source.kind），无法安装")
            else -> throw ColumnFetchException("未知的模块来源类型：$kind")
        }

    private suspend fun downloadUpstream(step: RepoColumnStep): Uri {
        val repo = step.source.repo.trim()
        if (!REPO_PATTERN.matches(repo)) {
            throw ColumnFetchException(
                "上游仓库地址不合法（应为 owner/repo）：${step.source.repo.ifBlank { "（空）" }}"
            )
        }
        val pattern = step.source.assetPattern.trim()
        if (pattern.isEmpty()) {
            throw ColumnFetchException("索引没有给资产名通配（assetPattern），无法确定要下载哪个文件")
        }
        if (!hasAnyNetwork(ksuApp)) {
            throw ColumnFetchException("当前没有可用网络，无法从 GitHub 下载模块")
        }

        val url = "$GITHUB_API_BASE/repos/$repo/releases?per_page=$RELEASES_PER_PAGE"
        val release = findRelease(repo, url, step.source.pinnedVersion.trim())
        val asset = findAsset(repo, release, pattern)
        val destination = File(downloadDirFor(repo), safeFileName(asset.name))
        download(repo, asset.url, destination, step.source.sha256.trim())
        return Uri.fromFile(destination)
    }

    /**
     * 选 release：优先 tag 与 `pinnedVersion` 完全一致的那个；没有就退到 latest。
     *
     * 「latest」按 GitHub 的语义取第一个非草稿、非预发布的 release；仓库只有预发布时退到第一个
     * 非草稿 release——总比一个都选不出来强，且后续 sha256/安装步骤仍会兜底。
     */
    private fun findRelease(repo: String, url: String, pinnedVersion: String): JSONObject {
        val body = fetchText(repo, url)
        val releases = try {
            JSONArray(body)
        } catch (e: Exception) {
            throw ColumnFetchException("GitHub 返回的不是 release 列表（仓库 $repo）", e)
        }

        var latest: JSONObject? = null
        var fallback: JSONObject? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft")) continue
            if (pinnedVersion.isNotEmpty() &&
                release.optString("tag_name").equals(pinnedVersion, ignoreCase = true)
            ) {
                return release
            }
            if (fallback == null) fallback = release
            if (latest == null && !release.optBoolean("prerelease")) latest = release
        }
        return latest ?: fallback
            ?: throw ColumnFetchException("$repo 没有任何可用的 release，无法下载模块")
    }

    private fun fetchText(repo: String, url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        return guarded(repo, "访问 GitHub 接口") {
            apiClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw httpFailure(repo, response.code)
                val bytes = response.peekBody(MAX_RELEASES_BODY_BYTES + 1).bytes()
                if (bytes.size > MAX_RELEASES_BODY_BYTES) {
                    throw ColumnFetchException(
                        "GitHub 返回内容超过 ${MAX_RELEASES_BODY_BYTES / (1024 * 1024)} MiB，已中止"
                    )
                }
                String(bytes, Charsets.UTF_8)
            }
        }
    }

    /** 在 release 的资产里找第一个名字匹配 [pattern] 的；`*` 通配、忽略大小写。 */
    private fun findAsset(repo: String, release: JSONObject, pattern: String): RemoteAsset {
        val matcher = globToRegex(pattern)
        val assets = release.optJSONArray("assets") ?: JSONArray()
        val names = mutableListOf<String>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name").trim()
            if (name.isEmpty()) continue
            names += name
            if (!matcher.matches(name)) continue
            val downloadUrl = asset.optString("browser_download_url").trim()
            if (downloadUrl.isEmpty()) continue
            return RemoteAsset(name, downloadUrl)
        }

        val tag = release.optString("tag_name").ifBlank { "（未知版本）" }
        val available = names.take(8).joinToString("、").ifBlank { "没有资产" }
        throw ColumnFetchException(
            "release $tag 里没有匹配「$pattern」的资产（现有：$available），" +
                "可手动到 https://github.com/$repo/releases 下载后自行安装"
        )
    }

    /**
     * 流式下载 + 边下边算 sha256，先把内容写到 `.part`，校验通过后才改名成目标文件。
     *
     * 地址由 [GithubProxyRouter] 展开成候选列表（粘性代理 → 其余代理 → 直连），逐个尝试：
     * 任何一个候选失败（包括 sha256 对不上）都删掉临时文件、换下一个；全部失败才把最后一个
     * 异常抛出去。这样缓存里永远只会留下「校验过、能装」的 zip，`ksud` 拿到的就是这份字节。
     */
    private suspend fun download(repo: String, url: String, destination: File, expectedSha256: String) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw ColumnFetchException("模块下载地址必须是 https，已拒绝：$url")
        }
        val dir = destination.parentFile
        if (dir == null || (!dir.isDirectory && !dir.mkdirs())) {
            throw ColumnFetchException("无法创建下载目录：${destination.parentFile?.absolutePath ?: "（未知）"}")
        }

        val (candidates, proxies) = downloadCandidates(url, verifiable = expectedSha256.isNotEmpty())
        var lastError: Exception? = null
        for (candidate in candidates) {
            // 只有「确实是代理包出来的」地址才计入代理健康度；直连地址不写粘性记忆。
            val proxied = ProxyUrl.entryOf(candidate, proxies)
            try {
                downloadOne(repo, candidate, destination, expectedSha256)
                proxied?.let { ProxyRuntime.prefs.recordSuccess(it, 0L) }
                return
            } catch (e: CancellationException) {
                // 取消不是失败：继续往上抛，否则上层再也停不下来。
                throw e
            } catch (e: Exception) {
                proxied?.let { ProxyRuntime.prefs.recordFailure(it.id) }
                lastError = e
                Log.w("ColumnModuleFetcher", "候选地址下载失败，尝试下一个：$candidate", e)
            }
        }
        throw lastError ?: ColumnFetchException("没有可用的模块下载地址：$url")
    }

    /**
     * 把 [url] 展开成候选列表，并带回代理条目，便于事后记成功/失败。
     *
     * 代理配置读不到（远程 json 坏掉、还没初始化）时退回「只下原始地址」：
     * 加速是锦上添花，不能因为它把正常下载挡死。
     */
    private suspend fun downloadCandidates(
        url: String,
        verifiable: Boolean,
    ): Pair<List<String>, List<ProxyEntry>> {
        // 索引没登记 sha256 时只许直连：代理是 TLS 终点，能改内容，而模块包落地后直接进刷入。
        // 想给这个模块开加速，就先把 sha256 补进 columns.json。
        if (!verifiable) return listOf(url) to emptyList()

        val proxies = runCatching { ProxyRuntime.list.load() }.getOrDefault(emptyList())
        val candidates = runCatching {
            val mode = ProxyRuntime.prefs.mode()
            // AUTO 才竞速：ranking 自带 1 小时 + 同网络指纹缓存，正常不会多等。
            val ranking = if (mode == ProxyMode.AUTO) {
                runCatching { ProxyRuntime.prober.ranking(url) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            GithubProxyRouter.candidates(
                originalUrl = url,
                mode = mode,
                entries = proxies,
                pinnedId = ProxyRuntime.prefs.pinnedId(),
                lastGoodId = ProxyRuntime.prefs.lastGoodId(),
                ranking = ranking,
            )
        }.getOrElse {
            Log.w("ColumnModuleFetcher", "代理路由不可用，退回直连", it)
            listOf(url)
        }
        return candidates to proxies
    }

    /** 单个候选地址的一次完整下载尝试；失败时自己清掉 `.part`，不留半截文件。 */
    private suspend fun downloadOne(repo: String, url: String, destination: File, expectedSha256: String) {
        val dir = destination.parentFile
            ?: throw ColumnFetchException("下载目录不可用：${destination.absolutePath}")
        val temp = File(dir, destination.name + ".part")
        temp.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L

        try {
            guarded(repo, "下载模块包") {
                val request = Request.Builder().url(url).build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw httpFailure(repo, response.code)
                    val declared = response.body.contentLength()
                    if (declared > MAX_MODULE_BYTES) throw tooLargeFailure()

                    response.body.byteStream().use { input ->
                        temp.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                            while (true) {
                                // 长下载是用户最容易按「取消」的地方：每个缓冲区检查一次，
                                // 真的能停下来，而不是等整个包下完。
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_MODULE_BYTES) throw tooLargeFailure()
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                }
            }

            if (total == 0L) {
                throw ColumnFetchException("下载到的模块包是空的（0 字节），已中止")
            }

            if (expectedSha256.isNotEmpty()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    throw ColumnFetchException(
                        "模块包 sha256 与索引登记值不一致：期望 $expectedSha256，实际 $actual" +
                            "（已删除下载内容，未安装）"
                    )
                }
            }

            if (destination.exists() && !destination.delete()) {
                throw ColumnFetchException("无法覆盖旧的下载缓存：${destination.absolutePath}")
            }
            if (!temp.renameTo(destination)) {
                throw ColumnFetchException("无法把下载内容移动到 ${destination.absolutePath}")
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun tooLargeFailure() = ColumnFetchException(
        "模块包超过 ${MAX_MODULE_BYTES / (1024 * 1024)} MiB，已中止下载（不会写入缓存）"
    )

    private fun httpFailure(repo: String, code: Int): IOException {
        val hint = "可手动到 https://github.com/$repo/releases 下载后自行安装"
        return when (code) {
            403 -> ColumnFetchException("GitHub 接口返回 HTTP 403（可能触发限流，或网络被拦截），$hint")
            404 -> ColumnFetchException("GitHub 上找不到 $repo 的 release（仓库可能已改名/转为私有，或网络被拦截），$hint")
            else -> ColumnFetchException("请求 GitHub 失败：HTTP $code，$hint")
        }
    }

    /**
     * 把网络层异常翻译成能指导用户的中文。
     *
     * 只包 [IOException]：我们已经想清楚的 [ColumnFetchException] 原样抛出，避免一句话里叠两次
     * 「可能被墙」；协程取消不是 IOException，也会自然穿过。
     */
    private inline fun <T> guarded(repo: String, what: String, block: () -> T): T =
        try {
            block()
        } catch (e: ColumnFetchException) {
            throw e
        } catch (e: IOException) {
            throw ColumnFetchException(
                "${what}失败：${e.message ?: "未知错误"}（GitHub 可能被网络屏蔽，${
                    "可手动到 https://github.com/$repo/releases 下载后自行安装"
                }）",
                e
            )
        }

    private fun downloadDirFor(repo: String): File =
        File(cacheRoot, repo.replace('/', '_').replace(UNSAFE_FILE_NAME_CHARS, "_"))

    /** 资产名只保留安全字符：它来自上游，不能带着 `../` 或引号进文件系统。 */
    private fun safeFileName(raw: String): String {
        val cleaned = raw.trim()
            .replace(UNSAFE_FILE_NAME_CHARS, "_")
            .trimStart('.')
            .ifEmpty { "module.zip" }
        return if (cleaned.length <= 120) cleaned else cleaned.take(120)
    }

    private fun globToRegex(pattern: String): Regex {
        val builder = StringBuilder("^")
        pattern.forEach { ch ->
            when (ch) {
                '*' -> builder.append(".*")
                '.', '\\', '+', '?', '(', ')', '[', ']', '{', '}', '^', '$', '|' ->
                    builder.append('\\').append(ch)
                else -> builder.append(ch)
            }
        }
        builder.append('$')
        return Regex(builder.toString(), RegexOption.IGNORE_CASE)
    }

    private data class RemoteAsset(val name: String, val url: String)

    private companion object {
        const val SOURCE_UPSTREAM = "upstream"
        const val SOURCE_STORE = "store"
    }
}
