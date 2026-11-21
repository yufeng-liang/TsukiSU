package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MCP 配置粘贴导入的解析规则：两套外壳共用这一份。
 */
class AiMcpImportTest {

    @Test
    fun readsTheMcpServersWrapperWithHeaders() {
        val raw = """
            {
              "mcpServers": {
                "github": {
                  "type": "streamable_http",
                  "url": "https://api.example.com/mcp",
                  "headers": { "Authorization": "Bearer abc", "X-Trace": "1" }
                },
                "local": { "url": "http://127.0.0.1:3000/mcp" }
              }
            }
        """.trimIndent()
        val result = importMcpServers(raw)!!
        assertEquals(2, result.servers.size)
        assertEquals(listOf("github", "local"), result.servers.map { it.name })
        assertEquals("Bearer abc", result.servers[0].headers["Authorization"])
        assertEquals("http://127.0.0.1:3000/mcp", result.servers[1].url)
        assertTrue(result.skipped.isEmpty())
        assertTrue(result.servers[0].enabled)
    }

    @Test
    fun readsASingleServerObjectAndKeepsItsName() {
        val raw = """{"name":"DeepWiki","serverUrl":"https://mcp.deepwiki.com/mcp"}"""
        val result = importMcpServers(raw)!!
        assertEquals(1, result.servers.size)
        assertEquals("DeepWiki", result.servers[0].name)
        assertEquals("https://mcp.deepwiki.com/mcp", result.servers[0].url)
    }

    @Test
    fun skipsStdioEntriesButKeepsTheHttpOnes() {
        val raw = """
            {
              "mcpServers": {
                "fs": { "command": "npx", "args": ["-y", "server-filesystem"] },
                "remote": { "url": "https://example.com/mcp" }
              }
            }
        """.trimIndent()
        val result = importMcpServers(raw)!!
        assertEquals(listOf("remote"), result.servers.map { it.name })
        assertEquals(listOf("fs"), result.skipped)
    }

    @Test
    fun returnsNullWhenNothingUsable() {
        assertNull(importMcpServers(""))
        assertNull(importMcpServers("not json at all"))
        assertNull(importMcpServers("""{"mcpServers":{"fs":{"command":"npx"}}}"""))
    }
}
