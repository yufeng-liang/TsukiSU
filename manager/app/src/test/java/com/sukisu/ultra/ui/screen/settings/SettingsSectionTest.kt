package com.sukisu.ultra.ui.screen.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设置页拆分之后锚点仍全局唯一：先由 pageFor 决定去哪个二级页，
 * 再由 cardFor 决定页内滚到哪个卡片。这里锁住三张表与页面归属的一致性。
 */
class SettingsSectionTest {

    @Test
    fun settingsSectionsResolvePreciseAliases() {
        assertEquals(SettingsSection.UI_MODE, SettingsSection.normalize("ui_mode"))
        // 一级页的四行各自注册了行级 requester，cardFor 要原样返回而不是归到 general
        assertEquals(SettingsSection.UI_MODE, SettingsSection.cardFor("ui_mode"))
        assertEquals(SettingsSection.THEME, SettingsSection.cardFor("theme"))
        assertEquals(SettingsSection.SECURITY, SettingsSection.cardFor("selinux_hide"))
        assertEquals(
            SettingsSection.MODULE_SETTINGS_ENTRY,
            SettingsSection.cardFor("auto_delete_module_zip"),
        )
        // 未注册的锚点一律降级成一级页 general
        assertEquals(SettingsSection.GENERAL, SettingsSection.cardFor("diagnostics"))
        assertEquals(SettingsSection.GENERAL, SettingsSection.normalize("not_a_section"))
    }

    @Test
    fun settingsSectionsMapToTheRightPage() {
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("theme_store"))
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("not_a_section"))
        assertEquals(SettingsPage.MODULE, SettingsSection.pageFor("module_share"))
        assertEquals(SettingsPage.ROOT_SECURITY, SettingsSection.pageFor("package_rename"))
        assertEquals(SettingsPage.MAINTENANCE, SettingsSection.pageFor("uninstall"))
    }
}
