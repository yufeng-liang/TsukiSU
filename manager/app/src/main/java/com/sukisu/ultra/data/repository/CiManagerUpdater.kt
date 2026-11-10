package com.sukisu.ultra.data.repository

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.R
import com.sukisu.ultra.data.proxy.GithubProxyRouter
import com.sukisu.ultra.data.proxy.ProxyEntry
import com.sukisu.ultra.data.proxy.ProxyMode
import com.sukisu.ultra.data.proxy.ProxyRuntime
import com.sukisu.ultra.data.proxy.ProxyUrl
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.module.LatestVersionInfo
import com.sukisu.ultra.ui.util.getRootShell
import com.sukisu.ultra.ui.util.withNewRootShell
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile


class CiManagerUpdater(private val context: Context) {
    /**
     * [keepApk] false installs straight from the private cache copy and never
     * writes the APK to Downloads, so nothing is left behind after the update.
     */
    fun updateAndInstall(update: LatestVersionInfo, keepApk: Boolean = true): Boolean {
        // 传进来的地址可能已经被上一跳套了代理前缀（下载链路会做候选展开）。前缀校验必须针对
        // 还原后的原始 GitHub 地址，否则被代理包过的地址永远通不过 startsWith，更新直接判成「地址无效」。
        // 还原不出来（代理列表加载失败/不是已知代理）时保留原样，交给下面的校验去拒绝。
        val entries = proxyEntries()
        val originalUrl = ProxyUrl.unwrap(update.downloadUrl, entries) ?: update.downloadUrl
        if (!originalUrl.startsWith(RELEASE_ASSET_PREFIX)) {
            throw IOException(context.getString(R.string.ci_update_request_failed))
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.takeIf { it in SUPPORTED_ABIS }
            ?: throw IOException(context.getString(R.string.ci_update_unsupported_arch))
        val apk = File.createTempFile("manager-ci-", ".apk", context.cacheDir)
        try {
            // 候选通道都基于原始地址展开，所以把归一化后的地址连同 sha256 一起往下传。
            download(update.copy(downloadUrl = originalUrl), apk, entries)
            verifyApk(apk, abi, update.versionCode)
            if (keepApk) {
                // 文件名从原始地址取：别把代理域名带进 Downloads。
                saveToDownloads(apk, originalUrl)
            }
            if (getRootShell().isRoot) {
                val result = withNewRootShell {
                    if (!isRoot) throw IOException(context.getString(R.string.ci_update_install_failed))
                    // Stream via stdin: system_server cannot open an app-private cache path on SELinux devices.
                    newJob().add("cat ${apk.absolutePath.shellQuote()} | pm install -r -S ${apk.length()}").exec()
                }
                if (!result.isSuccess) {
                    throw IOException((result.err + result.out).joinToString("\n").ifBlank {
                        context.getString(R.string.ci_update_install_failed)
                    })
                }
                return true
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    throw IOException(context.getString(R.string.ci_update_install_permission))
                }
                submitInstall(apk)
                return false
            }
        } finally {
            apk.delete()
        }
    }

    private fun saveToDownloads(apk: File, url: String) {
        val name = url.toUri().lastPathSegment
            ?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.takeIf { it.endsWith(".apk") } ?: "SukiSU-update.apk"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create APK in Downloads")
            try {
                val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot write APK to Downloads")
                output.use { out -> apk.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                if (resolver.update(uri, values, null, null) != 1) throw IOException("Cannot publish APK in Downloads")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create Downloads directory")
            // Never overwrite another user's download; an incomplete copy is deleted on failure.
            var destination = File(directory, name)
            var index = 1
            while (!destination.createNewFile()) {
                destination = File(directory, "${name.removeSuffix(".apk")} (${index++}).apk")
            }
            try {
                apk.inputStream().use { input -> destination.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
            MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath),
                arrayOf("application/vnd.android.package-archive"), null)
        }
    }

    /**
     * 下载 [update] 到 [destination]：按路由展开候选通道、逐个尝试，并按 [LatestVersionInfo.sha256]
     * 校验内容。
     *
     * 安全底线（这是唯一一条会把 APK 装进系统的链路，不能容忍内容被中间人改写）：
     * - 代理是 TLS 终点，能看见也能改下载内容，所以**拿不到 sha256 时只许直连**，一个代理都不套；
     * - 有 sha256 才展开代理候选，每个候选下完立刻比对，不匹配就丢弃并换下一个；
     * - 直连同样会比对 sha256（发布端换了包却没改版本时能提前发现）。
     */
    private fun download(update: LatestVersionInfo, destination: File, entries: List<ProxyEntry>) {
        val expectedSha256 = expectedSha256Of(update.sha256)
        val originalUrl = update.downloadUrl
        // sha256 为空 = 没有校验依据：候选列表只有直连，绝不套代理（代理能改包，改完也无从发现）。
        // 用户选了「官方直连」时同理：不竞速、不把请求打到第三方。
        val mode = ProxyRuntime.prefs.mode()
        val candidates = if (expectedSha256 == null || mode == ProxyMode.OFFICIAL) {
            listOf(originalUrl)
        } else {
            // 竞速排序（含直连）可能为空：拿不到就交给路由的保守默认顺序。
            val ranking = runBlocking {
                runCatching { ProxyRuntime.prober.ranking(originalUrl) }.getOrDefault(emptyList())
            }
            GithubProxyRouter.candidates(
                originalUrl = originalUrl,
                mode = mode,
                entries = entries,
                pinnedId = ProxyRuntime.prefs.pinnedId(),
                lastGoodId = ProxyRuntime.prefs.lastGoodId(),
                ranking = ranking,
            )
        }

        var lastError: IOException? = null
        for (candidate in candidates) {
            val startedAt = System.nanoTime()
            try {
                val bytes = downloadOne(candidate, destination, expectedSha256)
                if (expectedSha256 != null) {
                    // 粘性记忆只记代理通道；直连没有 id 可记。
                    ProxyUrl.entryOf(candidate, entries)?.let { entry ->
                        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                        val speed = if (elapsedMs > 0) bytes * 1000 / elapsedMs else 0L
                        ProxyRuntime.prefs.recordSuccess(entry, speed)
                    }
                }
                return
            } catch (e: IOException) {
                // 半截文件必须清掉：verifyApk 会把残留字节当成坏包，而且下一次尝试要重新截断写。
                destination.delete()
                ProxyUrl.entryOf(candidate, entries)?.let { ProxyRuntime.prefs.recordFailure(it.id) }
                if (candidates.size > 1) {
                    Log.w(TAG, "update asset failed on channel, trying next: $candidate", e)
                }
                lastError = e
            }
        }
        throw lastError ?: IOException(context.getString(R.string.ci_update_download_failed))
    }

    /**
     * 单通道流式下载到 [destination]，边写边算 sha256。
     *
     * 绝不把整包读进内存：上限 [MAX_APK_BYTES] 是 512MB，整包读取在低端机上必 OOM。
     * 大小上限查两次（Content-Length + 实际累计），避免服务端不报长度时无限写盘。
     *
     * @return 实际写入的字节数。
     */
    private fun downloadOne(url: String, destination: File, expectedSha256: String?): Long {
        val digest = expectedSha256?.let { MessageDigest.getInstance("SHA-256") }
        var total = 0L
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(context.getString(R.string.ci_update_http_error, response.code))
            }
            val body = response.body
            if (body.contentLength() > MAX_APK_BYTES) {
                throw IOException(context.getString(R.string.ci_update_archive_too_large))
            }
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) {
                            throw IOException(context.getString(R.string.ci_update_archive_too_large))
                        }
                        output.write(buffer, 0, count)
                        digest?.update(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        }
        if (digest != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            val expected = expectedSha256.orEmpty()
            // 常量时间比较：哈希比对不需要防时序，但和仓库里其它校验保持一致。
            if (!MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())) {
                Log.w(TAG, "sha256 mismatch: expected=$expected actual=$actual url=$url")
                // 文案复用「更新失败」：这里不加新字符串，避免只改两个文件的边界被撑开。
                throw IOException(context.getString(R.string.ci_update_download_failed))
            }
        }
        return total
    }

    /**
     * 读当前可用的代理列表（失败按「没有代理」处理，只会退回直连，不会放行未校验的代理）。
     *
     * [ProxyRuntime.list.load] 是 suspend 的，而 [updateAndInstall] 是非 suspend 的旧接口；
     * 调用点 `HomeViewModel.installUpdate` 已经把它包在 `Dispatchers.IO` 上，所以这里的
     * `runBlocking` 只阻塞 IO 线程，不会卡主线程、也不会 ANR。
     * 将来若出现主线程调用方，应当把整条链路改成 suspend，而不是在主线程 runBlocking。
     */
    private fun proxyEntries(): List<ProxyEntry> = runBlocking {
        runCatching { ProxyRuntime.list.load() }.getOrDefault(emptyList())
    }

    /**
     * 把 GitHub API 的 `digest`（形如 `sha256:<64 位 hex>`）归一化成裸小写 hex。
     *
     * 返回 null 表示「没有可用的校验值」，调用方据此**禁止走代理**。
     * 长度/字符不合法也返回 null：发布端字段写错时宁可退回直连，也不要拿一个半截值去比对。
     */
    private fun expectedSha256Of(raw: String): String? {
        val trimmed = raw.trim()
        val hex = (if (trimmed.startsWith(SHA256_PREFIX, ignoreCase = true)) {
            trimmed.substring(SHA256_PREFIX.length)
        } else {
            trimmed
        }).trim().lowercase()
        return hex.takeIf { candidate ->
            candidate.length == SHA256_HEX_LENGTH &&
                candidate.all { it in '0'..'9' || it in 'a'..'f' }
        }
    }

    private fun verifyApk(apk: File, abi: String, expectedVersionCode: Long) {
        val candidate = archivePackageInfo(apk)
            ?: throw IOException(context.getString(R.string.ci_update_invalid_apk))
        if (candidate.packageName != BuildConfig.APPLICATION_ID ||
            candidate.longVersionCodeCompat() != expectedVersionCode ||
            abi !in apkAbis(apk)) {
            throw IOException(context.getString(R.string.ci_update_invalid_apk))
        }
        val installed = try {
            context.packageManager.getPackageInfo(context.packageName, packageInfoFlags())
        } catch (e: Exception) {
            throw IOException(context.getString(R.string.ci_update_invalid_apk), e)
        }
        if (candidate.longVersionCodeCompat() <= installed.longVersionCodeCompat()) {
            throw IOException(context.getString(R.string.ci_update_not_newer))
        }
        if (!sameSignature(candidate, installed)) {
            throw IOException(context.getString(R.string.ci_update_signature_mismatch))
        }
    }

    private fun packageInfoFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    }

    private fun archivePackageInfo(apk: File): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, packageInfoFlags())

    private fun apkAbis(apk: File): Set<String> = ZipFile(apk).use { zip ->
        val entries = zip.entries()
        buildSet {
            while (entries.hasMoreElements()) {
                val parts = entries.nextElement().name.split('/')
                if (parts.size >= 3 && parts[0] == "lib" && parts[1] in SUPPORTED_ABIS) add(parts[1])
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun sameSignature(candidate: PackageInfo, installed: PackageInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signers = candidate.signingInfo?.apkContentsSigners ?: return false
            return signers.isNotEmpty() && signers.all { signer ->
                context.packageManager.hasSigningCertificate(
                    context.packageName,
                    signer.toByteArray(),
                    PackageManager.CERT_INPUT_RAW_X509,
                )
            }
        }
        val candidateSignatures = candidate.signatures ?: return false
        val installedSignatures = installed.signatures ?: return false
        return candidateSignatures.isNotEmpty() && candidateSignatures.all { signature ->
            installedSignatures.any { it == signature }
        }
    }

    private fun submitInstall(apk: File) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
            }
        )
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("manager.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, CiPackageInstallReceiver::class.java)
                    .setAction(CiPackageInstallReceiver.ACTION_INSTALL_RESULT)
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_MUTABLE
                } else 0
                val pending = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or mutable,
                )
                session.commit(pending.intentSender)
                committed = true
            }
        } finally {
            if (!committed) installer.abandonSession(sessionId)
        }
    }

    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    companion object {
        /**
         * 本 fork 的 release 资产前缀。
         *
         * 上游把这个常量写死成官方仓库，但本 fork 的更新检查走
         * [com.sukisu.ultra.ui.util.checkNewVersion] → fork 自己的 latest release，
         * 拿回来的 downloadUrl 一定是 `yufeng-liang/SukiSU-Ultra` 下的资产。
         * 若继续用官方前缀，`startsWith` 校验会把每一次更新都判成「地址无效」，
         * 所以这里必须跟 Downloader 里的 SELF_UPDATE_REPO 保持一致。
         */
        private const val RELEASE_ASSET_PREFIX =
            "https://github.com/yufeng-liang/SukiSU-Ultra/releases/download/"
        private const val MAX_APK_BYTES = 512L * 1024 * 1024
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "riscv64")

        private const val TAG = "CiManagerUpdater"

        /** GitHub digest 的算法前缀（`sha256:<hex>`），归一化时剥掉。 */
        private const val SHA256_PREFIX = "sha256:"

        /** sha256 的十六进制长度。 */
        private const val SHA256_HEX_LENGTH = 64
    }
}
