package com.sukisu.ultra.data.repository

import com.sukisu.ultra.ui.util.matchSelfUpdateApk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfUpdateAssetTest {

    @Test
    fun acceptsForkAssetsBeforeAndAfterRename() {
        // 仓库改名前的地址
        assertTrue(
            isAllowedSelfUpdateAsset(
                "https://github.com/yufeng-liang/SukiSU-Ultra/releases/download/custom-repo-backup-41151/SukiSU_mod_41151-release.apk"
            )
        )
        // 改名后 GitHub 实际返回的地址（写死仓库名的旧前缀就是在这里失效的）
        assertTrue(
            isAllowedSelfUpdateAsset(
                "https://github.com/yufeng-liang/TsukiSU/releases/download/custom-repo-backup-41151/SukiSU_mod_41151-release.apk"
            )
        )
    }

    @Test
    fun rejectsOtherOwnersEvenWithValidShape() {
        val bad = listOf(
            "https://github.com/SukiSU-Ultra/SukiSU-Ultra/releases/download/v1/SukiSU_v4_1-release.apk",
            "https://github.com/yufeng-liang-evil/TsukiSU/releases/download/v1/a.apk",
            "https://github.com/YUFENG-LIANG/TsukiSU/releases/download/v1/a.apk".replace("YUFENG-LIANG", "someone"),
        )
        for (url in bad) assertFalse(url, isAllowedSelfUpdateAsset(url))
    }

    @Test
    fun rejectsNonReleaseAndUnsafeUrls() {
        val bad = listOf(
            "https://github.com/yufeng-liang/TsukiSU/archive/refs/tags/v1.zip",
            "https://github.com/yufeng-liang/TsukiSU/releases/latest",
            "http://github.com/yufeng-liang/TsukiSU/releases/download/v1/a.apk",
            "https://github.com/yufeng-liang/TsukiSU/releases/download/v1/a.apk?x=1",
            "https://github.com/yufeng-liang/TsukiSU/releases/download/v1/a.apk#frag",
            "https://user@github.com/yufeng-liang/TsukiSU/releases/download/v1/a.apk",
            "https://gh-proxy.org/https://github.com/yufeng-liang/TsukiSU/releases/download/v1/a.apk",
            "",
        )
        for (url in bad) assertFalse(url, isAllowedSelfUpdateAsset(url))
    }

    @Test
    fun extractsManagerVersionCodeFromAssetName() {
        assertEquals(41553L, matchSelfUpdateApk("SukiSU_mod_41553-release.apk"))
        assertEquals(41151L, matchSelfUpdateApk("SukiSU_custom-repo.backup_41151-release.apk"))
        // 官方命名与随机包名变体都不认，避免把覆盖安装不了的包推给用户
        assertNull(matchSelfUpdateApk("SukiSU_v4.2.1_40994-release.apk"))
        assertNull(matchSelfUpdateApk("SukiSU_mod_41553-release-spoofed.apk"))
        assertNull(matchSelfUpdateApk("android15-6.6-aarch64_kernelsu.ko"))
        assertNull(matchSelfUpdateApk("SukiSU_mod_-release.apk"))
    }
}
