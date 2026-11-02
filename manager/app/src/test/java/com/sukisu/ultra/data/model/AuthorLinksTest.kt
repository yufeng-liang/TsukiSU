package com.sukisu.ultra.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 作者主页链接的归一化。
 *
 * 与 [GitHubRepoAddressTest] 同一类锁：索引、网页、Worker 三处必须对「这个值算不算合法」
 * 给出同一个答案。作者链接由上传者手填、没人核过，形状卡不严就会变成指向任意站点的文本框；
 * 卡太严又会把服务端认得的写法拦在本地。
 */
class AuthorLinksTest {

    // ---- 酷安主页 ----

    @Test
    fun `coolapk accepts the canonical form`() {
        assertEquals(
            "https://www.coolapk.com/u/123456",
            AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/123456"),
        )
    }

    @Test
    fun `coolapk upgrades http and normalizes the host`() {
        assertEquals(
            "https://www.coolapk.com/u/1234",
            AuthorLinks.normalizeCoolapk("http://www.coolapk.com/u/1234"),
        )
        assertEquals(
            "https://www.coolapk.com/u/1234",
            AuthorLinks.normalizeCoolapk("www.coolapk.com/u/1234"),
        )
        assertEquals(
            "https://www.coolapk.com/u/1234",
            AuthorLinks.normalizeCoolapk("coolapk.com/u/1234"),
        )
        assertEquals(
            "https://www.coolapk.com/u/1234",
            AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/1234/"),
        )
    }

    @Test
    fun `coolapk rejects other hosts`() {
        assertNull(AuthorLinks.normalizeCoolapk("https://evil-coolapk.com/u/1234"))
        assertNull(AuthorLinks.normalizeCoolapk("https://coolapk.com.evil.test/u/1234"))
        assertNull(AuthorLinks.normalizeCoolapk("https://github.com/u/1234"))
    }

    @Test
    fun `coolapk rejects paths that are not a user profile`() {
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/feed/1234"))
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/1234/posts"))
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/"))
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/"))
    }

    @Test
    fun `coolapk rejects uids outside four to twelve digits`() {
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/123"))
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/1234567890123"))
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/abc123"))
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/1234abc"))
    }

    @Test
    fun `coolapk rejects query fragment port and userinfo`() {
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/1234?from=share"))
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com/u/1234#top"))
        assertNull(AuthorLinks.normalizeCoolapk("https://www.coolapk.com:8443/u/1234"))
        assertNull(AuthorLinks.normalizeCoolapk("https://user:pass@www.coolapk.com/u/1234"))
    }

    @Test
    fun `coolapk rejects empty and non-http schemes`() {
        assertNull(AuthorLinks.normalizeCoolapk(""))
        assertNull(AuthorLinks.normalizeCoolapk("   "))
        assertNull(AuthorLinks.normalizeCoolapk("javascript:alert(1)"))
        assertNull(AuthorLinks.normalizeCoolapk("ftp://www.coolapk.com/u/1234"))
    }

    // ---- GitHub 主页 ----

    @Test
    fun `github accepts the canonical form and keeps the login case`() {
        assertEquals(
            "https://github.com/octocat",
            AuthorLinks.normalizeGithub("https://github.com/octocat"),
        )
        assertEquals(
            "https://github.com/OctoCat",
            AuthorLinks.normalizeGithub("https://github.com/OctoCat"),
        )
    }

    @Test
    fun `github upgrades http and accepts shorthand and a trailing slash`() {
        assertEquals(
            "https://github.com/octocat",
            AuthorLinks.normalizeGithub("http://github.com/octocat"),
        )
        assertEquals(
            "https://github.com/octocat",
            AuthorLinks.normalizeGithub("github.com/octocat"),
        )
        assertEquals(
            "https://github.com/octocat",
            AuthorLinks.normalizeGithub("https://github.com/octocat/"),
        )
    }

    @Test
    fun `github rejects hosts that are not a profile`() {
        assertNull(AuthorLinks.normalizeGithub("https://www.github.com/octocat"))
        assertNull(AuthorLinks.normalizeGithub("https://gist.github.com/octocat"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com.evil.test/octocat"))
    }

    @Test
    fun `github rejects repositories and multi segment paths`() {
        assertNull(AuthorLinks.normalizeGithub("https://github.com/octocat/hello-world"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/octocat/hello-world.git"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/octocat.git"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/"))
    }

    @Test
    fun `github rejects reserved paths regardless of case`() {
        assertNull(AuthorLinks.normalizeGithub("https://github.com/settings"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/Settings"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/marketplace"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/login"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/topics"))
    }

    @Test
    fun `github rejects logins with bad hyphen placement`() {
        assertNull(AuthorLinks.normalizeGithub("https://github.com/-octocat"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/octocat-"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/octo--cat"))
    }

    @Test
    fun `github login length is bounded`() {
        val longest = "a".repeat(39)
        assertEquals(
            "https://github.com/$longest",
            AuthorLinks.normalizeGithub("https://github.com/$longest"),
        )
        assertNull(AuthorLinks.normalizeGithub("https://github.com/" + "a".repeat(40)))
    }

    @Test
    fun `github rejects query fragment port and userinfo`() {
        assertNull(AuthorLinks.normalizeGithub("https://github.com/octocat?tab=repos"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com/octocat#readme"))
        assertNull(AuthorLinks.normalizeGithub("https://github.com:8443/octocat"))
        assertNull(AuthorLinks.normalizeGithub("https://user:pass@github.com/octocat"))
    }

    @Test
    fun `github rejects empty and non-http schemes`() {
        assertNull(AuthorLinks.normalizeGithub(""))
        assertNull(AuthorLinks.normalizeGithub("   "))
        assertNull(AuthorLinks.normalizeGithub("javascript:alert(1)"))
        assertNull(AuthorLinks.normalizeGithub("ftp://github.com/octocat"))
    }
}
