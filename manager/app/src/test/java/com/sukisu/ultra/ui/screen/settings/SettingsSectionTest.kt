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
        // 一级页三个入口行与「关于」也各有行级 requester，cardFor 不能把它们并成整张卡
        assertEquals(SettingsSection.MODULE_SETTINGS_ENTRY, SettingsSection.cardFor("module_settings_entry"))
        assertEquals(SettingsSection.ROOT_SECURITY_ENTRY, SettingsSection.cardFor("root_security_entry"))
        assertEquals(SettingsSection.MAINTENANCE_ENTRY, SettingsSection.cardFor("maintenance_entry"))
        assertEquals(SettingsSection.ABOUT, SettingsSection.cardFor("about"))
        // 未注册的锚点降级到一级页第一张卡（appearance，唯一永远存在的锚点）
        assertEquals(SettingsSection.APPEARANCE, SettingsSection.cardFor("diagnostics"))
        assertEquals(SettingsSection.GENERAL, SettingsSection.normalize("not_a_section"))
    }

    @Test
    fun settingsSectionsMapToTheRightPage() {
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("theme_store"))
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("not_a_section"))
        assertEquals(SettingsPage.MODULE, SettingsSection.pageFor("module_share"))
        assertEquals(SettingsPage.ROOT_SECURITY, SettingsSection.pageFor("package_rename"))
        assertEquals(SettingsPage.MAINTENANCE, SettingsSection.pageFor("uninstall"))
        // 「更新后不保留安装包」已搬进关于页，page 归属必须跟着走
        assertEquals(SettingsPage.ABOUT, SettingsSection.pageFor("auto_delete_update_apk"))
        // 一级页上的入口行 / 关于行都必须落回一级页，否则深链会送人去一个没有这一行的页
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("module_settings_entry"))
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("root_security_entry"))
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("maintenance_entry"))
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("module_upload"))
        assertEquals(SettingsPage.APPEARANCE, SettingsSection.pageFor("about"))
    }
}
