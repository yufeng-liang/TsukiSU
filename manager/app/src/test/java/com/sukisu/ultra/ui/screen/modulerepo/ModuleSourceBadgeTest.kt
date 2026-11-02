package com.sukisu.ultra.ui.screen.modulerepo

import com.sukisu.ultra.data.model.MmrlModuleInfo
import com.sukisu.ultra.data.model.MmrlVersion
import com.sukisu.ultra.data.model.RepoModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 列表上的源码 chip 与详情页证据块的取数。
 *
 * chip 只有四种状态、且同一时刻只显示一种：把「已校验但没覆盖最新版」画成绿勾，
 * 用户会以为这一版源码也能对上；反过来把「校验失效」画成灰字却没说明，等于什么都没说。
 */
class ModuleSourceBadgeTest {

    private fun version(
        versionCode: Long = 1L,
        repoTag: String = "",
        repoDigest: String = "",
    ) = MmrlVersion(
        version = "v$versionCode",
        versionCode = versionCode,
        timestamp = 0L,
        time = "",
        zipUrl = "https://example.com/$versionCode.zip",
        zipUrlFallback = null,
        changelogUrl = null,
        size = 0L,
        repoTag = repoTag,
        repoDigest = repoDigest,
    )

    private fun module(
        repoUrl: String = "",
        verified: Boolean = false,
        coversLatest: Boolean = false,
        license: String = "",
        versions: List<MmrlVersion> = emptyList(),
    ) = RepoModule(
        moduleId = "m",
        moduleName = "M",
        authors = "me",
        authorList = emptyList(),
        summary = "",
        metamodule = false,
        stargazerCount = 0,
        updatedAt = "",
        createdAt = "",
        latestRelease = "",
        latestReleaseTime = "",
        latestVersionCode = 0L,
        latestAsset = null,
        repoUrl = repoUrl,
        repoVerified = verified,
        repoCoversLatest = coversLatest,
        repoLicense = license,
        mmrl = MmrlModuleInfo(readmeUrl = null, supportUrl = null, versions = versions),
    )

    @Test
    fun `no repo means no chip`() {
        assertNull(repoSourceBadge(module()))
    }

    @Test
    fun `verified and covering the latest is a green badge`() {
        assertEquals(
            RepoSourceBadge.OPEN_SOURCE,
            repoSourceBadge(module("https://github.com/o/r", verified = true, coversLatest = true, license = "GPL-3.0")),
        )
        assertEquals(
            RepoSourceBadge.PUBLIC_SOURCE,
            repoSourceBadge(module("https://github.com/o/r", verified = true, coversLatest = true)),
        )
    }

    @Test
    fun `verified but not covering the latest is its own state`() {
        assertEquals(
            RepoSourceBadge.LATEST_NOT_COVERED,
            repoSourceBadge(module("https://github.com/o/r", verified = true, coversLatest = false, license = "MIT")),
        )
    }

    @Test
    fun `unverified repository is stale even when it claims coverage`() {
        assertEquals(
            RepoSourceBadge.STALE_VERIFICATION,
            repoSourceBadge(module("https://github.com/o/r", verified = false, coversLatest = true, license = "MIT")),
        )
    }

    @Test
    fun `green badges are the two verified ones`() {
        assertEquals(true, RepoSourceBadge.OPEN_SOURCE.isVerified)
        assertEquals(true, RepoSourceBadge.PUBLIC_SOURCE.isVerified)
        assertEquals(false, RepoSourceBadge.LATEST_NOT_COVERED.isVerified)
        assertEquals(false, RepoSourceBadge.STALE_VERIFICATION.isVerified)
    }

    @Test
    fun `evidence picks the newest version that carries a tag or digest`() {
        val tagged = version(2L, repoTag = "v1.2.3", repoDigest = "sha256:" + "a".repeat(64))
        val plain = version(3L)
        val data = module(
            "https://github.com/o/r",
            verified = true,
            versions = listOf(plain, tagged),
        )
        assertEquals(tagged, evidenceVersion(data))
    }

    @Test
    fun `evidence is null without tags or digests`() {
        assertNull(evidenceVersion(module("https://github.com/o/r", versions = listOf(version(1L)))))
    }

    @Test
    fun `short digest drops the prefix and keeps twelve hex chars`() {
        assertEquals("abcdef012345", shortRepoDigest("sha256:abcdef012345" + "f".repeat(52)))
        assertEquals("abcdef012345", shortRepoDigest("abcdef0123456789"))
    }
}
