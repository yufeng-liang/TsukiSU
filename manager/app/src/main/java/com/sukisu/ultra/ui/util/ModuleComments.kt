package com.sukisu.ultra.ui.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 模块商店「点赞 + 评论」的网络层（`/likes`、`/comments`、`/my-comments`）。
 *
 * 与投稿链路（[ModuleContribution]）共用同一批基建，但**不复用它的私有件**：
 * [ModuleContribution.client] 与 [ModuleContribution.execute] 是 private，拷一份最小实现
 * 比把它改 public 牵动更小——这里只有轻量 JSON 请求，没有上传那样的取消语义。
 *
 * ── 地址 ─────────────────────────────────────────────────────────────────────
 *
 * 与投稿同一个 Worker：地址必须走 [ModuleContribution.BASE_URL]（Cloudflare Pages 网关），
 * 而不是 Worker 的 `*.workers.dev` 直连——后者在国内被 DNS 投毒 + SNI 阻断（理由见
 * [ModuleContribution.BASE_URL] 的注释），评论请求若直连会把整个评论区打成永久空白。
 *
 * ── 鉴权 ─────────────────────────────────────────────────────────────────────
 *
 * 写操作（发评论、删评论、点赞）与 `/my-comments` 用 `Authorization: Bearer <token>`，
 * token 就是投稿链路验证过的那张九十天会话令牌（[UploadSession.token]）：
 * 调用方传进来的是**已经过 [UploadSession.validAt] 检查**的会话，这里只做最后一道
 * 形状校验（64 位小写 hex），不合格直接按「需要验证」返回，不发请求。
 *
 * ── 错误降级 ─────────────────────────────────────────────────────────────────
 *
 * 每个请求都返回 [CommentsResult]，不抛异常：评论区是附加功能，网络坏了让界面显示
 * 「加载失败/重试」，比把异常甩给 Composable 再崩掉整个详情页诚实得多。
 * 401 单独成态（[CommentsResult.NeedVerify]）：会话过期是用户能自己解决的事
 * （去上传页重新验证），与「服务器坏了」的提示走向不同。
 */
object ModuleComments {

    /** 查询类请求（列表、点赞状态）的超时：与投稿查询同一档。 */
    private const val QUERY_TIMEOUT_MS = 8_000L

    /** 发评论/点赞这类写操作的超时：后端只是一次 KV 写 + 计数，8 秒绑绑有余；放宽到与查询同档即可。 */
    private const val WRITE_TIMEOUT_MS = 12_000L

    /** 服务端单条评论的长度上限（500 字）；客户端先拦一道，省一次注定失败的往返。 */
    const val MAX_COMMENT_LENGTH = 500

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 评论请求的统一出口。
     *
     * [ok] 携带解析好的载荷；[NeedVerify] 表示服务端回了 401 或本地令牌形状不合格——
     * 两种情况的下一步都是「去重新验证身份」；[Failed] 是网络/服务器/解析失败，
     * [message] 是给人看的一句话（服务端的 code 优先），界面直接展示。
     */
    sealed interface CommentsResult {
        data class Ok(val value: JSONObject) : CommentsResult
        data object NeedVerify : CommentsResult
        data class Failed(val message: String) : CommentsResult
    }

    /** 一条评论（或楼中楼里的一条回复）。字段与 Worker 的行结构一一对应。 */
    data class CommentItem(
        val id: String,
        val uid: String,
        val nickname: String,
        val avatar: String,
        val content: String,
        /** ISO-8601；空串表示服务端没给（按「未知时间」显示）。 */
        val createdAt: String,
        /** 被回复人的昵称（仅楼中楼回复带）：UI 拿它画「@昵称」前缀。 */
        val replyToNickname: String = "",
        /** 楼中楼：只挂在这条评论是顶级评论时；回复本身不再有回复。 */
        val replies: List<CommentItem> = emptyList(),
    )

    /** 一页评论：列表 + 翻页游标（空串表示没有下一页）。 */
    data class CommentPage(
        val comments: List<CommentItem>,
        val nextCursor: String,
    )

    /** 「我的评论」里的一行。 */
    data class MyCommentItem(
        val id: String,
        val moduleId: String,
        val content: String,
        val createdAt: String,
        /** 服务端已删（楼主删的顶级评论），界面显示「已删除」占位。 */
        val deleted: Boolean,
    )

    // ---- 请求出口 ------------------------------------------------------------

    /**
     * 拉一页评论。游客也能看：[sessionToken] 为空时按未登录发请求。
     *
     * [cursor] 传上一页返回的 nextCursor；首页传空串。
     */
    suspend fun fetchComments(
        moduleId: String,
        cursor: String = "",
        sessionToken: String? = null,
    ): CommentsResult = withContext(Dispatchers.IO) {
        if (moduleId.isBlank()) return@withContext CommentsResult.Failed("")
        // BASE_URL 是写死的 https 常量，parse 不会失败；失败时整个请求当网络故障降级。
        val url = ModuleContribution.BASE_URL.trimEnd('/').toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("comments")
            ?.addQueryParameter("module_id", moduleId.trim())
            ?.apply {
                if (cursor.isNotBlank()) addQueryParameter("cursor", cursor.trim())
                addQueryParameter("limit", "20")
            }
            ?.build()
            ?.toString() ?: return@withContext CommentsResult.Failed("")
        val request = Request.Builder().url(url).get().build()
        executeRequest(request, QUERY_TIMEOUT_MS)
    }

    /** 发一条评论（顶级或楼中楼回复）。 */
    suspend fun postComment(
        sessionToken: String,
        moduleId: String,
        content: String,
        parentId: String? = null,
        replyToUid: String? = null,
    ): CommentsResult = withContext(Dispatchers.IO) {
        val session = requireSession(sessionToken) ?: return@withContext CommentsResult.NeedVerify
        val body = JSONObject().apply {
            put("module_id", moduleId.trim())
            put("content", content.trim())
            if (!parentId.isNullOrBlank()) put("parent_id", parentId.trim())
            if (!replyToUid.isNullOrBlank()) put("reply_to_uid", replyToUid.trim())
        }
        val request = Request.Builder()
            .url(ModuleContribution.BASE_URL.trimEnd('/') + "/comments")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer " + session.token)
            .build()
        executeRequest(request, WRITE_TIMEOUT_MS)
    }

    /** 删除自己的评论（Worker 侧按令牌 UID 归属校验）。 */
    suspend fun deleteComment(sessionToken: String, commentId: String): CommentsResult =
        withContext(Dispatchers.IO) {
            val session = requireSession(sessionToken) ?: return@withContext CommentsResult.NeedVerify
            val url = ModuleContribution.BASE_URL.trimEnd('/').toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments("comments")
                ?.addQueryParameter("id", commentId.trim())
                ?.build()
                ?.toString() ?: return@withContext CommentsResult.Failed("")
            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer " + session.token)
                .build()
            executeRequest(request, WRITE_TIMEOUT_MS)
        }

    /** 点赞/取消点赞：[liked] 为 true 是点赞，false 是取消。 */
    suspend fun setLike(sessionToken: String, moduleId: String, liked: Boolean): CommentsResult =
        withContext(Dispatchers.IO) {
            val session = requireSession(sessionToken) ?: return@withContext CommentsResult.NeedVerify
            val body = JSONObject().apply { put("module_id", moduleId.trim()) }
            val builder = Request.Builder()
                .url(ModuleContribution.BASE_URL.trimEnd('/') + "/likes")
                .header("Authorization", "Bearer " + session.token)
            val request = if (liked) {
                builder.post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
            } else {
                builder.delete(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
            }
            executeRequest(request, WRITE_TIMEOUT_MS)
        }

    /** 我的评论（跨模块），cursor 翻页。 */
    suspend fun fetchMyComments(sessionToken: String, cursor: String = ""): CommentsResult =
        withContext(Dispatchers.IO) {
            val session = requireSession(sessionToken) ?: return@withContext CommentsResult.NeedVerify
            val url = ModuleContribution.BASE_URL.trimEnd('/').toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments("my-comments")
                ?.apply { if (cursor.isNotBlank()) addQueryParameter("cursor", cursor.trim()) }
                ?.build()
                ?.toString() ?: return@withContext CommentsResult.Failed("")
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + session.token)
                .build()
            executeRequest(request, QUERY_TIMEOUT_MS)
        }

    // ---- 解析 ----------------------------------------------------------------

    /** 把 `{"ok":true,"comments":[...],"next_cursor":...}` 拆成一页。 */
    fun parseCommentPage(json: JSONObject): CommentPage {
        val arr = json.optJSONArray("comments") ?: JSONArray()
        val list = ArrayList<CommentItem>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            parseComment(row)?.let { list.add(it) }
        }
        return CommentPage(comments = list, nextCursor = json.optString("next_cursor", "").trim())
    }

    /** 把单条评论对象拆成 [CommentItem]；id 缺失的行丢弃。 */
    fun parseComment(row: JSONObject): CommentItem? {
        val id = row.optString("id", "").trim()
        if (id.isEmpty()) return null
        val repliesArr = row.optJSONArray("replies") ?: JSONArray()
        val replies = ArrayList<CommentItem>(repliesArr.length())
        for (i in 0 until repliesArr.length()) {
            // 楼中楼里不再有楼中楼：递归一层即可，避免服务端形状变化时死循环。
            (repliesArr.optJSONObject(i) ?: continue).let { r ->
                val rid = r.optString("id", "").trim()
                if (rid.isNotEmpty()) {
                    replies.add(
                        CommentItem(
                            id = rid,
                            uid = r.optString("uid", "").trim(),
                            nickname = r.optString("nickname", "").trim(),
                            avatar = r.optString("avatar", "").trim(),
                            content = r.optString("content", "").trim(),
                            createdAt = r.optString("created_at", "").trim(),
                            replyToNickname = r.optString("reply_to_nickname", "").trim(),
                        )
                    )
                }
            }
        }
        return CommentItem(
            id = id,
            uid = row.optString("uid", "").trim(),
            nickname = row.optString("nickname", "").trim(),
            avatar = row.optString("avatar", "").trim(),
            content = row.optString("content", "").trim(),
            createdAt = row.optString("created_at", "").trim(),
            replies = replies,
        )
    }

    /** 「我的评论」一行：id 缺失丢弃；deleted 缺省按 false。 */
    fun parseMyComment(row: JSONObject): MyCommentItem? {
        val id = row.optString("id", "").trim()
        if (id.isEmpty()) return null
        return MyCommentItem(
            id = id,
            moduleId = row.optString("module_id", "").trim(),
            content = row.optString("content", "").trim(),
            createdAt = row.optString("created_at", "").trim(),
            deleted = row.optBoolean("deleted", false),
        )
    }

    /** 把 `{"ok":true,"comment":{...}}` 拆成单条；结构异常时返回 null 由调用方降级。 */
    fun parsePostedComment(json: JSONObject): CommentItem? =
        json.optJSONObject("comment")?.let { parseComment(it) }

    // ---- 内部 ----------------------------------------------------------------

    /** 令牌形状校验 + 构造鉴权会话；不合格返回 null（调用方回 NeedVerify）。 */
    private fun requireSession(token: String): UploadSession? {
        val t = token.trim()
        if (!ModuleContribution.SESSION_TOKEN_RE.matches(t)) return null
        return UploadSession(token = t, uid = "", nickname = "", avatar = "", expiresAt = Long.MAX_VALUE)
    }

    private fun client(): OkHttpClient =
        runCatching { com.sukisu.ultra.ksuApp.okhttpClient }
            .getOrElse { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build() }

    /**
     * 发一个请求并把响应统一成 [CommentsResult]。
     *
     * 401 → NeedVerify；其余非 2xx → Failed（服务端 body 里的 code 优先展示）。
     * 网络异常按 Failed 降级，超时也一样——评论区没有「值得让用户等」的操作。
     */
    private suspend fun executeRequest(request: Request, timeoutMs: Long): CommentsResult =
        withContext(Dispatchers.IO) {
            val call = runCatching { client().newCall(request) }.getOrNull()
                ?: return@withContext CommentsResult.Failed("")
            try {
                withTimeout(timeoutMs) {
                    runCatching { call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS) }
                    call.execute().use { response ->
                        val body = runCatching { response.body.string() }.getOrNull().orEmpty()
                        if (response.code == 401) return@use CommentsResult.NeedVerify
                        if (!response.isSuccessful) {
                            val reason = runCatching { JSONObject(body).optString("code", "") }.getOrNull().orEmpty()
                            return@use CommentsResult.Failed(reason)
                        }
                        val json = runCatching { JSONObject(body) }.getOrNull()
                            ?: return@use CommentsResult.Failed("")
                        if (!json.optBoolean("ok", false)) {
                            // ok=false 但 HTTP 200：服务端的业务失败（如 sensitive/rate_limited），code 在 body 里。
                            val reason = json.optString("code", "").trim()
                            return@use if (reason == "unauthorized") CommentsResult.NeedVerify else CommentsResult.Failed(reason)
                        }
                        CommentsResult.Ok(json)
                    }
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) CommentsResult.Failed("") else throw e
            } catch (e: Throwable) {
                CommentsResult.Failed("")
            }
        }
}
