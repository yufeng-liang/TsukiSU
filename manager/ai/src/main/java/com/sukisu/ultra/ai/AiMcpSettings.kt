package com.sukisu.ultra.ai

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一个 MCP 服务器配置。
 *
 * [url] 是 Streamable HTTP 端点（只支持 https，本机调试允许 http://127.0.0.1 与
 * http://localhost）。[headers] 会附加到每个请求上，可用于 Bearer Token 等鉴权。
 */
/**
 * 存进配置里的 MCP 工具条目：只保留名字和描述，够详情页展示与开关用。
 */
@Serializable
data class AiMcpToolInfo(
    val name: String,
    val description: String = "",
)

@Serializable
data class AiMcpServer(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    /**
     * 传输方式。目前客户端只实现 STREAMABLE_HTTP；SSE（legacy）握手尚未实现，
     * 设置页已隐藏该选项，这里保留字段仅用于兼容旧配置读取。
     */
    val transport: String = "STREAMABLE_HTTP",
    val headers: Map<String, String> = emptyMap(),
    /** 上次成功拉取到的工具；离线打开详情页时先画这一份，联网后再刷新。 */
    val tools: List<AiMcpToolInfo> = emptyList(),
    /** 用户在详情页关掉的工具名：列表展示和实际调用都会跳过。 */
    val disabledTools: List<String> = emptyList(),
)

/**
 * MCP 服务器列表的持久化。
 *
 * 独立使用 `ai_mcp` 这个 SharedPreferences 文件，避免和主配置混在一起；
 * 序列化格式变化时也只影响这一份数据。
 */
object AiMcpSettings {

    private const val PREFS_NAME = "ai_mcp"
    private const val KEY_SERVERS = "servers"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun prefs() = aiContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取全部服务器；数据损坏时返回空列表，不让设置页崩溃。 */
    fun list(): List<AiMcpServer> {
        val stored = prefs().getString(KEY_SERVERS, null) ?: return emptyList()
        val decoded = runCatching {
            json.decodeFromString<List<AiMcpServer>>(stored)
        }.getOrDefault(emptyList())
        return decoded.mapNotNull { server ->
            val id = server.id.trim()
            if (id.isEmpty()) null else server.copy(id = id)
        }
    }

    /** 覆盖保存整个列表，按 id 去重（后出现的覆盖先出现的）。 */
    fun save(servers: List<AiMcpServer>) {
        val unique = LinkedHashMap<String, AiMcpServer>()
        servers.forEach { server ->
            val id = server.id.trim()
            if (id.isEmpty()) return@forEach
            unique[id] = server.copy(id = id)
        }
        prefs().edit { putString(KEY_SERVERS, json.encodeToString(unique.values.toList())) }
    }

    /** 新增服务器；同 id 已存在时替换，避免出现重复项。 */
    fun add(server: AiMcpServer) {
        val normalized = server.copy(id = server.id.trim())
        val current = list()
        val next = if (current.any { it.id == normalized.id }) {
            current.map { if (it.id == normalized.id) normalized else it }
        } else {
            current + normalized
        }
        save(next)
    }

    /** 按 id 更新服务器；找不到时追加。 */
    fun update(server: AiMcpServer) {
        val normalized = server.copy(id = server.id.trim())
        val current = list()
        val next = if (current.any { it.id == normalized.id }) {
            current.map { if (it.id == normalized.id) normalized else it }
        } else {
            current + normalized
        }
        save(next)
    }

    /** 只覆盖工具缓存，名称、地址和用户关掉的开关都保留（详情页刷新工具时用）。 */
    fun updateTools(id: String, tools: List<AiMcpToolInfo>) {
        val current = list().firstOrNull { it.id == id.trim() } ?: return
        update(current.copy(tools = tools))
    }

    /** 按 id 删除服务器。 */
    fun remove(id: String) {
        save(list().filterNot { it.id == id.trim() })
    }
}
