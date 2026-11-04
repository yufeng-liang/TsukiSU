package com.sukisu.ultra.data.column

import com.sukisu.ultra.data.backup.sha256Hex
import com.sukisu.ultra.data.model.RepoColumnKeybox
import com.sukisu.ultra.data.proxy.GithubProxyRouter
import com.sukisu.ultra.data.proxy.ProxyEntry
import com.sukisu.ultra.data.proxy.ProxyRuntime
import com.sukisu.ultra.data.proxy.ProxyUrl
import com.sukisu.ultra.ksuApp
import android.util.Base64
import com.sukisu.ultra.ui.util.hasAnyNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * 密钥材料（keybox.xml）的体积上限。
 *
 * keybox 是几 KB 的 XML；给到 1 MiB 已经非常宽松。没有上限的话，一个被换掉的地址就能让
 * 整份响应进内存——而这份内容随后还要写进系统目录，不能任其无限大。
 */
internal const val MAX_KEYBOX_BYTES = 1L * 1024 * 1024

/**
 * 整次拉取（连接 + 重定向 + 读体）的上限。
 *
 * 全局 OkHttpClient 只配了默认的连接/读超时，一个每十几秒吐一个字节的地址永远碰不到它们；
 * 只有整次调用的封顶拦得住这种慢速滴流。
 */
internal const val KEYBOX_CALL_TIMEOUT_SECONDS = 15L

/**
 * 自动获取用的默认源。
 *
 * MeowDump 的 Integrity-Box 把一份 keybox 转存成了 GitHub Release 资产（固定 tag `archive`、
 * 文件名 `Megatron`），内容经过 base64 ×10 → hex → ROT13 混淆。选它做默认源的理由：GitHub
 * Release 是我们代理体系唯一能加速的形态，而 dpejoh 那套 catalog 的下载接口当前返回 500。
 */
internal const val MEOW_KEYBOX_URL =
    "https://github.com/MeowDump/Integrity-Box/releases/download/archive/Megatron"

/** 混淆后的原始体积上限：实测约 239 KB，给 4 MiB 足够宽松。 */
internal const val MAX_MEOW_RAW_BYTES = 4L * 1024 * 1024

/**
 * 现拉并校验专栏需要的密钥材料。
 *
 * 商店**不内置、不缓存、不代理** keybox：这里只负责「按登记的地址取回来，且字节的 sha256 与
 * 登记值一致」。校验不过就中止——我们只对「字节没被换过」负责，不对「这份密钥今天还能不能
 * 过检测」负责（后者随时可能被 Google 吊销）。
 *
 * 失败一律以 [Result.failure] 返回（协程取消除外，取消必须继续往上抛，否则上层再也取消不掉），
 * 调用方拿到的是可以直接展示给用户的中文原因。
 */
class ColumnKeyboxFetcher(
    private val http: OkHttpClient = ksuApp.okhttpClient,
) {

    /** 单次调用超时只作用于这一条链路，不动全局客户端的设置。 */
    private val client: OkHttpClient by lazy {
        http.newBuilder()
            .callTimeout(KEYBOX_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetch(keybox: RepoColumnKeybox): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(download(keybox))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 自动获取一份可用的密钥材料。
     *
     * 与 [fetch] 的区别：这里没有「登记的 sha256」——源本身就是混淆过的，校验靠解出来的 XML
     * 结构与证书字段。任何一步不过就失败，由调用方退回「用户自选本地文件」。
     */
    suspend fun fetchAuto(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(downloadAuto())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadAuto(): String {
        if (!hasAnyNetwork(ksuApp)) {
            throw IOException("当前没有可用网络，无法自动获取密钥材料")
        }
        val (candidates, proxies) = downloadCandidates(MEOW_KEYBOX_URL)
        var lastError: Exception? = null
        for (candidate in candidates) {
            val proxied = ProxyUrl.entryOf(candidate, proxies)
            val raw = try {
                fetchRaw(candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                proxied?.let { ProxyRuntime.prefs.recordFailure(it.id) }
                lastError = e
                continue
            }
            val xml = try {
                decodeMeow(raw)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                proxied?.let { ProxyRuntime.prefs.recordFailure(it.id) }
                lastError = e
                continue
            }
            try {
                validateKeybox(xml)
            } catch (e: Exception) {
                proxied?.let { ProxyRuntime.prefs.recordFailure(it.id) }
                lastError = e
                continue
            }
            proxied?.let { ProxyRuntime.prefs.recordSuccess(it, 0L) }
            return xml
        }
        throw lastError ?: IOException("自动获取密钥材料失败：没有可用的下载地址")
    }

    /** Meow 源的解码：base64 连续 10 层 → hex → ROT13。 */
    private fun decodeMeow(raw: ByteArray): String {
        var data = raw
        repeat(10) { step ->
            val text = stripSpace(String(data, Charsets.ISO_8859_1))
            data = try {
                Base64.decode(text, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw IOException("密钥源解码失败：第 ${step + 1} 层 base64 不合法")
            }
            if (data.isEmpty()) throw IOException("密钥源解码失败：第 ${step + 1} 层为空")
        }
        val hexText = stripSpace(String(data, Charsets.ISO_8859_1))
        if (hexText.length % 2 != 0) throw IOException("密钥源解码失败：hex 长度不是偶数")
        val bytes = ByteArray(hexText.length / 2) { index ->
            val high = Character.digit(hexText[index * 2], 16)
            val low = Character.digit(hexText[index * 2 + 1], 16)
            if (high < 0 || low < 0) throw IOException("密钥源解码失败：hex 里有非法字符")
            ((high shl 4) or low).toByte()
        }
        return rot13(bytes)
    }

    private fun stripSpace(text: String): String =
        text.filterNot { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
    private fun rot13(bytes: ByteArray): String {
        val out = CharArray(bytes.size)
        bytes.forEachIndexed { index, byte ->
            val c = (byte.toInt() and 0xFF).toChar()
            out[index] = when (c) {
                in 'a'..'z' -> 'a' + ((c - 'a' + 13) % 26)
                in 'A'..'Z' -> 'A' + ((c - 'A' + 13) % 26)
                else -> c
            }
        }
        return String(out)
    }

    /** 结构校验：这是不是一份「看起来能用」的 keybox.xml。 */
    private fun validateKeybox(xml: String) {
        if (xml.length > MAX_KEYBOX_BYTES) {
            throw IOException("解出来的密钥材料超过 1 MiB，拒绝使用")
        }
        if (!xml.contains("<AndroidAttestation")) {
            throw IOException("解出来的内容不是 keybox.xml（缺少 AndroidAttestation 根节点）")
        }
        if (!xml.contains("<Keybox") || !xml.contains("DeviceID=")) {
            throw IOException("解出来的内容不是 keybox.xml（缺少 Keybox 或 DeviceID）")
        }
        val hasEcdsa = xml.contains("<Key algorithm=\"ecdsa\">")
        val hasRsa = xml.contains("<Key algorithm=\"rsa\">")
        if (!hasEcdsa && !hasRsa) {
            throw IOException("解出来的 keybox 里没有可用的密钥对（ecdsa/rsa 都没有）")
        }
    }

    /** 原始体的取回，上限比 keybox 本身宽松（解码前是混淆文本）。 */
    private fun fetchRaw(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载密钥源失败：HTTP ${response.code}")
            }
            if (!response.request.url.isHttps) {
                throw IOException("密钥地址被重定向到非 https 地址，已拒绝：${response.request.url}")
            }
            val body = response.peekBody(MAX_MEOW_RAW_BYTES + 1).bytes()
            if (body.size > MAX_MEOW_RAW_BYTES) {
                throw IOException("密钥源响应超过 4 MiB，拒绝下载")
            }
            body
        }
    }

    private suspend fun download(keybox: RepoColumnKeybox): String {
        val url = keybox.url.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw IOException("密钥地址必须是 https，已拒绝下载：${keybox.url}")
        }

        // 空 sha256 意味着「登记人没给校验依据」。这时下载等于无条件信任一个远程地址，
        // 而这份内容会落到系统目录里，所以宁可让专题停下，也不猜。
        val expected = keybox.sha256.trim()
        if (expected.isEmpty()) {
            throw IOException("没有登记 sha256，拒绝下载")
        }

        if (!hasAnyNetwork(ksuApp)) {
            throw IOException("当前没有可用网络，无法下载密钥材料")
        }

        // 候选顺序：粘性代理 → 其余代理 → 直连。keybox 地址通常不在 GitHub Release 白名单里，
        // 这时路由只返回原始地址，行为与以前完全一致。
        val (candidates, proxies) = downloadCandidates(url)
        var lastError: Exception? = null
        for (candidate in candidates) {
            val proxied = ProxyUrl.entryOf(candidate, proxies)
            val bytes = try {
                fetchBytes(candidate)
            } catch (e: CancellationException) {
                // 取消不是失败：继续往上抛，否则向导里的「停止」再也停不下来。
                throw e
            } catch (e: Exception) {
                proxied?.let { ProxyRuntime.prefs.recordFailure(it.id) }
                lastError = e
                continue
            }

            // 每个候选下完都要校验：代理是 TLS 终点，能改写内容，哈希对不上就换下一条路。
            val actual = bytes.sha256Hex()
            if (!actual.equals(expected, ignoreCase = true)) {
                proxied?.let { ProxyRuntime.prefs.recordFailure(it.id) }
                lastError = IOException(
                    "密钥材料 sha256 与登记值不一致：期望 $expected，实际 $actual" +
                        "（地址里的文件可能已被替换，已中止，未写入任何内容）"
                )
                continue
            }

            proxied?.let { ProxyRuntime.prefs.recordSuccess(it, 0L) }
            return String(bytes, Charsets.UTF_8)
        }
        throw lastError ?: IOException("密钥材料下载失败：没有可用的下载地址")
    }

    /**
     * 把登记地址展开成候选列表，并带回代理条目，便于事后记成功/失败。
     *
     * 代理配置读不到时退回「只下原始地址」——加速不能成为下载的前置条件。
     */
    private suspend fun downloadCandidates(url: String): Pair<List<String>, List<ProxyEntry>> {
        val proxies = runCatching { ProxyRuntime.list.load() }.getOrDefault(emptyList())
        val candidates = runCatching {
            GithubProxyRouter.candidates(
                originalUrl = url,
                mode = ProxyRuntime.prefs.mode(),
                entries = proxies,
                pinnedId = ProxyRuntime.prefs.pinnedId(),
                lastGoodId = ProxyRuntime.prefs.lastGoodId(),
            )
        }.getOrElse { listOf(url) }
        return candidates to proxies
    }

    /** 单个候选地址的一次取回；体积上限与 https 强制都在这里兜住。 */
    private fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载密钥材料失败：HTTP ${response.code}")
            }
            // 只允许 https：重定向可能把请求带到 http，这种降级不能接受。
            if (!response.request.url.isHttps) {
                throw IOException("密钥地址被重定向到非 https 地址，已拒绝：${response.request.url}")
            }
            // 先看响应声明的长度，能省掉一次「读到 1 MiB 才发现超限」的流量。
            val declared = response.body.contentLength()
            if (declared > MAX_KEYBOX_BYTES) {
                throw IOException("密钥材料超过 1 MiB（声明 $declared 字节），拒绝下载")
            }
            // peekBody 只多取 1 字节用来判断有没有超限，绝不整份读进内存。
            val body = response.peekBody(MAX_KEYBOX_BYTES + 1).bytes()
            if (body.size > MAX_KEYBOX_BYTES) {
                throw IOException("密钥材料超过 1 MiB，拒绝下载")
            }
            body
        }
    }
}
