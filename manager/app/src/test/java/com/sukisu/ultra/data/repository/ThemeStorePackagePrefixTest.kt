package com.sukisu.ultra.data.repository

import com.sukisu.ultra.data.model.ThemeStoreCatalog
import com.sukisu.ultra.data.model.ThemeStoreEntry
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeStorePackagePrefixTest {

    @Test
    fun learnsPrefixFromCnbRedirectWithEncodedHyphens() {
        assertEquals(
            "https://cnb.cool/shirkneko/sukisu-theme/-/git/raw/main/",
            ThemeStoreRepository.packagePrefix(
                "https://cnb.cool/shirkneko/sukisu-theme/-/git/raw/main/themes/11281%2D01%2Dv4%2Esukisu",
                "themes/11281-01-v4.sukisu",
            ),
        )
    }

    @Test
    fun mediaRedirectLearnsTheSamePrefix() {
        val learned = ThemeCnbPrefixes.derivePrefix(
            "https://theme.example/previews/11281-01-v4.webp".toHttpUrl(),
            "https://cnb.cool/shirkneko/sukisu-theme/-/git/raw/main/previews/11281%2D01%2Dv4.webp".toHttpUrl(),
        )
        assertEquals(
            "https://theme.example/" to "https://cnb.cool/shirkneko/sukisu-theme/-/git/raw/main/",
            learned,
        )
    }

    @Test
    fun storeMountedUnderAPathKeepsItInTheBase() {
        assertEquals(
            "https://host.example/site/",
            ThemeCnbPrefixes.baseOf("https://host.example/site/screenshots/a-v1.webp".toHttpUrl()),
        )
    }

    @Test
    fun mediaRedirectToAnotherHostIsIgnored() {
        assertNull(
            ThemeCnbPrefixes.derivePrefix(
                "https://theme.example/previews/a.webp".toHttpUrl(),
                "https://mirror.example.com/previews/a.webp".toHttpUrl(),
            ),
        )
    }

    @Test
    fun ignoresNonCnbPackageRedirects() {
        assertNull(
            ThemeStoreRepository.packagePrefix(
                "https://mirror.example.com/themes/a-v1.sukisu",
                "themes/a-v1.sukisu",
            ),
        )
    }

    @Test
    fun ignoresPackagePathThatDoesNotMatchTheRedirect() {
        assertNull(
            ThemeStoreRepository.packagePrefix(
                "https://cnb.cool/o/r/-/git/raw/main/themes/other-v1.sukisu",
                "themes/a-v1.sukisu",
            ),
        )
    }

    @Test
    fun rejectsTraversalInsidePath() {
        assertNull(
            ThemeStoreRepository.packagePrefix(
                "https://cnb.cool/o/r/-/git/raw/main/themes/a-v1.sukisu",
                "../themes/a-v1.sukisu",
            ),
        )
    }

    @Test
    fun unflushedDownloadsMoveBothCounters() {
        val catalog = ThemeStoreCatalog(
            themes = listOf(
                ThemeStoreEntry(id = "a", downloads = 10, heat = 40),
                ThemeStoreEntry(id = "b", downloads = 3, heat = 3),
            ),
        )
        val merged = ThemeStoreRepository.mergeDownloadDeltas(catalog, mapOf("a" to 2, "gone" to 9))
        assertEquals(12, merged.themes.first { it.id == "a" }.downloads)
        assertEquals(42, merged.themes.first { it.id == "a" }.heat)
        assertEquals(3, merged.themes.first { it.id == "b" }.downloads)
        assertEquals(2, merged.themes.size)
    }

    @Test
    fun emptyOrZeroDeltasLeaveTheCatalogAlone() {
        val catalog = ThemeStoreCatalog(themes = listOf(ThemeStoreEntry(id = "a", downloads = 1)))
        assertEquals(catalog, ThemeStoreRepository.mergeDownloadDeltas(catalog, emptyMap()))
        assertEquals(catalog, ThemeStoreRepository.mergeDownloadDeltas(catalog, mapOf("a" to 0)))
    }
}
