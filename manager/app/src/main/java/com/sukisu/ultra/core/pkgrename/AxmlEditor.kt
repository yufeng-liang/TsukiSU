package com.sukisu.ultra.core.pkgrename

import java.io.ByteArrayOutputStream

/** AXML 解析或改写失败。 */
class AxmlException(message: String) : Exception(message)

/**
 * AXML（Android 二进制 XML）字符串池改写器。
 *
 * 只做一件事：把字符串池里的字符串按 [transform] 替换，其余字节原样保留。
 * 换安装包名用得上，因为 AndroidManifest.xml 的 package 就是池里的一个字符串。
 *
 * 三条不变量，违反任何一条都会被系统的 ResXMLTree 拒收：
 *  1. 字符串数量与顺序不能变 —— XML 节点是按**索引**引用字符串的，顺序一动全部错位；
 *  2. 编码风格（UTF-8 / UTF-16）必须与原池一致；
 *  3. 每条字符串补齐到几字节边界，必须与原池一致。
 *
 * 第 3 点各家构建工具不一样，所以解析时用「重建 + 逐字节比对」反推：
 * 依次尝试 4 / 0 / 2 字节补齐，只有能完整复现原块的才算解析成功。
 * 宁可报错，也不产出一个会被系统读坏的清单。
 */
internal object AxmlEditor {

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_TYPE = 0x0003
    private const val UTF8_FLAG = 0x100

    /** 按 [transform] 改写 AXML 的字符串池。 */
    fun rewriteStrings(data: ByteArray, transform: (String) -> String): ByteArray {
        if (data.size < 8) throw AxmlException("AXML 太短")
        val type = u16(data, 0)
        if (type != RES_XML_TYPE) {
            throw AxmlException("不是 AXML 文件（type=0x%04x）".format(type))
        }
        val fileHeaderSize = u16(data, 2)
        val fileSize = u32(data, 4)
        var off = fileHeaderSize
        while (off < fileSize) {
            val chunkType = u16(data, off)
            val chunkSize = u32(data, off + 4)
            if (chunkSize <= 0) throw AxmlException("块长度非法：$chunkSize @ $off")
            if (chunkType == RES_STRING_POOL_TYPE) {
                val (pool, oldSize) = StringPool.parse(data, off)
                val replaced = pool.strings.map(transform)
                pool.strings = replaced
                val newPool = pool.build()
                val out = ByteArrayOutputStream(data.size + newPool.size)
                out.write(data, 0, off)
                out.write(newPool)
                out.write(data, off + oldSize, data.size - off - oldSize)
                val bytes = out.toByteArray()
                // 字符串池长度变了，回填文件头里的总长度
                writeU32(bytes, 4, bytes.size)
                return bytes
            }
            off += chunkSize
        }
        throw AxmlException("AXML 里没有字符串池")
    }

    /** 列出字符串池内容，调试用。 */
    fun listStrings(data: ByteArray): List<String> {
        val fileHeaderSize = u16(data, 2)
        val fileSize = u32(data, 4)
        var off = fileHeaderSize
        while (off < fileSize) {
            if (off + 8 > data.size) throw AxmlException("AXML 块头越界 @ $off")
            val chunkType = u16(data, off)
            val chunkSize = u32(data, off + 4)
            // 损坏的清单可能给 0 或超出文件的长度，不拦住就会在这里死循环。
            if (chunkSize <= 0 || off + chunkSize > data.size) {
                throw AxmlException("AXML 块长度异常 @ $off：$chunkSize")
            }
            if (chunkType == RES_STRING_POOL_TYPE) return StringPool.parse(data, off).first.strings
            off += chunkSize
        }
        throw AxmlException("AXML 里没有字符串池")
    }

    // ---------------- 字符串池 ----------------

    private class StringPool(
        var strings: List<String>,
        private val styleCount: Int,
        private val flags: Int,
        private val padUnit: Int,
    ) {
        private val utf8: Boolean get() = (flags and UTF8_FLAG) != 0

        fun build(): ByteArray {
            val encoded = ArrayList<ByteArray>(strings.size)
            for (s in strings) {
                var buf: ByteArray
                if (utf8) {
                    val body = s.toByteArray(Charsets.UTF_8)
                    // AXML 的 UTF-8 串记录的是 UTF-16 码元数，不是字节数
                    val codeUnits = s.toByteArray(Charsets.UTF_16LE).size / 2
                    buf = ByteArrayOutputStream().apply {
                        write(lenUtf8(codeUnits))
                        write(lenUtf8(body.size))
                        write(body)
                        write(0)
                    }.toByteArray()
                } else {
                    val body = s.toByteArray(Charsets.UTF_16LE)
                    buf = ByteArrayOutputStream().apply {
                        write(lenUtf16(body.size / 2))
                        write(body)
                        write(0)
                        write(0)
                    }.toByteArray()
                }
                if (padUnit != 0) {
                    val rem = buf.size % padUnit
                    if (rem != 0) buf += ByteArray(padUnit - rem)
                }
                encoded += buf
            }

            val dataLen = encoded.sumOf { it.size }
            val strCount = encoded.size
            val headerSize = 28
            val stringsStart = headerSize + 4 * strCount + 4 * styleCount
            var total = stringsStart + dataLen + 4 * styleCount
            val pad = (4 - total % 4) % 4
            total += pad

            val out = ByteArrayOutputStream(total)
            out.writeU16(RES_STRING_POOL_TYPE)
            out.writeU16(headerSize)
            out.writeU32(total)
            out.writeU32(strCount)
            out.writeU32(styleCount)
            out.writeU32(flags)
            out.writeU32(stringsStart)
            out.writeU32(if (styleCount == 0) 0 else stringsStart + dataLen)

            var cursor = 0
            for (buf in encoded) {
                out.writeU32(cursor)
                cursor += buf.size
            }
            repeat(styleCount) { out.writeU32(0) }
            for (buf in encoded) out.write(buf)
            repeat(pad) { out.write(0) }

            val bytes = out.toByteArray()
            if (bytes.size != total) {
                throw AxmlException("字符串池长度自检失败：${bytes.size} != $total")
            }
            return bytes
        }

        companion object {
            fun parse(data: ByteArray, off: Int): Pair<StringPool, Int> {
                val type = u16(data, off)
                if (type != RES_STRING_POOL_TYPE) {
                    throw AxmlException("偏移 $off 不是字符串池（type=0x%04x）".format(type))
                }
                val headerSize = u16(data, off + 2)
                val size = u32(data, off + 4)
                val strCount = u32(data, off + 8)
                val styleCount = u32(data, off + 12)
                val flags = u32(data, off + 16)
                val stringsStart = u32(data, off + 20)
                val utf8 = (flags and UTF8_FLAG) != 0
                if (strCount < 0 || strCount > 1 shl 20) throw AxmlException("字符串数量异常：$strCount")

                val offsBase = off + headerSize
                val strings = ArrayList<String>(strCount)
                for (i in 0 until strCount) {
                    val valueOff = u32(data, offsBase + 4 * i)
                    var p = off + stringsStart + valueOff
                    if (p < 0 || p >= data.size) throw AxmlException("字符串偏移越界：$p")
                    if (utf8) {
                        p = readLenUtf8(data, p).second // 字符数，忽略
                        val (byteLen, next) = readLenUtf8(data, p)
                        p = next
                        if (p + byteLen > data.size) throw AxmlException("UTF-8 串越界")
                        strings += String(data, p, byteLen, Charsets.UTF_8)
                    } else {
                        val (charLen, next) = readLenUtf16(data, p)
                        p = next
                        if (p + charLen * 2 > data.size) throw AxmlException("UTF-16 串越界")
                        strings += String(data, p, charLen * 2, Charsets.UTF_16LE)
                    }
                }

                if (styleCount != 0) {
                    throw AxmlException("字符串池带 style 数据（$styleCount 条），本实现不改写这类清单")
                }
                val chunk = data.copyOfRange(off, off + size)
                for (pad in intArrayOf(4, 0, 2)) {
                    val candidate = StringPool(strings, styleCount, flags, pad)
                    if (candidate.build().contentEquals(chunk)) return candidate to size
                }
                throw AxmlException("无法逐字节复现原字符串池，拒绝改写")
            }
        }
    }

    // ---------------- 小端读写 ----------------

    private fun u16(d: ByteArray, off: Int): Int =
        (d[off].toInt() and 0xFF) or ((d[off + 1].toInt() and 0xFF) shl 8)

    private fun u32(d: ByteArray, off: Int): Int =
        (d[off].toInt() and 0xFF) or
            ((d[off + 1].toInt() and 0xFF) shl 8) or
            ((d[off + 2].toInt() and 0xFF) shl 16) or
            ((d[off + 3].toInt() and 0xFF) shl 24)

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

    /** UTF-8 池的变长长度：最高位为 1 时占两字节。 */
    private fun readLenUtf8(d: ByteArray, start: Int): Pair<Int, Int> {
        var p = start
        val b = d[p++].toInt() and 0xFF
        if (b and 0x80 != 0) {
            val v = ((b and 0x7F) shl 8) or (d[p++].toInt() and 0xFF)
            return v to p
        }
        return b to p
    }

    private fun lenUtf8(n: Int): ByteArray =
        if (n > 0x7F) byteArrayOf((0x80 or ((n ushr 8) and 0x7F)).toByte(), (n and 0xFF).toByte())
        else byteArrayOf(n.toByte())

    private fun readLenUtf16(d: ByteArray, start: Int): Pair<Int, Int> {
        var p = start
        val v = u16(d, p); p += 2
        if (v and 0x8000 != 0) {
            val v2 = u16(d, p); p += 2
            return (((v and 0x7FFF) shl 16) or v2) to p
        }
        return v to p
    }

    private fun lenUtf16(n: Int): ByteArray {
        if (n > 0x7FFF) {
            val hi = 0x8000 or ((n ushr 16) and 0x7FFF)
            val lo = n and 0xFFFF
            return byteArrayOf(
                (hi and 0xFF).toByte(), ((hi ushr 8) and 0xFF).toByte(),
                (lo and 0xFF).toByte(), ((lo ushr 8) and 0xFF).toByte(),
            )
        }
        return byteArrayOf((n and 0xFF).toByte(), ((n ushr 8) and 0xFF).toByte())
    }
}
