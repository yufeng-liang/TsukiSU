package com.sukisu.ultra.data.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 代理层纯函数测试（不碰网络、不碰 SharedPreferences）。
 *
 * 这些规则决定了「哪些地址允许套代理」，一旦放宽就是安全回退，所以用例按反例写得多。
 */
class GithubProxyTest {

    private val releaseUrl = "https://github.com/owner/repo/releases/download/v1.0.0/module.zip"
    private val entry = ProxyEntry("p1", "gh-proxy", "https://gh-proxy.org")

    @Test
    fun acceptsBareHttpsOrigins() {
        assertTrue(ProxyUrl.isValidBaseUrl("https://gh-proxy.org"))
        assertTrue(ProxyUrl.isValidBaseUrl("https://gh-proxy.org/"))
        assertTrue(ProxyUrl.isValidBaseUrl("https://gh-proxy.org:443"))
    }

    @Test
    fun rejectsOriginsWithPathQueryOrPlainHttp() {
        val bad = listOf(
            "",
            "gh-proxy.org",
            "http://gh-proxy.org",
            "https://gh-proxy.org/prefix",
            "https://gh-proxy.org/?x=1",
            "https://gh-proxy.org/#frag",
            "https://user:pass@gh-proxy.org",
            "https://gh-proxy.org:8443",
            "https://" + "a".repeat(200),
        )
        for (url in bad) assertFalse(url, ProxyUrl.isValidBaseUrl(url))
    }

    @Test
    fun buildsAndUnwrapsProxyUrls() {
        assertEquals(
            "https://gh-proxy.org/$releaseUrl",
            ProxyUrl.build(entry, releaseUrl),
        )
        assertEquals(releaseUrl, ProxyUrl.unwrap(ProxyUrl.build(entry, releaseUrl), listOf(entry)))
        assertEquals(entry, ProxyUrl.entryOf(ProxyUrl.build(entry, releaseUrl), listOf(entry)))
    }

    @Test
    fun unwrapRejectsInnerUrlsThatAreNotReleaseDownloads() {
        // 代理前缀 + 任意地址（比如首页）不算合法封装，否则代理能把下载重定向到任意站。
        val homepage = "https://gh-proxy.org/https://github.com/owner/repo"
        assertNull(ProxyUrl.unwrap(homepage, listOf(entry)))
        assertNull(ProxyUrl.unwrap("https://gh-proxy.org.evil.com/$releaseUrl", listOf(entry)))
    }

    @Test
    fun looksLikeProxiedOnlyChecksPath() {
        assertTrue(ProxyUrl.looksLikeProxied(ProxyUrl.build(entry, releaseUrl)))
        // query 里出现 :// 不代表这是代理封装；误判会让一次合法直连被服务层拒掉。
        assertFalse(
            ProxyUrl.looksLikeProxied("https://example.org/asset.zip?next=https://github.com/x")
        )
        assertFalse(ProxyUrl.looksLikeProxied(releaseUrl))
        assertFalse(ProxyUrl.looksLikeProxied("not a url"))
    }

    @Test
    fun officialModeNeverUsesAProxy() {
        val candidates = GithubProxyRouter.candidates(
            originalUrl = releaseUrl,
            mode = ProxyMode.OFFICIAL,
            entries = BUILTIN_PROXIES,
        )
        assertEquals(listOf(releaseUrl), candidates)
    }

    @Test
    fun autoModeFollowsRankingAndKeepsDirectLast() {
        val entries = BUILTIN_PROXIES.take(2)
        val candidates = GithubProxyRouter.candidates(
            originalUrl = releaseUrl,
            mode = ProxyMode.AUTO,
            entries = entries,
            ranking = listOf(entries[1].id, DIRECT_ID, entries[0].id),
        )
        assertEquals(
            listOf(
                ProxyUrl.build(entries[1], releaseUrl),
                releaseUrl,
                ProxyUrl.build(entries[0], releaseUrl),
            ),
            candidates,
        )
    }

    @Test
    fun pinnedProxyThatDisappearedFallsBackToAutoOrder() {
        val candidates = GithubProxyRouter.candidates(
            originalUrl = releaseUrl,
            mode = ProxyMode.PINNED,
            entries = listOf(entry),
            pinnedId = "gone",
        )
        // 用户选代理通常就是因为直连不通，所以失效时仍要留一条代理候选。
        assertEquals(listOf(ProxyUrl.build(entry, releaseUrl), releaseUrl), candidates)
    }

    @Test
    fun nonReleaseUrlsAreNeverProxied() {
        val githubPage = "https://github.com/owner/repo"
        assertEquals(
            listOf(githubPage),
            GithubProxyRouter.candidates(githubPage, ProxyMode.AUTO, BUILTIN_PROXIES),
        )
    }

    @Test
    fun parsesProxyDocumentAndDropsBadEntries() {
        val body = """
            {
              "schemaVersion": 1,
              "proxies": [
                {"id": "a", "name": "A", "baseUrl": "https://a.example", "note": "ok"},
                {"id": "a", "name": "dup", "baseUrl": "https://dup.example"},
                {"id": "b", "name": "B", "baseUrl": "http://plain.example"},
                {"id": "c", "name": "C", "baseUrl": "https://a.example/"},
                {"name": "no-id", "baseUrl": "https://noid.example"},
                {"id": "d", "name": "D", "baseUrl": "https://a.example/path"}
              ]
            }
        """.trimIndent()
        val parsed = parseProxyList(body)!!
        // 只应留下第一条：重复 id、非 https、同地址（仅尾斜杠不同）、缺 id、带路径的全部丢掉。
        assertEquals(1, parsed.size)
        assertEquals("a", parsed[0].id)
    }

    @Test
    fun rejectsDocumentsWithoutProxyArray() {
        assertNull(parseProxyList("{}"))
        assertNull(parseProxyList("<html>404</html>"))
        assertNull(parseProxyList(""))
    }
}
