package com.sukisu.ultra.ui.util

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackageExportTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun exportsEncryptedV4PackageWithAppearance() = runBlocking {
        val file = File.createTempFile("sukisu-theme-export", ".sukisu")
        try {
            val data = ThemePackageData(
                version = ThemePackageStorage.APPEARANCE_VERSION,
                appearance = AppearanceOverlayData(
                    colors = AppearanceColorsData(
                        shared = ColorTokensData(textPrimary = "#FFFFFFFF"),
                    ),
                    image = AppearanceImageData(
                        background = ImageEffectsData(sharpen = 30f),
                    ),
                ),
                homeLayout = "classic",
                homeStatusAlignment = "end",
                homeStatusScale = 1.15f,
                homePillScale = 1.2f,
                homeStatusWidthFraction = 0.8f,
                homeCardImageStyle = "banner",
                showHomeCardImageWithoutBackground = true,
            )

            ThemePackageStorage.exportToFile(file = file, data = data, backgroundFile = null)
                .getOrThrow()

            val encrypted = file.readBytes()
            assertTrue(ThemePackageCrypto.hasValidHeader(encrypted))
            val plain = ThemePackageCrypto.decrypt(encrypted)

            ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
                val entry = zip.nextEntry
                assertEquals("theme.json", entry?.name)
                val decoded = json.decodeFromString(
                    ThemePackageData.serializer(),
                    zip.readBytes().toString(Charsets.UTF_8),
                )
                assertEquals(ThemePackageStorage.APPEARANCE_VERSION, decoded.version)
                assertEquals(
                    "#FFFFFFFF",
                    decoded.appearance?.colors?.shared?.textPrimary,
                )
                assertEquals(30f, decoded.appearance?.image?.background?.sharpen ?: 0f)
                assertEquals("classic", decoded.homeLayout)
                assertEquals("end", decoded.homeStatusAlignment)
                assertEquals(1.15f, decoded.homeStatusScale ?: 0f, 0f)
                assertEquals(1.2f, decoded.homePillScale ?: 0f, 0f)
                assertEquals(0.8f, decoded.homeStatusWidthFraction ?: 0f, 0f)
                assertEquals("banner", decoded.homeCardImageStyle)
                assertEquals(true, decoded.showHomeCardImageWithoutBackground)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun exportsV5PackageForImmersiveHome() = runBlocking {
        val file = File.createTempFile("sukisu-theme-export", ".sukisu")
        try {
            val data = ThemePackageData(
                homeLayout = "immersive",
                homeStatusAlignment = "end",
                homeStatusScale = 1.5f,
                homePillScale = 1.2f,
                homeStatusWidthFraction = 0.8f,
                homeCardImageStyle = "banner",
                showHomeCardImageWithoutBackground = true,
            )

            ThemePackageStorage.exportToFile(file = file, data = data, backgroundFile = null)
                .getOrThrow()

            val plain = ThemePackageCrypto.decrypt(file.readBytes())
            ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
                val entry = zip.nextEntry
                assertEquals("theme.json", entry?.name)
                val decoded = json.decodeFromString(
                    ThemePackageData.serializer(),
                    zip.readBytes().toString(Charsets.UTF_8),
                )
                assertEquals(ThemePackageStorage.HOME_STATUS_VERSION, decoded.version)
                assertEquals("immersive", decoded.homeLayout)
                assertEquals("end", decoded.homeStatusAlignment)
                assertEquals(1.5f, decoded.homeStatusScale ?: 0f, 0f)
                assertEquals(1.2f, decoded.homePillScale ?: 0f, 0f)
                assertEquals(0.8f, decoded.homeStatusWidthFraction ?: 0f, 0f)
                assertEquals("banner", decoded.homeCardImageStyle)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun exportsV6PackageWithDynamicWallpaper() = runBlocking {
        val file = File.createTempFile("sukisu-theme-export", ".sukisu")
        val wallpaper = File.createTempFile("dynamic-wallpaper", ".webp")
        try {
            wallpaper.writeBytes(
                byteArrayOf(
                    'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
                    0, 0, 0, 0,
                    'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
                ) + ByteArray(32),
            )
            val data = ThemePackageData(
                dynamic = DynamicWallpaperPackage(
                    hasWallpaper = true,
                    hasHomeCard = false,
                    wallpaperTransform = DynamicWallpaperGeometry(
                        fit = 0,
                        zoom = 1.5f,
                        offsetX = -0.25f,
                        offsetY = 0.1f,
                    ),
                ),
            )

            ThemePackageStorage.exportToFile(
                file = file,
                data = data,
                backgroundFile = null,
                dynamicWallpaperFile = wallpaper,
            ).getOrThrow()

            val plain = ThemePackageCrypto.decrypt(file.readBytes())
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val decoded = json.decodeFromString(
                ThemePackageData.serializer(),
                entries.getValue("theme.json").toString(Charsets.UTF_8),
            )
            assertEquals(ThemePackageStorage.DYNAMIC_VERSION, decoded.version)
            assertEquals(true, decoded.dynamic?.hasWallpaper ?: false)
            assertEquals(false, decoded.dynamic?.hasHomeCard ?: true)
            assertEquals(1.5f, decoded.dynamic?.wallpaperTransform?.zoom ?: 0f, 0f)
            assertTrue(entries.containsKey("dynamic_wallpaper"))
        } finally {
            file.delete()
            wallpaper.delete()
        }
    }
}
