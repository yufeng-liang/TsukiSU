package com.sukisu.ultra.ui.util.module

data class LatestVersionInfo(
    val versionCode: Long = 0L,
    val downloadUrl: String = "",
    val changelog: String = "",
    /**
     * 资产内容的 sha256，裸 64 位小写十六进制（不带 `sha256:` 前缀）。
     *
     * 来源于 GitHub API release asset 的 `digest` 字段；老 release 上该字段可能缺失，此时留空。
     * 空值在下载侧是一条安全边界（见 `CiManagerUpdater.download`）：没有校验值就不允许走代理，
     * 因为代理是 TLS 终点，改包没人看得出来。
     */
    val sha256: String = ""
)
