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
@Serializable
data class AiMcpServer(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
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

    /** 按 id 删除服务器。 */
    fun remove(id: String) {
        save(list().filterNot { it.id == id.trim() })
    }
}
