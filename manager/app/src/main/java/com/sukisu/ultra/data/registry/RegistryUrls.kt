package com.sukisu.ultra.data.registry

import java.net.URI
import java.net.URLDecoder

object RegistryUrls {

    
    const val DEFAULT_BASE_URL = "https://modules.kernelsu.org/"

    const val INDEX_PATH = "api/v1/index.json"

    
    /** 允许走 GitHub 加速的下载域名。只收 GitHub 自己拥有的域名，防止代理被当成任意 URL 转发器。 */
    val ACCELERABLE_GITHUB_HOSTS: Set<String> = setOf(
        "github.com",
        "www.github.com",
        "gist.github.com",
        "raw.githubusercontent.com",
        "gist.githubusercontent.com",
        "codeload.github.com",
        "media.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    /** 下载重定向允许的域名；与可加速域名保持一致，内容仍由 size + SHA-256 兜底。 */
    val DOWNLOAD_HOSTS: Set<String> = ACCELERABLE_GITHUB_HOSTS

    private val API_PATH = Regex("^[A-Za-z0-9._\\-/]+$")
    private val REPO_PART = Regex("^[A-Za-z0-9._-]+$")

    
    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()) {
            "Registry base URL must be an https URL: $baseUrl"
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    
    fun isValidApiPath(path: String): Boolean =
        path.isNotEmpty() && API_PATH.matches(path) && !path.startsWith("/") && !path.contains("..")

    
    fun resolve(baseUrl: String, path: String): String {
        if (!isValidApiPath(path)) throw RegistryFormatException("Invalid path in registry document: $path")
        return normalizeBaseUrl(baseUrl) + path
    }

    fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    
    fun isHttpsUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.isAbsolute && !uri.isOpaque &&
            uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrEmpty() && uri.rawUserInfo == null
    }

    
    fun isWebUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.isAbsolute && !uri.isOpaque &&
            (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
            !uri.host.isNullOrEmpty() && uri.rawUserInfo == null
    }

    
    fun isInPageAnchor(url: String, documentUrl: String): Boolean {
        val hash = url.indexOf('#')
        if (hash <= 0 || hash == url.length - 1) return false
        return url.substring(0, hash) == documentUrl
    }

    
    fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }

    /**
     * Same origin: scheme, host and (effective) port are equal. Hosts are compared as written
     * in ASCII; a non-ASCII (IDN) host does not parse as a server authority and never matches,
     * and a punycode host only matches the identical punycode host.
     */
    fun isSameOrigin(url: String, origin: String): Boolean {
        val a = runCatching { URI(url) }.getOrNull() ?: return false
        val b = runCatching { URI(origin) }.getOrNull() ?: return false
        val hostA = a.host ?: return false
        val hostB = b.host ?: return false
        if (a.scheme == null || b.scheme == null) return false
        return a.scheme.equals(b.scheme, ignoreCase = true) &&
            hostA.equals(hostB, ignoreCase = true) &&
            effectivePort(a) != -1 &&
            effectivePort(a) == effectivePort(b)
    }

    /**
     * Sub-resource rule of the restricted (registry) renderer: https GET from the registry
     * origin only. Applied to the request and to every redirect hop, so a same-origin URL can
     * not redirect to another host.
     */
    fun isAllowedRestrictedResource(url: String, registryOrigin: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return false
        return isSameOrigin(url, registryOrigin)
    }

    private fun decodeSegment(s: String): String? =
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrNull()

    
    fun isAllowedReleaseDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.isAbsolute || uri.isOpaque) return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.host.equals("github.com", ignoreCase = true)) return false
        if (uri.port != -1 && uri.port != 443) return false
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return false
        val rawPath = uri.rawPath ?: return false
        if (!rawPath.startsWith("/")) return false
        val segments = rawPath.substring(1).split('/')
        if (segments.size < 6) return false
        for (raw in segments) {
            val decoded = decodeSegment(raw) ?: return false
            if (decoded.isEmpty() || decoded == "." || decoded == ".." ||
                decoded.contains('/') || decoded.contains('\\')
            ) return false
        }
        val owner = segments[0]
        val repo = segments[1]
        if (!REPO_PART.matches(owner) || !REPO_PART.matches(repo)) return false
        if (segments[2] != "releases" || segments[3] != "download") return false
        return true
    }

    /**
     * 是否是允许交给 GitHub 加速代理的地址。
     *
     * 比 [isAllowedReleaseDownloadUrl] 宽：只要是 GitHub 拥有的下载域名，release 直链、
     * raw 文件、archive、gist、codeload 等都算。但仍然只收 https、无 userinfo、443 端口、
     * 路径段合法的地址，避免代理被当成任意 URL 转发器。
     */
    fun isAccelerableGithubUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.isAbsolute || uri.isOpaque) return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.port != -1 && uri.port != 443) return false
        if (uri.rawUserInfo != null || uri.rawFragment != null) return false
        val host = uri.host?.lowercase() ?: return false
        if (host !in ACCELERABLE_GITHUB_HOSTS) return false
        val rawPath = uri.rawPath ?: return false
        if (!rawPath.startsWith("/") || rawPath == "/") return false
        for (raw in rawPath.substring(1).split('/')) {
            val decoded = decodeSegment(raw) ?: return false
            if (decoded.isEmpty() || decoded == "." || decoded == ".." ||
                decoded.contains('/') || decoded.contains('\\')
            ) return false
        }
        return true
    }

    
    fun isAllowedDownloadHop(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.port != -1 && uri.port != 443) return false
        if (uri.rawUserInfo != null) return false
        return host in DOWNLOAD_HOSTS
    }
}
