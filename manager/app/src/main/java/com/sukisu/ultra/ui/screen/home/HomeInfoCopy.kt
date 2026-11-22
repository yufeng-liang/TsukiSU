package com.sukisu.ultra.ui.screen.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.sukisu.ultra.R

/**
 * 把系统信息卡中某一行的值复制到剪贴板，并提示「已复制到剪贴板」。
 *
 * 长按行即调用本函数；空值（例如未取到的内核完整版本）直接忽略，
 * 避免把空字符串写进剪贴板覆盖掉用户原有的内容。
 */
internal fun copySystemInfoValue(context: Context, value: String) {
    if (value.isBlank()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("SukiSU system info", value))
    Toast.makeText(context, R.string.home_info_copied, Toast.LENGTH_SHORT).show()
}
