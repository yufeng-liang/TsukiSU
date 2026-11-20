package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MCP 认证头的拆解与拼装：只填密钥这条路必须稳定，否则用户填了密钥却发不出请求。 */
class AiMcpAuthTest {

    @Test
    fun detectSplitsBearerKeyAndKeepsExtraHeaders() {
        val input = detectMcpAuth(
            mapOf(
                "Authorization" to "Bearer sk-abc123",
                "X-Trace" to "1",
            ),
        )
        assertEquals(McpAuthMode.Bearer, input.mode)
        assertEquals("sk-abc123", input.key)
        assertEquals("X-Trace: 1", input.extraHeaders)
    }

    @Test
    fun detectFindsApiKeyHeader() {
        val input = detectMcpAuth(mapOf("X-API-Key" to " k-9 "))
        assertEquals(McpAuthMode.ApiKey, input.mode)
        assertEquals("k-9", input.key)
        assertEquals("", input.extraHeaders)
    }

    @Test
    fun detectFallsBackToCustomWhenHeaderIsNotKnown() {
        val input = detectMcpAuth(mapOf("Authorization" to "Basic dXNlcjpwYXNz"))
        assertEquals(McpAuthMode.Custom, input.mode)
        assertEquals("", input.key)
        assertEquals("Authorization: Basic dXNlcjpwYXNz", input.extraHeaders)
    }

    @Test
    fun buildAddsBearerPrefixOnce() {
        assertEquals(
            mapOf("Authorization" to "Bearer sk-1"),
            buildMcpHeaders(McpAuthMode.Bearer, "sk-1", ""),
        )
        assertEquals(
            mapOf("Authorization" to "Bearer sk-1"),
            buildMcpHeaders(McpAuthMode.Bearer, "Bearer sk-1", ""),
        )
    }

    @Test
    fun buildWritesApiKeyHeaderAndKeepsExtras() {
        val headers = buildMcpHeaders(McpAuthMode.ApiKey, "k-2", "X-Trace: 1")
        assertEquals("k-2", headers["X-API-Key"])
        assertEquals("1", headers["X-Trace"])
    }

    @Test
    fun buildWithEmptyKeySendsNoAuthHeader() {
        assertTrue(buildMcpHeaders(McpAuthMode.Bearer, "   ", "").isEmpty())
    }

    @Test
    fun buildInCustomModeUsesRawHeadersOnly() {
        val headers = buildMcpHeaders(McpAuthMode.Custom, "ignored", "Authorization: Bearer x\nA: b")
        assertEquals(mapOf("Authorization" to "Bearer x", "A" to "b"), headers)
    }
}
