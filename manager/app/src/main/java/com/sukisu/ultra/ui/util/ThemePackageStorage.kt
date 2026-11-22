package com.sukisu.ultra.ui.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.sukisu.ultra.data.repository.ThemeStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Appearance fields shared by both halves of an adaptive theme. A variant is applied as a whole
 * when the system switches between light and dark mode.
 */
@Serializable
data class ThemeVariantData(
    val themeMode: Int? = null,
    val miuixMonet: Boolean? = null,
    val keyColor: Int? = null,
    val colorStyle: String? = null,
    val colorSpec: String? = null,
    val cardBackgroundAlphaPercent: Int? = null,
    val customBackgroundBlurPercent: Int? = null,
    val customBackgroundScrimPercent: Int? = null,
    val hasBackground: Boolean? = null,
    val hasCardBackground: Boolean? = null,
    val hideHomeCardStatusText: Boolean? = null,
    val hideHomeCardDecoration: Boolean? = null,
    val homeCardStickerTransforms: List<HomeCardStickerTransform>? = null,
    val homeLayout: String? = null,
    val homeStatusAlignment: String? = null,
    val homeStatusScale: Float? = null,
    val homePillScale: Float? = null,
    val homeStatusWidthFraction: Float? = null,
    val homeCardImageStyle: String? = null,
    val showHomeCardImageWithoutBackground: Boolean? = null,
    val uiMode: String? = null,
    val enableFloatingBottomBar: Boolean? = null,
    val enableFloatingBottomBarBlur: Boolean? = null,
    val topBarStyle: Int? = null,
    val topBarPillFade: Boolean? = null,
    val enableNavigationBadge: Boolean? = null,
    val enablePredictiveBack: Boolean? = null,
    val enableSwipeDismiss: Boolean? = null,
    val enableBlur: Boolean? = null,
    val enableProgressiveBlur: Boolean? = null,
    val pagerInterceptionMode: Int? = null,
    val pageScale: Float? = null,
    val moduleDescriptionMaxLines: Int? = null,
)

/**
 * Shared dynamic wallpaper settings for both package styles.
 *
 * Dynamic media is deliberately not part of [ThemeVariantData]: the wallpaper is global, so an
 * adaptive theme records one file for both light and dark instead of one per mode. [hasWallpaper]
 * and [hasHomeCard] are tri-state like the static image flags: `true` requires the matching zip
 * entry, `false` explicitly clears it, and `null` leaves the current choice untouched.
 */
@Serializable
data class DynamicWallpaperPackage(
    val hasWallpaper: Boolean? = null,
    val hasHomeCard: Boolean? = null,
    val wallpaperTransform: DynamicWallpaperGeometry? = null,
    val homeCardTransform: DynamicWallpaperGeometry? = null,
)

@Serializable
data class DynamicWallpaperGeometry(
    val fit: Int = 1,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun sanitized(): DynamicWallpaperGeometry = copy(
        fit = fit.coerceIn(0, 2),
        zoom = zoom.coerceIn(1f, 4f),
        offsetX = offsetX.coerceIn(-1f, 1f),
        offsetY = offsetY.coerceIn(-1f, 1f),
    )
}

/**
 * Serializable part of a theme package. Fields are nullable so that packages written by newer
 * builds can still be applied by older ones as long as the known fields are valid.
 *
 * Format v3 adds [adaptive]: when it is set, [light] and [dark] each describe one complete
 * appearance and the package stores the image set for each mode. Older managers reject v3 rather
 * than applying only one half of the theme.
 */
@Serializable
data class ThemePackageData(
    val version: Int = ThemePackageStorage.BASE_VERSION,
    val themeMode: Int? = null,
    val miuixMonet: Boolean? = null,
    val keyColor: Int? = null,
    val colorStyle: String? = null,
    val colorSpec: String? = null,
    val cardBackgroundAlphaPercent: Int? = null,
    val customBackgroundBlurPercent: Int? = null,
    val customBackgroundScrimPercent: Int? = null,
    val hasBackground: Boolean? = null,
    val hasCardBackground: Boolean? = null,
    val hideHomeCardStatusText: Boolean? = null,
    val hideHomeCardDecoration: Boolean? = null,
    val homeCardStickerTransforms: List<HomeCardStickerTransform>? = null,
    val homeLayout: String? = null,
    val homeStatusAlignment: String? = null,
    val homeStatusScale: Float? = null,
    val homePillScale: Float? = null,
    val homeStatusWidthFraction: Float? = null,
    val homeCardImageStyle: String? = null,
    val showHomeCardImageWithoutBackground: Boolean? = null,
    val uiMode: String? = null,
    val appIconId: String? = null,
    val enableFloatingBottomBar: Boolean? = null,
    val enableFloatingBottomBarBlur: Boolean? = null,
    val topBarStyle: Int? = null,
    val topBarPillFade: Boolean? = null,
    val enableNavigationBadge: Boolean? = null,
    val enablePredictiveBack: Boolean? = null,
    val enableSwipeDismiss: Boolean? = null,
    val enableBlur: Boolean? = null,
    val enableProgressiveBlur: Boolean? = null,
    val pagerInterceptionMode: Int? = null,
    val pageScale: Float? = null,
    val moduleDescriptionMaxLines: Int? = null,
    val moduleSortEnabledFirst: Boolean? = null,
    val moduleSortActionFirst: Boolean? = null,
    val moduleSortWebUiFirst: Boolean? = null,
    val moduleSortZygiskFirst: Boolean? = null,
    val moduleSortMetamoduleFirst: Boolean? = null,
    val moduleSortNameOrder: String? = null,
    val moduleSortManual: Boolean? = null,
    val customHomeTitle: String? = null,
    val customTexts: Map<String, String>? = null,
    /**
     * Bottom bar icon slots that carry a custom image. The images themselves live in
     * `bottom_bar_icon_<slot>` entries. Global for both adaptive and plain packages.
     */
    val bottomBarIcons: List<String>? = null,
    /**
     * Static colour / image-effect overlay. Top-level for both adaptive and plain packages:
     * the overlay itself carries the light/dark split. Added in package format v4.
     */
    val appearance: AppearanceOverlayData? = null,
    /**
     * Dynamic wallpaper settings. The media entries are top-level and shared by an adaptive
     * package's two halves. Carrying them requires format v6; older managers reject the package.
     */
    val dynamic: DynamicWallpaperPackage? = null,

    val adaptive: Boolean? = null,
    val light: ThemeVariantData? = null,
    val dark: ThemeVariantData? = null,
)


data class ImportedThemeVariant(
    val data: ThemeVariantData,
    val backgroundPath: String? = null,
    val cardBackgroundPath: String? = null,
    val stickerPaths: List<String> = emptyList(),
)

/**
 * Parsed theme package plus paths to extracted temporary image files.
 * Callers are responsible for deleting or promoting these temporary files.
 */
data class ImportedTheme(
    val data: ThemePackageData,
    val backgroundPath: String? = null,
    val cardBackgroundPath: String? = null,
    val stickerPaths: List<String> = emptyList(),
    val bottomBarIconPaths: Map<String, String> = emptyMap(),
    val dynamicWallpaperPath: String? = null,
    val dynamicHomeCardPath: String? = null,
    val light: ImportedThemeVariant? = null,
    val dark: ImportedThemeVariant? = null,
) {
    val isAdaptive: Boolean
        get() = data.adaptive == true && light != null && dark != null


    fun temporaryFiles(): List<String> = buildList {
        backgroundPath?.let(::add)
        cardBackgroundPath?.let(::add)
        dynamicWallpaperPath?.let(::add)
        dynamicHomeCardPath?.let(::add)
        addAll(stickerPaths)
        addAll(bottomBarIconPaths.values)
        light?.let { variant ->
            variant.backgroundPath?.let(::add)
            variant.cardBackgroundPath?.let(::add)
            addAll(variant.stickerPaths)
        }
        dark?.let { variant ->
            variant.backgroundPath?.let(::add)
            variant.cardBackgroundPath?.let(::add)
            addAll(variant.stickerPaths)
        }
    }
}


data class ThemePackageVariantExport(
    val data: ThemeVariantData,
    val backgroundFile: File? = null,
    val cardBackgroundFile: File? = null,
    val cardStickerFiles: List<File> = emptyList(),
)

/** What had to be left out to keep the package under the shared size ceiling. */
data class ThemePackageExportReport(
    val skippedDynamicWallpaper: Boolean = false,
    val skippedDynamicHomeCard: Boolean = false,
) {
    val skippedDynamic: Boolean
        get() = skippedDynamicWallpaper || skippedDynamicHomeCard
}


data class ThemePackageAdaptiveExport(
    val light: ThemePackageVariantExport,
    val dark: ThemePackageVariantExport,
)

object ThemePackageStorage {
    
    const val BASE_VERSION = 2
    const val ADAPTIVE_VERSION = 3
    /** Adds the optional static [ThemePackageData.appearance] overlay. */
    const val APPEARANCE_VERSION = 4
    /** Adds the immersive home layout, pill/status sizing, banner image position and bottom bar icons. */
    const val HOME_STATUS_VERSION = 5
    /**
     * Adds the shared dynamic wallpaper/card media (`dynamic_wallpaper`, `dynamic_home_card`)
     * and their fit/zoom/offset geometry.
     */
    const val DYNAMIC_VERSION = 6

    /**
     * v6 is the first format that can carry dynamic media. Older managers reject a v6 package
     * as a whole instead of applying everything except the wallpaper, as with any newer format.
     */
    const val CURRENT_VERSION = DYNAMIC_VERSION
    const val MIN_SUPPORTED_VERSION = 1
    const val THEME_MIME_TYPE = "application/x-sukisu-theme"

    
    const val ADAPTIVE_TAG = "adaptive"

    fun isSupportedVersion(version: Int): Boolean =
        version in MIN_SUPPORTED_VERSION..CURRENT_VERSION

    private const val THEME_ENTRY = "theme.json"
    private const val BACKGROUND_ENTRY = "background"
    private const val CARD_BACKGROUND_ENTRY = "card_background"
    private const val DYNAMIC_WALLPAPER_ENTRY = "dynamic_wallpaper"
    private const val DYNAMIC_HOME_CARD_ENTRY = "dynamic_home_card"
    private const val CARD_STICKER_ENTRY_PREFIX = "card_sticker_"
    private const val BACKGROUND_LIGHT_ENTRY = "background_light"
    private const val BACKGROUND_DARK_ENTRY = "background_dark"
    private const val CARD_BACKGROUND_LIGHT_ENTRY = "card_background_light"
    private const val CARD_BACKGROUND_DARK_ENTRY = "card_background_dark"
    private const val CARD_STICKER_LIGHT_PREFIX = "card_sticker_light_"
    private const val CARD_STICKER_DARK_PREFIX = "card_sticker_dark_"
    private const val BOTTOM_BAR_ICON_ENTRY_PREFIX = "bottom_bar_icon_"
    private const val MAX_JSON_BYTES = 64L * 1024
    private const val MAX_BACKGROUND_BYTES = 64L * 1024 * 1024
    /** Dynamic media must be able to use the whole package budget on its own. */
    private const val MAX_DYNAMIC_ENTRY_BYTES = ThemeStoreRepository.MAX_PACKAGE_BYTES
    /** Keep a small reserve for zip headers, the GCM tag and rounding. */
    private const val PACKAGE_SIZE_RESERVE = 2L * 1024 * 1024
    private const val PACKAGE_ENTRY_OVERHEAD = 192L
    
    private const val MAX_EXTRACTED_IMAGE_BYTES = 256L * 1024 * 1024
    
    private const val STREAM_BUFFER_SIZE = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Keep exported theme.json compact and avoid writing explicit nulls that older/third
        // party validators may not expect. Missing nullable fields decode as null.
        explicitNulls = false
    }

    fun packageFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "SukiSU-theme-$stamp.sukisu"
    }

    suspend fun export(
        context: Context,
        uri: Uri,
        data: ThemePackageData,
        backgroundFile: File?,
        cardBackgroundFile: File? = null,
        cardStickerFiles: List<File> = emptyList(),
        adaptive: ThemePackageAdaptiveExport? = null,
        bottomBarIconFiles: Map<String, File> = emptyMap(),
        dynamicWallpaperFile: File? = null,
        dynamicHomeCardFile: File? = null,
    ): Result<ThemePackageExportReport> = withContext(Dispatchers.IO) {
        runCatching {
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("Cannot open the destination file")
            BufferedOutputStream(output).use {
                writeEncryptedZip(
                    it,
                    data,
                    backgroundFile,
                    cardBackgroundFile,
                    cardStickerFiles,
                    adaptive,
                    bottomBarIconFiles,
                    dynamicWallpaperFile,
                    dynamicHomeCardFile,
                )
            }
        }
    }


    suspend fun exportToFile(
        file: File,
        data: ThemePackageData,
        backgroundFile: File?,
        cardBackgroundFile: File? = null,
        cardStickerFiles: List<File> = emptyList(),
        adaptive: ThemePackageAdaptiveExport? = null,
        bottomBarIconFiles: Map<String, File> = emptyMap(),
        dynamicWallpaperFile: File? = null,
        dynamicHomeCardFile: File? = null,
    ): Result<ThemePackageExportReport> = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            BufferedOutputStream(file.outputStream()).use {
                writeEncryptedZip(
                    it,
                    data,
                    backgroundFile,
                    cardBackgroundFile,
                    cardStickerFiles,
                    adaptive,
                    bottomBarIconFiles,
                    dynamicWallpaperFile,
                    dynamicHomeCardFile,
                )
            }
        }
    }

    private fun writeEncryptedZip(
        output: OutputStream,
        data: ThemePackageData,
        backgroundFile: File?,
        cardBackgroundFile: File?,
        cardStickerFiles: List<File>,
        adaptive: ThemePackageAdaptiveExport?,
        bottomBarIconFiles: Map<String, File> = emptyMap(),
        dynamicWallpaperFile: File? = null,
        dynamicHomeCardFile: File? = null,
    ): ThemePackageExportReport {
        val usesImmersiveHome = data.homeLayout == HomeLayout.IMMERSIVE.value ||
            adaptive?.light?.data?.homeLayout == HomeLayout.IMMERSIVE.value ||
            adaptive?.dark?.data?.homeLayout == HomeLayout.IMMERSIVE.value
        // Custom bottom bar icons require v5 and dynamic media requires v6: managers that cannot
        // read a feature reject the package instead of applying everything except the new parts.
        val usesBottomBarIcons = bottomBarIconFiles.isNotEmpty()
        val packageData = if (adaptive != null) {
            validateAdaptiveExport(adaptive)
            data.copy(
                adaptive = true,
                light = adaptive.light.data,
                dark = adaptive.dark.data,
                // Image presence lives on the variants; keep the top-level flags out of the way.
                hasBackground = null,
                hasCardBackground = null,
                homeCardStickerTransforms = null,
                homeLayout = null,
                homeStatusAlignment = null,
                homeStatusScale = null,
                homePillScale = null,
                homeStatusWidthFraction = null,
                homeCardImageStyle = null,
                showHomeCardImageWithoutBackground = null,
            )
        } else {
            data.copy(
                adaptive = false,
                light = null,
                dark = null,
            )
        }

        val requestedDynamic = packageData.dynamic
        var dynamicWallpaper = dynamicWallpaperFile?.takeIf {
            it.isFile && requestedDynamic?.hasWallpaper == true
        }
        var dynamicHomeCard = dynamicHomeCardFile?.takeIf {
            it.isFile && requestedDynamic?.hasHomeCard == true
        }
        var skippedWallpaper = false
        var skippedHomeCard = false

        fun existingSize(file: File?): Long = file?.takeIf { it.isFile }?.length() ?: 0L

        fun dynamicPackage(): DynamicWallpaperPackage? {
            val settings = requestedDynamic ?: return null
            val wallpaperPresent = dynamicWallpaper != null
            val cardPresent = dynamicHomeCard != null
            return settings.copy(
                hasWallpaper = when (settings.hasWallpaper) {
                    true -> wallpaperPresent
                    else -> settings.hasWallpaper
                },
                hasHomeCard = when (settings.hasHomeCard) {
                    true -> cardPresent
                    else -> settings.hasHomeCard
                },
                wallpaperTransform = settings.wallpaperTransform?.sanitized()
                    ?.takeIf { wallpaperPresent },
                homeCardTransform = settings.homeCardTransform?.sanitized()
                    ?.takeIf { cardPresent },
            )
        }

        /**
         * The version follows the media that actually made it into the package. If a dynamic
         * file was dropped to satisfy the size ceiling, the archive no longer needs v6 and can
         * stay readable by managers that predate the dynamic entries.
         */
        fun effectiveVersion(hasDynamicMedia: Boolean): Int {
            val usesV5 = usesImmersiveHome || usesBottomBarIcons
            return if (adaptive != null) {
                when {
                    hasDynamicMedia -> DYNAMIC_VERSION
                    usesV5 -> HOME_STATUS_VERSION
                    data.appearance != null -> APPEARANCE_VERSION
                    else -> ADAPTIVE_VERSION
                }
            } else {
                when {
                    hasDynamicMedia -> DYNAMIC_VERSION
                    usesV5 -> HOME_STATUS_VERSION
                    data.appearance != null -> APPEARANCE_VERSION
                    data.version >= ADAPTIVE_VERSION -> BASE_VERSION
                    else -> data.version
                }
            }
        }

        fun encodePackage(): ByteArray = json.encodeToString(
            ThemePackageData.serializer(),
            packageData.copy(
                version = effectiveVersion(dynamicWallpaper != null || dynamicHomeCard != null),
                dynamic = dynamicPackage(),
            ),
        ).toByteArray(Charsets.UTF_8)

        val staticBytes = if (adaptive != null) {
            listOf(adaptive.light, adaptive.dark).sumOf { variant ->
                (if (variant.data.hasBackground == true) {
                    existingSize(variant.backgroundFile)
                } else {
                    0L
                }) +
                    (if (variant.data.hasCardBackground == true) {
                        existingSize(variant.cardBackgroundFile)
                    } else {
                        0L
                    }) +
                    variant.cardStickerFiles.sumOf { existingSize(it) }
            }
        } else {
            (if (data.hasBackground == true) existingSize(backgroundFile) else 0L) +
                (if (data.hasCardBackground == true) existingSize(cardBackgroundFile) else 0L) +
                cardStickerFiles.sumOf { existingSize(it) }
        } + bottomBarIconFiles.values.sumOf { existingSize(it) }
        val staticEntryCount = 4L + cardStickerFiles.size + bottomBarIconFiles.size +
            (adaptive?.let {
                2L + it.light.cardStickerFiles.size + it.dark.cardStickerFiles.size
            } ?: 0L)
        // Headers, the deflated theme.json and a spare margin; the exact figure is verified by the
        // hard limiter on the encrypted stream below.
        val fixedOverhead = PACKAGE_ENTRY_OVERHEAD * (staticEntryCount + 2L) + 8L * 1024L
        val limit = ThemeStoreRepository.MAX_PACKAGE_BYTES - PACKAGE_SIZE_RESERVE

        if (fixedOverhead + staticBytes > limit) error("Theme package is too large")

        var themeJson = encodePackage()
        var totalBytes = fixedOverhead + staticBytes + themeJson.size +
            existingSize(dynamicWallpaper) + existingSize(dynamicHomeCard)
        while (totalBytes > limit && (dynamicWallpaper != null || dynamicHomeCard != null)) {
            val currentWallpaper = dynamicWallpaper
            val currentCard = dynamicHomeCard
            if (currentWallpaper != null &&
                (currentCard == null || currentWallpaper.length() >= currentCard.length())
            ) {
                skippedWallpaper = true
                dynamicWallpaper = null
            } else {
                skippedHomeCard = true
                dynamicHomeCard = null
            }
            themeJson = encodePackage()
            totalBytes = fixedOverhead + staticBytes + themeJson.size +
                existingSize(dynamicWallpaper) + existingSize(dynamicHomeCard)
        }
        if (totalBytes > limit) error("Theme package is too large")

        val encrypted = ThemePackageCrypto.encryptingStream(
            SizeLimitedOutputStream(output, ThemeStoreRepository.MAX_PACKAGE_BYTES),
        )
        ZipOutputStream(encrypted).use { zip ->
            // Pack every entry at the maximum level. The media is already entropy-coded, so the
            // gain is small, but deflating is lossless: the unpacked bytes, and therefore the
            // wallpaper quality, stay exactly the same.
            zip.setLevel(Deflater.BEST_COMPRESSION)
            zip.putNextEntry(ZipEntry(THEME_ENTRY))
            zip.write(themeJson)
            zip.closeEntry()
            if (adaptive != null) {
                writeVariantEntries(zip, adaptive.light, BACKGROUND_LIGHT_ENTRY, CARD_BACKGROUND_LIGHT_ENTRY, CARD_STICKER_LIGHT_PREFIX)
                writeVariantEntries(zip, adaptive.dark, BACKGROUND_DARK_ENTRY, CARD_BACKGROUND_DARK_ENTRY, CARD_STICKER_DARK_PREFIX)
            } else {
                if (data.hasBackground == true && backgroundFile != null && backgroundFile.isFile) {
                    zip.putNextEntry(ZipEntry(BACKGROUND_ENTRY))
                    backgroundFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                if (data.hasCardBackground == true && cardBackgroundFile != null && cardBackgroundFile.isFile) {
                    zip.putNextEntry(ZipEntry(CARD_BACKGROUND_ENTRY))
                    cardBackgroundFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                cardStickerFiles.filter { it.isFile }.forEachIndexed { index, file ->
                    zip.putNextEntry(ZipEntry(CARD_STICKER_ENTRY_PREFIX + index))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            bottomBarIconFiles.entries
                .filter { (slot, file) -> file.isFile && BottomBarIconSlot.fromKey(slot) != null }
                .forEach { (slot, file) ->
                    zip.putNextEntry(ZipEntry(BOTTOM_BAR_ICON_ENTRY_PREFIX + slot))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            dynamicWallpaper?.let { file ->
                zip.putNextEntry(ZipEntry(DYNAMIC_WALLPAPER_ENTRY))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            dynamicHomeCard?.let { file ->
                zip.putNextEntry(ZipEntry(DYNAMIC_HOME_CARD_ENTRY))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return ThemePackageExportReport(
            skippedDynamicWallpaper = skippedWallpaper,
            skippedDynamicHomeCard = skippedHomeCard,
        )
    }

    /** Throws before the encrypted output can grow past the package ceiling. */
    private class SizeLimitedOutputStream(
        private val output: OutputStream,
        private val maxBytes: Long,
    ) : OutputStream() {
        private var written = 0L

        override fun write(b: Int) {
            ensure(1)
            output.write(b)
            written += 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            ensure(len.toLong())
            output.write(b, off, len)
            written += len
        }

        private fun ensure(count: Long) {
            if (written + count > maxBytes) {
                throw IOException("Theme package is too large")
            }
        }

        override fun flush() = output.flush()

        override fun close() = output.close()
    }

    
    private fun validateAdaptiveExport(adaptive: ThemePackageAdaptiveExport) {
        listOf(adaptive.light, adaptive.dark).forEach { variant ->
            if (variant.data.hasBackground == true && variant.backgroundFile?.isFile != true) {
                error("Adaptive background image is missing")
            }
            if (variant.data.hasCardBackground == true && variant.cardBackgroundFile?.isFile != true) {
                error("Adaptive card background image is missing")
            }
        }
    }

    private fun writeVariantEntries(
        zip: ZipOutputStream,
        variant: ThemePackageVariantExport,
        backgroundEntry: String,
        cardBackgroundEntry: String,
        stickerPrefix: String,
    ) {
        if (variant.data.hasBackground == true &&
            variant.backgroundFile != null &&
            variant.backgroundFile.isFile
        ) {
            zip.putNextEntry(ZipEntry(backgroundEntry))
            variant.backgroundFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        if (variant.data.hasCardBackground == true &&
            variant.cardBackgroundFile != null &&
            variant.cardBackgroundFile.isFile
        ) {
            zip.putNextEntry(ZipEntry(cardBackgroundEntry))
            variant.cardBackgroundFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        variant.cardStickerFiles.filter { it.isFile }.forEachIndexed { index, file ->
            zip.putNextEntry(ZipEntry(stickerPrefix + index))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    suspend fun import(context: Context, uri: Uri): Result<ImportedTheme> = withContext(Dispatchers.IO) {
        runCatching {
            withDecryptedZip(context, uri) { zip -> parseThemeZip(context, zip) }
        }
    }

    /**
     * Reads `theme.json` without extracting the images. Used by the store upload form to show
     * whether the selected package is adaptive before the user submits it.
     */
    suspend fun inspect(context: Context, uri: Uri): Result<ThemePackageData> = withContext(Dispatchers.IO) {
        runCatching {
            withDecryptedZip(context, uri) { zip -> readThemeJson(zip) }
        }
    }

    /**
     * Decrypts and reads the ZIP in one streaming pass. The previous implementation wrote a
     * full decrypted copy and then read it again, doubling the I/O for large packages.
     * Closing the ZIP closes the cipher stream, which drains the remainder so the GCM tag
     * is still verified.
     */
    private fun <T> withDecryptedZip(
        context: Context,
        uri: Uri,
        block: (ZipInputStream) -> T,
    ): T {
        val raw = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open the theme package")
        raw.use { input ->
            val limited = LimitedInputStream(input, ThemeStoreRepository.MAX_PACKAGE_BYTES)
            ThemePackageCrypto.decryptingStream(limited).use { decrypted ->
                BufferedInputStream(decrypted, STREAM_BUFFER_SIZE).use { buffered ->
                    ZipInputStream(buffered).use { zip ->
                        return block(zip)
                    }
                }
            }
        }
    }

    private fun readThemeJson(zip: ZipInputStream): ThemePackageData {
        var themeText: String? = null
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            if (entry.name == THEME_ENTRY) {
                themeText = readLimited(zip, MAX_JSON_BYTES).toString(Charsets.UTF_8)
                break
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        val text = themeText ?: error("theme.json is missing")
        val data = json.decodeFromString(ThemePackageData.serializer(), text)
        validateVersionedData(data)
        return data
    }

    /**
     * Older managers reject newer packages instead of applying only part of the theme. Keep that
     * rule for the overlay: a manager that cannot read it must not silently render default
     * colours over the author's background.
     */
    private fun validateVersionedData(data: ThemePackageData) {
        if (!isSupportedVersion(data.version)) error("Unsupported theme package version")
        if (data.adaptive == true && data.version < ADAPTIVE_VERSION) {
            error("Adaptive theme packages require format v$ADAPTIVE_VERSION")
        }
        if (data.appearance != null && data.version < APPEARANCE_VERSION) {
            error("Appearance overlays require format v$APPEARANCE_VERSION")
        }
        val usesImmersiveHome = data.homeLayout == HomeLayout.IMMERSIVE.value ||
            data.light?.homeLayout == HomeLayout.IMMERSIVE.value ||
            data.dark?.homeLayout == HomeLayout.IMMERSIVE.value
        if (usesImmersiveHome && data.version < HOME_STATUS_VERSION) {
            error("Immersive home layouts require format v$HOME_STATUS_VERSION")
        }
        val usesDynamic = data.dynamic?.let {
            it.hasWallpaper == true || it.hasHomeCard == true
        } == true
        if (usesDynamic && data.version < DYNAMIC_VERSION) {
            error("Dynamic wallpapers require format v$DYNAMIC_VERSION")
        }
        data.appearance?.let { appearance ->
            val appearanceJson = json.encodeToString(
                AppearanceOverlayData.serializer(),
                appearance,
            )
            AppearanceOverlayStore.parse(appearanceJson).getOrElse { failure ->
                error("Invalid appearance overlay: ${failure.message}")
            }
        }
    }

    private fun parseThemeZip(context: Context, zip: ZipInputStream): ImportedTheme {
        var tempBackground: File? = null
        var tempCardBackground: File? = null
        var tempDynamicWallpaper: File? = null
        var tempDynamicHomeCard: File? = null
        val tempStickers = mutableMapOf<Int, File>()
        var lightBackground: File? = null
        var lightCardBackground: File? = null
        var darkBackground: File? = null
        var darkCardBackground: File? = null
        val lightStickers = mutableMapOf<Int, File>()
        val darkStickers = mutableMapOf<Int, File>()
        val bottomBarIconPaths = mutableMapOf<String, String>()
        var extractedImageBytes = 0L

        fun cleanup() {
            tempBackground?.delete()
            tempCardBackground?.delete()
            tempDynamicWallpaper?.delete()
            tempDynamicHomeCard?.delete()
            tempStickers.forEach { (_, file) -> file.delete() }
            lightBackground?.delete()
            lightCardBackground?.delete()
            darkBackground?.delete()
            darkCardBackground?.delete()
            lightStickers.forEach { (_, file) -> file.delete() }
            darkStickers.forEach { (_, file) -> file.delete() }
            bottomBarIconPaths.values.forEach { runCatching { File(it).delete() } }
        }

        fun copyExtractedImage(input: InputStream, output: OutputStream) {
            val remaining = MAX_EXTRACTED_IMAGE_BYTES - extractedImageBytes
            if (remaining <= 0L) error("Theme package images are too large")
            extractedImageBytes += copyLimited(
                input = input,
                output = output,
                maxBytes = minOf(MAX_BACKGROUND_BYTES, remaining),
            )
        }

        fun newTemp(prefix: String): File =
            File.createTempFile(prefix, ".tmp", context.cacheDir)

        fun readImageEntry(zip: ZipInputStream, prefix: String): File {
            val file = newTemp(prefix)
            file.outputStream().use { copyExtractedImage(zip, it) }
            return file
        }

        fun copyExtractedMedia(input: InputStream, output: OutputStream) {
            val remaining = MAX_EXTRACTED_IMAGE_BYTES - extractedImageBytes
            if (remaining <= 0L) error("Theme package media is too large")
            extractedImageBytes += copyLimited(
                input = input,
                output = output,
                maxBytes = minOf(MAX_DYNAMIC_ENTRY_BYTES, remaining),
            )
        }

        fun readDynamicEntry(zip: ZipInputStream, prefix: String): File {
            val file = newTemp(prefix)
            file.outputStream().use { copyExtractedMedia(zip, it) }
            return file
        }

        fun readStickerEntry(
            zip: ZipInputStream,
            prefix: String,
            target: MutableMap<Int, File>,
        ) {
            val index = prefix.substringAfterLast('_').toIntOrNull()
            if (index != null && index in 0 until HomeCardSticker.MAX_COUNT) {
                if (target.containsKey(index)) error("Duplicate card sticker entry")
                target[index] = readImageEntry(zip, "theme_import_sticker_")
            }
        }

        try {
            var themeText: String? = null
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == THEME_ENTRY -> themeText =
                        readLimited(zip, MAX_JSON_BYTES).toString(Charsets.UTF_8)

                    name == BACKGROUND_ENTRY -> {
                        if (tempBackground != null) error("Duplicate background entry")
                        tempBackground = readImageEntry(zip, "theme_import_bg_")
                    }

                    name == CARD_BACKGROUND_ENTRY -> {
                        if (tempCardBackground != null) {
                            error("Duplicate card background entry")
                        }
                        tempCardBackground = readImageEntry(zip, "theme_import_card_")
                    }

                    name == DYNAMIC_WALLPAPER_ENTRY -> {
                        if (tempDynamicWallpaper != null) {
                            error("Duplicate dynamic wallpaper entry")
                        }
                        tempDynamicWallpaper = readDynamicEntry(zip, "theme_import_dynamic_")
                    }

                    name == DYNAMIC_HOME_CARD_ENTRY -> {
                        if (tempDynamicHomeCard != null) {
                            error("Duplicate dynamic card entry")
                        }
                        tempDynamicHomeCard = readDynamicEntry(zip, "theme_import_dynamic_card_")
                    }

                    name == BACKGROUND_LIGHT_ENTRY -> {
                        if (lightBackground != null) error("Duplicate light background entry")
                        lightBackground = readImageEntry(zip, "theme_import_bg_light_")
                    }

                    name == BACKGROUND_DARK_ENTRY -> {
                        if (darkBackground != null) error("Duplicate dark background entry")
                        darkBackground = readImageEntry(zip, "theme_import_bg_dark_")
                    }

                    name == CARD_BACKGROUND_LIGHT_ENTRY -> {
                        if (lightCardBackground != null) {
                            error("Duplicate light card background entry")
                        }
                        lightCardBackground = readImageEntry(zip, "theme_import_card_light_")
                    }

                    name == CARD_BACKGROUND_DARK_ENTRY -> {
                        if (darkCardBackground != null) {
                            error("Duplicate dark card background entry")
                        }
                        darkCardBackground = readImageEntry(zip, "theme_import_card_dark_")
                    }

                    name.startsWith(BOTTOM_BAR_ICON_ENTRY_PREFIX) -> {
                        val slot = name.removePrefix(BOTTOM_BAR_ICON_ENTRY_PREFIX)
                        if (BottomBarIconSlot.fromKey(slot) != null) {
                            if (bottomBarIconPaths.containsKey(slot)) {
                                error("Duplicate bottom bar icon entry")
                            }
                            bottomBarIconPaths[slot] =
                                readImageEntry(zip, "theme_import_bottom_bar_").absolutePath
                        }
                    }

                    name.startsWith(CARD_STICKER_LIGHT_PREFIX) -> readStickerEntry(
                        zip,
                        name,
                        lightStickers,
                    )

                    name.startsWith(CARD_STICKER_DARK_PREFIX) -> readStickerEntry(
                        zip,
                        name,
                        darkStickers,
                    )

                    name.startsWith(CARD_STICKER_ENTRY_PREFIX) -> {
                        val index = name.removePrefix(CARD_STICKER_ENTRY_PREFIX).toIntOrNull()
                        if (index != null && index in 0 until HomeCardSticker.MAX_COUNT) {
                            if (tempStickers.containsKey(index)) {
                                error("Duplicate card sticker entry")
                            }
                            tempStickers[index] = readImageEntry(
                                zip,
                                "theme_import_sticker_",
                            )
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            val text = themeText ?: error("theme.json is missing")
            val data = json.decodeFromString(ThemePackageData.serializer(), text)
            validateVersionedData(data)
            data.bottomBarIcons?.forEach { slot ->
                if (BottomBarIconSlot.fromKey(slot) != null &&
                    !bottomBarIconPaths.containsKey(slot)
                ) {
                    error("Bottom bar icon entry is missing")
                }
            }

            // Dynamic media is shared by both adaptive halves, so it is resolved before the
            // adaptive branch and kept out of the legacy image cleanup below.
            val resolvedDynamicWallpaper = tempDynamicWallpaper
            val hasDynamicWallpaper = data.dynamic?.hasWallpaper ?: (resolvedDynamicWallpaper != null)
            if (hasDynamicWallpaper && resolvedDynamicWallpaper == null) {
                error("Dynamic wallpaper entry is missing")
            }
            if (resolvedDynamicWallpaper != null &&
                ImageStorage.dynamicWallpaperKindOf(resolvedDynamicWallpaper) == null
            ) {
                error("Dynamic wallpaper is invalid")
            }
            if (!hasDynamicWallpaper) {
                resolvedDynamicWallpaper?.delete()
                tempDynamicWallpaper = null
            }
            val resolvedDynamicHomeCard = tempDynamicHomeCard
            val hasDynamicHomeCard = data.dynamic?.hasHomeCard ?: (resolvedDynamicHomeCard != null)
            if (hasDynamicHomeCard && resolvedDynamicHomeCard == null) {
                error("Dynamic card background entry is missing")
            }
            if (resolvedDynamicHomeCard != null &&
                ImageStorage.dynamicWallpaperKindOf(resolvedDynamicHomeCard) == null
            ) {
                error("Dynamic card background is invalid")
            }
            if (!hasDynamicHomeCard) {
                resolvedDynamicHomeCard?.delete()
                tempDynamicHomeCard = null
            }
            val dynamicWallpaperPath = resolvedDynamicWallpaper
                ?.takeIf { hasDynamicWallpaper }
                ?.absolutePath
            val dynamicHomeCardPath = resolvedDynamicHomeCard
                ?.takeIf { hasDynamicHomeCard }
                ?.absolutePath

            if (data.adaptive == true) {
                val lightData = data.light ?: error("Light variant is missing")
                val darkData = data.dark ?: error("Dark variant is missing")
                val light = resolveVariant(
                    label = "Light",
                    data = lightData,
                    background = lightBackground,
                    cardBackground = lightCardBackground,
                    stickers = lightStickers,
                )
                val dark = resolveVariant(
                    label = "Dark",
                    data = darkData,
                    background = darkBackground,
                    cardBackground = darkCardBackground,
                    stickers = darkStickers,
                )
                // Adaptive packages do not use the legacy single image set.
                tempBackground?.delete()
                tempBackground = null
                tempCardBackground?.delete()
                tempCardBackground = null
                tempStickers.forEach { (_, file) -> file.delete() }
                tempStickers.clear()
                bottomBarIconPaths.values.forEach { path ->
                    validateImage(File(path), "Bottom bar icon is invalid")
                }
                return ImportedTheme(
                    data = data.copy(
                        light = light.data,
                        dark = dark.data,
                        hasBackground = null,
                        hasCardBackground = null,
                        homeCardStickerTransforms = null,
                    ),
                    bottomBarIconPaths = bottomBarIconPaths.toMap(),
                    dynamicWallpaperPath = dynamicWallpaperPath,
                    dynamicHomeCardPath = dynamicHomeCardPath,
                    light = light,
                    dark = dark,
                )
            }

            // v1/v2 package, or a v3 package that does not use the adaptive fields.
            lightBackground?.delete()
            lightBackground = null
            darkBackground?.delete()
            darkBackground = null
            lightCardBackground?.delete()
            lightCardBackground = null
            darkCardBackground?.delete()
            darkCardBackground = null
            lightStickers.forEach { (_, file) -> file.delete() }
            lightStickers.clear()
            darkStickers.forEach { (_, file) -> file.delete() }
            darkStickers.clear()

            val hasBackground = data.hasBackground ?: (tempBackground != null)
            if (hasBackground && tempBackground == null) error("Background image is missing")
            if (hasBackground && tempBackground != null) {
                validateImage(tempBackground, "Background image is invalid")
            }

            val hasCardBackground = data.hasCardBackground ?: (tempCardBackground != null)
            if (hasCardBackground && tempCardBackground == null) {
                error("Card background image is missing")
            }
            if (hasCardBackground && tempCardBackground != null) {
                validateImage(tempCardBackground, "Card background image is invalid")
            }

            tempStickers.forEach { (_, sticker) ->
                validateImage(sticker, "Card sticker image is invalid")
            }

            bottomBarIconPaths.values.forEach { path ->
                validateImage(File(path), "Bottom bar icon is invalid")
            }

            return ImportedTheme(
                data = data,
                backgroundPath = tempBackground?.takeIf { hasBackground }?.absolutePath,
                cardBackgroundPath = tempCardBackground?.takeIf { hasCardBackground }?.absolutePath,
                dynamicWallpaperPath = dynamicWallpaperPath,
                dynamicHomeCardPath = dynamicHomeCardPath,
                stickerPaths = stickerPaths(tempStickers),
                bottomBarIconPaths = bottomBarIconPaths.toMap(),
            )
        } catch (e: Exception) {
            cleanup()
            throw e
        }
    }

    /**
     * Validates one adaptive half and resolves `null` image flags from the extracted entries, so
     * the applied variant always knows whether it owns a background or clears it.
     */
    private fun resolveVariant(
        label: String,
        data: ThemeVariantData,
        background: File?,
        cardBackground: File?,
        stickers: MutableMap<Int, File>,
    ): ImportedThemeVariant {
        val hasBackground = data.hasBackground ?: (background != null)
        if (hasBackground && background == null) error("$label background image is missing")
        if (hasBackground && background != null) {
            validateImage(background, "$label background image is invalid")
        }
        val hasCardBackground = data.hasCardBackground ?: (cardBackground != null)
        if (hasCardBackground && cardBackground == null) {
            error("$label card background image is missing")
        }
        if (hasCardBackground && cardBackground != null) {
            validateImage(cardBackground, "$label card background image is invalid")
        }
        stickers.values.forEach { sticker ->
            validateImage(sticker, "$label card sticker image is invalid")
        }

        return ImportedThemeVariant(
            data = data.copy(
                hasBackground = hasBackground,
                hasCardBackground = hasCardBackground,
            ),
            backgroundPath = background?.takeIf { hasBackground }?.absolutePath,
            cardBackgroundPath = cardBackground?.takeIf { hasCardBackground }?.absolutePath,
            stickerPaths = stickerPaths(stickers),
        )
    }

    private fun stickerPaths(stickers: Map<Int, File>): List<String> =
        stickers.entries.sortedBy { it.key }.map { it.value.absolutePath }

    private fun validateImage(file: File, message: String) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error(message)
    }

    /**
     * Prevents encrypted packages above the shared 128 MB ceiling from being streamed.
     * One extra byte may be read so an exact-limit file can still reach EOF normally.
     */
    private class LimitedInputStream(
        private val input: InputStream,
        private val maxBytes: Long,
    ) : InputStream() {
        private var readBytes = 0L

        override fun read(): Int {
            if (readBytes >= maxBytes) {
                val extra = input.read()
                if (extra >= 0) throw IOException("Theme package is too large")
                return -1
            }
            val value = input.read()
            if (value >= 0) readBytes++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (readBytes == maxBytes) {
                val extra = input.read()
                if (extra >= 0) throw IOException("Theme package is too large")
                return -1
            }
            val allowed = minOf(length.toLong(), maxBytes - readBytes + 1L).toInt()
            val count = input.read(buffer, offset, allowed)
            if (count > 0) {
                readBytes += count
                if (readBytes > maxBytes) throw IOException("Theme package is too large")
            }
            return count
        }

        override fun close() = input.close()
    }

    
    suspend fun validate(context: Context, uri: Uri): Result<ThemePackageData> = withContext(Dispatchers.IO) {
        import(context, uri).map { imported ->
            imported.temporaryFiles().forEach { path ->
                runCatching { File(path).delete() }
            }
            imported.data
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) error("Theme package entry is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) error("Theme package entry is too large")
            output.write(buffer, 0, read)
        }
        return total
    }
}
