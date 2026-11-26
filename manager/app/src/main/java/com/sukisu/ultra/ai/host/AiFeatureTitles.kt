package com.sukisu.ultra.ai.host

import com.sukisu.ultra.R
import com.sukisu.ultra.ksuApp
import java.util.Locale

/**
 * 把 AiFeatureCatalog 里的英文页面标题翻译成当前系统语言。
 * 标题用 " · " 分段，例如 "Settings · General" -> "设置 · 通用"。
 */
internal object AiFeatureTitles {
    private val segmentRes: Map<String, Int> = mapOf(
        "home" to R.string.ai_page_home,
        "superuser" to R.string.ai_page_superuser,
        "app root profile" to R.string.ai_page_app_root_profile,
        "modules" to R.string.ai_page_modules,
        "module actions" to R.string.ai_page_module_actions,
        "module repository" to R.string.ai_page_module_repository,
        "repository module detail" to R.string.ai_page_repository_module_detail,
        "hide-environment column" to R.string.ai_page_hide_environment_column,
        "column topic wizard" to R.string.ai_page_column_topic_wizard,
        "column" to R.string.ai_page_column,
        "wizard" to R.string.ai_page_wizard,
        "install / update" to R.string.ai_page_install_update,
        "flash image" to R.string.ai_page_flash_image,
        "extensions" to R.string.ai_page_extensions,
        "kpm" to R.string.ai_page_kpm,
        "cpu spoof" to R.string.ai_page_cpu_spoof,
        "umount paths" to R.string.ai_page_umount_paths,
        "dynamic manager" to R.string.ai_page_dynamic_manager,
        "susfs" to R.string.ai_page_susfs,
        "basic settings" to R.string.ai_page_basic_settings,
        "feature toggles" to R.string.ai_page_feature_toggles,
        "paths" to R.string.ai_page_paths,
        "loop paths" to R.string.ai_page_loop_paths,
        "maps" to R.string.ai_page_maps,
        "kstat" to R.string.ai_page_kstat,
        "priority uid filter" to R.string.ai_page_priority_uid_filter,
        "enabled features" to R.string.ai_page_enabled_features,
        "enabled_features" to R.string.ai_page_enabled_features,
        "version spoof" to R.string.ai_page_version_spoof,
        "kernel module blocklist" to R.string.ai_page_kernel_module_blocklist,
        "tools" to R.string.ai_page_tools,
        "su log" to R.string.ai_page_su_log,
        "profile templates" to R.string.ai_page_profile_templates,
        "profile_templates" to R.string.ai_page_profile_templates,
        "template editor" to R.string.ai_page_template_editor,
        "script runner" to R.string.ai_page_script_runner,
        "about" to R.string.ai_page_about,
        "settings" to R.string.ai_page_settings,
        "general" to R.string.ai_page_general,
        "security" to R.string.ai_page_security,
        "su compatibility" to R.string.ai_page_su_compat,
        "su_compat" to R.string.ai_page_su_compat,
        "kernel umount" to R.string.ai_page_kernel_umount,
        "kernel_umount" to R.string.ai_page_kernel_umount,
        "selinux hide" to R.string.ai_page_selinux_hide,
        "selinux_hide" to R.string.ai_page_selinux_hide,
        "avc log spoofing" to R.string.ai_page_avc_spoof,
        "avc_spoof" to R.string.ai_page_avc_spoof,
        "mount hide" to R.string.ai_page_mount_hide,
        "mount_hide" to R.string.ai_page_mount_hide,
        "adb root" to R.string.ai_page_adb_root,
        "adb_root" to R.string.ai_page_adb_root,
        "shell su adb only" to R.string.ai_page_shell_su_adb_only,
        "shell_su_adb_only" to R.string.ai_page_shell_su_adb_only,
        "soft reboot" to R.string.ai_page_soft_reboot,
        "soft_reboot" to R.string.ai_page_soft_reboot,
        "default umount modules" to R.string.ai_page_default_umount_modules,
        "default_umount_modules" to R.string.ai_page_default_umount_modules,
        "auto-delete module zip" to R.string.ai_page_auto_delete_module_zip,
        "auto_delete_module_zip" to R.string.ai_page_auto_delete_module_zip,
        "web debugging" to R.string.ai_page_web_debugging,
        "web_debugging" to R.string.ai_page_web_debugging,
        "auto jailbreak" to R.string.ai_page_auto_jailbreak,
        "auto_jailbreak" to R.string.ai_page_auto_jailbreak,
        "temporary root default" to R.string.ai_page_temporary_root_default,
        "temporary_root" to R.string.ai_page_temporary_root,
        "module behaviour" to R.string.ai_page_module_behaviour,
        "module_behaviour" to R.string.ai_page_module_behaviour,
        "uninstall" to R.string.ai_page_uninstall,
        "diagnostics" to R.string.ai_page_diagnostics,
        "appearance" to R.string.ai_page_appearance,
        "appearance_section" to R.string.ai_page_icon_text,
        "preview" to R.string.ai_page_preview,
        "color_mode" to R.string.ai_page_color_mode,
        "palette" to R.string.ai_page_color_palette,
        "chrome" to R.string.ai_page_bottom_bar_navigation,
        "gestures" to R.string.ai_page_interaction,
        "predictive_back" to R.string.ai_page_predictive_back,
        "scale" to R.string.ai_page_page_scale,
        "module_description" to R.string.ai_page_details,
        "sulog" to R.string.ai_page_su_log,
        "background" to R.string.ai_page_background,
        "colors" to R.string.ai_page_colors,
        "theme package" to R.string.ai_page_theme_package,
        "theme_package" to R.string.ai_page_theme_package,
        "behavior" to R.string.ai_page_behavior,
        "behaviour" to R.string.ai_page_behavior,
        "color palette" to R.string.ai_page_color_palette,
        "custom texts" to R.string.ai_page_custom_texts,
        "app icon" to R.string.ai_page_app_icon,
        "ai assistant" to R.string.ai_page_ai_assistant,
        "assistant settings" to R.string.ai_page_assistant_settings,
        "model" to R.string.ai_page_model,
        "search" to R.string.ai_page_search,
        "read_scope" to R.string.ai_page_read_scope,
        "read" to R.string.ai_page_read_scope,
        "write_scope" to R.string.ai_page_write_scope,
        "write" to R.string.ai_page_write_scope,
        "basic" to R.string.ai_page_basic,
        "features" to R.string.ai_page_features,
        "priority_uid" to R.string.ai_page_priority_uid,
        "sus_paths" to R.string.ai_page_paths,
        "sus_loop_paths" to R.string.ai_page_loop_paths,
        "sus_maps" to R.string.ai_page_maps,
        "module upload" to R.string.ai_page_module_upload,
        "module_upload" to R.string.ai_page_module_upload,
        "module share" to R.string.ai_page_module_share,
        "module_share" to R.string.ai_page_module_share,
        "ui mode" to R.string.ai_page_ui_mode,
        "ui_mode" to R.string.ai_page_ui_mode,
        "language" to R.string.ai_page_language,
        "theme" to R.string.ai_page_theme,
        "theme store" to R.string.ai_page_theme_store,
        "theme_store" to R.string.ai_page_theme_store,
        "module settings entry" to R.string.ai_page_module_settings_entry,
        "module_settings_entry" to R.string.ai_page_module_settings_entry,
        "root security entry" to R.string.ai_page_root_security_entry,
        "root_security_entry" to R.string.ai_page_root_security_entry,
        "maintenance entry" to R.string.ai_page_maintenance_entry,
        "maintenance_entry" to R.string.ai_page_maintenance_entry,
        "package rename" to R.string.ai_page_package_rename,
        "package_rename" to R.string.ai_page_package_rename,
        "builtin zygisk" to R.string.ai_page_builtin_zygisk,
        "builtin_zygisk" to R.string.ai_page_builtin_zygisk,
        "builtin zygisk dashboard" to R.string.ai_page_builtin_zygisk_dashboard,
        "builtin_zygisk_dashboard" to R.string.ai_page_builtin_zygisk_dashboard,
        "auto delete update apk" to R.string.ai_page_auto_delete_update_apk,
        "auto_delete_update_apk" to R.string.ai_page_auto_delete_update_apk,
        "github proxy" to R.string.ai_page_github_proxy,
        "github_proxy" to R.string.ai_page_github_proxy,
        "backup" to R.string.ai_page_backup,
        "send log" to R.string.ai_page_send_log,
        "send_log" to R.string.ai_page_send_log,
    )

    fun displayTitle(raw: String, resolve: (Int) -> String? = { runCatching { ksuApp.getString(it) }.getOrNull() }): String {
        if (raw.isBlank()) return raw
        val parts = raw.split(" · ")
        return parts.mapIndexed { index, segment ->
            val trimmed = segment.trim()
            // “SuSFS basic settings”这类标题没有分隔符，前缀保留、后半段再翻译。
            if (trimmed.startsWith("SuSFS ")) {
                "SuSFS " + localize(trimmed.removePrefix("SuSFS "), trimmed, resolve)
            } else {
                // 分段标题里第二段起的 “Appearance” 实为「应用图标与文字」分组，别译成「外观」。
                val key = if (index > 0 && trimmed.lowercase(Locale.ROOT) == "appearance") {
                    "appearance_section"
                } else {
                    trimmed
                }
                localize(key, trimmed, resolve)
            }
        }.joinToString(" · ")
    }

    private fun localize(segment: String, fallback: String, resolve: (Int) -> String?): String {
        val res = segmentRes[segment.lowercase(Locale.ROOT)] ?: return fallback
        return resolve(res) ?: fallback
    }
}
