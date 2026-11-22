package com.sukisu.ultra.ui.screen.settings

/**
 * 设置项所属的页面。一级页只留「外观与语言」，其余三组各自有独立路由。
 */
enum class SettingsPage {
    APPEARANCE,
    MODULE,
    ROOT_SECURITY,
    MAINTENANCE,

    /** 「关于」页：管理器自更新相关开关，独立路由，不属于四个设置分组。 */
    ABOUT,
}

/**
 * Stable anchors used by the AI assistant to scroll the Settings page to an
 * exact card or row. Aliases let a precise setting id resolve to the card that
 * contains it when the row is not individually registered.
 *
 * 页面拆分之后锚点仍是全局唯一的，先由 [pageFor] 决定去哪个页面，再由页面内的
 * 行级 requester 负责滚动与高亮。因此新增锚点时三张表（常量、[ALL]、[pageFor]）
 * 必须同时改，漏一处会让 AI 的跳转被 normalize 静默降级成 general。
 */
object SettingsSection {

    // 一级页：外观与语言
    const val GENERAL = "general"
    const val UI_MODE = "ui_mode"
    const val LANGUAGE = "language"
    const val THEME = "theme"
    const val THEME_STORE = "theme_store"

    // 一级页：三个入口行
    const val APPEARANCE = "appearance"
    const val MODULE_SETTINGS_ENTRY = "module_settings_entry"
    const val ROOT_SECURITY_ENTRY = "root_security_entry"
    const val MAINTENANCE_ENTRY = "maintenance_entry"

    // 模块与扩展页
    const val MODULE_UPLOAD = "module_upload"
    const val MODULE_SHARE = "module_share"
    const val PROFILE_TEMPLATES = "profile_templates"
    const val EXTENSIONS = "extensions"
    const val TOOLS = "tools"
    const val DEFAULT_UMOUNT_MODULES = "default_umount_modules"
    const val AUTO_DELETE_MODULE_ZIP = "auto_delete_module_zip"
    const val AUTO_DELETE_UPDATE_APK = "auto_delete_update_apk"

    // Root 与安全页
    const val TEMPORARY_ROOT = "temporary_root"
    const val SECURITY = "security"
    // Individual security rows; they resolve to the security card unless the
    // screen registers a finer requester.
    const val SU_COMPAT = "su_compat"
    const val KERNEL_UMOUNT = "kernel_umount"
    const val SELINUX_HIDE = "selinux_hide"
    const val AVC_SPOOF = "avc_spoof"
    const val MOUNT_HIDE = "mount_hide"
    const val SULOG = "sulog"
    const val ADB_ROOT = "adb_root"
    const val SHELL_SU_ADB_ONLY = "shell_su_adb_only"
    const val SOFT_REBOOT = "soft_reboot"
    const val BUILTIN_ZYGISK = "builtin_zygisk"
    const val BUILTIN_ZYGISK_DASHBOARD = "builtin_zygisk_dashboard"
    const val PACKAGE_RENAME = "package_rename"
    const val AUTO_JAILBREAK = "auto_jailbreak"

    // 诊断与维护页
    const val GITHUB_PROXY = "github_proxy"
    const val BACKUP = "backup"
    const val SEND_LOG = "send_log"
    const val WEB_DEBUGGING = "web_debugging"
    const val UNINSTALL = "uninstall"
    const val ABOUT = "about"

    val ALL: Set<String> = setOf(
        GENERAL, UI_MODE, LANGUAGE, THEME, THEME_STORE,
        APPEARANCE, MODULE_SETTINGS_ENTRY, ROOT_SECURITY_ENTRY, MAINTENANCE_ENTRY,
        MODULE_UPLOAD, MODULE_SHARE, PROFILE_TEMPLATES, EXTENSIONS, TOOLS,
        DEFAULT_UMOUNT_MODULES, AUTO_DELETE_MODULE_ZIP, AUTO_DELETE_UPDATE_APK,
        TEMPORARY_ROOT, SECURITY,
        SU_COMPAT, KERNEL_UMOUNT, SELINUX_HIDE, AVC_SPOOF, MOUNT_HIDE, SULOG,
        ADB_ROOT, SHELL_SU_ADB_ONLY, SOFT_REBOOT,
        BUILTIN_ZYGISK, BUILTIN_ZYGISK_DASHBOARD, PACKAGE_RENAME, AUTO_JAILBREAK,
        GITHUB_PROXY, BACKUP, SEND_LOG, WEB_DEBUGGING, UNINSTALL, ABOUT,
    )

    /**
     * 锚点 → 页面。未知锚点归到一级页，与 [normalize] 的兜底一致。
     */
    fun pageFor(anchor: String): SettingsPage = when (normalize(anchor)) {
        // 三个入口行与「关于」行都渲染在一级页，锚点必须落回一级页才滚得到那一行
        GENERAL, UI_MODE, LANGUAGE, THEME, THEME_STORE, APPEARANCE,
        MODULE_SETTINGS_ENTRY, ROOT_SECURITY_ENTRY, MAINTENANCE_ENTRY, MODULE_UPLOAD, ABOUT,
        -> SettingsPage.APPEARANCE
        MODULE_SHARE, PROFILE_TEMPLATES, EXTENSIONS, TOOLS,
        DEFAULT_UMOUNT_MODULES, AUTO_DELETE_MODULE_ZIP,
        -> SettingsPage.MODULE
        // 安装后删除安装包已搬进「关于」页，只有 About 页能高亮它
        AUTO_DELETE_UPDATE_APK -> SettingsPage.ABOUT
        TEMPORARY_ROOT, SECURITY, SU_COMPAT, KERNEL_UMOUNT, SELINUX_HIDE,
        AVC_SPOOF, MOUNT_HIDE, SULOG, ADB_ROOT, SHELL_SU_ADB_ONLY, SOFT_REBOOT,
        BUILTIN_ZYGISK, BUILTIN_ZYGISK_DASHBOARD, PACKAGE_RENAME, AUTO_JAILBREAK,
        -> SettingsPage.ROOT_SECURITY
        GITHUB_PROXY, BACKUP, SEND_LOG, WEB_DEBUGGING, UNINSTALL,
        -> SettingsPage.MAINTENANCE
        else -> SettingsPage.APPEARANCE
    }

    /**
     * 锚点 → 卡片。页面拆分后一级页只剩外观与语言，卡片粒度仅供页内分组使用。
     */
    fun cardFor(anchor: String): String = when (val id = anchor.trim().lowercase().replace('-', '_')) {
        // 一级页的四行、三个入口行与「关于」各自注册了行级 requester，跳转要命中那一行而不是整张卡
        UI_MODE, LANGUAGE, THEME, THEME_STORE, APPEARANCE,
        MODULE_SETTINGS_ENTRY, ROOT_SECURITY_ENTRY, MAINTENANCE_ENTRY, ABOUT,
        -> id
        PROFILE_TEMPLATES -> PROFILE_TEMPLATES
        EXTENSIONS -> EXTENSIONS
        PACKAGE_RENAME -> PACKAGE_RENAME
        TOOLS -> TOOLS
        TEMPORARY_ROOT -> TEMPORARY_ROOT
        SU_COMPAT, KERNEL_UMOUNT, SELINUX_HIDE, AVC_SPOOF, MOUNT_HIDE, SULOG,
        ADB_ROOT, SHELL_SU_ADB_ONLY, SOFT_REBOOT, BUILTIN_ZYGISK,
        BUILTIN_ZYGISK_DASHBOARD,
        -> SECURITY
        DEFAULT_UMOUNT_MODULES, AUTO_DELETE_MODULE_ZIP, AUTO_DELETE_UPDATE_APK,
        WEB_DEBUGGING, AUTO_JAILBREAK, MODULE_UPLOAD, MODULE_SHARE,
        -> MODULE_SETTINGS_ENTRY
        GITHUB_PROXY, BACKUP, SEND_LOG -> GITHUB_PROXY
        UNINSTALL -> UNINSTALL
        else -> APPEARANCE
    }

    fun normalize(anchor: String): String {
        val id = anchor.trim().lowercase().replace('-', '_')
        return if (id in ALL) id else GENERAL
    }
}
