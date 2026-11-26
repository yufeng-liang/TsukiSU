package com.sukisu.ultra.ui.screen.about

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 将构建时记录的时间戳格式化为设备本地时间。
 *
 * 构建脚本只写入一个时间点，避免把构建机时区固化进 APK；
 * 这里显式使用设备默认时区，保证同一份安装包在不同地区显示对应当地时间。
 */
internal fun formatBuildTime(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        this.timeZone = timeZone
    }.format(Date(epochMillis))
