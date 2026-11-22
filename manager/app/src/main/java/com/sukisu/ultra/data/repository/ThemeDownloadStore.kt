package com.sukisu.ultra.data.repository

import android.content.Context
import com.sukisu.ultra.ui.util.ThemeMediaCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A theme package kept on the device after a successful store download.
 *
 * The package is copied out of the evictable download cache into the app's private files so
 * "My downloads" survives cache trimming, offline use and process restarts. [previewFile] is a
 * locally cached cover image so the list never re-hits the mirror for thumbnails.
 */
@Serializable
data class ThemeDownloadRecord(
    val key: String,
    val themeId: String,
    val sourceId: String = "",
    val baseUrl: String = "",
    val name: String = "",
    val author: String = "",
    val version: Int = 1,
    val formatVersion: Int = 1,
    val size: Long = 0L,
    val sha256: String = "",
    val fileName: String = "",
    val previewFile: String = "",
    /** Catalog preview path, kept so a missing local cover can be fetched again later. */
    val previewPath: String = "",
    val adaptive: Boolean = false,
    val dynamic: Boolean = false,
    val r18: Boolean = false,
    val updatedAt: String = "",
    val tags: List<String> = emptyList(),
    val downloadedAt: Long = 0L,
)

/** Everything [ThemeDownloadStore] needs to persist one downloaded package. */
data class ThemeDownloadInput(
    val key: String,
    val themeId: String,
    val sourceId: String,
    val baseUrl: String,
    val name: String,
    val author: String,
    val version: Int,
    val formatVersion: Int,
    val size: Long,
    val sha256: String,
    val adaptive: Boolean,
    val dynamic: Boolean,
    val r18: Boolean,
    val updatedAt: String,
    val tags: List<String>,
    /** Catalog preview path used to refetch the cover when the local copy is missing. */
    val previewPath: String = "",
    /** Cover image bytes fetched during the download; ignored when null. */
    val previewBytes: ByteArray? = null,
)

/**
 * Persistent store for downloaded theme packages.
 *
 * The index is a single JSON file inside the same directory as the packages. All mutations are
 * serialized with a [Mutex] and run on [Dispatchers.IO]; the index is replaced atomically so a
 * crash mid-write cannot corrupt previously stored entries.
 */
class ThemeDownloadStore(context: Context) {

    companion object {
        private const val DIR_NAME = "theme_downloads"
        private const val INDEX_NAME = "index.json"
        private const val INDEX_VERSION = 1
        private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]+")
    }

    @Serializable
    private data class IndexFile(
        val version: Int = INDEX_VERSION,
        val items: List<ThemeDownloadRecord> = emptyList(),
    )

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIR_NAME)
    private val indexFile = File(dir, INDEX_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val mutex = Mutex()

    /** Package file for [record]; callers must check [File.isFile] before applying it. */
    fun fileFor(record: ThemeDownloadRecord): File = File(dir, record.fileName)

    /** Preview image for [record], or null when the cover could not be cached. */
    fun previewFor(record: ThemeDownloadRecord): File? =
        record.previewFile.takeIf { it.isNotBlank() }?.let { File(dir, it) }?.takeIf { it.isFile }

    /** Cached packages, newest download first, with missing files dropped. */
    suspend fun list(): List<ThemeDownloadRecord> = mutex.withLock {
        withContext(Dispatchers.IO) { readLocked() }
    }

    /**
     * Copies [packageFile] into the store and upserts its metadata. Returns the stored record, or
     * null when the package could not be copied. A failed copy leaves the previous record alone.
     */
    suspend fun save(
        input: ThemeDownloadInput,
        packageFile: File,
    ): ThemeDownloadRecord? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!packageFile.isFile || packageFile.length() <= 0L) return@withContext null
            dir.mkdirs()
            val safeKey = input.key.replace(UNSAFE_FILE_CHARS, "_").trim('_').ifBlank {
                input.themeId
            }
            val fileName = "$safeKey-v${input.version}.sukisu"
            val target = File(dir, fileName)
            if (!(target.isFile && target.length() == packageFile.length())) {
                runCatching {
                    packageFile.copyTo(target, overwrite = true)
                }.getOrNull() ?: return@withContext null
            }

            val existing = readLocked().firstOrNull { it.key == input.key }
            if (existing != null && existing.fileName.isNotBlank() && existing.fileName != fileName) {
                // A new version supersedes the previous package; drop the stale copy.
                runCatching { File(dir, existing.fileName).delete() }
            }
            val previewFile = writePreviewLocked(input, existing)
            val record = ThemeDownloadRecord(
                key = input.key,
                themeId = input.themeId,
                sourceId = input.sourceId,
                baseUrl = input.baseUrl,
                name = input.name,
                author = input.author,
                version = input.version,
                formatVersion = input.formatVersion,
                size = input.size.takeIf { it > 0L } ?: target.length(),
                sha256 = input.sha256,
                fileName = fileName,
                previewFile = previewFile,
                previewPath = input.previewPath,
                adaptive = input.adaptive,
                dynamic = input.dynamic,
                r18 = input.r18,
                updatedAt = input.updatedAt,
                tags = input.tags,
                downloadedAt = System.currentTimeMillis(),
            )
            val items = readLocked()
                .filterNot { it.key == record.key }
                .let { listOf(record) + it }
                .sortedByDescending { it.downloadedAt }
            writeIndexLocked(items)
            record
        }
    }

    /**
     * Attaches a freshly fetched cover to an already stored package. Kept separate from [save]
     * because the source package may be deleted by the import flow before the cover arrives.
     */
    suspend fun attachPreview(key: String, bytes: ByteArray?): ThemeDownloadRecord? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (bytes == null || bytes.isEmpty()) return@withContext null
            // Never cache the sealed CNB envelope: the image loader cannot decode it.
            if (ThemeMediaCrypto.isSealed(bytes)) return@withContext null
            val extension = previewExtension(bytes) ?: return@withContext null
            val current = readLocked()
            val record = current.firstOrNull { it.key == key } ?: return@withContext null
            val safeKey = key.replace(UNSAFE_FILE_CHARS, "_").trim('_').ifBlank { record.themeId }
            val name = "preview-$safeKey-v${record.version}.$extension"
            val target = File(dir, name)
            runCatching { target.writeBytes(bytes) }.getOrNull() ?: return@withContext null
            if (record.previewFile.isNotBlank() && record.previewFile != name) {
                runCatching { File(dir, record.previewFile).delete() }
            }
            val updated = record.copy(previewFile = name)
            writeIndexLocked(current.map { if (it.key == key) updated else it })
            updated
        }
    }

    /** Deletes the packages (and their previews) for [keys]; returns the remaining records. */
    suspend fun delete(keys: Set<String>): List<ThemeDownloadRecord> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (keys.isEmpty()) return@withContext readLocked()
            val current = readLocked()
            val removed = current.filter { it.key in keys }
            removed.forEach { record ->
                runCatching { fileFor(record).delete() }
                record.previewFile.takeIf { it.isNotBlank() }?.let { name ->
                    runCatching { File(dir, name).delete() }
                }
            }
            val remaining = current.filterNot { it.key in keys }
            writeIndexLocked(remaining)
            remaining
        }
    }

    /** Removes every cached package and resets the index. */
    suspend fun clear(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) runCatching { file.delete() }
            }
            runCatching { dir.mkdirs() }
            writeIndexLocked(emptyList())
        }
    }

    private fun readLocked(): List<ThemeDownloadRecord> {
        val stored = runCatching {
            if (!indexFile.isFile) return@runCatching emptyList()
            json.decodeFromString<IndexFile>(indexFile.readText()).items
        }.getOrDefault(emptyList())
        val present = stored
            .filter { it.fileName.isNotBlank() && fileFor(it).isFile }
            .map { it.withoutSealedPreview() }
            .sortedByDescending { it.downloadedAt }
        if (present != stored) {
            // Drop entries whose package was deleted outside the app (clear-data, manual cleanup)
            // and forget covers written as raw `SKM1` envelopes by older builds.
            writeIndexLocked(present)
        }
        return present
    }

    /**
     * Sealed CNB media cannot be decoded by the image loader. Older builds could store those
     * bytes, so the reference is cleared here and the next list refresh fetches a plaintext
     * cover again instead of showing an empty tile forever.
     */
    private fun ThemeDownloadRecord.withoutSealedPreview(): ThemeDownloadRecord {
        if (previewFile.isBlank()) return this
        val file = File(dir, previewFile)
        if (!file.isFile) return copy(previewFile = "")
        val usable = runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(ThemeMediaCrypto.HEADER_SIZE)
                val read = input.read(header)
                read >= 4 &&
                    !ThemeMediaCrypto.isSealed(header.copyOf(read)) &&
                    previewExtension(header.copyOf(read)) != null
            }
        }.getOrDefault(false)
        if (usable) return this
        runCatching { file.delete() }
        return copy(previewFile = "")
    }

    private fun writePreviewLocked(
        input: ThemeDownloadInput,
        existing: ThemeDownloadRecord?,
    ): String {
        val bytes = input.previewBytes
        if (bytes != null && bytes.isNotEmpty()) {
            val extension = previewExtension(bytes)
            if (extension != null) {
                val safeKey = input.key.replace(UNSAFE_FILE_CHARS, "_").trim('_').ifBlank {
                    input.themeId
                }
                val name = "preview-$safeKey-v${input.version}.$extension"
                val target = File(dir, name)
                if (runCatching { target.writeBytes(bytes) }.isSuccess) {
                    if (existing != null && existing.previewFile.isNotBlank() && existing.previewFile != name) {
                        runCatching { File(dir, existing.previewFile).delete() }
                    }
                    return name
                }
            }
        }
        val kept = existing?.previewFile.orEmpty()
        return if (kept.isNotBlank() && File(dir, kept).isFile) kept else ""
    }

    private fun writeIndexLocked(items: List<ThemeDownloadRecord>) {
        dir.mkdirs()
        val temp = File(dir, "$INDEX_NAME.tmp")
        runCatching {
            temp.writeText(json.encodeToString(IndexFile(items = items)))
            if (indexFile.exists()) indexFile.delete()
            temp.renameTo(indexFile)
        }
    }

    /**
     * Coil and `ImageDecoder` select a decoder from the file name's extension, so the cached
     * cover is named after the bytes it actually contains instead of a generic ".img".
     * Unknown bytes (HTML error pages, proxies) are rejected instead of being cached as images.
     */
    private fun previewExtension(bytes: ByteArray): String? = when {
        bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "webp"

        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"

        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "jpg"

        bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte() -> "gif"

        bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() -> "bmp"

        else -> null
    }
}
