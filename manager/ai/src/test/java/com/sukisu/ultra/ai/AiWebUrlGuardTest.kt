package com.sukisu.ultra.ai

import java.net.InetAddress
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 网页抓取 SSRF 防护的回归测试。 */
class AiWebUrlGuardTest {

    @Test
    fun rejectsNonHttpSchemes() {
        listOf("file:///etc/passwd", "ftp://example.com/a", "httpx://example.com/mcp").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                AiWebUrlGuard.requireSafeUrl(url)
            }
        }
    }

    @Test
    fun rejectsLoopbackAndPrivateTargets() {
        listOf(
            "http://localhost/mcp",
            "http://127.0.0.1/mcp",
            "http://[::1]/mcp",
            "http://10.0.0.1/mcp",
            "http://172.20.1.1/mcp",
            "http://192.168.1.1/mcp",
            "http://169.254.169.254/latest/meta-data",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                AiWebUrlGuard.requireSafeUrl(url)
            }
        }
    }

    @Test
    fun classifiesPrivateAddresses() {
        assertTrue(AiWebUrlGuard.isForbiddenAddress(InetAddress.getByName("192.168.1.10")))
        assertTrue(AiWebUrlGuard.isForbiddenAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(AiWebUrlGuard.isForbiddenAddress(InetAddress.getByName("10.1.2.3")))
        assertTrue(AiWebUrlGuard.isForbiddenAddress(InetAddress.getByName("172.31.0.1")))
    }
}
