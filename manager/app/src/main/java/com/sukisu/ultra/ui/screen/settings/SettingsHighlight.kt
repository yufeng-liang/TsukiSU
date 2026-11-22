package com.sukisu.ultra.ui.screen.settings

/**
 * 跨页锚点的交接点。
 *
 * 设置页收到锚点后先解析 [SettingsSection.pageFor]：落在一级页的直接滚动，落在二级页的
 * 把锚点存在这里再 push 目标页，目标页创建时 [consume] 一次，拿到后自己做滚动与高亮。
 * 用进程内单例而不是导航参数，是因为锚点只在这次跳转里有效，页面重建时不该再触发。
 */
object SettingsHighlight {

    @Volatile
    var pendingAnchor: String? = null
        private set

    fun request(anchor: String) {
        pendingAnchor = anchor
    }

    /** 取走待处理锚点，取完即清空，避免重组或返回时重复滚动。 */
    fun consume(): String? {
        val anchor = pendingAnchor
        pendingAnchor = null
        return anchor
    }
}
