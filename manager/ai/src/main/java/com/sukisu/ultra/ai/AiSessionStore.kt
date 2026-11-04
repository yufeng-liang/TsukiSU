package com.sukisu.ultra.ai

import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一个聊天会话：标题 + 轮次快照。
 *
 * 只存 [AiTurn]（role/content），不存带 tool 消息、图片路径的完整
 * [AiChatMessage]——那些是运行时状态，重进会话时按轮次重建即可，
 * 与现有 `AiSettings.loadHistory()` 的行为保持一致。
 */
@Serializable
data class AiSession(
    val id: String,
    val title: String = "",
    val updatedAt: Long = 0L,
    val turns: List<AiTurn> = emptyList(),
)

/**
 * 多会话存储。
 *
 * 用 SharedPreferences 存一段 JSON（几十条会话的量级足够），不引 Room。
 * 会话列表整体读写，`save` 内部按 id 覆盖。
 */
object AiSessionStore {

    private const val PREFS = "ai_sessions"
    private const val KEY_SESSIONS = "sessions_json"
    private const val KEY_ACTIVE = "active_session_id"
    private const val MAX_SESSIONS = 100

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun prefs() = aiContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun list(): List<AiSession> {
        val raw = prefs().getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AiSession>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }
    }

    fun save(session: AiSession) {
        val all = list().filterNot { it.id == session.id }
            .plus(session)
            .sortedByDescending { it.updatedAt }
            .take(MAX_SESSIONS)
        prefs().edit().putString(KEY_SESSIONS, json.encodeToString(all)).apply()
    }

    fun delete(id: String) {
        val all = list().filterNot { it.id == id }
        prefs().edit().putString(KEY_SESSIONS, json.encodeToString(all)).apply()
        if (activeId() == id) {
            prefs().edit().remove(KEY_ACTIVE).apply()
        }
    }

    fun load(id: String): AiSession? = list().firstOrNull { it.id == id }

    fun create(title: String = ""): AiSession = AiSession(
        id = "s_" + System.currentTimeMillis().toString(36) + "_" + (1000..9999).random(),
        title = title,
        updatedAt = System.currentTimeMillis(),
    )

    fun activeId(): String = prefs().getString(KEY_ACTIVE, "").orEmpty()

    fun setActiveId(id: String) {
        prefs().edit().putString(KEY_ACTIVE, id).apply()
    }

    /** 当前会话；没有就建一个并设为当前。 */
    fun ensureActive(): AiSession {
        val id = activeId()
        val existing = if (id.isNotEmpty()) load(id) else null
        if (existing != null) return existing
        val created = create()
        save(created)
        setActiveId(created.id)
        return created
    }
}
