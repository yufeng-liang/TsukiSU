@file:Suppress("UNEXHAUSTIVE_WHEN_BASED_ON_JAVA_ANNOTATIONS")

package com.sukisu.ultra.ui.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Process
import android.provider.OpenableColumns
import android.system.Os
import android.util.Base64
import android.util.Log
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

object ImageStorage {
    private const val TAG = "ImageStorage"
    private const val DIR_NAME = "backgrounds"
    private const val CROP_DIR_NAME = "background_crop"
    private const val FILE_NAME = "bg_image"
    private const val SYSTEM_WALLPAPER_FILE_NAME = "system_wallpaper"
    private const val HOME_CARD_PREFIX = "home_card_image"
    private const val HOME_CARD_STICKER_PREFIX = "home_card_sticker"
    private const val BOTTOM_BAR_ICON_PREFIX = "bottom_bar_icon_"
    private const val BOTTOM_BAR_ICON_MAX_PX = 256
    private const val ADAPTIVE_BACKGROUND_LIGHT = "adaptive_bg_light"
    private const val ADAPTIVE_BACKGROUND_DARK = "adaptive_bg_dark"
    private const val ADAPTIVE_CARD_LIGHT_PREFIX = "adaptive_card_light_"
    private const val ADAPTIVE_CARD_DARK_PREFIX = "adaptive_card_dark_"
    private const val ADAPTIVE_STICKER_LIGHT_PREFIX = "adaptive_sticker_light_"
    private const val ADAPTIVE_STICKER_DARK_PREFIX = "adaptive_sticker_dark_"
    private const val ADAPTIVE_DRAFT_BACKGROUND_LIGHT = "adaptive_draft_bg_light"
    private const val ADAPTIVE_DRAFT_BACKGROUND_DARK = "adaptive_draft_bg_dark"
    private const val ADAPTIVE_DRAFT_CARD_LIGHT_PREFIX = "adaptive_draft_card_light_"
    private const val ADAPTIVE_DRAFT_CARD_DARK_PREFIX = "adaptive_draft_card_dark_"
    private const val ADAPTIVE_DRAFT_STICKER_LIGHT_PREFIX = "adaptive_draft_sticker_light_"
    private const val ADAPTIVE_DRAFT_STICKER_DARK_PREFIX = "adaptive_draft_sticker_dark_"
    private const val SYSTEM_WALLPAPER_SIGNATURE_FILE_NAME = "system_wallpaper.sig"
    private const val MAX_SYSTEM_WALLPAPER_PIXELS = 150_000_000L
    private const val MAX_WALLPAPER_VALIDATION_DIMENSION = 1024
    /** Bound the number of root copies attempted when a ROM has many wallpaper files. */
    private const val MAX_WALLPAPER_CANDIDATES = 12
    private const val DYNAMIC_WALLPAPER_PREFIX = "dynamic_wallpaper"
    private const val DYNAMIC_CARD_PREFIX = "dynamic_home_card"
    private const val ADAPTIVE_DRAFT_DYNAMIC_WALLPAPER_PREFIX = "adaptive_draft_dynamic_wallpaper"
    private const val ADAPTIVE_DRAFT_DYNAMIC_HOME_CARD_PREFIX = "adaptive_draft_dynamic_home_card"
    private const val DYNAMIC_WALLPAPER_STAGE_DIR_NAME = "dynamic_wallpaper_preview"
    private const val DYNAMIC_WALLPAPER_COPY_BUFFER = 256 * 1024
    private const val DYNAMIC_WALLPAPER_SNIFF_BYTES = 64

    /**
     * One dynamic wallpaper may not exceed 308 MiB. The file is streamed in and out of app
     * storage, so the limit bounds the disk footprint rather than RAM use.
     */
    const val DYNAMIC_WALLPAPER_MAX_BYTES = 308L * 1024L * 1024L

    private val systemWallpaperMutex = Mutex()


    data class SystemWallpaperResult(
        val path: String?,

        val changed: Boolean,
    )

    /** The two media formats accepted by the WebGL dynamic wallpaper renderer. */
    enum class DynamicWallpaperKind(val extension: String, val mimeType: String) {
        MP4("mp4", "video/mp4"),
        WEBP("webp", "image/webp");

        companion object {
            fun fromFileName(name: String?): DynamicWallpaperKind? =
                when (name?.substringAfterLast('.', "")?.lowercase()) {
                    MP4.extension -> MP4
                    WEBP.extension -> WEBP
                    else -> null
                }
        }
    }

    sealed interface DynamicWallpaperResult {
        data class Ready(
            val path: String,
            val kind: DynamicWallpaperKind,
            val sizeBytes: Long,
            val displayName: String,
        ) : DynamicWallpaperResult

        data object TooLarge : DynamicWallpaperResult
        data object UnsupportedFormat : DynamicWallpaperResult
        data object Failed : DynamicWallpaperResult
    }

    const val CARD_ALPHA_MIN = 10
    const val CARD_ALPHA_MAX = 91
    const val DEFAULT_CARD_ALPHA = 60

    const val BACKGROUND_BLUR_PERCENT_MIN = 0
    const val BACKGROUND_BLUR_PERCENT_MAX = 100
    const val DEFAULT_BACKGROUND_BLUR_PERCENT = 50

    
    const val BACKGROUND_BLUR_MAX_RADIUS_DP = 40f

    /**
     * Full-screen wallpaper blur is a noticeable GPU cost on low-RAM devices, so new installs
     * and "restore defaults" start without it there. Existing preferences are left untouched.
     */
    fun defaultBackgroundBlurPercent(context: Context): Int =
        if ((context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true) {
            0
        } else {
            DEFAULT_BACKGROUND_BLUR_PERCENT
        }

    
    const val BACKGROUND_SCRIM_MIN = -100
    const val BACKGROUND_SCRIM_MAX = 100
    const val DEFAULT_BACKGROUND_SCRIM = -35

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val targetFile = prepareFile(context)
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    /** Resolves the applied dynamic wallpaper, if its file is still present. */
    fun dynamicWallpaperFile(context: Context): File? =
        newestDynamicFile(context, DYNAMIC_WALLPAPER_PREFIX)

    /** Resolves the applied dynamic home-card background, if its file is still present. */
    fun dynamicHomeCardFile(context: Context): File? =
        newestDynamicFile(context, DYNAMIC_CARD_PREFIX)

    private fun newestDynamicFile(context: Context, prefix: String): File? =
        backgroundsDir(context)
            .listFiles { file ->
                file.isFile &&
                    file.name.startsWith("$prefix.") &&
                    DynamicWallpaperKind.fromFileName(file.name) != null
            }
            ?.maxByOrNull { it.lastModified() }

    fun dynamicWallpaperKind(path: String): DynamicWallpaperKind? =
        DynamicWallpaperKind.fromFileName(File(path).name)

    /**
     * Maps an app-private wallpaper file to the relative URL served by the dynamic wallpaper
     * renderer. Only the two directories it exposes are accepted.
     */
    fun dynamicWallpaperAssetPath(context: Context, path: String): String? {
        if (path.isBlank()) return null
        val file = File(path)
        val backgrounds = File(context.filesDir, DIR_NAME)
        val staged = File(context.cacheDir, DYNAMIC_WALLPAPER_STAGE_DIR_NAME)
        return when (file.parentFile) {
            backgrounds -> "dynamic/${file.name}"
            staged -> "dynamic-preview/${file.name}"
            else -> null
        }
    }

    /**
     * Copies a picked MP4/WebP into the preview cache after validating its size and container.
     * The file is only promoted into app storage by [commitStagedDynamicWallpaper], so backing
     * out of the preview leaves the current wallpaper untouched.
     */
    suspend fun stageDynamicWallpaper(context: Context, uri: Uri): DynamicWallpaperResult =
        withContext(Dispatchers.IO) {
            val (displayName, declaredSize) = dynamicWallpaperMetadata(context, uri)
            if (declaredSize != null && declaredSize > DYNAMIC_WALLPAPER_MAX_BYTES) {
                return@withContext DynamicWallpaperResult.TooLarge
            }
            val dir = dynamicWallpaperStageDir(context)
            // A new selection replaces the previous preview; leaving it around would let the
            // cache grow past the one-wallpaper budget.
            dir.listFiles()?.forEach { it.delete() }
            val temp = runCatching { File.createTempFile("stage_", ".tmp", dir) }.getOrNull()
                ?: return@withContext DynamicWallpaperResult.Failed
            val outcome = runCatchingCancellable {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copyDynamicWallpaper(input, temp)
                } ?: CopyOutcome.Failed
            }.getOrDefault(CopyOutcome.Failed)
            if (outcome !is CopyOutcome.Valid) {
                temp.delete()
                return@withContext when (outcome) {
                    CopyOutcome.TooLarge -> DynamicWallpaperResult.TooLarge
                    CopyOutcome.Unsupported -> DynamicWallpaperResult.UnsupportedFormat
                    else -> DynamicWallpaperResult.Failed
                }
            }
            val target = File(dir, "preview_${System.currentTimeMillis()}.${outcome.kind.extension}")
            if (!temp.renameTo(target)) {
                val copied = runCatchingCancellable {
                    temp.inputStream().use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output, DYNAMIC_WALLPAPER_COPY_BUFFER)
                        }
                    }
                    target.length() > 0L
                }.getOrDefault(false)
                temp.delete()
                if (!copied) {
                    target.delete()
                    return@withContext DynamicWallpaperResult.Failed
                }
            }
            DynamicWallpaperResult.Ready(
                path = target.absolutePath,
                kind = outcome.kind,
                sizeBytes = target.length(),
                displayName = displayName ?: target.name,
            )
        }

    /**
     * Promotes a validated preview into the backgrounds directory. The previous file is removed
     * only after the new copy is complete, so an interrupted commit cannot destroy both.
     */
    suspend fun commitStagedDynamicWallpaper(
        context: Context,
        stagedPath: String,
        targetPrefix: String = DYNAMIC_WALLPAPER_PREFIX,
    ): DynamicWallpaperResult = withContext(Dispatchers.IO) {
        val staged = File(stagedPath)
        val kind = DynamicWallpaperKind.fromFileName(staged.name)
            ?: return@withContext DynamicWallpaperResult.UnsupportedFormat
        if (!staged.isFile || staged.length() == 0L) {
            return@withContext DynamicWallpaperResult.Failed
        }
        if (staged.length() > DYNAMIC_WALLPAPER_MAX_BYTES) {
            return@withContext DynamicWallpaperResult.TooLarge
        }
        val dir = backgroundsDir(context)
        val target = File(dir, "$targetPrefix.${kind.extension}")
        val temp = File(dir, "${target.name}.tmp")
        val copied = runCatchingCancellable {
            staged.inputStream().use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output, DYNAMIC_WALLPAPER_COPY_BUFFER)
                }
            }
            temp.length() in 1..DYNAMIC_WALLPAPER_MAX_BYTES
        }.getOrDefault(false)
        if (!copied) {
            temp.delete()
            return@withContext DynamicWallpaperResult.Failed
        }
        // Drop the previous format now. The same-name target is replaced atomically by the
        // rename below, so a renderer reading the old file never observes a missing path.
        dir.listFiles { file ->
            file.name.startsWith("$targetPrefix.") &&
                file != temp &&
                file != target
        }?.forEach { it.delete() }
        if (!temp.renameTo(target)) {
            val moved = runCatchingCancellable {
                if (target.exists()) target.delete()
                temp.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, DYNAMIC_WALLPAPER_COPY_BUFFER)
                    }
                }
                target.length() > 0L
            }.getOrDefault(false)
            temp.delete()
            if (!moved) {
                target.delete()
                return@withContext DynamicWallpaperResult.Failed
            }
        }
        DynamicWallpaperResult.Ready(
            path = target.absolutePath,
            kind = kind,
            sizeBytes = target.length(),
            displayName = target.name,
        )
    }

    suspend fun commitStagedDynamicHomeCard(
        context: Context,
        stagedPath: String,
    ): DynamicWallpaperResult =
        commitStagedDynamicWallpaper(context, stagedPath, DYNAMIC_CARD_PREFIX)

    suspend fun clearDynamicWallpaper(context: Context) = withContext(Dispatchers.IO) {
        backgroundsDir(context)
            .listFiles { file -> file.name.startsWith("$DYNAMIC_WALLPAPER_PREFIX.") }
            ?.forEach { it.delete() }
        dynamicWallpaperStageDir(context).listFiles()?.forEach { it.delete() }
    }

    suspend fun clearDynamicHomeCard(context: Context) = withContext(Dispatchers.IO) {
        backgroundsDir(context)
            .listFiles { file -> file.name.startsWith("$DYNAMIC_CARD_PREFIX.") }
            ?.forEach { it.delete() }
    }

    /** Identifies a dynamic media file from its header bytes instead of its name. */
    fun dynamicWallpaperKindOf(file: File): DynamicWallpaperKind? {
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(DYNAMIC_WALLPAPER_SNIFF_BYTES)
                var length = 0
                while (length < header.size) {
                    val read = input.read(header, length, header.size - length)
                    if (read < 0) break
                    length += read
                }
                if (length <= 0) null else sniffDynamicWallpaperKind(header, length)
            }
        }.getOrNull()
    }

    /**
     * Copies a media file into one of the live dynamic slots, sniffing the container instead of
     * trusting the source name. Used by theme package import and adaptive draft promotion.
     */
    suspend fun adoptDynamicWallpaper(
        context: Context,
        source: File,
        targetPrefix: String = DYNAMIC_WALLPAPER_PREFIX,
    ): DynamicWallpaperResult = withContext(Dispatchers.IO) {
        val kind = dynamicWallpaperKindOf(source)
            ?: return@withContext DynamicWallpaperResult.UnsupportedFormat
        if (source.length() > DYNAMIC_WALLPAPER_MAX_BYTES) {
            return@withContext DynamicWallpaperResult.TooLarge
        }
        val dir = backgroundsDir(context)
        val target = File(dir, "$targetPrefix.${kind.extension}")
        val temp = File(dir, "${target.name}.tmp")
        val copied = runCatchingCancellable {
            source.inputStream().use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output, DYNAMIC_WALLPAPER_COPY_BUFFER)
                }
            }
            temp.length() in 1..DYNAMIC_WALLPAPER_MAX_BYTES
        }.getOrDefault(false)
        if (!copied) {
            temp.delete()
            return@withContext DynamicWallpaperResult.Failed
        }
        dir.listFiles { file ->
            file.name.startsWith("$targetPrefix.") && file != temp && file != target
        }?.forEach { it.delete() }
        if (!temp.renameTo(target)) {
            val moved = runCatchingCancellable {
                if (target.exists()) target.delete()
                temp.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, DYNAMIC_WALLPAPER_COPY_BUFFER)
                    }
                }
                target.length() > 0L
            }.getOrDefault(false)
            temp.delete()
            if (!moved) {
                target.delete()
                return@withContext DynamicWallpaperResult.Failed
            }
        }
        DynamicWallpaperResult.Ready(
            path = target.absolutePath,
            kind = kind,
            sizeBytes = target.length(),
            displayName = target.name,
        )
    }

    suspend fun adoptDynamicHomeCard(context: Context, source: File): DynamicWallpaperResult =
        adoptDynamicWallpaper(context, source, DYNAMIC_CARD_PREFIX)

    private fun adaptiveDraftDynamicPrefix(homeCard: Boolean): String =
        if (homeCard) ADAPTIVE_DRAFT_DYNAMIC_HOME_CARD_PREFIX else ADAPTIVE_DRAFT_DYNAMIC_WALLPAPER_PREFIX

    /**
     * Snapshots the shared live dynamic media for the adaptive draft. Both light and dark capturer
     * runs write the same slots because a dynamic wallpaper is global, not per mode.
     */
    suspend fun replaceAdaptiveDraftDynamicMedia(
        context: Context,
        source: File,
        homeCard: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val kind = dynamicWallpaperKindOf(source) ?: return@runCatching null
            val dir = backgroundsDir(context)
            val prefix = adaptiveDraftDynamicPrefix(homeCard)
            val target = File(dir, "$prefix.${kind.extension}")
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, DYNAMIC_WALLPAPER_COPY_BUFFER)
                }
            }
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            dir.listFiles { file -> file.name.startsWith("$prefix.") && file != target }
                ?.forEach { it.delete() }
            target.absolutePath
        }.getOrNull()
    }

    suspend fun clearAdaptiveDraftDynamicMedia(context: Context, homeCard: Boolean) =
        withContext(Dispatchers.IO) {
            val prefix = adaptiveDraftDynamicPrefix(homeCard)
            backgroundsDir(context)
                .listFiles { file -> file.name.startsWith("$prefix.") }
                ?.forEach { it.delete() }
        }

    suspend fun clearAdaptiveDraftDynamicMedia(context: Context) =
        withContext(Dispatchers.IO) {
            val dir = backgroundsDir(context)
            dir.listFiles { file ->
                file.name.startsWith("$ADAPTIVE_DRAFT_DYNAMIC_WALLPAPER_PREFIX.") ||
                    file.name.startsWith("$ADAPTIVE_DRAFT_DYNAMIC_HOME_CARD_PREFIX.")
            }?.forEach { it.delete() }
        }

    fun discardStagedDynamicWallpaper(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.isFile && file.name.startsWith("preview_")) file.delete()
        }
    }

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private sealed interface CopyOutcome {
        data class Valid(val kind: DynamicWallpaperKind) : CopyOutcome
        data object TooLarge : CopyOutcome
        data object Unsupported : CopyOutcome
        data object Failed : CopyOutcome
    }

    private fun copyDynamicWallpaper(input: java.io.InputStream, output: File): CopyOutcome {
        val header = ByteArray(DYNAMIC_WALLPAPER_SNIFF_BYTES)
        var headerLength = 0
        var total = 0L
        try {
            output.outputStream().use { out ->
                input.use { raw ->
                    val buffer = ByteArray(DYNAMIC_WALLPAPER_COPY_BUFFER)
                    while (true) {
                        val read = raw.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        total += read
                        if (total > DYNAMIC_WALLPAPER_MAX_BYTES) {
                            return CopyOutcome.TooLarge
                        }
                        if (headerLength < header.size) {
                            val take = minOf(header.size - headerLength, read)
                            System.arraycopy(buffer, 0, header, headerLength, take)
                            headerLength += take
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CopyOutcome.Failed
        }
        if (total <= 0L) return CopyOutcome.Failed
        val kind = sniffDynamicWallpaperKind(header, headerLength)
            ?: return CopyOutcome.Unsupported
        return CopyOutcome.Valid(kind)
    }

    /**
     * The picker MIME type is advisory only, so the container is identified from its magic
     * bytes: RIFF/WEBP and ISO-BMFF ftyp. This also keeps an HTML or APK renamed to .mp4 out
     * of the WebView's same-origin asset space.
     */
    private fun sniffDynamicWallpaperKind(header: ByteArray, length: Int): DynamicWallpaperKind? {
        fun matches(offset: Int, text: String): Boolean {
            if (offset < 0 || offset + text.length > length) return false
            for (index in text.indices) {
                if (header[offset + index] != text[index].code.toByte()) return false
            }
            return true
        }

        if (matches(0, "RIFF") && matches(8, "WEBP")) return DynamicWallpaperKind.WEBP
        val lastOffset = (length - 4).coerceAtLeast(0)
        for (offset in 0..lastOffset) {
            if (matches(offset, "ftyp")) return DynamicWallpaperKind.MP4
        }
        return null
    }

    private fun dynamicWallpaperMetadata(context: Context, uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        }
        return name to size
    }

    /** Directory served to the renderer as `/dynamic/`; only app-written wallpaper files live here. */
    fun dynamicWallpaperBackgroundsDir(context: Context): File = backgroundsDir(context)

    /** Cache directory served to the preview renderer as `/dynamic-preview/`. */
    fun dynamicWallpaperPreviewDir(context: Context): File = dynamicWallpaperStageDir(context)

    private fun dynamicWallpaperStageDir(context: Context): File {
        val dir = File(context.cacheDir, DYNAMIC_WALLPAPER_STAGE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }


    fun systemWallpaperFile(context: Context): File =
        File(backgroundsDir(context), SYSTEM_WALLPAPER_FILE_NAME)

    /**
     * Copies the launcher wallpaper into app-private storage.
     *
     * Android 13+ no longer lets third-party apps read the wallpaper through WallpaperManager
     * (Android 14 always throws), so the file behind the wallpaper service is copied with root
     * access instead and then consumed by the normal background pipeline. Android 15/16 and
     * HyperOS may keep the wallpaper in a directory (`wallpaper/wallpaper`) or under the theme
     * manager, so every known layout is probed until one produces a decodable image. [force]
     * skips the signature check and is used when the user explicitly enables the switch.
     */
    suspend fun captureSystemWallpaper(
        context: Context,
        force: Boolean = false,
    ): SystemWallpaperResult = withContext(Dispatchers.IO) {
        systemWallpaperMutex.withLock {
            try {
                val dest = systemWallpaperFile(context)
                val signatureFile = File(dest.parentFile, SYSTEM_WALLPAPER_SIGNATURE_FILE_NAME)
                val previousSignature = runCatching { signatureFile.readText() }.getOrNull()

                // Fast path: the file copied last time is still the current wallpaper.
                if (!force && dest.isFile && previousSignature != null) {
                    val cachedSource = previousSignature.substringBeforeLast('|')
                    val cachedKey = systemWallpaperSignature(cachedSource)
                        ?.let { "$cachedSource|$it" }
                    if (cachedKey != null && cachedKey == previousSignature &&
                        isDecodableImage(dest)
                    ) {
                        return@withLock SystemWallpaperResult(dest.absolutePath, false)
                    }
                }

                val sources = resolveSystemWallpaperSources(systemWallpaperUserId())
                for (source in sources) {
                    if (!writeSystemWallpaperWithRoot(source, dest)) {
                        Log.w(TAG, "Unreadable system wallpaper candidate: $source")
                        continue
                    }
                    val signatureKey = systemWallpaperSignature(source)?.let { "$source|$it" }
                    if (signatureKey != null) {
                        runCatching { signatureFile.writeText(signatureKey) }
                    }
                    return@withLock SystemWallpaperResult(dest.absolutePath, true)
                }

                Log.w(TAG, "No readable system wallpaper; candidates: $sources")
                SystemWallpaperResult(
                    path = dest.takeIf { !force && isDecodableImage(it) }?.absolutePath,
                    changed = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                SystemWallpaperResult(null, false)
            }
        }
    }

    
    private fun writeSystemWallpaperWithRoot(source: String, dest: File): Boolean {
        val parent = dest.parentFile ?: return false
        val staging = runCatching {
            File.createTempFile("wallpaper_capture_", ".tmp", parent)
        }.getOrNull() ?: return false

        return try {
            // Create the file as the app first so its owner and SELinux label remain readable
            // after root writes into it. This also keeps a failed capture away from the cache.
            val copiedDirectly = runCatching {
                ShellUtils.fastCmdResult(
                    getRootShell(),
                    "cat ${source.shellQuote()} > ${staging.absolutePath.shellQuote()}",
                )
            }.getOrDefault(false)
            val copied = copiedDirectly || streamBase64Wallpaper(source, staging)
            if (!copied || !isDecodableImage(staging)) return false

            runCatching {
                Os.rename(staging.absolutePath, dest.absolutePath)
                true
            }.getOrDefault(false)
        } finally {
            if (staging.exists()) staging.delete()
        }
    }

    private fun isDecodableImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0 || width.toLong() * height > MAX_SYSTEM_WALLPAPER_PIXELS) {
                return false
            }

            val maxDimension = maxOf(width, height)
            var sampleSize = 1
            while (maxDimension / sampleSize > MAX_WALLPAPER_VALIDATION_DIMENSION) {
                sampleSize *= 2
            }
            val bitmap = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            ) ?: return false
            try {
                bitmap.width > 0 && bitmap.height > 0
            } finally {
                bitmap.recycle()
            }
        } catch (_: OutOfMemoryError) {
            false
        } catch (_: Exception) {
            false
        }
    }

    
    private fun streamBase64Wallpaper(source: String, dest: File): Boolean {
        val output = runCatching { dest.outputStream() }.getOrNull() ?: return false
        val writeFailed = AtomicBoolean(false)
        val stdout = object : CallbackList<String?>(Executor { it.run() }) {
            override fun onAddElement(line: String?) {
                if (line.isNullOrEmpty() || writeFailed.get()) return
                try {
                    output.write(Base64.decode(line, Base64.DEFAULT))
                } catch (_: Exception) {
                    writeFailed.set(true)
                }
            }
        }
        val commandSucceeded = try {
            getRootShell().newJob()
                .add("base64 ${source.shellQuote()} | fold -w 4096")
                .to(stdout, null)
                .exec()
                .isSuccess
        } catch (_: Exception) {
            false
        }
        val closeSucceeded = runCatching { output.close() }.isSuccess
        return commandSucceeded && closeSucceeded && !writeFailed.get()
    }

    
    private fun systemWallpaperUserId(): Int = Process.myUid() / 100000

    private fun resolveSystemWallpaperSources(userId: Int): List<String> {
        // Home-screen candidates first; lock-screen copies are only a fallback.
        val home = listOf(
            // AOSP and most OEMs keep this as a file. Android 15/16 may turn it into a
            // directory with `wallpaper` / `wallpaper_lock` children.
            "/data/system/users/$userId/wallpaper",
            "/data/system/users/$userId/wallpaper/wallpaper",
            "/data/system/users/$userId/wallpaper/home_wallpaper.jpg",
            "/data/system/users/$userId/wallpaper/home_wallpaper.png",
            "/data/system/users/$userId/wallpaper/home_wallpaper.webp",
            // MIUI/HyperOS keep their own home wallpaper under the theme manager. On some ROMs
            // only this copy is updated, so it is a first-class candidate rather than a fallback.
            "/data/system/theme_magic/users/$userId/wallpaper/image/home_wallpaper.jpg",
            "/data/system/theme_magic/users/$userId/wallpaper/image/home_wallpaper.png",
            "/data/system/theme_magic/users/$userId/wallpaper/image/home_wallpaper.webp",
            "/data/system/theme_magic/users/$userId/wallpaper/home_wallpaper.jpg",
            "/data/system/theme_magic/users/$userId/wallpaper/home_wallpaper.png",
            "/data/system/theme_magic/users/$userId/wallpaper/home_wallpaper.webp",
            // The pre-crop original is a last home-screen fallback.
            "/data/system/users/$userId/wallpaper/wallpaper_orig",
            "/data/system/users/$userId/wallpaper_orig",
        )
        val lock = listOf(
            "/data/system/users/$userId/wallpaper/wallpaper_lock",
            "/data/system/users/$userId/wallpaper_lock",
            "/data/system/theme_magic/users/$userId/wallpaper/image/lock_wallpaper.jpg",
            "/data/system/theme_magic/users/$userId/wallpaper/image/lock_wallpaper.png",
            "/data/system/theme_magic/users/$userId/wallpaper/image/lock_wallpaper.webp",
        )

        fun existing(paths: List<String>): List<Pair<String, Long>> =
            paths.mapNotNull { path ->
                wallpaperFileInfo(path)?.let { info -> path to info.first }
            }.sortedByDescending { it.second }

        val knownHome = existing(home)
        val knownLock = existing(lock)
        val knownPaths = (knownHome + knownLock).map { it.first }.toSet()
        val discovered = discoverSystemWallpaperFiles(userId)
            .asSequence()
            .filter { it !in knownPaths }
            .mapNotNull { path -> wallpaperFileInfo(path)?.let { info -> path to info.first } }
            .sortedByDescending { it.second }
            .toList()
        val discoveredHome = discovered.filterNot { it.first.isLockWallpaper() }
        val discoveredLock = discovered.filter { it.first.isLockWallpaper() }

        // Newest first inside each tier so a wallpaper change wins over a stale copy, even when
        // the fresh file was only found by the layout discovery above.
        val homeCandidates = (knownHome + discoveredHome).sortedByDescending { it.second }
        val lockCandidates = (discoveredLock + knownLock).sortedByDescending { it.second }
        return (homeCandidates + lockCandidates)
            .map { it.first }
            .distinct()
            .take(MAX_WALLPAPER_CANDIDATES)
    }

    private fun String.isLockWallpaper(): Boolean =
        substringAfterLast('/').contains("lock", ignoreCase = true)

    /**
     * Last resort for ROMs with a custom layout. Android 15/16 and HyperOS can create files with
     * names we do not know ahead of time (for example `wallpaper_lock_<timestamp>.jpg`), so the
     * user and theme directories are searched for anything that looks like wallpaper media.
     */
    private fun discoverSystemWallpaperFiles(userId: Int): List<String> {
        val roots = listOf(
            "/data/system/users/$userId",
            "/data/system/theme_magic/users/$userId",
        )
        val command = "find " + roots.joinToString(" ") { it.shellQuote() } +
            " -maxdepth 8 -type f -iname '*wallpaper*' -size +10k 2>/dev/null | head -40"
        return runCatching {
            getRootShell().newJob()
                .add(command)
                .exec().out
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.isWallpaperImageFile() }
        }.getOrDefault(emptyList())
    }

    private fun String.isWallpaperImageFile(): Boolean {
        val name = substringAfterLast('/').lowercase()
        if ('.' !in name) return true
        return name.substringAfterLast('.') in setOf(
            "jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp",
        )
    }

    
    private fun wallpaperFileInfo(path: String): Pair<Long, Long>? = runCatching {
        val quoted = path.shellQuote()
        val out = ShellUtils.fastCmd(getRootShell(), "[ -f $quoted ] && stat -c '%Y:%s' $quoted").trim()
        val parts = out.split(':')
        if (parts.size != 2) return@runCatching null
        val modified = parts[0].toLongOrNull() ?: return@runCatching null
        val size = parts[1].toLongOrNull() ?: return@runCatching null
        if (size > 0L) modified to size else null
    }.getOrNull()

    private fun systemWallpaperSignature(source: String): String? {
        val info = wallpaperFileInfo(source) ?: return null
        return "${info.first}:${info.second}"
    }

    /**
     * Copies a picked image into the crop cache. The crop screen and the vendor gallery
     * both work on this private copy so a picker URI never has to stay readable.
     */
    suspend fun stageUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File.createTempFile("source_", ".img", cropCacheDir(context))
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                target.delete()
                return@runCatching null
            }
            if (target.length() == 0L) {
                target.delete()
                null
            } else {
                target.absolutePath
            }
        }.getOrNull()
    }

    
    suspend fun stageFile(context: Context, source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!source.isFile) return@runCatching null
            val target = File.createTempFile("source_", ".img", cropCacheDir(context))
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.absolutePath
        }.getOrNull()
    }

    
    fun newCropOutput(context: Context): File =
        File.createTempFile("crop_", ".jpg", cropCacheDir(context))

    fun discardStaged(path: String?) {
        if (path.isNullOrEmpty()) return
        runCatching {
            val file = File(path)
            if (file.isFile) file.delete()
        }
    }

    private fun cropCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CROP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    
    suspend fun replace(context: Context, source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = prepareFile(context)
            if (targetFile.exists() && !targetFile.delete()) return@runCatching null
            if (!source.renameTo(targetFile)) {
                source.inputStream().use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            targetFile.absolutePath
        }.getOrNull()
    }

    suspend fun extractSeedColor(
        file: File,
        fallbackColorArgb: Int = 0xFFF27297.toInt()
    ): Int = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext fallbackColorArgb
        var bitmapToRecycle: Bitmap? = null
        try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext fallbackColorArgb
            }

            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 512) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: return@withContext fallbackColorArgb
            bitmapToRecycle = sampledBitmap

            val scaledBitmap = if (sampledBitmap.width != 128 || sampledBitmap.height != 128) {
                Bitmap.createScaledBitmap(sampledBitmap, 128, 128, true).also { scaled ->
                    if (scaled != sampledBitmap) {
                        sampledBitmap.recycle()
                        bitmapToRecycle = scaled
                    }
                }
            } else {
                sampledBitmap
            }

            val width = scaledBitmap.width
            val height = scaledBitmap.height
            val pixels = IntArray(width * height)
            scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val colorToCountMap: Map<Int, Int> = QuantizerCelebi.quantize(pixels, 128)
            val sortedColors: List<Int> = Score.score(colorToCountMap, 10, fallbackColorArgb, true)
            sortedColors.firstOrNull() ?: fallbackColorArgb
        } catch (_: OutOfMemoryError) {
            fallbackColorArgb
        } catch (e: Exception) {
            fallbackColorArgb
        } finally {
            bitmapToRecycle?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private fun backgroundsDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun prepareFile(context: Context): File = File(backgroundsDir(context), FILE_NAME)

    /**
     * Copies a picked image into the home status card slot and returns its path.
     * Each save uses a fresh file name so Coil's cache never serves the previous image.
     */
    suspend fun saveHomeCardImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir(context)
            val target = File(dir, HOME_CARD_PREFIX + "_" + System.currentTimeMillis())
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            dir.listFiles { file -> file.name.startsWith(HOME_CARD_PREFIX) && file != target }
                ?.forEach { it.delete() }
            target.absolutePath
        }.getOrNull()
    }

    suspend fun clearHomeCardImage(context: Context) = withContext(Dispatchers.IO) {
        backgroundsDir(context)
            .listFiles { file -> file.name.startsWith(HOME_CARD_PREFIX) }
            ?.forEach { it.delete() }
    }

    private fun bottomBarIconPrefix(slot: BottomBarIconSlot) =
        BOTTOM_BAR_ICON_PREFIX + slot.key + "_"

    /**
     * Decodes the picked image, scales it down to an icon-sized PNG and writes it to [target].
     * Keeping the alpha channel matters because custom icons are drawn without a tint.
     */
    private fun writeBottomBarIcon(target: File, openInput: () -> java.io.InputStream?): Boolean {
        val raw = File(target.parentFile, target.name + ".raw")
        try {
            openInput()?.use { input -> raw.outputStream().use { input.copyTo(it) } } ?: return false
            if (raw.length() == 0L) return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= BOTTOM_BAR_ICON_MAX_PX &&
                bounds.outHeight / (sample * 2) >= BOTTOM_BAR_ICON_MAX_PX
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(raw.absolutePath, options) ?: return false
            val longest = maxOf(decoded.width, decoded.height)
            val scaled = if (longest > BOTTOM_BAR_ICON_MAX_PX) {
                val ratio = BOTTOM_BAR_ICON_MAX_PX.toFloat() / longest
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                decoded
            }
            val written = target.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            return written && target.length() > 0L
        } catch (_: Exception) {
            return false
        } finally {
            raw.delete()
        }
    }

    suspend fun saveBottomBarIcon(
        context: Context,
        slot: BottomBarIconSlot,
        uri: Uri,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir(context)
            val prefix = bottomBarIconPrefix(slot)
            val target = File(dir, prefix + System.currentTimeMillis() + "_" + System.nanoTime())
            if (!writeBottomBarIcon(target) { context.contentResolver.openInputStream(uri) }) {
                target.delete()
                return@runCatching null
            }
            dir.listFiles { file -> file.name.startsWith(prefix) && file != target }
                ?.forEach { it.delete() }
            target.absolutePath
        }.getOrNull()
    }

    suspend fun importBottomBarIcon(
        context: Context,
        slot: BottomBarIconSlot,
        source: File,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir(context)
            val prefix = bottomBarIconPrefix(slot)
            val target = File(dir, prefix + System.currentTimeMillis() + "_" + System.nanoTime())
            if (!writeBottomBarIcon(target) { source.inputStream() }) {
                target.delete()
                return@runCatching null
            }
            dir.listFiles { file -> file.name.startsWith(prefix) && file != target }
                ?.forEach { it.delete() }
            target.absolutePath
        }.getOrNull()
    }

    suspend fun clearBottomBarIcon(context: Context, slot: BottomBarIconSlot) =
        withContext(Dispatchers.IO) {
            backgroundsDir(context)
                .listFiles { file -> file.name.startsWith(bottomBarIconPrefix(slot)) }
                ?.forEach { it.delete() }
        }

    suspend fun clearBottomBarIcons(context: Context) = withContext(Dispatchers.IO) {
        backgroundsDir(context)
            .listFiles { file -> file.name.startsWith(BOTTOM_BAR_ICON_PREFIX) }
            ?.forEach { it.delete() }
    }

    suspend fun saveHomeCardSticker(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir(context)
            val target = File(dir, HOME_CARD_STICKER_PREFIX + "_" + System.currentTimeMillis() + "_" + System.nanoTime())
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            target.absolutePath
        }.getOrNull()
    }

    
    suspend fun deleteHomeCardSticker(context: Context, path: String) = withContext(Dispatchers.IO) {
        val dir = backgroundsDir(context)
        File(path).takeIf { it.parentFile == dir }?.delete()
    }

    suspend fun clearHomeCardSticker(context: Context) = withContext(Dispatchers.IO) {
        backgroundsDir(context)
            .listFiles { file -> file.name.startsWith(HOME_CARD_STICKER_PREFIX) }
            ?.forEach { it.delete() }
    }

    
    suspend fun importHomeCardSticker(context: Context, source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir(context)
            val target = File(dir, HOME_CARD_STICKER_PREFIX + "_" + System.currentTimeMillis() + "_" + System.nanoTime())
            if (!source.renameTo(target)) {
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            target.absolutePath
        }.getOrNull()
    }

    
    suspend fun replaceHomeCardImage(context: Context, source: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir(context)
            val target = File(dir, HOME_CARD_PREFIX + "_" + System.currentTimeMillis())
            if (!source.renameTo(target)) {
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            dir.listFiles { file -> file.name.startsWith(HOME_CARD_PREFIX) && file != target }
                ?.forEach { it.delete() }
            target.absolutePath
        }.getOrNull()
    }

    private fun adaptiveBackgroundName(dark: Boolean, draft: Boolean): String = when {
        draft && dark -> ADAPTIVE_DRAFT_BACKGROUND_DARK
        draft -> ADAPTIVE_DRAFT_BACKGROUND_LIGHT
        dark -> ADAPTIVE_BACKGROUND_DARK
        else -> ADAPTIVE_BACKGROUND_LIGHT
    }

    private fun adaptiveCardPrefix(dark: Boolean, draft: Boolean): String = when {
        draft && dark -> ADAPTIVE_DRAFT_CARD_DARK_PREFIX
        draft -> ADAPTIVE_DRAFT_CARD_LIGHT_PREFIX
        dark -> ADAPTIVE_CARD_DARK_PREFIX
        else -> ADAPTIVE_CARD_LIGHT_PREFIX
    }

    private fun adaptiveStickerPrefix(dark: Boolean, draft: Boolean): String = when {
        draft && dark -> ADAPTIVE_DRAFT_STICKER_DARK_PREFIX
        draft -> ADAPTIVE_DRAFT_STICKER_LIGHT_PREFIX
        dark -> ADAPTIVE_STICKER_DARK_PREFIX
        else -> ADAPTIVE_STICKER_LIGHT_PREFIX
    }

    /**
     * Adaptive themes keep one background per system mode. The files live beside the normal
     * background but use their own names so a manual background change cannot delete them.
     * [draft] uses a separate slot set for the in-app builder, so capturing a draft never
     * overwrites the theme that is currently applied.
     */
    suspend fun replaceAdaptiveBackground(
        context: Context,
        source: File,
        dark: Boolean,
        draft: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(backgroundsDir(context), adaptiveBackgroundName(dark, draft))
            if (!source.renameTo(target)) {
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            target.absolutePath
        }.getOrNull()
    }

    suspend fun clearAdaptiveBackground(
        context: Context,
        dark: Boolean,
        draft: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val file = File(backgroundsDir(context), adaptiveBackgroundName(dark, draft))
        if (file.isFile) file.delete()
    }

    
    suspend fun replaceAdaptiveHomeCardImage(
        context: Context,
        source: File,
        dark: Boolean,
        draft: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir(context)
            val prefix = adaptiveCardPrefix(dark, draft)
            val target = File(dir, prefix + System.currentTimeMillis())
            if (!source.renameTo(target)) {
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            dir.listFiles { file -> file.name.startsWith(prefix) && file != target }
                ?.forEach { it.delete() }
            target.absolutePath
        }.getOrNull()
    }

    suspend fun clearAdaptiveHomeCardImage(
        context: Context,
        dark: Boolean,
        draft: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val prefix = adaptiveCardPrefix(dark, draft)
        backgroundsDir(context)
            .listFiles { file -> file.name.startsWith(prefix) }
            ?.forEach { it.delete() }
    }

    
    suspend fun importAdaptiveHomeCardSticker(
        context: Context,
        source: File,
        dark: Boolean,
        index: Int,
        draft: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundsDir(context)
            val prefix = adaptiveStickerPrefix(dark, draft)
            val target = File(
                dir,
                prefix + index + "_" + System.currentTimeMillis() + "_" + System.nanoTime(),
            )
            if (!source.renameTo(target)) {
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            target.absolutePath
        }.getOrNull()
    }

    suspend fun clearAdaptiveHomeCardStickers(
        context: Context,
        dark: Boolean,
        draft: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val prefix = adaptiveStickerPrefix(dark, draft)
        backgroundsDir(context)
            .listFiles { file -> file.name.startsWith(prefix) }
            ?.forEach { it.delete() }
    }

    
    suspend fun clearAdaptiveImages(context: Context) = withContext(Dispatchers.IO) {
        val dir = backgroundsDir(context)
        dir.listFiles { file ->
            file.name == ADAPTIVE_BACKGROUND_LIGHT ||
                file.name == ADAPTIVE_BACKGROUND_DARK ||
                file.name == ADAPTIVE_DRAFT_BACKGROUND_LIGHT ||
                file.name == ADAPTIVE_DRAFT_BACKGROUND_DARK ||
                file.name.startsWith(ADAPTIVE_CARD_LIGHT_PREFIX) ||
                file.name.startsWith(ADAPTIVE_CARD_DARK_PREFIX) ||
                file.name.startsWith(ADAPTIVE_DRAFT_CARD_LIGHT_PREFIX) ||
                file.name.startsWith(ADAPTIVE_DRAFT_CARD_DARK_PREFIX) ||
                file.name.startsWith(ADAPTIVE_STICKER_LIGHT_PREFIX) ||
                file.name.startsWith(ADAPTIVE_STICKER_DARK_PREFIX) ||
                file.name.startsWith(ADAPTIVE_DRAFT_STICKER_LIGHT_PREFIX) ||
                file.name.startsWith(ADAPTIVE_DRAFT_STICKER_DARK_PREFIX) ||
                file.name.startsWith("$ADAPTIVE_DRAFT_DYNAMIC_WALLPAPER_PREFIX.") ||
                file.name.startsWith("$ADAPTIVE_DRAFT_DYNAMIC_HOME_CARD_PREFIX.")
        }?.forEach { it.delete() }
    }

    /**
     * Unified exporter for custom images (background wallpaper, home status card and sticker).
     * Multiple images are bundled into a standard unencrypted ZIP archive; a single image is
     * copied directly.
     */
    suspend fun exportImages(
        context: Context,
        uri: Uri,
        backgroundFile: File?,
        cardBackgroundFile: File?,
        stickerFiles: List<File> = emptyList(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = buildList {
                backgroundFile?.takeIf { it.isFile }?.let { add("background.png" to it) }
                cardBackgroundFile?.takeIf { it.isFile }?.let { add("home_card.png" to it) }
                stickerFiles.filter { it.isFile }.forEachIndexed { index, file ->
                    add("sticker_${index + 1}.png" to file)
                }
            }
            if (entries.isEmpty()) error("No image to export")

            val output = context.contentResolver.openOutputStream(uri)
                ?: error("Cannot open destination")

            if (entries.size > 1) {
                java.io.BufferedOutputStream(output).use { out ->
                    java.util.zip.ZipOutputStream(out).use { zip ->
                        entries.forEach { (name, file) ->
                            zip.putNextEntry(java.util.zip.ZipEntry(name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            } else {
                java.io.BufferedOutputStream(output).use { out ->
                    entries.single().second.inputStream().use { it.copyTo(out) }
                }
            }
            Unit
        }
    }

    
    fun exportImagesFileName(
        hasBackground: Boolean,
        hasCardBackground: Boolean,
        stickerCount: Int,
    ): String = when {
        listOf(hasBackground, hasCardBackground).count { it } + stickerCount > 1 -> "SukiSU-images.zip"
        hasBackground -> "SukiSU-background.png"
        hasCardBackground -> "SukiSU-home-card.png"
        stickerCount > 0 -> "SukiSU-sticker.png"
        else -> "SukiSU-images.zip"
    }
}
