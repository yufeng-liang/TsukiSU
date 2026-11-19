package com.sukisu.ultra.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.sukisu.ultra.data.registry.DownloadVerificationException
import com.sukisu.ultra.data.registry.ExpectedFile
import com.sukisu.ultra.data.registry.canShareDownload
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object DownloadManager {

    enum class Status { PENDING, DOWNLOADING, COMPLETED, FAILED }

    data class DownloadState(
        val id: Int,
        val fileName: String,
        val url: String,
        val targetPath: String? = null,
        val mimeType: String? = null,
        val completionAction: DownloadCompletionAction = DownloadCompletionAction.INSTALL_MODULE,
        val progress: Int = 0,
        val status: Status = Status.PENDING,
        val resultUri: Uri? = null,
        val error: String? = null,
        
        val verificationFailure: DownloadVerificationException.Reason? = null,
        
        val expected: ExpectedFile? = null,
        /** 下载完成时落地的字节数；未完成或旧记录为 null。 */
        val bytes: Long? = null,
        /** 从开始到完成的耗时（毫秒）；未完成时 null。 */
        val elapsedMs: Long? = null,
        /** 完成/失败的时间戳，只用于清理旧记录。 */
        val finishedAt: Long? = null,
    ) {
        /** 实际下载速度（字节/秒）；拿不到字节或耗时就记 0，不编数字。 */
        val speedBytesPerSec: Long
            get() {
                val size = bytes ?: return 0L
                val elapsed = elapsedMs?.takeIf { it > 0 } ?: return 0L
                return size * 1000 / elapsed
            }
    }

    private val idCounter = AtomicInteger(0)
    private val _downloads = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<Int, DownloadState>> = _downloads.asStateFlow()

    private val completionCallbacks = ConcurrentHashMap<Int, (Uri) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    internal fun registerLocalSave(
        fileName: String,
        targetPath: String? = null,
        mimeType: String? = null,
        completionAction: DownloadCompletionAction = DownloadCompletionAction.OPEN_FILE,
    ): Int {
        val id = idCounter.incrementAndGet()
        val state = DownloadState(
            id = id,
            fileName = fileName,
            url = targetPath ?: fileName,
            targetPath = targetPath,
            mimeType = mimeType,
            completionAction = completionAction,
        )
        _downloads.update { it + (id to state) }
        return id
    }

    fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        targetPath: String? = null,
        mimeType: String? = null,
        cookie: String? = null,
        userAgent: String? = null,
        completionAction: DownloadCompletionAction = DownloadCompletionAction.INSTALL_MODULE,
        onCompleted: ((Uri) -> Unit)? = null,
        expected: ExpectedFile? = null,
        /** 这个候选所属的代理基址（直连为 null）；服务层据此授权，不再重新读远程清单。 */
        proxyBase: String? = null,
        /** 失败时是否弹通知。一条候选链只有最后一条该弹。 */
        notifyOnFailure: Boolean = true,
    ): Int {
        // Only share an in-flight download that is verified exactly like this request.
        val existing = _downloads.value.values.find {
            it.fileName == fileName &&
                it.targetPath == targetPath &&
                it.completionAction == completionAction &&
                (it.status == Status.PENDING || it.status == Status.DOWNLOADING) &&
                canShareDownload(url, expected, it.url, it.expected)
        }
        if (existing != null) {
            if (onCompleted != null) {
                completionCallbacks.merge(existing.id, onCompleted) { a, b -> { uri -> a(uri); b(uri) } }
                // It may have finished in between; markCompleted / markFailed already drained the
                // callbacks then, so run (completed) or drop (failed) what was merged afterwards.
                val now = _downloads.value[existing.id]
                if (now?.status == Status.COMPLETED && now.resultUri != null) {
                    completionCallbacks.remove(existing.id)?.let { cb -> mainHandler.post { cb(now.resultUri) } }
                } else if (now == null || now.status == Status.FAILED) {
                    completionCallbacks.remove(existing.id)
                }
            }
            return existing.id
        }

        val id = idCounter.incrementAndGet()
        val state = DownloadState(
            id = id,
            fileName = fileName,
            url = url,
            targetPath = targetPath,
            mimeType = mimeType,
            completionAction = completionAction,
            expected = expected,
        )
        _downloads.update { it + (id to state) }

        // 地址白名单校验已后移到 DownloadService.downloadVerified：这里的入队早于候选展开，
        // 而「GitHub 下载加速」会把 release 地址套成 https://<代理>/https://github.com/...，
        // 在这一层判白名单会把所有代理候选当场判失败（带 expected 的模块安装正好全走这条）。
        // 后移不降低安全性：DownloadService 仍要求「内层是合法 release 地址」并逐跳校验。

        if (onCompleted != null) {
            completionCallbacks[id] = onCompleted
        }

        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, id)
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_FILE_NAME, fileName)
            if (expected != null) {
                putExtra(DownloadService.EXTRA_EXPECTED_SIZE, expected.size)
                putExtra(DownloadService.EXTRA_EXPECTED_SHA256, expected.sha256)
            }
            putExtra(DownloadService.EXTRA_TARGET_PATH, targetPath)
            putExtra(DownloadService.EXTRA_MIME_TYPE, mimeType)
            putExtra(DownloadService.EXTRA_COOKIE, cookie)
            putExtra(DownloadService.EXTRA_USER_AGENT, userAgent)
            putExtra(DownloadService.EXTRA_COMPLETION_ACTION, completionAction.name)
            putExtra(DownloadService.EXTRA_PROXY_BASE, proxyBase)
            putExtra(DownloadService.EXTRA_NOTIFY_ON_FAILURE, notifyOnFailure)
        }
        // 后台启动前台服务在部分 ROM 上会被拒（后台限制 / 省电策略）。这里必须兜住：
        // 异常抛出去会让整条候选链断掉，而调用方连 downloadId 都拿不到。
        val started = runCatching { ContextCompat.startForegroundService(context, intent) }
        if (started.isFailure) {
            Log.w("DownloadManager", "无法启动下载服务: $url", started.exceptionOrNull())
            markFailed(id, started.exceptionOrNull()?.message ?: "startForegroundService failed")
            return -1
        }

        return id
    }

    internal fun updateProgress(id: Int, progress: Int) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            map + (id to state.copy(progress = progress, status = Status.DOWNLOADING))
        }
    }

    internal fun markCompleted(id: Int, uri: Uri, bytes: Long? = null, elapsedMs: Long? = null) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            prune(
                map + (id to state.copy(
                    status = Status.COMPLETED,
                    progress = 100,
                    resultUri = uri,
                    bytes = bytes,
                    elapsedMs = elapsedMs,
                    finishedAt = System.currentTimeMillis(),
                ))
            )
        }

        val callback = completionCallbacks.remove(id)
        if (callback != null) {
            mainHandler.post { callback(uri) }
        }
    }

    internal fun markFailed(
        id: Int,
        error: String,
        verificationFailure: DownloadVerificationException.Reason? = null,
    ) {
        _downloads.update { map ->
            val state = map[id] ?: return@update map
            prune(
                map + (id to state.copy(
                    status = Status.FAILED,
                    error = error,
                    verificationFailure = verificationFailure,
                    finishedAt = System.currentTimeMillis(),
                ))
            )
        }
        completionCallbacks.remove(id)
    }

    /**
     * 状态表只保留最近 [MAX_FINISHED_STATES] 条已结束记录。
     *
     * 这张表是进程级单例，原先只增不减：一次更新会串行试掉好几个候选代理，每个候选一条
     * 失败记录，越积越多，而每次进度回调都要复制整张 Map。未结束的记录永远保留。
     */
    private fun prune(map: Map<Int, DownloadState>): Map<Int, DownloadState> {
        val finished = map.values.filter { it.status == Status.COMPLETED || it.status == Status.FAILED }
        if (finished.size <= MAX_FINISHED_STATES) return map
        val keep = finished.sortedByDescending { it.finishedAt ?: 0L }
            .take(MAX_FINISHED_STATES)
            .map { it.id }
            .toSet()
        return map.filter { (id, state) ->
            state.status == Status.PENDING || state.status == Status.DOWNLOADING || id in keep
        }
    }

    private const val MAX_FINISHED_STATES = 24
}
