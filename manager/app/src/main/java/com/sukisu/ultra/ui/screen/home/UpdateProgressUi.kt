package com.sukisu.ultra.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import java.util.Locale

/** 更新进度卡标题：先给阶段结论，下载中再区分正式版与 CI 构建。 */
@Composable
fun updateProgressTitle(progress: UpdateProgress): String = when (progress.stage) {
    UpdateStage.DOWNLOADING ->
        stringResource(
            if (progress.isCi) R.string.update_progress_downloading_ci
            else R.string.update_progress_downloading_release
        )

    UpdateStage.VERIFYING -> stringResource(R.string.update_progress_verifying)
    UpdateStage.INSTALLING -> stringResource(R.string.update_progress_installing)
    UpdateStage.COMPLETED -> stringResource(R.string.update_progress_completed)
    UpdateStage.AWAITING_USER -> stringResource(R.string.update_progress_awaiting_user)
    UpdateStage.FAILED -> stringResource(R.string.update_progress_failed)
}

/** 下载中给出「百分比 · 已下载 / 总量 · 速度」；失败时退回错误文案；其余阶段没有明细。 */
@Composable
fun updateProgressDetail(progress: UpdateProgress): String? = when (progress.stage) {
    UpdateStage.DOWNLOADING -> {
        val downloaded = formatBytes(progress.downloadedBytes)
        val speed = formatBytes(progress.bytesPerSecond)
        val percent = progress.percent
        if (percent != null && progress.totalBytes > 0L) {
            stringResource(
                R.string.update_progress_detail,
                percent,
                downloaded,
                formatBytes(progress.totalBytes),
                speed,
            )
        } else {
            stringResource(R.string.update_progress_detail_unknown, downloaded, speed)
        }
    }

    UpdateStage.FAILED -> progress.error
    else -> null
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb)
    return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
}
