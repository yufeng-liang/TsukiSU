package com.sukisu.ultra.ui.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.sukisu.ultra.R
import com.sukisu.ultra.data.proxy.ProxyUrl
import com.sukisu.ultra.data.registry.DownloadVerificationException
import com.sukisu.ultra.data.registry.ExpectedFile
import com.sukisu.ultra.data.registry.RegistryDownloadStore
import com.sukisu.ultra.data.registry.RegistryUrls
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.MainActivity
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap

enum class DownloadCompletionAction {
    INSTALL_MODULE,
    OPEN_FILE,
}

internal fun resolveDownloadMimeType(fileName: String, providedMimeType: String?): String =
    providedMimeType?.trim()?.takeIf { it.isNotEmpty() }
        ?: URLConnection.guessContentTypeFromName(fileName)
        ?: "application/octet-stream"

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val ACTION_DOWNLOAD = "com.sukisu.ultra.action.DOWNLOAD"
        const val ACTION_CANCEL = "com.sukisu.ultra.action.CANCEL_DOWNLOAD"
        const val ACTION_DISMISS_DOWNLOAD = "com.sukisu.ultra.action.DISMISS_DOWNLOAD"
        const val ACTION_INSTALL_MODULE = "com.sukisu.ultra.action.INSTALL_MODULE"
        const val EXTRA_URL = "url"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_FILE_NAME = "fileName"
        const val EXTRA_DOWNLOAD_ID = "downloadId"
        const val EXTRA_MODULE_URI = "moduleUri"
        const val EXTRA_EXPECTED_SIZE = "expectedSize"
        const val EXTRA_EXPECTED_SHA256 = "expectedSha256"
        const val EXTRA_TARGET_PATH = "targetPath"
        const val EXTRA_MIME_TYPE = "mimeType"
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_USER_AGENT = "userAgent"
        const val EXTRA_COMPLETION_ACTION = "completionAction"
        const val EXTRA_DELETE_FILE_ON_DISMISS = "deleteFileOnDismiss"
        const val EXTRA_PROXY_BASE = "proxyBase"
        const val EXTRA_NOTIFY_ON_FAILURE = "notifyOnFailure"

        private const val COMPLETION_NOTIFICATION_ID_BASE = 100000
        private const val REGISTRY_DOWNLOAD_MAX_AGE_MS = 24L * 60 * 60 * 1000

        /**
         * 取消一条下载。
         *
         * 调用方（候选链）等超时后必须主动收尾：服务被杀时它不会再发任何回调，而这条
         * 记录会一直占着通知栏。
         */
        fun cancel(context: Context, downloadId: Int) {
            runCatching {
                context.startService(Intent(context, DownloadService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                })
            }
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        return base.replace(Regex("[\\x00-\\x1F]"), "_").trim().ifBlank { "download.bin" }
    }

    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val activeCalls = ConcurrentHashMap<Int, Call>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: return START_NOT_STICKY
                val downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE)
                val cookie = intent.getStringExtra(EXTRA_COOKIE)
                val userAgent = intent.getStringExtra(EXTRA_USER_AGENT)
                val completionAction = intent.getStringExtra(EXTRA_COMPLETION_ACTION)
                    ?.let { runCatching { DownloadCompletionAction.valueOf(it) }.getOrNull() }
                    ?: DownloadCompletionAction.INSTALL_MODULE
                if (downloadId == -1) return START_NOT_STICKY

                val notification = buildProgressNotification(downloadId, fileName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        downloadId, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(downloadId, notification)
                }

                val expectedSha256 = intent.getStringExtra(EXTRA_EXPECTED_SHA256)
                val expectedSize = intent.getLongExtra(EXTRA_EXPECTED_SIZE, -1L)
                val expected = expectedSha256?.let { ExpectedFile(expectedSize, it) }
                // 代理前缀是入队那一刻的快照，服务端不再重新读远程清单；中间候选失败不弹通知。
                val proxyBase = intent.getStringExtra(EXTRA_PROXY_BASE)
                val notifyOnFailure = intent.getBooleanExtra(EXTRA_NOTIFY_ON_FAILURE, true)
                startDownload(
                    downloadId, url, fileName, mimeType, cookie, userAgent,
                    completionAction, expected, proxyBase, silentFailure = !notifyOnFailure,
                )
            }

            ACTION_CANCEL -> {
                val downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != -1) {
                    activeJobs[downloadId]?.cancel()
                    activeCalls[downloadId]?.cancel()
                }
            }

            ACTION_DISMISS_DOWNLOAD -> {
                val downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
                val uri = intent.getStringExtra(EXTRA_MODULE_URI)?.let(Uri::parse)
                val deleteFileOnDismiss = intent.getBooleanExtra(EXTRA_DELETE_FILE_ON_DISMISS, true)
                if (downloadId != -1) {
                    notificationManager.cancel(COMPLETION_NOTIFICATION_ID_BASE + downloadId)
                }
                serviceScope.launch {
                    if (deleteFileOnDismiss && uri != null) deleteDownload(uri)
                    stopForegroundIfIdle()
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Registry downloads ([expected] != null) may only come from GitHub Release download URLs
     * and their CDN redirect targets; every hop is checked.
     *
     * 走加速时代理本身就是被请求的主机，它不在 GitHub 的域名表里，所以额外放行
     * 「[proxyBase] + 白名单内层地址」这种形状，见 [proxiedInnerReleaseUrl]。
     */
    private fun registryClient(proxyBase: String?): OkHttpClient = ksuApp.okhttpClient.newBuilder()
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val requestUrl = chain.request().url
            if (!isAllowedRegistryHop(requestUrl.toString(), proxyBase)) {
                throw DownloadVerificationException(
                    DownloadVerificationException.Reason.URL_NOT_ALLOWED,
                    "Download host not allowed: ${requestUrl.host}"
                )
            }
            chain.proceed(chain.request())
        }
        .build()

    /** 单跳放行：白名单域名，或「入队时记下的代理前缀 + 白名单内层地址」。 */
    private fun isAllowedRegistryHop(url: String, proxyBase: String?): Boolean {
        if (RegistryUrls.isAllowedDownloadHop(url)) return true
        return proxiedInnerReleaseUrl(url, proxyBase) != null
    }

    /**
     * 「代理前缀 + 白名单内层地址」→ 内层地址；不成立返回 null。
     *
     * 前缀取自入队时的候选快照：下载途中远程清单被刷新、过期、甚至暂时读不出来，都不该让一个
     * 已经排到队首的候选当场失效（旧实现每次下载都重新 load 一遍，最长还要等 15 秒网络）。
     * 前缀必须是无路径的 https 基址，内层必须通过 [RegistryUrls.isAllowedReleaseDownloadUrl]，
     * 所以放行代理域名不会变成放行任意 host。
     */
    private fun proxiedInnerReleaseUrl(url: String, proxyBase: String?): String? {
        val base = proxyBase?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        if (!ProxyUrl.isValidBaseUrl(base)) return null
        if (!url.startsWith("$base/", ignoreCase = true)) return null
        return url.substring(base.length + 1)
            .takeIf { RegistryUrls.isAllowedReleaseDownloadUrl(it) }
    }

    private fun startDownload(
        id: Int,
        url: String,
        fileName: String,
        mimeType: String?,
        cookie: String?,
        userAgent: String?,
        completionAction: DownloadCompletionAction,
        expected: ExpectedFile?,
        proxyBase: String?,
        silentFailure: Boolean,
    ) {
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            var pendingUri: Uri? = null
            var displayName = fileName
            var transferred = 0L
            val startedAt = System.currentTimeMillis()
            try {
                val uri = if (expected != null) {
                    // Already stored under its final name: delete it if cancelled before completion.
                    downloadVerified(id, url, fileName, expected, proxyBase).also {
                        pendingUri = it
                        transferred = expected.size
                    }
                } else {
                    // 没有哈希也能走加速：只要「代理前缀 + 白名单内层地址」成立就放行。
                    // 这里不再要求代理仍出现在最新列表里——清单刷新不该让已在跑的候选作废；
                    // 但内层必须是 GitHub release 地址，否则代理就成了任意 URL 转发器。
                    if (ProxyUrl.looksLikeProxied(url) && proxiedInnerReleaseUrl(url, proxyBase) == null) {
                        throw DownloadVerificationException(
                            DownloadVerificationException.Reason.URL_NOT_ALLOWED,
                            "Download URL not allowed"
                        )
                    }
                    val call = ksuApp.okhttpClient.newCall(Request.Builder().url(url).apply {
                        cookie?.takeIf { it.isNotEmpty() }?.let { addHeader("Cookie", it) }
                        userAgent?.takeIf { it.isNotEmpty() }?.let { addHeader("User-Agent", it) }
                    }.build())
                    activeCalls[id] = call
                    ensureActive()
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                        val body = resp.body
                        val total = body.contentLength()

                        ensureActive()
                        val destination = DownloadDestination.create(
                            this@DownloadService,
                            fileName,
                            mimeType,
                        )
                        pendingUri = destination.uri
                        displayName = destination.displayName

                        destination.openOutputStream().use { fos ->
                            val buf = ByteArray(8 * 1024)
                            var soFar = 0L
                            val progress = ProgressReporter(id, displayName, total)
                            val source = body.byteStream()

                            while (true) {
                                ensureActive()
                                val read = source.read(buf)
                                if (read == -1) break
                                fos.write(buf, 0, read)
                                soFar += read
                                progress.report(soFar)
                            }
                            fos.flush()
                            transferred = soFar
                        }
                        ensureActive()
                        destination.publish()
                        destination.uri
                    }
                }
                ensureActive()
                pendingUri = null
                DownloadManager.markCompleted(id, uri, transferred, System.currentTimeMillis() - startedAt)

                notificationManager.cancel(id)
                notificationManager.notify(
                    COMPLETION_NOTIFICATION_ID_BASE + id,
                    buildCompletionNotification(id, displayName, uri, mimeType, completionAction)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                val verificationFailure = (e as? DownloadVerificationException)?.reason
                if (verificationFailure != null) {
                    Log.w("DownloadService", "Rejected download $url: ${e.message}")
                }
                DownloadManager.markFailed(id, e.message ?: "Unknown error", verificationFailure)

                notificationManager.cancel(id)
                // 只让链上最后一个候选报错：中间候选的失败对用户没有信息量。
                if (!silentFailure) {
                    notificationManager.notify(
                        COMPLETION_NOTIFICATION_ID_BASE + id,
                        buildFailureNotification(displayName, verificationFailure)
                    )
                }
            } finally {
                pendingUri?.let { deleteDownload(it) }
            }
        }
        activeJobs[id] = job
        job.invokeOnCompletion { cause ->
            val status = DownloadManager.downloads.value[id]?.status
            if (cause is CancellationException &&
                status != DownloadManager.Status.COMPLETED &&
                status != DownloadManager.Status.FAILED
            ) {
                DownloadManager.markFailed(id, "Cancelled")
                notificationManager.cancel(id)
            }
            activeCalls.remove(id)?.cancel()
            activeJobs.remove(id)
            stopForegroundIfIdle()
        }
        job.start()
    }

    private inner class ProgressReporter(private val id: Int, private val displayName: String, private val total: Long) {
        private var lastNotifiedProgress = -1

        fun report(soFar: Long) {
            if (total <= 0) return
            val percent = ((soFar * 100L) / total).toInt().coerceIn(0, 100)
            DownloadManager.updateProgress(id, percent)
            if (percent - lastNotifiedProgress >= 2 || percent == 100) {
                notificationManager.notify(id, buildProgressNotification(id, displayName, percent))
                lastNotifiedProgress = percent
            }
        }
    }

    private fun registryStoreRoot(): File = File(filesDir, RegistryDownloadStore.DIR_NAME)

    /**
     * Registry release ZIP: only GitHub Release URLs (and their CDN redirect targets), streamed
     * into app-private storage while size + SHA-256 are checked. A mismatching file is deleted
     * and never published; the flash step re-verifies the stored bytes before installing.
     *
     * 允许的地址有两类：直连的 GitHub Release 地址，以及「已知加速代理 + 白名单内层地址」。
     * 后者只是换一条到同一个 release 资产的路，sha256 仍然由直连拿到的登记值把关，
     * 所以代理看到/改写内容也骗不过校验。
     */
    private suspend fun CoroutineScope.downloadVerified(
        id: Int,
        url: String,
        fileName: String,
        expected: ExpectedFile,
        proxyBase: String?,
    ): Uri {
        // 直连直接放行；加速地址只认「入队快照里的代理 + 白名单内层」，不接受任意 host。
        val direct = RegistryUrls.isAllowedReleaseDownloadUrl(url)
        if (!direct && proxiedInnerReleaseUrl(url, proxyBase) == null) {
            throw DownloadVerificationException(
                DownloadVerificationException.Reason.URL_NOT_ALLOWED,
                "Download URL not allowed"
            )
        }
        val root = registryStoreRoot()
        runCatching { RegistryDownloadStore.cleanup(root, REGISTRY_DOWNLOAD_MAX_AGE_MS) }
        val call = registryClient(proxyBase).newCall(Request.Builder().url(url).build())
        activeCalls[id] = call
        ensureActive()
        val file = call.execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val progress = ProgressReporter(id, fileName, expected.size)
            RegistryDownloadStore.download(
                input = resp.body.byteStream(),
                root = root,
                expected = expected,
                fileName = fileName,
                onProgress = progress::report,
                checkActive = { ensureActive() },
            )
        }
        return Uri.fromFile(file)
    }

    private fun deleteDownload(uri: Uri) {
        if (LegacyDownloadFiles.delete(uri)) return
        runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { RegistryDownloadStore.delete(registryStoreRoot(), File(it)) }
                return@runCatching
            }
            contentResolver.delete(uri, null, null)
        }.onFailure {
            Log.w("DownloadService", "Failed to delete download: $uri", it)
        }
    }

    private fun buildProgressNotification(
        id: Int,
        fileName: String,
        progress: Int
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.download_progress_title, fileName))
        .setContentText("$progress%")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setProgress(100, progress, progress == 0)
        .setOngoing(true)
        .setSilent(true)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.download_cancel),
            createCancelPendingIntent(id)
        )
        .build()

    private fun buildCompletionNotification(
        id: Int,
        fileName: String,
        uri: Uri,
        mimeType: String?,
        completionAction: DownloadCompletionAction,
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_complete_title))
            .setContentText(getString(R.string.download_complete_content, fileName))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)

        val primaryPendingIntent = when (completionAction) {
            DownloadCompletionAction.INSTALL_MODULE -> PendingIntent.getActivity(
                this, id, Intent(this, MainActivity::class.java).apply {
                    action = ACTION_INSTALL_MODULE
                    putExtra(EXTRA_MODULE_URI, uri.toString())
                    putExtra(EXTRA_DOWNLOAD_ID, id)
                    putExtra(EXTRA_TOKEN, SettingsRepositoryImpl().intentToken)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).also { builder.addAction(android.R.drawable.ic_menu_save, getString(R.string.download_install), it) }
            DownloadCompletionAction.OPEN_FILE -> PendingIntent.getActivity(
                this, id, Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, resolveDownloadMimeType(fileName, mimeType))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }, getString(R.string.open)), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).also { builder.addAction(android.R.drawable.ic_menu_view, getString(R.string.open), it) }
        }
        builder.setContentIntent(primaryPendingIntent)

        // Add "Cancel" action button
        val dismissIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_DISMISS_DOWNLOAD
            putExtra(EXTRA_DOWNLOAD_ID, id)
            putExtra(EXTRA_MODULE_URI, uri.toString())
            putExtra(EXTRA_DELETE_FILE_ON_DISMISS, completionAction == DownloadCompletionAction.INSTALL_MODULE)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            COMPLETION_NOTIFICATION_ID_BASE + id,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.download_cancel),
            dismissPendingIntent
        )

        return builder.build()
    }

    private fun buildFailureNotification(
        fileName: String,
        verificationFailure: DownloadVerificationException.Reason? = null,
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.download_failed_title))
        .setContentText(
            when (verificationFailure) {
                null -> getString(R.string.download_failed_content, fileName)
                DownloadVerificationException.Reason.URL_NOT_ALLOWED -> getString(R.string.module_repo_download_url_blocked)
                DownloadVerificationException.Reason.SIZE_MISMATCH,
                DownloadVerificationException.Reason.SHA256_MISMATCH ->
                    getString(R.string.module_repo_download_verify_failed, fileName)
            }
        )
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setAutoCancel(true)
        .build()

    private fun createCancelPendingIntent(downloadId: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getService(
            this,
            downloadId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun stopForegroundIfIdle() {
        if (activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        // 服务被系统杀掉时协程也会被取消，invokeOnCompletion 未必来得及跑：这里主动把还挂着
        // 的任务标成失败，免得调用方永远等一个 PENDING（下载器的等待超时是最后一道保险）。
        activeJobs.keys.forEach { id ->
            val status = DownloadManager.downloads.value[id]?.status
            if (status != DownloadManager.Status.COMPLETED && status != DownloadManager.Status.FAILED) {
                DownloadManager.markFailed(id, "Service destroyed")
            }
        }
        serviceScope.cancel()
        activeCalls.values.forEach { it.cancel() }
        super.onDestroy()
    }
}
