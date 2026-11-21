package com.sukisu.ultra.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 「添加服务器」向导的三个界面：先选入口，再进粘贴导入或手动表单。
 */
internal enum class McpAddServerMode { Choose, Import, Form }

/**
 * 一次 JSON 导入的结果。
 *
 * [servers] 是能直接落库的服务器；[skipped] 是因为不是 HTTP 传输（比如 stdio 的 command
 * 配置）被跳过的名字——给用户一句提示，而不是悄悄丢掉。
 */
internal data class McpImportResult(
    val servers: List<AiMcpServer>,
    val skipped: List<String>,
)

/**
 * 解析粘贴进来的 MCP 配置 JSON。兼容三种常见写法：
 *
 * 1. Claude Desktop / Cursor 的包裹格式：`{"mcpServers": {"名字": {...}}}`；
 * 2. 单个服务器对象：顶层直接带 `url`（或 `serverUrl` / `endpoint`）；
 * 3. 裸映射：`{"名字": {...}, "另一个": {...}}`。
 *
 * 只收 HTTP 传输：带 url 的条目才建服务器，command 型（stdio）记进 skipped。
 * JSON 解析失败、或一个可用条目都没有时返回 null，让界面提示「没认出可用的服务器」。
 */
internal fun importMcpServers(raw: String, idPrefix: String = "mcp_import_"): McpImportResult? {
    if (raw.isBlank()) return null
    val root = runCatching { Json.parseToJsonElement(raw.trim()) }.getOrNull() as? JsonObject ?: return null
    val candidates = linkedMapOf<String, JsonObject>()
    val wrapped = root["mcpServers"] ?: root["servers"]
    when {
        wrapped is JsonObject -> wrapped.forEach { (name, value) ->
            (value as? JsonObject)?.let { candidates[name] = it }
        }
        root.httpUrl() != null -> {
            candidates[root.stringOrNull("name").orEmpty().ifBlank { "MCP" }] = root
        }
        else -> root.forEach { (name, value) ->
            (value as? JsonObject)?.let { candidates[name] = it }
        }
    }

    val servers = mutableListOf<AiMcpServer>()
    val skipped = mutableListOf<String>()
    val stamp = System.currentTimeMillis()
    candidates.forEach { (name, obj) ->
        val url = obj.httpUrl()
        if (url == null) {
            skipped += name
            return@forEach
        }
        val label = name.trim().ifBlank { "MCP " + (servers.size + 1) }
        servers += AiMcpServer(
            id = idPrefix + stamp + "_" + servers.size,
            name = label,
            url = url,
            headers = obj.headers(),
        )
    }
    if (servers.isEmpty()) return null
    return McpImportResult(servers = servers, skipped = skipped)
}

/** 取服务器地址：三种键名都见过，取到第一个非空的。 */
private fun JsonObject.httpUrl(): String? {
    val raw = stringOrNull("url") ?: stringOrNull("serverUrl") ?: stringOrNull("endpoint") ?: return null
    return raw.trim().ifBlank { null }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

/** headers 里只收字符串值，数字/布尔值当字符串用。 */
private fun JsonObject.headers(): Map<String, String> {
    val obj = this["headers"] as? JsonObject ?: return emptyMap()
    val result = LinkedHashMap<String, String>()
    obj.forEach { (key, value) ->
        val text = (value as? JsonPrimitive)?.content?.trim().orEmpty()
        if (key.isNotBlank() && text.isNotEmpty()) result[key.trim()] = text
    }
    return result
}
