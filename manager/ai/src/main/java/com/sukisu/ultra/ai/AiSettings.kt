package com.sukisu.ultra.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire format spoken by the configured endpoint. [AUTO] picks a format from the
 * address: Ollama ports/routes, Anthropic routes, everything else OpenAI-compatible.
 */
@Serializable
enum class AiThinkingMode {
    AUTO,
    OFF,
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromStored(value: String?): AiThinkingMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/**
 * 该模型可选的推理强度档位：「自动」永远可选；模型明确不支持推理时返回空列表。
 * models.dev 的 reasoning_options 有三种形态：
 * - effort：直接给出档位名（none/low/medium/high/max），按它裁刻度；
 * - toggle：只有开/关，用「自动 / 关闭 / 开」；
 * - budget_tokens：给最小/最大预算，用「自动 / 关闭 / 低 / 中 / 高」三档估算。
 */
fun AiModelInfo?.supportedReasoningModes(): List<AiThinkingMode> {
    if (this == null) return AiThinkingMode.entries.toList()
    val options = reasoningOptions
    val effort = options.firstOrNull { it.type.equals("effort", ignoreCase = true) }
    if (effort != null && effort.values.isNotEmpty()) {
        return buildList {
            add(AiThinkingMode.AUTO)
            effort.values.forEach { add(it.toThinkingMode()) }
        }
    }
    if (options.any { it.type.equals("toggle", ignoreCase = true) }) {
        return listOf(AiThinkingMode.AUTO, AiThinkingMode.OFF, AiThinkingMode.LOW)
    }
    if (options.any { it.type.equals("budget_tokens", ignoreCase = true) }) {
        return listOf(
            AiThinkingMode.AUTO, AiThinkingMode.OFF, AiThinkingMode.LOW,
            AiThinkingMode.MEDIUM, AiThinkingMode.HIGH,
        )
    }
    if (!reasoning) return emptyList()
    return AiThinkingMode.entries.toList()
}

/** 把 models.dev 的 effort 名称映射到本项目的能力枚举。 */
fun String.toThinkingMode(): AiThinkingMode = when (trim().lowercase()) {
    "none", "off", "disabled" -> AiThinkingMode.OFF
    "minimal", "low" -> AiThinkingMode.LOW
    "medium", "mid" -> AiThinkingMode.MEDIUM
    "high", "max", "highest" -> AiThinkingMode.HIGH
    else -> AiThinkingMode.MEDIUM
}

@Serializable
enum class AiProviderFormat {
    AUTO,
    OPENAI,
    OLLAMA,
    ANTHROPIC;

    companion object {
        fun fromStored(value: String?): AiProviderFormat =
            entries.firstOrNull { it.name == value } ?: AUTO

        /** Never returns [AUTO]; used to build the request. */
        fun detect(endpoint: String): AiProviderFormat {
            val lower = endpoint.lowercase()
            return when {
                lower.contains("anthropic") || lower.contains("/messages") -> ANTHROPIC
                lower.contains("/api/chat") || lower.contains("/api/generate") -> OLLAMA
                // Ollama's OpenAI-compatible endpoint lives under /v1 on the same port.
                lower.contains("/v1") -> OPENAI
                lower.contains(":11434") -> OLLAMA
                lower.contains("ollama") -> OLLAMA
                else -> OPENAI
            }
        }
    }
}

/** models.dev 给出的“可调推理档位”描述，UI 据此裁剪滑杆刻度。 */
@Serializable
data class AiReasoningOption(
    /** effort / toggle / budget_tokens */
    val type: String = "",
    /** type=effort 时的档位名（none/low/medium/high/max…）。 */
    val values: List<String> = emptyList(),
    /** type=budget_tokens 时的最小/最大预算。 */
    val min: Int = 0,
    val max: Int = 0,
)

/**
 * 单个模型的能力信息。id 是请求里使用的模型标识，name 是界面展示名。
 */
@Serializable
data class AiModelInfo(
    val id: String,
    val name: String = "",
    /** 上下文窗口（token），0 表示未知/不限制。 */
    val contextWindow: Int = 0,
    /** 单次最大输出（token），0 表示未知/不限制。 */
    val maxOutput: Int = 0,
    /** 是否支持推理（reasoning）能力。 */
    val reasoning: Boolean = false,
    /** 是否支持图片输入。 */
    val supportsImages: Boolean = false,
    /** 模型支持的推理档位；空表示未知（按兼容档位回退）。 */
    val reasoningOptions: List<AiReasoningOption> = emptyList(),
)

/**
 * 一个模型供应商及其模型目录。endpoint/apiKey 为空时回落到 [AiConfig] 的旧字段。
 */
@Serializable
data class AiProvider(
    val id: String,
    val name: String = "",
    val format: AiProviderFormat = AiProviderFormat.AUTO,
    val endpoint: String = "",
    val apiKey: String = "",
    val models: List<AiModelInfo> = emptyList(),
    /** 该供应商下已选中的模型 id（多供应商各自记住自己的选择）。 */
    val modelId: String = "",
    /** 该供应商当前选中的推理强度。 */
    val thinkingMode: AiThinkingMode = AiThinkingMode.AUTO,
)

/**
 * Everything the assistant needs to talk to a model. Endpoint is the only field
 * without a usable default; a local Ollama server works with just the address
 * and model name.
 */
@Serializable
data class AiConfig(
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** 会话标题生成使用的模型。空 = 跟随主模型。 */
    val titleModel: String = "",
    /** 标题生成的推理强度；默认关，模型不支持时回落到最低档。 */
    val titleEffort: AiThinkingMode = AiThinkingMode.OFF,
    /** Optional OpenAI-compatible image model. Blank disables image generation/editing. */
    val imageModel: String = "",
    val format: AiProviderFormat = AiProviderFormat.AUTO,
    val enabled: Boolean = true,
    val thinkingMode: AiThinkingMode = AiThinkingMode.AUTO,
    /** Let the assistant run root-backed tools. Off by default; one request can be granted inline. */
    val allowRootAccess: Boolean = false,
    /** Read granted apps / superuser data. */
    val allowSuperuserAccess: Boolean = false,
    /** Read installed modules and search module sources. */
    val allowModuleAccess: Boolean = false,
    /** Read extension/KPM/SuSFS/version-spoof state. */
    val allowExtensionAccess: Boolean = false,
    /** Search the public web and GitHub. */
    val allowWebAccess: Boolean = false,
    /** Read appearance/theme settings. On by default because it is read-only. */
    val allowThemeRead: Boolean = true,
    /** Master switch for appearance/theme writes. */
    val allowThemeWrite: Boolean = false,
    /** Fine-grained theme write scopes; only consulted when [allowThemeWrite] is on. */
    val themeWriteFeatures: Set<String> = AiThemeFeature.ALL_IDS,
    /** Permit the assistant to change extension state (version/CPU spoof, umount, dynamic manager, KPM). */
    val allowExtensionWrite: Boolean = false,
    /** Fine-grained extension write scopes; only consulted when [allowExtensionWrite] is on. */
    val extensionWriteFeatures: Set<String> = AiExtensionFeature.ALL_IDS,
    /** Permit permanent root grants/revocations for a specified app. */
    val allowSuperuserRootControl: Boolean = false,
    /** Permit temporary root grants/revocations (with a duration) for a specified app. */
    val allowSuperuserTemporaryRoot: Boolean = false,
    /** Permit switching the global su compatibility mode. */
    val allowSuperuserSwitch: Boolean = false,
    /** Permit the assistant to download and install modules from the module registry. */
    val allowModuleInstall: Boolean = false,
    val requireConfirmation: Boolean = true,
    /** Show a Run action on shell code blocks (manual, confirmed). */
    val allowCodeExecution: Boolean = false,
    val includeContext: Boolean = true,
    val streaming: Boolean = true,
    val toolCalling: Boolean = true,
    val keepHistory: Boolean = true,
    val temperature: Float = 0.7f,
    val systemPrompt: String = "",
    /** Expert mode; advanced fields are ignored until this is enabled. */
    val advancedMode: Boolean = false,
    /** JSON object of [AiAdvancedOptions]; parsed only in advanced mode. */
    val advancedOptionsJson: String = "",
    /** 多供应商列表；为空时使用旧的 endpoint/apiKey/model 单配置字段。 */
    val providers: List<AiProvider> = emptyList(),
    /** 当前选中的供应商 id；空 = 取第一个供应商。 */
    val currentProviderId: String = "",
    /** 当前选中的模型 id；空 = 取第一个模型。 */
    val currentModelId: String = "",
) {
    val isConfigured: Boolean
        get() = AiSettings.activeProvider(this)?.let {
            it.endpoint.isNotBlank() && (it.models.isNotEmpty() || model.isNotBlank())
        } ?: (endpoint.isNotBlank() && model.isNotBlank())

    fun effectiveFormat(): AiProviderFormat {
        val provider = AiSettings.activeProvider(this)
        val rawFormat = provider?.format ?: format
        val rawEndpoint = provider?.endpoint?.takeIf { it.isNotBlank() } ?: endpoint
        return if (rawFormat == AiProviderFormat.AUTO) AiProviderFormat.detect(rawEndpoint) else rawFormat
    }

    val isImageConfigured: Boolean
        get() = imageModel.isNotBlank() && effectiveFormat() == AiProviderFormat.OPENAI

    fun themeFeatureEnabled(feature: AiThemeFeature): Boolean =
        allowThemeWrite && feature.id in themeWriteFeatures

    fun extensionFeatureEnabled(feature: AiExtensionFeature): Boolean =
        allowExtensionWrite && feature.id in extensionWriteFeatures

    fun disabledThemeFeatures(): List<String> =
        AiThemeFeature.entries.filterNot { it.id in themeWriteFeatures }.map { it.id }

    fun disabledExtensionFeatures(): List<String> =
        AiExtensionFeature.entries.filterNot { it.id in extensionWriteFeatures }.map { it.id }

    /** Validated expert options, or safe defaults when disabled/invalid. */
    fun advancedOptions(): AiAdvancedOptions =
        if (!advancedMode) {
            AiAdvancedOptions.DEFAULT
        } else {
            AiAdvancedOptions.parse(advancedOptionsJson).getOrDefault(AiAdvancedOptions.DEFAULT)
        }

    /** OpenAI-compatible image endpoints derived from the chat endpoint. */
    fun resolvedImageEndpoint(): String = resolvedEndpoint()
        .removeSuffix("/chat/completions") + "/images/generations"

    fun resolvedImageEditEndpoint(): String = resolvedEndpoint()
        .removeSuffix("/chat/completions") + "/images/edits"

    /**
     * Accepts a base address or a complete endpoint. Missing schemes default to
     * plain HTTP because local inference servers rarely have TLS.
     */
    fun resolvedEndpoint(): String {
        val raw = (AiSettings.activeProvider(this)?.endpoint?.takeIf { it.isNotBlank() } ?: endpoint).trim()
        if (raw.isEmpty()) return ""
        val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
            raw
        } else {
            "http://$raw"
        }
        val base = withScheme.trimEnd('/')
        val path = runCatching { URI(base).path.orEmpty() }.getOrDefault("")
        val hasCustomPath = path.isNotEmpty() && path != "/"
        return when (effectiveFormat()) {
            AiProviderFormat.OPENAI -> when {
                base.endsWith("/chat/completions", true) -> base
                base.endsWith("/v1", true) -> "$base/chat/completions"
                else -> "$base/v1/chat/completions"
            }

            AiProviderFormat.OLLAMA -> when {
                base.endsWith("/api/chat", true) -> base
                base.endsWith("/api", true) -> "$base/chat"
                hasCustomPath -> "$base/api/chat"
                else -> "$base/api/chat"
            }

            AiProviderFormat.ANTHROPIC -> when {
                base.endsWith("/v1/messages", true) || base.endsWith("/messages", true) -> base
                base.endsWith("/v1", true) -> "$base/messages"
                hasCustomPath -> "$base/v1/messages"
                else -> "$base/v1/messages"
            }

            // effectiveFormat() never returns AUTO, but keep the when exhaustive.
            AiProviderFormat.AUTO -> base
        }
    }
}

@Serializable
data class AiTurn(val role: String, val content: String)

object AiSettings {

    const val PREFS_NAME = "ai_assistant"

    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_TITLE_MODEL = "title_model"
    private const val KEY_TITLE_EFFORT = "title_effort"
    private const val KEY_IMAGE_MODEL = "image_model"
    private const val KEY_FORMAT = "format"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_THINKING_MODE = "thinking_mode"
    private const val KEY_ALLOW_ROOT = "allow_root_access"
    private const val KEY_ALLOW_SUPERUSER = "allow_superuser_access"
    private const val KEY_ALLOW_MODULE = "allow_module_access"
    private const val KEY_ALLOW_EXTENSION = "allow_extension_access"
    private const val KEY_ALLOW_WEB = "allow_web_access"
    private const val KEY_ALLOW_THEME_READ = "allow_theme_read"
    private const val KEY_ALLOW_THEME_WRITE = "allow_theme_write"
    private const val KEY_THEME_WRITE_FEATURES = "theme_write_features"
    private const val KEY_ALLOW_EXTENSION_WRITE = "allow_extension_write"
    private const val KEY_EXTENSION_WRITE_FEATURES = "extension_write_features"
    private const val KEY_ALLOW_SUPERUSER_ROOT = "allow_superuser_root_control"
    private const val KEY_ALLOW_SUPERUSER_TEMP_ROOT = "allow_superuser_temp_root"
    private const val KEY_ALLOW_SUPERUSER_SWITCH = "allow_superuser_switch"
    private const val KEY_ALLOW_MODULE_INSTALL = "allow_module_install"
    private const val KEY_REQUIRE_CONFIRMATION = "require_confirmation"
    private const val KEY_ALLOW_CODE_EXECUTION = "allow_code_execution"
    private const val KEY_INCLUDE_CONTEXT = "include_context"
    private const val KEY_STREAMING = "streaming"
    private const val KEY_TOOL_CALLING = "tool_calling"
    private const val KEY_KEEP_HISTORY = "keep_history"
    private const val KEY_TEMPERATURE = "temperature"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_ADVANCED_MODE = "advanced_mode"
    private const val KEY_ADVANCED_OPTIONS = "advanced_options"
    private const val KEY_PROVIDERS = "providers_json"
    private const val KEY_CURRENT_PROVIDER = "current_provider_id"
    private const val KEY_CURRENT_MODEL = "current_model_id"
    private const val KEY_HISTORY = "chat_history"
    private const val KEY_SESSION_TITLE = "session_title"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun prefs(): SharedPreferences =
        aiContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Missing key means "all features" (migration from the old coarse switches);
     * an explicit empty set means the user deselected every feature.
     */
    private fun readFeatureSet(
        p: SharedPreferences,
        key: String,
        allIds: Set<String>,
        isValid: (String) -> Boolean,
    ): Set<String> {
        val stored = p.getStringSet(key, null) ?: return allIds
        return stored.asSequence().filter(isValid).toSet()
    }

    fun load(): AiConfig {
        val p = prefs()
        val stored = AiConfig(
            endpoint = p.getString(KEY_ENDPOINT, "").orEmpty(),
            apiKey = p.getString(KEY_API_KEY, "").orEmpty(),
            model = p.getString(KEY_MODEL, "").orEmpty(),
            titleModel = p.getString(KEY_TITLE_MODEL, "").orEmpty(),
            titleEffort = AiThinkingMode.fromStored(p.getString(KEY_TITLE_EFFORT, null)),
            imageModel = p.getString(KEY_IMAGE_MODEL, "").orEmpty(),
            format = AiProviderFormat.fromStored(p.getString(KEY_FORMAT, null)),
            enabled = p.getBoolean(KEY_ENABLED, true),
            thinkingMode = AiThinkingMode.fromStored(p.getString(KEY_THINKING_MODE, null)),
            allowRootAccess = p.getBoolean(KEY_ALLOW_ROOT, false),
            allowSuperuserAccess = p.getBoolean(KEY_ALLOW_SUPERUSER, false),
            allowModuleAccess = p.getBoolean(KEY_ALLOW_MODULE, false),
            allowExtensionAccess = p.getBoolean(KEY_ALLOW_EXTENSION, false),
            allowWebAccess = p.getBoolean(KEY_ALLOW_WEB, false),
            allowThemeRead = p.getBoolean(KEY_ALLOW_THEME_READ, true),
            allowThemeWrite = p.getBoolean(KEY_ALLOW_THEME_WRITE, false),
            themeWriteFeatures = readFeatureSet(
                p = p,
                key = KEY_THEME_WRITE_FEATURES,
                allIds = AiThemeFeature.ALL_IDS,
            ) { AiThemeFeature.fromId(it) != null },
            allowExtensionWrite = p.getBoolean(KEY_ALLOW_EXTENSION_WRITE, false),
            extensionWriteFeatures = readFeatureSet(
                p = p,
                key = KEY_EXTENSION_WRITE_FEATURES,
                allIds = AiExtensionFeature.ALL_IDS,
            ) { AiExtensionFeature.fromId(it) != null },
            allowSuperuserRootControl = p.getBoolean(KEY_ALLOW_SUPERUSER_ROOT, false),
            allowSuperuserTemporaryRoot = p.getBoolean(KEY_ALLOW_SUPERUSER_TEMP_ROOT, false),
            allowSuperuserSwitch = p.getBoolean(KEY_ALLOW_SUPERUSER_SWITCH, false),
            allowModuleInstall = p.getBoolean(KEY_ALLOW_MODULE_INSTALL, false),
            requireConfirmation = p.getBoolean(KEY_REQUIRE_CONFIRMATION, true),
            allowCodeExecution = p.getBoolean(KEY_ALLOW_CODE_EXECUTION, false),
            includeContext = p.getBoolean(KEY_INCLUDE_CONTEXT, true),
            streaming = p.getBoolean(KEY_STREAMING, true),
            toolCalling = p.getBoolean(KEY_TOOL_CALLING, true),
            keepHistory = p.getBoolean(KEY_KEEP_HISTORY, true),
            temperature = p.getFloat(KEY_TEMPERATURE, 0.7f).coerceIn(0f, 2f),
            systemPrompt = p.getString(KEY_SYSTEM_PROMPT, "").orEmpty(),
            advancedMode = p.getBoolean(KEY_ADVANCED_MODE, false),
            advancedOptionsJson = p.getString(KEY_ADVANCED_OPTIONS, "").orEmpty(),
            providers = loadProviders(),
            currentProviderId = loadCurrentProviderId(),
            currentModelId = loadCurrentModelId(),
        )
        // 旧单配置迁移：providers 为空且旧 endpoint 非空时生成 legacy 供应商。
        val (providers, currentProviderId) = migrateLegacyToProviders(stored)
        return stored.copy(providers = providers, currentProviderId = currentProviderId)
    }

    fun save(config: AiConfig) {
        prefs().edit {
            putString(KEY_ENDPOINT, config.endpoint.trim())
            putString(KEY_API_KEY, config.apiKey.trim())
            putString(KEY_MODEL, config.model.trim())
            putString(KEY_TITLE_MODEL, config.titleModel.trim())
            putString(KEY_TITLE_EFFORT, config.titleEffort.name)
            putString(KEY_IMAGE_MODEL, config.imageModel.trim())
            putString(KEY_FORMAT, config.format.name)
            putBoolean(KEY_ENABLED, config.enabled)
            putString(KEY_THINKING_MODE, config.thinkingMode.name)
            putBoolean(KEY_ALLOW_ROOT, config.allowRootAccess)
            putBoolean(KEY_ALLOW_SUPERUSER, config.allowSuperuserAccess)
            putBoolean(KEY_ALLOW_MODULE, config.allowModuleAccess)
            putBoolean(KEY_ALLOW_EXTENSION, config.allowExtensionAccess)
            putBoolean(KEY_ALLOW_WEB, config.allowWebAccess)
            putBoolean(KEY_ALLOW_THEME_READ, config.allowThemeRead)
            putBoolean(KEY_ALLOW_THEME_WRITE, config.allowThemeWrite)
            putStringSet(
                KEY_THEME_WRITE_FEATURES,
                config.themeWriteFeatures.filter { AiThemeFeature.fromId(it) != null }.toSet(),
            )
            putBoolean(KEY_ALLOW_EXTENSION_WRITE, config.allowExtensionWrite)
            putStringSet(
                KEY_EXTENSION_WRITE_FEATURES,
                config.extensionWriteFeatures.filter { AiExtensionFeature.fromId(it) != null }.toSet(),
            )
            putBoolean(KEY_ALLOW_SUPERUSER_ROOT, config.allowSuperuserRootControl)
            putBoolean(KEY_ALLOW_SUPERUSER_TEMP_ROOT, config.allowSuperuserTemporaryRoot)
            putBoolean(KEY_ALLOW_SUPERUSER_SWITCH, config.allowSuperuserSwitch)
            putBoolean(KEY_ALLOW_MODULE_INSTALL, config.allowModuleInstall)
            putBoolean(KEY_REQUIRE_CONFIRMATION, config.requireConfirmation)
            putBoolean(KEY_ALLOW_CODE_EXECUTION, config.allowCodeExecution)
            putBoolean(KEY_INCLUDE_CONTEXT, config.includeContext)
            putBoolean(KEY_STREAMING, config.streaming)
            putBoolean(KEY_TOOL_CALLING, config.toolCalling)
            putBoolean(KEY_KEEP_HISTORY, config.keepHistory)
            putFloat(KEY_TEMPERATURE, config.temperature.coerceIn(0f, 2f))
            putString(KEY_SYSTEM_PROMPT, config.systemPrompt)
            putBoolean(KEY_ADVANCED_MODE, config.advancedMode)
            putString(KEY_ADVANCED_OPTIONS, config.advancedOptionsJson)
            putString(KEY_PROVIDERS, json.encodeToString(config.providers))
            putString(KEY_CURRENT_PROVIDER, config.currentProviderId.trim())
            putString(KEY_CURRENT_MODEL, config.currentModelId.trim())
        }
    }

    fun loadProviders(): List<AiProvider> {
        val stored = prefs().getString(KEY_PROVIDERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AiProvider>>(stored) }.getOrDefault(emptyList())
    }

    fun saveProviders(list: List<AiProvider>) {
        prefs().edit { putString(KEY_PROVIDERS, json.encodeToString(list)) }
    }

    fun loadCurrentProviderId(): String = prefs().getString(KEY_CURRENT_PROVIDER, "").orEmpty()

    fun saveCurrentProviderId(id: String) {
        prefs().edit { putString(KEY_CURRENT_PROVIDER, id.trim()) }
    }

    fun loadCurrentModelId(): String = prefs().getString(KEY_CURRENT_MODEL, "").orEmpty()

    fun saveCurrentModelId(id: String) {
        prefs().edit { putString(KEY_CURRENT_MODEL, id.trim()) }
    }

    /**
     * 旧单配置迁移：providers 为空且旧 endpoint 非空时构造 legacy 供应商。
     * 返回迁移后的供应商列表与当前供应商 id。
     */
    fun migrateLegacyToProviders(config: AiConfig): Pair<List<AiProvider>, String> {
        if (config.providers.isNotEmpty() || config.endpoint.isBlank()) {
            return config.providers to config.currentProviderId
        }
        val provider = AiProvider(
            id = "legacy",
            name = hostOf(config.endpoint).ifBlank { "默认" },
            format = config.format,
            endpoint = config.endpoint,
            apiKey = config.apiKey,
            models = listOf(AiModelInfo(id = config.model)),
        )
        return listOf(provider) to "legacy"
    }

    /** 当前供应商：优先 currentProviderId，其次第一个，最后用旧字段合成 legacy 供应商。 */
    fun activeProvider(config: AiConfig): AiProvider? {
        if (config.providers.isNotEmpty()) {
            if (config.currentProviderId.isNotBlank()) {
                config.providers.firstOrNull { it.id == config.currentProviderId }?.let { return it }
            }
            return config.providers.first()
        }
        if (config.endpoint.isBlank() && config.model.isBlank()) return null
        return AiProvider(
            id = "legacy",
            name = hostOf(config.endpoint).ifBlank { "默认" },
            format = config.format,
            endpoint = config.endpoint,
            apiKey = config.apiKey,
            models = listOf(AiModelInfo(id = config.model)),
        )
    }

    /** 当前模型：优先 currentModelId，其次与旧 model 同名，最后取第一个。 */
    fun activeModel(config: AiConfig): AiModelInfo? {
        val provider = activeProvider(config) ?: return null
        // 供应商自己记住的模型优先，其次是全局 currentModelId。
        if (provider.modelId.isNotBlank()) {
            provider.models.firstOrNull { it.id == provider.modelId }?.let { return it }
        }
        if (config.currentModelId.isNotBlank()) {
            provider.models.firstOrNull { it.id == config.currentModelId }?.let { return it }
        }
        if (config.model.isNotBlank()) {
            provider.models.firstOrNull { it.id == config.model || it.name == config.model }?.let { return it }
        }
        return provider.models.firstOrNull()
    }

    /** 统一取值口：当前供应商优先，缺失时回落到旧 endpoint 字段。 */
    fun resolveEndpoint(config: AiConfig): String =
        activeProvider(config)?.endpoint?.takeIf { it.isNotBlank() } ?: config.endpoint

    fun resolveApiKey(config: AiConfig): String =
        activeProvider(config)?.apiKey?.takeIf { it.isNotBlank() } ?: config.apiKey

    fun resolveModel(config: AiConfig): String =
        activeModel(config)?.id?.takeIf { it.isNotBlank() } ?: config.model

    /** 从 endpoint 提取 host，用于迁移时生成供应商展示名。 */
    private fun hostOf(endpoint: String): String {
        val raw = endpoint.trim()
        if (raw.isEmpty()) return ""
        val withScheme =
            if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
                raw
            } else {
                "http://$raw"
            }
        return runCatching { URI(withScheme).host.orEmpty() }.getOrDefault("")
    }

    fun loadHistory(): List<AiTurn> {
        val stored = prefs().getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AiTurn>>(stored) }.getOrDefault(emptyList())
    }

    fun saveHistory(turns: List<AiTurn>) {
        val encoded = json.encodeToString(turns)
        prefs().edit { putString(KEY_HISTORY, encoded) }
    }

    fun loadSessionTitle(): String = prefs().getString(KEY_SESSION_TITLE, "").orEmpty()

    fun saveSessionTitle(title: String) {
        prefs().edit { putString(KEY_SESSION_TITLE, title.trim()) }
    }

    fun clearHistory() {
        prefs().edit {
            remove(KEY_HISTORY)
            remove(KEY_SESSION_TITLE)
        }
    }
}
