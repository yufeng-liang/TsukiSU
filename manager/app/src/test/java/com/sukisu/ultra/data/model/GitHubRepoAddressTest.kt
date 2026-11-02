package com.sukisu.ultra.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 源码仓库地址的归一化。
 *
 * 这里锁的是「什么算一个合法的仓库地址」：服务端按同一规则判，客户端先按它拦，
 * 所以放行一个服务端会拒的形状，等于让用户白填一遍；反过来拦下服务端认得的写法，
 * 则是让人莫名其妙传不上去。
 */
class GitHubRepoAddressTest {

    @Test
    fun `accepts the canonical form`() {
        assertEquals(
            "https://github.com/owner/repo",
            GitHubRepoAddress.normalize("https://github.com/owner/repo"),
        )
    }

    @Test
    fun `accepts shorthand and normalizes the scheme`() {
        assertEquals("https://github.com/o/r", GitHubRepoAddress.normalize("github.com/o/r"))
        assertEquals("https://github.com/o/r", GitHubRepoAddress.normalize("http://github.com/o/r"))
        assertEquals("https://github.com/o/r", GitHubRepoAddress.normalize("  https://github.com/o/r  "))
    }

    @Test
    fun `lowercases the host but keeps owner and repo case`() {
        assertEquals(
            "https://github.com/Owner/Repo",
            GitHubRepoAddress.normalize("https://GitHub.COM/Owner/Repo"),
        )
        // www 不认：服务端按 github.com 精确判，客户端放行只会换来一次 400。
        assertNull(GitHubRepoAddress.normalize("https://www.github.com/Owner/Repo"))
    }

    @Test
    fun `drops a trailing slash and a git suffix`() {
        assertEquals("https://github.com/o/r", GitHubRepoAddress.normalize("https://github.com/o/r/"))
        assertEquals("https://github.com/o/r", GitHubRepoAddress.normalize("https://github.com/o/r.git"))
        assertEquals("https://github.com/o/r", GitHubRepoAddress.normalize("https://github.com/o/r.git/"))
    }

    @Test
    fun `rejects empty and blank input`() {
        assertNull(GitHubRepoAddress.normalize(""))
        assertNull(GitHubRepoAddress.normalize("   "))
    }

    @Test
    fun `rejects other hosts and non-http schemes`() {
        assertNull(GitHubRepoAddress.normalize("https://gitlab.com/o/r"))
        assertNull(GitHubRepoAddress.normalize("https://gist.github.com/o/r"))
        assertNull(GitHubRepoAddress.normalize("ftp://github.com/o/r"))
    }

    @Test
    fun `rejects query fragment port and userinfo`() {
        assertNull(GitHubRepoAddress.normalize("https://github.com/o/r?tab=readme"))
        assertNull(GitHubRepoAddress.normalize("https://github.com/o/r#readme"))
        assertNull(GitHubRepoAddress.normalize("https://github.com:8443/o/r"))
        assertNull(GitHubRepoAddress.normalize("https://user@github.com/o/r"))
    }

    @Test
    fun `rejects the wrong number of path segments`() {
        assertNull(GitHubRepoAddress.normalize("https://github.com/o"))
        assertNull(GitHubRepoAddress.normalize("https://github.com/o/r/tree/main"))
        assertNull(GitHubRepoAddress.normalize("https://github.com"))
    }

    @Test
    fun `rejects a repo that is only dots`() {
        assertNull(GitHubRepoAddress.normalize("https://github.com/o/."))
        assertNull(GitHubRepoAddress.normalize("https://github.com/o/.."))
        assertNull(GitHubRepoAddress.normalize("https://github.com/o/.git"))
    }

    @Test
    fun `ownerRepo returns the short form`() {
        assertEquals("owner/repo", GitHubRepoAddress.ownerRepo("https://github.com/owner/repo"))
        assertNull(GitHubRepoAddress.ownerRepo("nope"))
    }
}
