package com.sukisu.ultra.ui.screen.settings

/**
 * 跨页锚点的交接点。
 *
 * 设置页收到锚点后先解析 [SettingsSection.pageFor]：落在一级页的直接滚动，落在二级页的
 * 把锚点连着所属页面存在这里再 push 目标页，目标页创建时 [consume] 一次，拿到后自己做滚动与高亮。
 * 用进程内单例而不是导航参数，是因为锚点只在这次跳转里有效，页面重建时不该再触发。
 *
 * 锚点记着页面归属：二级页只领取属于自己那一页的锚点，别页残留的锚点不会被顺手领走。
 */
object SettingsHighlight {

    /** 待处理锚点与它所属的页面，页面归属统一由 [SettingsSection.pageFor] 推导。 */
    @Volatile
    private var pending: Pair<SettingsPage, String>? = null

    fun request(anchor: String) {
        val page = SettingsSection.pageFor(anchor)
        // 「关于」页没有滚动高亮机制（自更新开关只有一行），存进去只会永远挂着
        pending = if (page == SettingsPage.ABOUT) null else page to anchor
    }

    /**
     * 取走属于 [page] 的待处理锚点，取完即清空，避免重组或返回时重复滚动。
     *
     * 不属于本页的锚点原样留着等它自己的页面来领；没有待处理锚点时返回 null。
     */
    fun consume(page: SettingsPage): String? {
        val current = pending ?: return null
        if (current.first != page) return null
        pending = null
        return current.second
    }
}
