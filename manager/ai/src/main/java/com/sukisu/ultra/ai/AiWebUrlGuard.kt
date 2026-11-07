package com.sukisu.ultra.ai

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import okhttp3.Response

/**
 * 网页 URL 安全校验（SSRF 防护）。
 *
 * 只允许 http/https，并且目标主机解析出的所有 IP 都不能是环回、私有、链路本地、
 * 组播等内网地址；重定向的每一跳都要重新走这里的校验。
 */
object AiWebUrlGuard {

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /**
     * 校验并返回规范化后的 URL；不通过时抛出 [IllegalArgumentException]，
     * 错误信息可以直接展示给模型/用户。
     */
    fun requireSafeUrl(raw: String): String {
        val cleaned = raw.trim()
        require(cleaned.isNotEmpty()) { "url is required" }
        val uri = runCatching { URI(cleaned) }.getOrNull()
            ?: throw IllegalArgumentException("url is invalid")
        val scheme = uri.scheme?.lowercase()
        require(scheme in ALLOWED_SCHEMES) { "url must start with http:// or https://" }
        val host = uri.host?.trim()?.trim('[', ']')?.trimEnd('.')?.lowercase()
        require(!host.isNullOrBlank()) { "url must include a host" }
        require(!isForbiddenHostname(host)) { "拒绝访问内网地址：$host" }

        // 先做 DNS 解析，域名指向内网时直接拒绝。
        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (error: UnknownHostException) {
            throw IllegalArgumentException("无法解析目标主机：$host")
        }
        require(addresses.isNotEmpty()) { "无法解析目标主机：$host" }
        require(addresses.none { isForbiddenAddress(it) }) { "拒绝访问内网地址：$host" }
        return cleaned
    }

    /** 相对 Location 解析成绝对地址；解析失败按非法 URL 处理。 */
    fun resolveRedirect(currentUrl: String, location: String): String {
        val resolved = runCatching { URI(currentUrl).resolve(location.trim()).toString() }.getOrNull()
            ?: throw IllegalArgumentException("重定向地址无效")
        return requireSafeUrl(resolved)
    }

    private fun isForbiddenHostname(host: String): Boolean =
        host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.endsWith(".lan") ||
            host.endsWith(".home")

    /** 判断单个已解析地址是否属于内网/本机范围。 */
    fun isForbiddenAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address.map { it.toInt() and 0xFF }
        return when (address) {
            is Inet4Address -> isForbiddenIpv4(bytes)
            is Inet6Address -> isForbiddenIpv6(bytes)
            else -> false
        }
    }

    private fun isForbiddenIpv4(bytes: List<Int>): Boolean {
        if (bytes.size != 4) return false
        val first = bytes[0]
        val second = bytes[1]
        return first == 0 ||                       // 0.0.0.0/8
            first == 10 ||                          // 10.0.0.0/8
            first == 127 ||                         // 127.0.0.0/8
            (first == 169 && second == 254) ||      // 169.254.0.0/16 链路本地
            (first == 172 && second in 16..31) ||   // 172.16.0.0/12
            (first == 192 && second == 168) ||      // 192.168.0.0/16
            (first == 100 && second in 64..127) ||  // 100.64.0.0/10 运营商级 NAT
            (first == 198 && second in 18..19) ||   // 198.18.0.0/15 基准测试网段
            first >= 224                            // 组播/保留
    }

    private fun isForbiddenIpv6(bytes: List<Int>): Boolean {
        if (bytes.size != 16) return false
        // IPv4-mapped：::ffff:a.b.c.d，按内嵌的 IPv4 判断。
        if (bytes.slice(0 until 10).all { it == 0 } && bytes[10] == 0xFF && bytes[11] == 0xFF) {
            return isForbiddenIpv4(bytes.slice(12 until 16))
        }
        // :: 与 ::1
        if (bytes.slice(0 until 15).all { it == 0 }) {
            return bytes[15] == 0 || bytes[15] == 1
        }
        val first = bytes[0]
        val second = bytes[1]
        if ((first and 0xFE) == 0xFC) return true          // fc00::/7 唯一本地地址
        if (first == 0xFE && (second and 0xC0) == 0x80) return true // fe80::/10 链路本地
        if (first == 0xFF) return true                     // ff00::/8 组播
        return false
    }
}

/**
 * 流式读取响应体并限制原始字节数，避免大页面/大响应把内存打满。
 * 超过 [maxBytes] 时截断，并在末尾追加一段说明。
 */
internal fun Response.readBodyLimited(maxBytes: Int): String {
    val input = body.byteStream()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = maxBytes
    var truncated = false
    while (remaining > 0) {
        val read = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        output.write(buffer, 0, read)
        remaining -= read
    }
    if (remaining == 0 && input.read() != -1) {
        truncated = true
    }
    val text = output.toByteArray().toString(Charsets.UTF_8)
    return if (truncated) {
        "$text\n\n[响应超过 ${maxBytes / 1024} KB，已截断]"
    } else {
        text
    }
}
