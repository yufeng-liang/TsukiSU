package com.sukisu.ultra.core.pkgrename

import android.content.Context
import java.io.File

/** 换包名失败。 */
class PackageRenameException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 一次换包名的产物。 */
internal data class PackageRenameResult(
    val apkFile: File,
    val oldPackage: String,
    val newPackage: String,
    val certSha256Hex: String,
    val certSize: Int,
)

/**
 * 运行时换包名：把当前正在运行的 APK 复制一份、改掉 AndroidManifest 里的包名、用内置密钥重签。
 *
 * 为什么改完的 APK 还能跑：Android 里包名是清单里的一个字符串，
 * 代码里的 R 类、资源引用全部基于编译期命名空间（`com.sukisu.ultra`），与安装包名无关，
 * 所以只换清单字符串就够了，不需要重新编译，也不需要 aapt2。
 *
 * 代价：新包名与内核里编译期写死的 `KSU_PACKAGE_NAME` 不一致，内核凭包名认不出它。
 * 两条后路见 [ManagerKeyMaterial] 的注释。
 */
internal object PackageRenameEngine {

    /** 改名产物的落盘目录（私有，不进 cache，改名成功后还要留给用户回退）。 */
    private const val OUT_DIR = "pkgrename"

    /**
     * 构建改名后的 APK。
     *
     * @param sourceApk 当前运行中的 APK，一般是 `context.applicationInfo.sourceDir`
     * @param oldPackage 当前包名，一般是 `context.packageName`
     * @param newPackage 目标包名，必须是 [PackageNameUtils.isValid] 的
     */
    fun buildRenamedApk(
        context: Context,
        sourceApk: File,
        oldPackage: String,
        newPackage: String,
        key: ManagerKeyMaterial = ManagerKeyProvider.get(context),
    ): PackageRenameResult {
        PackageNameUtils.requireValid(newPackage)
        if (newPackage == oldPackage) {
            throw PackageRenameException("新包名与当前包名相同")
        }
        if (!sourceApk.isFile) {
            throw PackageRenameException("找不到当前 APK：${sourceApk.absolutePath}")
        }

        val workDir = File(context.cacheDir, OUT_DIR).apply { mkdirs() }
        val unsigned = File(workDir, "unsigned.apk")
        val output = File(outputDir(context), "$newPackage.apk")

        try {
            ApkRepackager.repack(sourceApk, unsigned) { manifest ->
                val hits = ManifestPackageRewriter.countOccurrences(manifest, oldPackage)
                if (hits == 0) {
                    throw PackageRenameException(
                        "清单里没有找到包名 $oldPackage，APK 可能被加固或经过特殊处理",
                    )
                }
                val patched = ManifestPackageRewriter.rewrite(manifest, oldPackage, newPackage)
                if (!ManifestPackageRewriter.verifyReplaced(patched, oldPackage, newPackage)) {
                    throw PackageRenameException("清单替换后校验失败：旧包名仍有残留或新包名缺失")
                }
                patched
            }

            ApkV2Signer.sign(unsigned, output, key.privateKey, listOf(key.certificate))
        } catch (e: PackageRenameException) {
            throw e
        } catch (e: Exception) {
            throw PackageRenameException("换包名失败：${e.message}", e)
        } finally {
            unsigned.delete()
        }

        return PackageRenameResult(
            apkFile = output,
            oldPackage = oldPackage,
            newPackage = newPackage,
            certSha256Hex = key.certSha256Hex,
            certSize = key.certSize,
        )
    }

    /** 改名产物的存放目录。 */
    fun outputDir(context: Context): File = File(context.filesDir, OUT_DIR).apply { mkdirs() }

    /** 清掉所有改名产物（回退或清理时用）。 */
    fun clearOutputs(context: Context) {
        outputDir(context).listFiles()?.forEach { it.delete() }
    }
}
