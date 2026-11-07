package com.sukisu.ultra.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class ModuleUpdateInfo(
    val downloadUrl: String,
    val version: String,
    val changelog: String,
    val versionCode: Long = 0L,
    /** updateJson 里的 sha256（大小写不敏感，可能为空）。有值才允许走 GitHub 下载加速。 */
    val sha256: String = "",
    /** updateJson 里的文件大小（字节，0 表示未提供）。 */
    val size: Long = 0L,
) {
    companion object {
        val Empty = ModuleUpdateInfo("", "", "")
    }
}
