package com.sukisu.ultra.core.pkgrename

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/** APK 重打包失败。 */
class ApkRepackagerException(message: String) : Exception(message)

/**
 * 零重压缩的 APK 重打包器。
 *
 * 只改动 `AndroidManifest.xml` 一个条目，其余条目的压缩流原样搬运：
 *
 *  1. 原文件 `[0, 签名块起点)` 这段字节直接拷贝 —— 所有条目保持原始偏移，
 *     `.so` / `resources.arsc` 的对齐不会被破坏；
 *  2. 改写后的清单作为**新条目**追加在末尾，旧清单字节变成无害的死区；
 *  3. 重写中央目录，只把清单条目的偏移 / 长度 / CRC 指到新条目；
 *  4. 丢掉原 APK Signing Block 与 v1 签名残留（重签时会重建 v2）。
 *
 * 代价与 APK 大小成正比、与压缩率无关：39MB 的包在 PC 上约 450ms。
 */
internal object ApkRepackager {

    private const val MANIFEST = "AndroidManifest.xml"
    private const val SIG_EOCD = 0x06054b50
    private const val SIG_CD = 0x02014b50
    private const val SIG_LFH = 0x04034b50
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATE = 8
    private val APK_SIG_MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)

    /** v1 签名残留，重签后必须从中央目录里消失。 */
    private val V1_LEFTOVERS = Regex("^META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))$", RegexOption.IGNORE_CASE)

    /**
     * 读取 [src] 的清单，交给 [manifestTransform] 改写，写出到 [dest]。
     * [dest] 是**未签名**的 APK，交给 [ApkV2Signer] 再签。
     */
    fun repack(src: File, dest: File, manifestTransform: (ByteArray) -> ByteArray) {
        RandomAccessFile(src, "r").use { raf ->
            val fileLen = raf.length()
            val eocdAbs = findEocd(raf, fileLen)
            val eocd = ByteArray(22)
            raf.seek(eocdAbs)
            raf.readFully(eocd)
            val totalEntries = u16(eocd, 10)
            val cdSize = u32(eocd, 12)
            val cdOffset = u32(eocd, 16)
            if (cdOffset <= 0 || cdSize <= 0) throw ApkRepackagerException("中央目录位置异常")
            // 本实现的偏移全程用 Int，超过 2GB 会静默截断；Zip64 的 0xFFFFFFFF 标记也走不到正常分支。
            if (fileLen >= 0x7FFF_FFFFL) throw ApkRepackagerException("APK 超过 2GB，本实现不支持")
            if (cdOffset == -1 || cdSize == -1) throw ApkRepackagerException("不支持的 Zip64 中央目录")
            if (hasZip64Locator(raf, eocdAbs)) throw ApkRepackagerException("不支持的 Zip64 结尾记录")

            val entryStart = signingBlockStart(raf, cdOffset)

            val cdBytes = ByteArray(cdSize)
            raf.seek(cdOffset.toLong())
            raf.readFully(cdBytes)
            val entries = parseCentralDirectory(cdBytes, totalEntries)

            val manifestEntry = entries.firstOrNull { it.name == MANIFEST }
                ?: throw ApkRepackagerException("APK 里没有 $MANIFEST")
            val plain = readEntryPayload(raf, manifestEntry)
            val patched = manifestTransform(plain)
            val newEntry = buildLocalEntry(MANIFEST, patched)
            val crc = CRC32().apply { update(patched) }.value
            val manifestCompSize = newEntry.size - 30 - MANIFEST.toByteArray(Charsets.UTF_8).size
            val manifestLocalOffset = entryStart
            val newCdOffset = entryStart + newEntry.size
            val kept = entries.filterNot { V1_LEFTOVERS.matches(it.name) }
            val cd = buildCentralDirectory(
                entries = kept,
                manifestName = MANIFEST,
                crc = crc,
                compSize = manifestCompSize,
                uncompSize = patched.size,
                newLocalOffset = manifestLocalOffset,
            )

            val eocdOut = eocd.copyOf()
            writeU16(eocdOut, 8, kept.size)
            writeU16(eocdOut, 10, kept.size)
            writeU32(eocdOut, 12, cd.size)
            writeU32(eocdOut, 16, newCdOffset.toInt())
            writeU16(eocdOut, 20, 0)

            if (kept.size > 0xFFFF) throw ApkRepackagerException("条目数超出 16 位上限：${kept.size}")

            dest.parentFile?.mkdirs()
            // 先写 .tmp 再改名：中途异常/进程被杀只留一个临时文件，不会产生「长度非 0 的半截 APK」
            // 被安装器当成成品拿去装。
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            tmp.delete()
            val expectedLen = entryStart + newEntry.size + cd.size + eocdOut.size
            try {
                RandomAccessFile(tmp, "rw").use { out ->
                    out.setLength(0)
                    // 用 RandomAccessFile 的 channel（java.nio.channels，API 1 起可用）：
                    // FileChannel.open(...Path...) 属于 java.nio.file（API 26+），minSdk 24 上会抛
                    // NoClassDefFoundError，那是 Error，上层的 catch(Exception) 抓不住。
                    var pos = 0L
                    while (pos < entryStart) {
                        val n = raf.channel.transferTo(pos, entryStart - pos, out.channel)
                        if (n <= 0L) throw ApkRepackagerException("拷贝条目区失败 @ $pos")
                        pos += n
                    }
                    out.seek(entryStart)
                    out.write(newEntry)
                    out.write(cd)
                    out.write(eocdOut)
                    out.channel.force(true)
                }
                verifyWritten(tmp, expectedLen, newCdOffset.toLong(), kept.size)
                if (!tmp.renameTo(dest)) {
                    // 部分文件系统（含 FAT/exFAT 的某些实现）不允许覆盖已存在的目标。
                    if (!dest.delete() || !tmp.renameTo(dest)) {
                        throw ApkRepackagerException("输出文件重命名失败")
                    }
                }
            } catch (t: Throwable) {
                tmp.delete()
                throw t
            }
        }
    }

    /** 读出 APK 里的 AndroidManifest.xml 原文（已解压）。 */
    fun readManifest(apk: File): ByteArray {
        RandomAccessFile(apk, "r").use { raf ->
            val fileLen = raf.length()
            val eocdAbs = findEocd(raf, fileLen)
            val eocd = ByteArray(22)
            raf.seek(eocdAbs)
            raf.readFully(eocd)
            val totalEntries = u16(eocd, 10)
            val cdSize = u32(eocd, 12)
            val cdOffset = u32(eocd, 16)
            val cdBytes = ByteArray(cdSize)
            raf.seek(cdOffset.toLong())
            raf.readFully(cdBytes)
            val entry = parseCentralDirectory(cdBytes, totalEntries)
                .firstOrNull { it.name == MANIFEST }
                ?: throw ApkRepackagerException("APK 里没有 $MANIFEST")
            val payload = readEntryPayload(raf, entry)
            // 用中央目录里的 CRC 与解压长度交叉验证；压缩流被截断时 inflate 可能仍「成功」。
            val crc = u32(entry.record, 16).toLong() and 0xFFFFFFFFL
            val uncomp = u32(entry.record, 24).toLong() and 0xFFFFFFFFL
            if (uncomp != 0L && payload.size.toLong() != uncomp) {
                throw ApkRepackagerException("清单解压长度不符：${payload.size} != $uncomp")
            }
            if (crc != 0L) {
                val actual = java.util.zip.CRC32().apply { update(payload) }.value
                if (actual != crc) throw ApkRepackagerException("清单 CRC 不符：$actual != $crc")
            }
            return payload
        }
    }

    // ---------------- 解析 ----------------

    private class CdEntry(
        val name: String,
        val record: ByteArray,
        val localOffset: Int,
    )

    private fun parseCentralDirectory(cd: ByteArray, totalEntries: Int): List<CdEntry> {
        val list = ArrayList<CdEntry>(totalEntries)
        var p = 0
        repeat(totalEntries) {
            if (p + 46 > cd.size) throw ApkRepackagerException("中央目录记录损坏 @ $p")
            if (u32(cd, p) != SIG_CD) throw ApkRepackagerException("中央目录记录签名不符 @ $p")
            val nameLen = u16(cd, p + 28)
            val extraLen = u16(cd, p + 30)
            val commentLen = u16(cd, p + 32)
            val recLen = 46 + nameLen + extraLen + commentLen
            if (p + recLen > cd.size) throw ApkRepackagerException("中央目录记录越界 @ $p")
            val name = String(cd, p + 46, nameLen, Charsets.UTF_8)
            val localOffset = u32(cd, p + 42)
            list += CdEntry(name, cd.copyOfRange(p, p + recLen), localOffset)
            p += recLen
        }
        if (p != cd.size) throw ApkRepackagerException("中央目录长度与记录不符：$p != ${cd.size}")
        return list
    }

    private fun readEntryPayload(raf: RandomAccessFile, entry: CdEntry): ByteArray {
        raf.seek(entry.localOffset.toLong())
        val lfh = ByteArray(30)
        raf.readFully(lfh)
        if (u32(lfh, 0) != SIG_LFH) throw ApkRepackagerException("清单条目本地头损坏")
        val flags = u16(lfh, 6)
        val method = u16(lfh, 8)
        val compSize = u32(lfh, 18)
        val nameLen = u16(lfh, 26)
        val extraLen = u16(lfh, 28)
        val lfhName = ByteArray(nameLen)
        raf.readFully(lfhName)
        if (lfhName.toString(Charsets.UTF_8) != MANIFEST) {
            throw ApkRepackagerException("本地头名字不符：${lfhName.toString(Charsets.UTF_8)}")
        }
        if (compSize.toLong() != (entry.record.size.let { u32(entry.record, 20).toLong() and 0xFFFFFFFFL })) {
            throw ApkRepackagerException("本地头与中央目录的压缩长度不一致")
        }
        if (flags and 0x08 != 0 || compSize == 0) {
            // 带 data descriptor 的条目长度写在数据后面，这里不做支持
            throw ApkRepackagerException("清单条目带 data descriptor，本实现不支持")
        }
        val bodyOff = entry.localOffset + 30 + nameLen + extraLen
        val body = ByteArray(compSize)
        raf.seek(bodyOff.toLong())
        raf.readFully(body)
        return when (method) {
            METHOD_STORED -> body
            METHOD_DEFLATE -> inflateRaw(body)
            else -> throw ApkRepackagerException("清单压缩方式不支持：$method")
        }
    }

    private fun signingBlockStart(raf: RandomAccessFile, cdOffset: Int): Long {
        if (cdOffset < 32) return cdOffset.toLong()
        val tail = ByteArray(16)
        raf.seek(cdOffset - 16L)
        raf.readFully(tail)
        if (!tail.contentEquals(APK_SIG_MAGIC)) return cdOffset.toLong()
        val sizeBuf = ByteArray(8)
        raf.seek(cdOffset - 24L)
        raf.readFully(sizeBuf)
        val blockSize = u64(sizeBuf, 0)
        val start = cdOffset - blockSize - 8
        if (start < 0 || start > cdOffset) throw ApkRepackagerException("APK Signing Block 长度非法")
        return start
    }

    private fun findEocd(raf: RandomAccessFile, fileLen: Long): Long {
        val scanLen = minOf(fileLen, 0xFFFFL + 22L).toInt()
        val buf = ByteArray(scanLen)
        raf.seek(fileLen - scanLen)
        raf.readFully(buf)
        var fallback = -1L
        for (i in scanLen - 22 downTo 0) {
            if (buf[i] == 0x50.toByte() && buf[i + 1] == 0x4b.toByte() &&
                buf[i + 2] == 0x05.toByte() && buf[i + 3] == 0x06.toByte()
            ) {
                // 只有「注释长度刚好补齐到文件尾」的候选才是真 EOCD：注释里也可能出现同样的
                // 签名，取错会一路报「中央目录记录签名不符」，把排查方向带偏。
                val commentLen = u16(buf, i + 20)
                val abs = fileLen - scanLen + i
                if (i + 22 + commentLen == scanLen) return abs
                if (fallback < 0) fallback = abs
            }
        }
        if (fallback >= 0) return fallback
        throw ApkRepackagerException("找不到 zip 结尾记录")
    }

    // ---------------- 构造 ----------------

    private fun buildLocalEntry(name: String, payload: ByteArray): ByteArray {
        val compressed = deflateRaw(payload)
        val crc = CRC32().apply { update(payload) }.value
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(30 + nameBytes.size + compressed.size)
        out.writeU32(SIG_LFH)
        out.writeU16(20)              // version needed
        out.writeU16(0)               // flags
        out.writeU16(METHOD_DEFLATE)
        out.writeU16(0)               // mod time
        out.writeU16(0)               // mod date
        out.writeU32(crc.toInt())
        out.writeU32(compressed.size)
        out.writeU32(payload.size)
        out.writeU16(nameBytes.size)
        out.writeU16(0)               // extra len
        out.write(nameBytes)
        out.write(compressed)
        return out.toByteArray()
    }

    /**
     * 重写中央目录：非清单条目原样保留，清单那一条**整条重生成**。
     *
     * 只回填 CRC / 长度会留下原记录里的 method、flags、extra 字段，而新本地头固定是
     * `flags=0 / deflate / extraLen=0`；两者一旦不一致（典型：zipalign -p 或非常规打包器
     * 产出 stored 清单），结果是签名合法、包却解析不出来，安装时报「解析软件包时出现问题」。
     */
    private fun buildCentralDirectory(
        entries: List<CdEntry>,
        manifestName: String,
        crc: Long,
        compSize: Int,
        uncompSize: Int,
        newLocalOffset: Long,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        for (e in entries) {
            if (e.name == manifestName) {
                out.write(rebuildManifestRecord(e.record, crc, compSize, uncompSize, newLocalOffset))
            } else {
                out.write(e.record)
            }
        }
        return out.toByteArray()
    }

    /** 按新本地头的真实参数重生成清单的中央目录记录（46 字节固定头 + 名字，无 extra / comment）。 */
    private fun rebuildManifestRecord(
        old: ByteArray,
        crc: Long,
        compSize: Int,
        uncompSize: Int,
        newLocalOffset: Long,
    ): ByteArray {
        val nameLen = u16(old, 28)
        val name = old.copyOfRange(46, 46 + nameLen)
        val rec = ByteArray(46 + nameLen)
        writeU32(rec, 0, SIG_CD)
        writeU16(rec, 4, 20)              // version made by
        writeU16(rec, 6, 20)              // version needed：stored→deflate 必须抬到 2.0
        writeU16(rec, 8, 0)               // flags：与本地头一致
        writeU16(rec, 10, METHOD_DEFLATE)
        writeU16(rec, 12, 0)              // mod time：本地头写的是 0，保持一致
        writeU16(rec, 14, 0)              // mod date
        writeU32(rec, 16, crc.toInt())
        writeU32(rec, 20, compSize)
        writeU32(rec, 24, uncompSize)
        writeU16(rec, 28, nameLen)
        writeU16(rec, 30, 0)              // extra len
        writeU16(rec, 32, 0)              // comment len
        writeU16(rec, 34, 0)              // disk number start
        writeU16(rec, 36, u16(old, 36))   // internal attrs
        writeU32(rec, 38, u32(old, 38))   // external attrs
        writeU32(rec, 42, newLocalOffset.toInt())
        name.copyInto(rec, 46)
        return rec
    }

    /** 写后自检：不重算摘要，只确认结构能对上，避免半成品被交付出去。 */
    private fun verifyWritten(file: File, expectedLen: Long, expectedCdOffset: Long, expectedEntries: Int) {
        RandomAccessFile(file, "r").use { out ->
            if (out.length() != expectedLen) {
                throw ApkRepackagerException("写后自检失败：文件长度 ${out.length()} != $expectedLen")
            }
            val eocdAbs = findEocd(out, expectedLen)
            val eocd = ByteArray(22)
            out.seek(eocdAbs)
            out.readFully(eocd)
            val entries = u16(eocd, 10)
            val cdSize = u32(eocd, 12)
            val cdOffset = u32(eocd, 16)
            if (cdOffset.toLong() != expectedCdOffset) {
                throw ApkRepackagerException("写后自检失败：中央目录偏移 $cdOffset != $expectedCdOffset")
            }
            if (entries != expectedEntries) {
                throw ApkRepackagerException("写后自检失败：条目数 $entries != $expectedEntries")
            }
            if (cdOffset.toLong() + cdSize + 22L != expectedLen) {
                throw ApkRepackagerException("写后自检失败：中央目录长度与文件长度不符")
            }
        }
    }

    /** Zip64 结尾定位器紧挨在 EOCD 之前；有它说明包里有 Zip64 字段，本实现会写坏。 */
    private fun hasZip64Locator(raf: RandomAccessFile, eocdAbs: Long): Boolean {
        if (eocdAbs < 20) return false
        val buf = ByteArray(4)
        raf.seek(eocdAbs - 20)
        raf.readFully(buf)
        return u32(buf, 0) == 0x07064b50
    }

    private fun inflateRaw(data: ByteArray): ByteArray {
        val inf = Inflater(true)
        return try {
            inf.setInput(data)
            val out = ByteArrayOutputStream(data.size * 4 + 64)
            val buf = ByteArray(8192)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break
                } else {
                    out.write(buf, 0, n)
                }
            }
            if (!inf.finished()) throw ApkRepackagerException("清单 deflate 流不完整")
            out.toByteArray()
        } finally {
            inf.end()
        }
    }

    private fun deflateRaw(data: ByteArray): ByteArray {
        val def = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        return try {
            def.setInput(data)
            def.finish()
            val out = ByteArrayOutputStream(data.size / 2 + 64)
            val buf = ByteArray(8192)
            while (!def.finished()) {
                val n = def.deflate(buf)
                if (n > 0) out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            def.end()
        }
    }

    // ---------------- 小端 ----------------

    private fun u16(d: ByteArray, off: Int): Int =
        (d[off].toInt() and 0xFF) or ((d[off + 1].toInt() and 0xFF) shl 8)

    private fun u32(d: ByteArray, off: Int): Int =
        (d[off].toInt() and 0xFF) or
            ((d[off + 1].toInt() and 0xFF) shl 8) or
            ((d[off + 2].toInt() and 0xFF) shl 16) or
            ((d[off + 3].toInt() and 0xFF) shl 24)

    private fun u64(d: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (d[off + i].toLong() and 0xFF)
        return v
    }

    private fun writeU16(d: ByteArray, off: Int, v: Int) {
        d[off] = (v and 0xFF).toByte()
        d[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun writeU32(d: ByteArray, off: Int, v: Int) {
        d[off] = (v and 0xFF).toByte()
        d[off + 1] = ((v ushr 8) and 0xFF).toByte()
        d[off + 2] = ((v ushr 16) and 0xFF).toByte()
        d[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun ByteArrayOutputStream.writeU16(v: Int) {
        write(v and 0xFF); write((v ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32(v: Int) {
        write(v and 0xFF); write((v ushr 8) and 0xFF)
        write((v ushr 16) and 0xFF); write((v ushr 24) and 0xFF)
    }
}
