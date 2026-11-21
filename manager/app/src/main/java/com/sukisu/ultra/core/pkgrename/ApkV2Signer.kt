package com.sukisu.ultra.core.pkgrename

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.interfaces.RSAKey

/** APK 签名失败。 */
class ApkSigningException(message: String) : Exception(message)

/**
 * APK Signature Scheme v2 签名器（自实现，不依赖 apksig）。
 *
 * 为什么不用 `com.android.tools.build:apksig`：那是纯 Java 桌面库，
 * 部分路径依赖 `java.nio.file`（API 26+），而本工程 minSdk 是 24。
 * 而内核的签名兜底认主只读 v2 块（`apk_sign.c` 读 id 0x7109871a），
 * Android 7.0+ 也只要求 v2 —— 所以只实现 v2 就够了。
 *
 * 签名流程（RFC 里叫 APK Signature Scheme v2）：
 *  1. 内容 = 「条目区」「中央目录」「结尾记录」**三段各自独立**；
 *  2. 每段自己按 1MB 切块（块不跨段），块摘要 = SHA-256(0xa5 || 块字节数 || 块内容)，
 *     再把所有段的块摘要按顺序拼起来算 SHA-256(0x5a || 块总数 || 各块摘要)；
 *  3. 参与摘要的结尾记录必须用**插入签名块之前**的原样内容（中央目录偏移还是旧值）；
 *     写进输出文件的结尾记录才是改过偏移的新值。实测 apksig 就是这么做的：
 *     用最终偏移会算出对不上的摘要，校验方只报 CHUNKED_SHA256 mismatch；
 *  4. 总摘要连同证书组成 signed data，用私钥对 signed data 做 RSA PKCS#1 签名；
 *  5. 签名块插在条目区与中央目录之间。
 */
internal object ApkV2Signer {

    private const val V2_BLOCK_ID = 0x7109871a
    private const val CHUNK_SIZE = 1024 * 1024
    /**
     * v2 的「摘要记录」头里放的是**签名算法 id**，不是摘要算法 id
     * （0x0103 = RSA PKCS#1 v1.5 + SHA-256）。写成 1 会被当成不支持的算法，
     * 校验方只报「签名记录畸形」，不指出是哪个字段。
     */
    private const val SIG_ALG_RSA_PKCS1_SHA256 = 0x0103
    private const val PADDING_BLOCK_ID = 0x42726577
    private val SIG_BLOCK_MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)

    /** 给 [unsigned] 签上 v2 签名，写出到 [out]。 */
    fun sign(unsigned: File, out: File, key: PrivateKey, certs: List<X509Certificate>) {
        if (certs.isEmpty()) throw ApkSigningException("缺少签名证书")

        val (cdOffset, cdSize, eocdAbs) = readLayout(unsigned)
        val eocdLen = (unsigned.length() - eocdAbs).toInt()
        val eocd = ByteArray(eocdLen)
        RandomAccessFile(unsigned, "r").use { raf ->
            raf.seek(eocdAbs)
            raf.readFully(eocd)
        }

        // 先用占位摘要做一次，拿到签名块的确切长度（只与定长字段有关，与摘要值无关）
        val probeSignedData = buildSignedData(ByteArray(32), key, certs)
        val probeBlock = buildSigningBlock(probeSignedData, sign(probeSignedData, key), key, certs)
        val blockTotal = probeBlock.size
        if (blockTotal % 4096 != 0) throw ApkSigningException("签名块长度未按 4096 对齐：$blockTotal")

        val patchedEocd = eocd.copyOf()
        writeU32(patchedEocd, 16, (cdOffset + blockTotal).toInt())

        // 参与摘要的是**未改偏移**的原样结尾记录（变量 eocd）；写进文件的是 patchedEocd。
        val digest = contentDigest(unsigned, cdOffset, cdSize, eocd)
        val signedData = buildSignedData(digest, key, certs)
        val block = buildSigningBlock(signedData, sign(signedData, key), key, certs)
        if (block.size != blockTotal) {
            throw ApkSigningException("签名块长度不稳定：${block.size} != $blockTotal")
        }

        out.parentFile?.mkdirs()
        FileChannel.open(unsigned.toPath(), StandardOpenOption.READ).use { src ->
            FileChannel.open(
                out.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { dst ->
                var pos = 0L
                while (pos < cdOffset) {
                    val n = src.transferTo(pos, cdOffset - pos, dst)
                    if (n <= 0L) throw ApkSigningException("拷贝条目区失败 @ $pos")
                    pos += n
                }
                dst.position(cdOffset)
                dst.write(ByteBuffer.wrap(block))
                var cdPos = 0L
                while (cdPos < cdSize) {
                    val n = src.transferTo(cdOffset + cdPos, cdSize - cdPos, dst)
                    if (n <= 0L) throw ApkSigningException("拷贝中央目录失败 @ $cdPos")
                    cdPos += n
                }
                dst.write(ByteBuffer.wrap(patchedEocd))
            }
        }
    }

    // ---------------- 内容摘要 ----------------

    /**
     * 按 v2 规范算内容摘要：三段各自分块，块摘要拼起来再求总摘要。
     *
     * [eocd] 必须是插入签名块之前的原样结尾记录，不能是改过中央目录偏移的那份。
     */
    private fun contentDigest(
        file: File,
        cdOffset: Long,
        cdSize: Long,
        eocd: ByteArray,
    ): ByteArray {
        val chunkDigests = ArrayList<ByteArray>()
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(CHUNK_SIZE)

        RandomAccessFile(file, "r").use { raf ->
            fun digestFileRange(base: Long, len: Long) {
                var pos = 0L
                while (pos < len) {
                    val n = minOf(CHUNK_SIZE.toLong(), len - pos).toInt()
                    raf.seek(base + pos)
                    raf.readFully(buf, 0, n)
                    chunkDigests.add(chunkDigest(md, buf, 0, n))
                    pos += n
                }
            }
            digestFileRange(0L, cdOffset)
            digestFileRange(cdOffset, cdSize)
        }

        var pos = 0
        while (pos < eocd.size) {
            val n = minOf(CHUNK_SIZE, eocd.size - pos)
            chunkDigests.add(chunkDigest(md, eocd, pos, n))
            pos += n
        }

        if (chunkDigests.isEmpty()) throw ApkSigningException("APK 内容为空")
        val finalMd = MessageDigest.getInstance("SHA-256")
        finalMd.update(0x5a.toByte())
        finalMd.update(le32(chunkDigests.size))
        for (d in chunkDigests) finalMd.update(d)
        return finalMd.digest()
    }

    /** 块摘要 = SHA-256(0xa5 || uint32LE(块字节数) || 块内容)。前缀错了只会得到 mismatch。 */
    private fun chunkDigest(md: MessageDigest, data: ByteArray, off: Int, len: Int): ByteArray {
        md.reset()
        md.update(0xa5.toByte())
        md.update(le32(len))
        md.update(data, off, len)
        return md.digest()
    }

    // ---------------- 签名块构造 ----------------

    private fun buildSignedData(digest: ByteArray, key: PrivateKey, certs: List<X509Certificate>): ByteArray {
        val digestRecord = ByteArrayOutputStream().apply {
            writeU32(sigAlgId(key))
            writeLp(digest)
        }.toByteArray()
        val digestsSeq = lp(digestRecord)

        val certsSeq = ByteArrayOutputStream().apply {
            for (c in certs) writeLp(c.encoded)
        }.toByteArray()
        // apksig 的 generateSignerBlock 里这一串是 4 个元素：
        // digests、certificates、additionalAttributes、空数组。
        // 末尾这个空 lp（4 个全零字节）不能省，否则 signedData 比 apksig 短 4 字节。
        return concat(lp(digestsSeq), lp(certsSeq), lp(ByteArray(0)), lp(ByteArray(0)))
    }

    private fun buildSigningBlock(
        signedData: ByteArray,
        signatureBytes: ByteArray,
        key: PrivateKey,
        certs: List<X509Certificate>,
    ): ByteArray {
        val sigRecord = ByteArrayOutputStream().apply {
            writeU32(sigAlgId(key))
            writeLp(signatureBytes)
        }.toByteArray()
        val sigsSeq = lp(sigRecord)

        val signer = concat(
            lp(signedData),
            lp(sigsSeq),
            lp(certs[0].publicKey.encoded),
        )
        // 两层长度前缀：pair 的值 = lp(若干 signer)，每个 signer 自己也带一层 lp。
        // 只写一层时校验方读到的 signer 会把签名序列当成尾部垃圾而判为畸形。
        val value = lp(lp(signer))

        val pair = ByteArrayOutputStream().apply {
            writeU64(4L + value.size)
            writeU32(V2_BLOCK_ID)
            write(value)
        }.toByteArray()

        // apksigner 的做法：补一个「填充对」，把整个签名块补到 4096 的整数倍。
        // 校验方会忽略不认识的 id；不补的话签名块长度是任意值，
        // 中央目录的起始偏移会跟着错位，zipalign 的对齐就此报废。
        // 注意 total = 8(前长度) + pair + 12(填充对头) + padBytes + 8(后长度) + 16(magic)
        val padBytes = (4096 - (pair.size + 44) % 4096) % 4096
        val blockSize = (pair.size + 44 + padBytes - 8).toLong()
        return ByteArrayOutputStream().apply {
            writeU64(blockSize)
            write(pair)
            writeU64(4L + padBytes)
            writeU32(PADDING_BLOCK_ID)
            write(ByteArray(padBytes))
            writeU64(blockSize)
            write(SIG_BLOCK_MAGIC)
        }.toByteArray()
    }

    private fun sign(data: ByteArray, key: PrivateKey): ByteArray {
        sigAlgId(key)
        val alg = "SHA256withRSA"
        return Signature.getInstance(alg).run {
            initSign(key)
            update(data)
            sign()
        }
    }

    /**
     * 只支持 RSA PKCS#1 v1.5 + SHA-256。
     *
     * 内置密钥固定是 RSA-2048，签名算法一旦换成别的，摘要算法也得跟着换
     * （SHA-512 的摘要长度是 64），两条路径没必要都留着。
     */
    private fun sigAlgId(key: PrivateKey): Int {
        val bits = (key as? RSAKey)?.modulus?.bitLength() ?: 0
        if (bits == 0 || bits > 3072) {
            throw ApkSigningException("只支持 RSA 2048 位（及以下）密钥签名，当前 $bits 位")
        }
        return SIG_ALG_RSA_PKCS1_SHA256
    }

    // ---------------- 布局读取 ----------------

    private data class Layout(val cdOffset: Long, val cdSize: Long, val eocdAbs: Long)

    private fun readLayout(file: File): Layout {
        val fileLen = file.length()
        val scanLen = minOf(fileLen, 0xFFFFL + 22L).toInt()
        val tail = ByteArray(scanLen)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(fileLen - scanLen)
            raf.readFully(tail)
        }
        for (i in scanLen - 22 downTo 0) {
            if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4b.toByte() &&
                tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()
            ) {
                val cdSize = u32(tail, i + 12).toLong() and 0xFFFFFFFFL
                val cdOffset = u32(tail, i + 16).toLong() and 0xFFFFFFFFL
                return Layout(cdOffset, cdSize, fileLen - scanLen + i)
            }
        }
        throw ApkSigningException("找不到 zip 结尾记录")
    }

    // ---------------- 字节工具 ----------------

    private fun lp(block: ByteArray): ByteArray = ByteArrayOutputStream(4 + block.size).apply {
        writeU32(block.size)
        write(block)
    }.toByteArray()

    /** 往流里写一个「长度前缀 + 内容」的块（长度是内容字节数）。 */
    private fun ByteArrayOutputStream.writeLp(block: ByteArray) {
        writeU32(block.size)
        write(block)
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(parts.sumOf { it.size })
        for (p in parts) out.write(p)
        return out.toByteArray()
    }

    private fun le32(v: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = (v and 0xFF).toByte()
        b[1] = ((v ushr 8) and 0xFF).toByte()
        b[2] = ((v ushr 16) and 0xFF).toByte()
        b[3] = ((v ushr 24) and 0xFF).toByte()
        return b
    }

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

    private fun ByteArrayOutputStream.writeU32(v: Int) {
        write(v and 0xFF); write((v ushr 8) and 0xFF)
        write((v ushr 16) and 0xFF); write((v ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU64(v: Long) {
        for (i in 0 until 8) write((((v ushr (8 * i)) and 0xFF).toInt()))
    }
}
