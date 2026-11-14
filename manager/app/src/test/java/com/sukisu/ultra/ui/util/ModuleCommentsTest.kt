
package com.sukisu.ultra.ui.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ModuleComments] 纯解析逻辑的单测：网络层不在单测里碰（依赖真机 OkHttp 调用栈），
 * 只保证「服务端 JSON → 数据类」的契约钉得死：字段名、默认值、楼中楼嵌套、游标。
 */
class ModuleCommentsTest {

    @Test
    fun `parseCommentPage maps comments cursor and replies`() {
        val body = JSONObject(
            """
            {
              "ok": true,
              "comments": [
                {
                  "id": "c1",
                  "uid": "10001",
                  "nickname": "xiaomo",
                  "avatar": "https://example.com/a1.png",
                  "content": "nice",
                  "created_at": "2026-11-14T08:00:00Z",
                  "replies": [
                    {
                      "id": "r1",
                      "uid": "10002",
                      "nickname": "chai",
                      "avatar": "",
                      "content": "same",
                      "reply_to_nickname": "xiaomo",
                      "created_at": "2026-11-14T09:00:00Z"
                    }
                  ]
                },
                {
                  "id": "c2",
                  "uid": "10003",
                  "nickname": "guest",
                  "avatar": "",
                  "content": "",
                  "created_at": "2026-11-14T07:00:00Z",
                  "replies": []
                }
              ],
              "next_cursor": "CUR-42"
            }
            """.trimIndent()
        )

        val page = ModuleComments.parseCommentPage(body)
        assertEquals(2, page.comments.size)
        assertEquals("CUR-42", page.nextCursor)

        val first = page.comments[0]
        assertEquals("c1", first.id)
        assertEquals("10001", first.uid)
        assertEquals("xiaomo", first.nickname)
        assertEquals("nice", first.content)
        assertEquals(1, first.replies.size)
        // 楼中楼的「@谁」前缀靠这个字段，服务端不给时为空串
        assertEquals("xiaomo", first.replies[0].replyToNickname)
        assertEquals("chai", first.replies[0].nickname)

        // 已删除的顶级评论：content 为空串，UI 层据此画「已删除」占位
        assertTrue(page.comments[1].content.isEmpty())
        assertEquals(0, page.comments[1].replies.size)
    }

    @Test
    fun `parseCommentPage tolerates missing cursor and null replies`() {
        val body = JSONObject(
            """
            {
              "ok": true,
              "comments": [
                {"id": "c1", "uid": "u", "nickname": "n", "content": "hello", "created_at": "2026-01-01T00:00:00Z"}
              ]
            }
            """.trimIndent()
        )

        val page = ModuleComments.parseCommentPage(body)
        assertEquals(1, page.comments.size)
        assertEquals("", page.nextCursor)
        assertEquals(0, page.comments[0].replies.size)
    }

    @Test
    fun `parseComment drops rows without id`() {
        val row = JSONObject("""{"uid": "u", "nickname": "n", "content": "x"}""")
        assertNull(ModuleComments.parseComment(row))
    }

    @Test
    fun `parseMyComment maps deleted flag and module binding`() {
        val pageJson = JSONObject(
            """
            {
              "ok": true,
              "comments": [
                {"id": "mc1", "module_id": "zygisk_assistant", "content": "good", "created_at": "2026-11-01T10:00:00Z", "deleted": false},
                {"id": "mc2", "module_id": "cert_bridge", "content": "x", "created_at": "2026-11-02T10:00:00Z", "deleted": true}
              ],
              "next_cursor": ""
            }
            """.trimIndent()
        )

        val rows = pageJson.optJSONArray("comments")!!
        val first = ModuleComments.parseMyComment(rows.optJSONObject(0)!!)!!
        val second = ModuleComments.parseMyComment(rows.optJSONObject(1)!!)!!
        assertEquals("zygisk_assistant", first.moduleId)
        assertEquals(false, first.deleted)
        assertEquals("cert_bridge", second.moduleId)
        assertEquals(true, second.deleted)
    }

    @Test
    fun `parsePostedComment reads the wrapped comment object`() {
        val body = JSONObject(
            """
            {
              "ok": true,
              "comment": {"id": "new1", "uid": "me", "nickname": "me", "content": "first!", "created_at": "2026-11-15T00:00:00Z", "replies": []}
            }
            """.trimIndent()
        )

        val posted = ModuleComments.parsePostedComment(body)!!
        assertEquals("new1", posted.id)
        assertEquals("me", posted.uid)
        assertEquals("me", posted.nickname)
        assertEquals("first!", posted.content)
        assertEquals(0, posted.replies.size)
    }
}