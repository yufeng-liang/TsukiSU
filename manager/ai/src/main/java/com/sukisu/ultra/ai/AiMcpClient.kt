package com.sukisu.ultra.ai

import android.util.Log
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 一个 MCP 工具。
 *
 * [inputSchemaJson] 保留服务器返回的原始 JSON Schema 字符串，直接交给模型即可；
 * [serverId] / [serverName] 用来在多服务器场景下定位工具的来源。
 */
data class AiMcpTool(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)

/**
 * MCP（Model Context Protocol）客户端，只实现 Streamable HTTP 传输
 * （协议版本 2025-03-26），不支持 stdio。
 *
 * 传输要点：
 * - 所有 JSON-RPC 消息都 POST 到同一个服务器 URL；
 * - `Accept` 同时声明 `application/json` 与 `text/event-stream`；
 * - initialize 响应里的 `Mcp-Session-Id` 会在后续请求中回传；
 * - 响应既可能是单个 JSON，也可能是一条 SSE 事件流，两种都要解析。
 */
object AiMcpClient {

    private const val TAG = "AiMcpClient"

    /** MCP 协议版本，与请求的 initialize.params.protocolVersion 保持一致。 */
    private const val PROTOCOL_VERSION = "2025-03-26"

    private const val SESSION_HEADER = "Mcp-Session-Id"
    private const val PROTOCOL_HEADER = "MCP-Protocol-Version"

    /** 单次 HTTP 调用的总超时（含读 SSE 流的时间）。 */
    private const val CALL_TIMEOUT_SECONDS = 30L

    /** tools/list 分页上限，防止服务器返回异常 cursor 造成死循环。 */
    private const val MAX_TOOL_PAGES = 20

    private const val MAX_ERROR_BODY = 500

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** 按服务器缓存的会话；无 Mcp-Session-Id 的无状态服务器不缓存。 */
    private val sessions = ConcurrentHashMap<String, McpSession>()

    // ------------------------------------------------------------------
    // 公开 API
    // ------------------------------------------------------------------

    /**
     * 列出某个服务器提供的工具；失败（地址非法、连不上、协议错误）返回空列表。
     *
     * 需要区分「服务器确实没有工具」和「连接失败」时，先调用 [ping]。
     */
    suspend fun listTools(server: AiMcpServer): List<AiMcpTool> = withContext(Dispatchers.IO) {
        try {
            val tools = mutableListOf<AiMcpTool>()
            withSession(server) { session ->
                var cursor: String? = null
                var page = 0
                do {
                    val payload = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 2 + page)
                        put("method", "tools/list")
                        if (!cursor.isNullOrBlank()) {
                            putJsonObject("params") { put("cursor", cursor) }
                        }
                    }
                    val result = postRpc(server, session, payload).body.requireResult()
                    val array = result["tools"] as? JsonArray ?: JsonArray(emptyList())
                    array.forEach { element ->
                        val tool = element as? JsonObject ?: return@forEach
                        val name = tool.string("name")?.takeIf { it.isNotBlank() } ?: return@forEach
                        tools += AiMcpTool(
                            serverId = server.id,
                            serverName = server.name,
                            name = name,
                            description = tool.string("description")
                                ?: tool.string("title")
                                ?: "",
                            inputSchemaJson = (tool["inputSchema"] as? JsonObject)?.toString() ?: "{}",
                        )
                    }
                    cursor = result.string("nextCursor")
                    page++
                } while (!cursor.isNullOrBlank() && page < MAX_TOOL_PAGES)
            }
            tools
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "列出 MCP 工具失败：${server.name}", error)
            emptyList()
        }
    }

    /**
     * 调用工具，返回文本结果；失败抛异常由上层处理。
     *
     * [argumentsJson] 必须是 JSON 对象字符串（空串按 `{}` 处理）。
     * `result.isError == true` 时抛 [IllegalStateException]，文本取自 content。
     */
    suspend fun callTool(
        server: AiMcpServer,
        toolName: String,
        argumentsJson: String,
    ): String = withContext(Dispatchers.IO) {
        require(toolName.isNotBlank()) { "工具名不能为空" }
        val arguments = parseArguments(argumentsJson)
        // tools/call 可能有副作用：会话过期时只清理缓存并抛错，不自动重发，避免重复执行。
        withSession(server, retryOnSessionExpired = false) { session ->
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 3)
                put("method", "tools/call")
                putJsonObject("params") {
                    put("name", toolName)
                    put("arguments", arguments)
                }
            }
            val result = postRpc(server, session, payload).body.requireResult()
            val content = result["content"] as? JsonArray ?: JsonArray(emptyList())
            val text = content.mapNotNull { element ->
                val block = element as? JsonObject ?: return@mapNotNull null
                if (block.string("type") != "text") return@mapNotNull null
                block.string("text")
            }.joinToString("\n")
            if ((result["isError"] as? JsonPrimitive)?.booleanOrNull == true) {
                throw IllegalStateException(text.ifBlank { "MCP 工具调用失败" })
            }
            text
        }
    }

    /**
     * 只做一次 initialize 握手（含 notifications/initialized），用于设置页
     * 验证地址、鉴权头和协议版本是否可用。
     */
    suspend fun ping(server: AiMcpServer): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 每次都重新握手，确保设置页的「测试连接」反映服务器当前状态。
            val session = initialize(server)
            if (session.id != null) sessions[sessionKey(server)] = session
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            sessions.remove(sessionKey(server))
            Result.failure(error)
        }
    }

    // ------------------------------------------------------------------
    // 会话管理
    // ------------------------------------------------------------------

    private data class McpSession(val id: String?, val protocolVersion: String)

    private data class RpcResponse(val body: JsonObject, val sessionId: String?)

    /**
     * 在具备会话的情况下执行 [block]；缓存失效（HTTP 404）时重新初始化。
     *
     * [retryOnSessionExpired] 为 false 时只清理失效会话并把异常抛给调用方，
     * 用于可能有副作用的请求（如 tools/call），避免重复执行。
     */
    private suspend fun <T> withSession(
        server: AiMcpServer,
        retryOnSessionExpired: Boolean = true,
        block: suspend (McpSession) -> T,
    ): T {
        val key = sessionKey(server)
        val cached = sessions[key]
        if (cached?.id != null) {
            try {
                return block(cached)
            } catch (error: McpHttpException) {
                // 服务器重启或会话过期时返回 404，丢掉缓存重新握手。
                if (!error.sessionExpired) throw error
                sessions.remove(key)
                if (!retryOnSessionExpired) throw error
            }
        }
        val session = initialize(server)
        if (session.id != null) sessions[key] = session
        try {
            return block(session)
        } catch (error: McpHttpException) {
            if (error.sessionExpired) sessions.remove(key)
            throw error
        }
    }

    /** 完整执行 initialize → notifications/initialized，返回可复用的会话。 */
    private suspend fun initialize(server: AiMcpServer): McpSession {
        val url = validUrl(server.url)
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "initialize")
            putJsonObject("params") {
                put("protocolVersion", PROTOCOL_VERSION)
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "SukiSU")
                    put("version", "1.0")
                }
            }
        }
        val response = executeRpc(buildRequest(server, url, null, payload), payload["id"])
        val result = response.body.requireResult()
        val session = McpSession(
            id = response.sessionId?.takeIf { it.isNotBlank() },
            protocolVersion = result.string("protocolVersion")?.takeIf { it.isNotBlank() }
                ?: PROTOCOL_VERSION,
        )
        // initialize 之后必须发送 initialized 通知；服务器通常返回 202 且无正文。
        val notification = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        executeRpc(buildRequest(server, url, session, notification), expectedId = null)
        return session
    }

    private suspend fun postRpc(
        server: AiMcpServer,
        session: McpSession,
        payload: JsonObject,
    ): RpcResponse {
        val url = validUrl(server.url)
        return executeRpc(buildRequest(server, url, session, payload), payload["id"])
    }

    private fun sessionKey(server: AiMcpServer): String =
        server.id + "|" + server.url.trim() + "|" + server.headers.hashCode()

    // ------------------------------------------------------------------
    // HTTP / JSON-RPC
    // ------------------------------------------------------------------

    private fun buildRequest(
        server: AiMcpServer,
        url: HttpUrl,
        session: McpSession?,
        payload: JsonObject,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(mediaType))
        // 自定义头先加，协议必需的头后加，保证关键头不会被配置项覆盖。
        server.headers.forEach { (name, value) ->
            if (name.isNotBlank()) builder.header(name.trim(), value)
        }
        builder.header("Accept", "application/json, text/event-stream")
        builder.header("Content-Type", "application/json")
        session?.id?.let { builder.header(SESSION_HEADER, it) }
        session?.protocolVersion?.let { builder.header(PROTOCOL_HEADER, it) }
        return builder.build()
    }

    /**
     * 发送一条 JSON-RPC 消息并读取响应。响应可能是 application/json，
     * 也可能是 text/event-stream。
     */
    private suspend fun executeRpc(
        request: Request,
        expectedId: JsonElement?,
    ): RpcResponse {
        val call = client.newCall(request)
        // 协程取消时同步取消 OkHttp 调用，避免阻塞的 execute() 拖住取消流程。
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            return call.execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = runCatching { response.body.string() }
                        .getOrDefault("")
                        .take(MAX_ERROR_BODY)
                    throw McpHttpException(response.code, detail.ifBlank { response.message })
                }
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val body = if (contentType.contains("text/event-stream")) {
                    readEventStream(response, expectedId)
                } else {
                    val text = response.body.string()
                    if (text.isBlank()) {
                        // notifications/initialized 等通知的 202 响应没有正文。
                        JsonObject(emptyMap())
                    } else {
                        json.parseToJsonElement(text) as? JsonObject
                            ?: throw IllegalStateException("MCP 服务器返回了非 JSON 响应")
                    }
                }
                RpcResponse(body, response.header(SESSION_HEADER))
            }
        } finally {
            handle?.dispose()
        }
    }

    /**
     * 解析 SSE 事件流：按空行切分事件，把同一事件的多行 `data:` 拼起来，
     * 只接受带 `jsonrpc` 字段的 JSON 对象。[expectedId] 非空时匹配对应 id。
     */
    private fun readEventStream(response: Response, expectedId: JsonElement?): JsonObject {
        val source = response.body.source()
        val data = StringBuilder()
        var lastResponse: JsonObject? = null
        while (true) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> {
                    if (data.isNotEmpty()) {
                        val message = parseEventData(data.toString())
                        data.clear()
                        if (message != null) {
                            if (expectedId == null || message["id"] == expectedId) return message
                            if (message["id"] != null) lastResponse = message
                        }
                    }
                }

                // 以冒号开头的是 SSE 注释/心跳。
                line.startsWith(":") -> Unit

                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        // 流没有以空行收尾时，最后一段 data 也要尝试解析。
        if (data.isNotEmpty()) {
            val message = parseEventData(data.toString())
            if (message != null && (expectedId == null || message["id"] == expectedId)) {
                return message
            }
        }
        return lastResponse ?: throw IllegalStateException("MCP 事件流里没有 JSON-RPC 响应")
    }

    private fun parseEventData(raw: String): JsonObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return null
        return obj.takeIf { it["jsonrpc"] != null }
    }

    private fun parseArguments(argumentsJson: String): JsonObject {
        val text = argumentsJson.trim().ifEmpty { "{}" }
        val element = runCatching { json.parseToJsonElement(text) }.getOrElse { error ->
            throw IllegalArgumentException("工具参数必须是合法 JSON：${error.message}")
        }
        return element as? JsonObject
            ?: throw IllegalArgumentException("工具参数必须是 JSON 对象")
    }

    /** 只允许 https；本机回环地址额外允许 http，方便本地调试 MCP 服务器。 */
    private fun validUrl(raw: String): HttpUrl {
        val url = raw.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("MCP 服务器地址无效：$raw")
        val host = url.host.lowercase().trim('[', ']')
        val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        require(url.scheme == "https" || (url.scheme == "http" && loopback)) {
            "MCP 服务器地址必须是 https://（仅本机调试允许 http://127.0.0.1 或 http://localhost）"
        }
        return url
    }

    private fun JsonObject.requireResult(): JsonObject {
        val error = this["error"] as? JsonObject
        if (error != null) {
            val code = (error["code"] as? JsonPrimitive)?.intOrNull
            val message = error.string("message").orEmpty()
            throw IllegalStateException(
                "MCP 错误${code?.let { " ($it)" }.orEmpty()}: " +
                    message.ifBlank { error.toString() },
            )
        }
        return this["result"] as? JsonObject
            ?: throw IllegalStateException("MCP 响应缺少 result 字段")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** HTTP 层错误；404 通常表示 Mcp-Session-Id 已失效。 */
    private class McpHttpException(statusCode: Int, detail: String) :
        IOException("MCP HTTP $statusCode: ${detail.take(300)}") {
        val sessionExpired: Boolean = statusCode == 404
    }
}
