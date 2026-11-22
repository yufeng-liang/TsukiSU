package com.sukisu.ultra.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.sukisu.ultra.R

/**
 * 设置搜索索引里的一行。
 *
 * 索引只描述「有哪些设置项、在哪一页、跳到哪个锚点」，不持有任何界面状态，
 * 所以整张表在编译期就是不可变的，搜索页直接按关键字过滤即可。
 *
 * @param id       稳定标识，只用于列表 key 与埋点，别和 [anchor] 混用
 * @param titleRes 行标题，复用设置页上真正展示的字符串，避免索引与界面对不上
 * @param groupRes 所属一级页的标题，搜索结果按它分组
 * @param page     点击后进入哪张一级页，取值与 [SettingsSection.pageFor] 保持一致
 * @param anchor   交给目标页滚动并高亮的锚点，取值来自 [SettingsSection]
 */
@Immutable
data class SettingsSearchEntry(
    val id: String,
    val titleRes: Int,
    val groupRes: Int,
    val page: SettingsPage,
    val anchor: String,
)

/**
 * 全量设置项索引，按一级页顺序排列：外观与语言 → 模块与扩展 → Root 与安全 → 诊断与维护。
 *
 * 两个硬约束，改索引时必须同时满足：
 * 1. [SettingsSearchEntry.anchor] 必须出现在 [SettingsSection.ALL] 里，否则 AI 助手拿它跳转时
 *    会被 [SettingsSection.normalize] 静默降级成 general；
 * 2. [SettingsSearchEntry.page] 必须和 [SettingsSection.pageFor] 对该锚点的结论一致，
 *    否则搜索页会把用户送到错误的二级页。
 */
object SettingsSearchIndex {

    val entries: List<SettingsSearchEntry> = listOf(
        // ── 外观与语言 ──────────────────────────────────────────────────────────
        SettingsSearchEntry(
            id = "theme_store",
            titleRes = R.string.theme_store_title,
            groupRes = R.string.settings_section_appearance,
            page = SettingsPage.APPEARANCE,
            anchor = SettingsSection.THEME_STORE,
        ),
        SettingsSearchEntry(
            id = "ui_mode",
            titleRes = R.string.settings_ui_mode,
            groupRes = R.string.settings_section_appearance,
            page = SettingsPage.APPEARANCE,
            anchor = SettingsSection.UI_MODE,
        ),
        SettingsSearchEntry(
            id = "language",
            titleRes = R.string.settings_language,
            groupRes = R.string.settings_section_appearance,
            page = SettingsPage.APPEARANCE,
            anchor = SettingsSection.LANGUAGE,
        ),
        SettingsSearchEntry(
            id = "theme",
            titleRes = R.string.settings_theme,
            groupRes = R.string.settings_section_appearance,
            page = SettingsPage.APPEARANCE,
            anchor = SettingsSection.THEME,
        ),

        // ── 模块与扩展 ──────────────────────────────────────────────────────────
        SettingsSearchEntry(
            id = "module_upload",
            titleRes = R.string.module_upload_entry_title,
            groupRes = R.string.settings_group_module,
            page = SettingsPage.APPEARANCE,
            anchor = SettingsSection.MODULE_UPLOAD,
        ),
        SettingsSearchEntry(
            id = "module_share",
            titleRes = R.string.module_upload_after_install_title,
            groupRes = R.string.settings_group_module,
            page = SettingsPage.MODULE,
            anchor = SettingsSection.MODULE_SHARE,
        ),
        SettingsSearchEntry(
            id = "profile_templates",
            titleRes = R.string.settings_profile_template,
            groupRes = R.string.settings_group_module,
            page = SettingsPage.MODULE,
            anchor = SettingsSection.PROFILE_TEMPLATES,
        ),
        SettingsSearchEntry(
            id = "extensions",
            titleRes = R.string.extension_section_title,
            groupRes = R.string.settings_group_module,
            page = SettingsPage.MODULE,
            anchor = SettingsSection.EXTENSIONS,
        ),
        SettingsSearchEntry(
            id = "tools",
            titleRes = R.string.settings_tools,
            groupRes = R.string.settings_group_module,
            page = SettingsPage.MODULE,
            anchor = SettingsSection.TOOLS,
        ),
        SettingsSearchEntry(
            id = "default_umount",
            titleRes = R.string.settings_umount_modules_default,
            groupRes = R.string.settings_group_module,
            page = SettingsPage.MODULE,
            anchor = SettingsSection.DEFAULT_UMOUNT_MODULES,
        ),
        SettingsSearchEntry(
            id = "auto_delete_module_zip",
            titleRes = R.string.settings_auto_delete_module_zip,
            groupRes = R.string.settings_group_module,
            page = SettingsPage.MODULE,
            anchor = SettingsSection.AUTO_DELETE_MODULE_ZIP,
        ),
        // 这一行已经搬进「关于」页：page 必须跟 SettingsSection.pageFor 一致（ABOUT），
        // 否则搜索页会把人送到维护页那个已经不存在的锚点上
        SettingsSearchEntry(
            id = "auto_delete_update_apk",
            titleRes = R.string.settings_auto_delete_update_apk,
            groupRes = R.string.settings_group_maintenance,
            page = SettingsPage.ABOUT,
            anchor = SettingsSection.AUTO_DELETE_UPDATE_APK,
        ),

        // ── Root 与安全 ─────────────────────────────────────────────────────────
        SettingsSearchEntry(
            id = "temporary_root",
            titleRes = R.string.temporary_root_title,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.TEMPORARY_ROOT,
        ),
        SettingsSearchEntry(
            id = "su_compat",
            titleRes = R.string.settings_sucompat,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.SU_COMPAT,
        ),
        SettingsSearchEntry(
            id = "kernel_umount",
            titleRes = R.string.settings_kernel_umount,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.KERNEL_UMOUNT,
        ),
        SettingsSearchEntry(
            id = "selinux_hide",
            titleRes = R.string.settings_selinux_hide,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.SELINUX_HIDE,
        ),
        SettingsSearchEntry(
            id = "avc_spoof",
            titleRes = R.string.settings_avc_spoof,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.AVC_SPOOF,
        ),
        SettingsSearchEntry(
            id = "mount_hide",
            titleRes = R.string.settings_mount_hide,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.MOUNT_HIDE,
        ),
        SettingsSearchEntry(
            id = "sulog",
            titleRes = R.string.settings_sulog,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.SULOG,
        ),
        SettingsSearchEntry(
            id = "adb_root",
            titleRes = R.string.settings_adb_root,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.ADB_ROOT,
        ),
        SettingsSearchEntry(
            id = "shell_su_adb_only",
            titleRes = R.string.settings_shell_su_adb_only,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.SHELL_SU_ADB_ONLY,
        ),
        SettingsSearchEntry(
            id = "soft_reboot",
            titleRes = R.string.settings_soft_reboot,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.SOFT_REBOOT,
        ),
        SettingsSearchEntry(
            id = "builtin_zygisk",
            titleRes = R.string.settings_builtin_zygisk,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.BUILTIN_ZYGISK,
        ),
        // 仪表盘行只在内置 Zygisk 打开时才渲染；索引照样保留，因为搜索要解决的是「找得到」，不是「此刻可见」。
        SettingsSearchEntry(
            id = "builtin_zygisk_dashboard",
            titleRes = R.string.settings_builtin_zygisk_dashboard,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.BUILTIN_ZYGISK_DASHBOARD,
        ),
        SettingsSearchEntry(
            id = "package_rename",
            titleRes = R.string.pkg_rename_title,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.PACKAGE_RENAME,
        ),
        SettingsSearchEntry(
            id = "auto_jailbreak",
            titleRes = R.string.settings_auto_jailbreak,
            groupRes = R.string.settings_group_root_security,
            page = SettingsPage.ROOT_SECURITY,
            anchor = SettingsSection.AUTO_JAILBREAK,
        ),

        // ── 诊断与维护 ──────────────────────────────────────────────────────────
        SettingsSearchEntry(
            id = "github_proxy",
            titleRes = R.string.settings_proxy_title,
            groupRes = R.string.settings_group_maintenance,
            page = SettingsPage.MAINTENANCE,
            anchor = SettingsSection.GITHUB_PROXY,
        ),
        SettingsSearchEntry(
            id = "backup",
            titleRes = R.string.backup_title,
            groupRes = R.string.settings_group_maintenance,
            page = SettingsPage.MAINTENANCE,
            anchor = SettingsSection.BACKUP,
        ),
        SettingsSearchEntry(
            id = "send_log",
            titleRes = R.string.send_log,
            groupRes = R.string.settings_group_maintenance,
            page = SettingsPage.MAINTENANCE,
            anchor = SettingsSection.SEND_LOG,
        ),
        SettingsSearchEntry(
            id = "web_debugging",
            titleRes = R.string.enable_web_debugging,
            groupRes = R.string.settings_group_maintenance,
            page = SettingsPage.MAINTENANCE,
            anchor = SettingsSection.WEB_DEBUGGING,
        ),
        SettingsSearchEntry(
            id = "uninstall",
            titleRes = R.string.settings_uninstall,
            groupRes = R.string.settings_group_maintenance,
            page = SettingsPage.MAINTENANCE,
            anchor = SettingsSection.UNINSTALL,
        ),
        SettingsSearchEntry(
            id = "about",
            titleRes = R.string.about,
            groupRes = R.string.settings_group_maintenance,
            page = SettingsPage.APPEARANCE,
            anchor = SettingsSection.ABOUT,
        ),
    )

    /**
     * 按锚点查回索引项。外部传进来的可能是别名、带空格或大写写法，
     * 先用 [SettingsSection.normalize] 归一化再比对，查不到返回 null 交给调用方兜底。
     */
    fun entryForAnchor(anchor: String): SettingsSearchEntry? {
        val id = SettingsSection.normalize(anchor)
        return entries.firstOrNull { it.anchor == id }
    }
}
