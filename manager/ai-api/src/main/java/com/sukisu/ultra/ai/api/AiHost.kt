package com.sukisu.ultra.ai.api

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Stable integration surface shared by the host application and the optional
 * AI library.
 *
 * The host only compiles against this module. The full assistant is an AAR
 * discovered at runtime through [AiFeatureBridge]; when it is absent the host
 * still builds and every AI entry stays hidden.
 */
interface AiHost {
    val context: Context

    
    fun isBackgroundImageActive(): Boolean

    
    fun cardBackgroundAlpha(): Float

    
    fun avatarDrawables(): AiAvatarDrawables?

    
    fun showAiHomeCard(): Boolean

    
    fun registerShowAiHomeCardListener(listener: () -> Unit): AutoCloseable

    
    suspend fun executeRootShell(command: String): AiShellOutput

    val environment: AiEnvironmentService
    val theme: AiThemeService
    val extensions: AiExtensionsService
    val superuser: AiSuperuserService
    val modules: AiModuleService
    val features: AiFeatureService
    val themeImages: AiThemeImageService

    /**
     * 「隐藏环境」专栏执行服务。
     *
     * 宿主没接入专栏时返回 null（旧版本宿主），助手侧工具会直接回答「这个宿主版本不支持」。
     */
    val column: AiColumnService? get() = null
}

data class AiAvatarDrawables(
    val iconRes: Int,
    val foregroundRes: Int,
)

data class AiShellOutput(
    val output: String,
    val exitCode: Int,
    val timedOut: Boolean = false,
)


interface AiNavigationTarget

data class AiOpenPageResult(
    val text: String,
    val target: AiNavigationTarget? = null,
    val title: String? = null,
)

data class AiThemeImageResult(
    val displayPath: String,
    val themePath: String,
    val target: String,
)

data class GithubModuleAsset(
    val repo: String,
    val tag: String,
    val name: String,
    val size: Long,
    val url: String,
    val sha256: String?,
)


data class AiEnvironmentScopes(
    val root: Boolean,
    val superuser: Boolean,
    val superuserRootControl: Boolean,
    val superuserTemporaryRoot: Boolean,
    val superuserSwitch: Boolean,
    val modules: Boolean,
    val extensions: Boolean,
    val web: Boolean,
    val theme: Boolean,
    val includeContext: Boolean,
)

interface AiEnvironmentService {
    suspend fun snapshot(scopes: AiEnvironmentScopes, force: Boolean = false): String
}


interface AiThemeService {
    suspend fun read(): String
    fun optionsJson(): String
    fun customTextKeysJson(): String
    suspend fun summaryJson(): String
    suspend fun set(key: String, value: String): String
    suspend fun setCustomText(key: String, text: String): String
    suspend fun resetAppearance(): String
    suspend fun resetAllCustomTexts(): String
    suspend fun clearThemeImage(target: String): String
    suspend fun exportThemePackage(uri: Uri): String
    suspend fun shareThemePackage(): String
}


interface AiExtensionsService {
    suspend fun readOverview(): String
    suspend fun capabilities(): String
    suspend fun toggleExtension(name: String, enabled: Boolean): String
    suspend fun setVersionSpoof(
        version: String? = null,
        full: String? = null,
        uapi: String? = null,
        hideWarning: Boolean? = null,
        reset: Boolean = false,
    ): String

    suspend fun setCpuSpoof(
        cpus: List<Int>,
        midr: String,
        bogomips: Int = 0,
        hwcap: String = "",
        hwcap2: String = "",
    ): String

    suspend fun umount(action: String, path: String? = null, flags: Int = -1): String
    suspend fun dynamicManager(action: String, enabled: Boolean? = null, name: String? = null): String
    suspend fun kpm(
        action: String,
        name: String? = null,
        path: String? = null,
        args: String? = null,
        embed: Boolean = false,
    ): String

    suspend fun susfs(action: String, enabled: Boolean? = null): String
    suspend fun susfsConfigRead(): String
    suspend fun susfsConfigSet(
        action: String,
        enabled: Boolean? = null,
        uname: String? = null,
        buildTime: String? = null,
        path: String? = null,
        newPath: String? = null,
        packageName: String? = null,
        oldConfig: String? = null,
        configFields: String? = null,
        packages: List<String> = emptyList(),
        fileUri: Uri? = null,
    ): String
}


interface AiSuperuserService {
    suspend fun capabilities(): String
    suspend fun readApps(limit: Int = 50, includeDenied: Boolean = false): String
    suspend fun appInfo(packageName: String? = null, uid: Int? = null): String
    suspend fun setRoot(packageName: String?, uid: Int?, enabled: Boolean): String
    suspend fun setTemporaryRoot(
        packageName: String?,
        uid: Int?,
        seconds: Long?,
        untilReboot: Boolean,
        revoke: Boolean,
    ): String

    suspend fun setGlobalSu(mode: String): String
}


interface AiModuleService {
    suspend fun installedModules(limit: Int = 80): String
    fun moduleCapabilities(): String
    suspend fun search(query: String?, limit: Int = 8): String
    suspend fun install(moduleId: String, version: String? = null, onProgress: (String) -> Unit = {}): String
    suspend fun installGithubAsset(asset: GithubModuleAsset, onProgress: (String) -> Unit = {}): String
}


interface AiFeatureService {
    suspend fun entriesJson(rootProbeAllowed: Boolean = true, extensionReadAllowed: Boolean = true): String
    suspend fun openPage(
        id: String,
        rootProbeAllowed: Boolean = true,
        extensionReadAllowed: Boolean = true,
        allowSuperuserRead: Boolean = true,
        packageName: String? = null,
        uid: Int? = null,
        moduleId: String? = null,
        section: String? = null,
        tab: String? = null,
        columnId: String? = null,
        topicId: String? = null,
    ): AiOpenPageResult
}

/**
 * Theme image storage. Image generation/editing stays in the optional library;
 * the host only stores the resulting files and exposes the current slots.
 */
interface AiThemeImageService {
    suspend fun applyToTheme(source: File, target: String): AiThemeImageResult
    suspend fun setFromUri(uri: Uri, target: String): AiThemeImageResult
    fun currentThemeImage(target: String): File?
    fun shareImages(files: List<File>, title: String): Boolean
}

/**
 * 「隐藏环境」专栏的执行契约。
 *
 * 只读动作（[overview]/[status]/[checks]）直接把结果拼成模型可读文本返回；写动作（[runStep]/[reboot]/
 * [rollback]）由宿主负责「先问用户、再执行」，助手侧只按 [AiColumnOutcome] 播报结果。
 *
 * 文本化而不是结构化，是为了让这个模块保持零依赖：专栏文档模型留在宿主应用里，
 * 助手库不需要跟着它升级。
 */
interface AiColumnService {
    /** 专栏 + 专题清单（档位、风险、步骤数、是否要密钥）。 */
    suspend fun overview(): String

    /** 进度账本 + 「重启后待继续」标记。 */
    suspend fun status(): String

    /** 12 项只读自检的红绿结果。 */
    suspend fun checks(): String

    /** 单个专题的步骤清单（id/标题/类型/是否可选/是否需要确认）。 */
    suspend fun steps(columnId: String, topicId: String): String

    /**
     * 执行一步；重启步骤会被拒绝，要走 [reboot]。
     *
     * [force] 是「重做 / 覆盖」：设备上已有的文件会被真的改写一遍，同时跳过顺序保护；
     * [optIn] 对应步骤上的「需勾选」，必须由用户明确同意后传 true。
     */
    suspend fun runStep(
        columnId: String,
        topicId: String,
        stepId: String,
        force: Boolean = false,
        optIn: Boolean = false,
    ): AiColumnOutcome

    /**
     * 重启后的接续：把重启那一步补记为完成、清掉「待继续」标记，并返回下一步是哪一步。
     *
     * 只做记账，不替用户跑下一步——设计上是被动接续，用户说「继续」之后助手才调用它。
     */
    suspend fun resume(columnId: String, topicId: String): AiColumnOutcome

    /** 用户回报「这一步我自己做完了」时补记完成；只记账，不执行任何命令。 */
    suspend fun markDone(columnId: String, topicId: String, stepId: String): AiColumnOutcome

    /** 给确认卡用的一句话：这一步到底会改什么（标题 + kind + 命令/写入路径）。 */
    suspend fun stepBrief(columnId: String, topicId: String, stepId: String): String

    /**
     * 审批策略要用的事实；拿不到时返回 null（调用方退回「每步都问、且不许记住」）。
     *
     * [AiColumnStepFacts.planDigest] 是当前索引版本下整个专题正文的摘要，用来把
     * 「本次请求始终允许」限制在**用户真正看过的那一版内容**上。
     */
    suspend fun stepFacts(columnId: String, topicId: String, stepId: String): AiColumnStepFacts?

    /** 写续跑标记后重启设备。返回后进程可能随时被杀。 */
    suspend fun reboot(columnId: String, topicId: String, stepId: String): AiColumnOutcome

    /** 逆序撤销该专题本次的改动。 */
    suspend fun rollback(columnId: String, topicId: String): AiColumnOutcome

    /** 记下用户选的档位（专题 id）；返回给用户看的一句话。 */
    fun selectTier(columnId: String, topicId: String, tier: String): String

    /** 读回档位偏好；没选过为 null。 */
    fun selectedTier(columnId: String, topicId: String): String?
}

/** 一步的审批事实：谁能批量放行、谁必须逐步确认。 */
data class AiColumnStepFacts(
    val stepId: String,
    val title: String,
    val kind: String,
    /** 命令 / 配置写入 / 带 confirm / 需勾选：每步都要真人点。 */
    val needsOwnConfirmation: Boolean,
    /** installapp / askuser：用户自己动手，助手不执行命令。 */
    val userAction: Boolean,
    val optIn: Boolean,
    /** 专题正文摘要（16 位），索引一改就变。 */
    val planDigest: String,
    /** 专题里可见步骤数。 */
    val topicSteps: Int,
    /** 其中可以走批量授权的常规步骤数。 */
    val routineSteps: Int,
    /** 这些常规步骤的标题：确认卡列出来，用户才知道「批量」放行的是哪几步。 */
    val routineTitles: List<String>,
)

/** 一步执行的结果：给模型看的文本 + 两个必须由调用方处理的状态位。 */
data class AiColumnOutcome(
    val text: String,
    /** 这一步是否成功（NEEDS_USER / 失败都是 false）。 */
    val ok: Boolean = false,
    /** 这一步要用户自己在设备上做，模型必须停下来问结果。 */
    val needsUser: Boolean = false,
    /** 已经发出重启命令，进程随时会死。 */
    val rebooting: Boolean = false,
)
