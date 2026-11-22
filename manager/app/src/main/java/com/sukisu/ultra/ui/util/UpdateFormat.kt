package com.sukisu.ultra.ui.util

import java.util.Locale

/** Compact byte count for download progress, e.g. `12.3 MB`. */
fun formatByteCount(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))

    bytes >= 1024L * 1024 ->
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))

    bytes >= 1024L ->
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)

    else -> "$bytes B"
}

/** Transfer rate derived from a bytes-per-second sample, e.g. `2.4 MB/s`. */
fun formatTransferRate(bytesPerSecond: Long): String =
    if (bytesPerSecond > 0L) {
        "${formatByteCount(bytesPerSecond)}/s"
    } else {
        "0 B/s"
    }
