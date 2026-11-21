package com.sukisu.ultra.core.pkgrename

import android.content.Context
import com.sukisu.ultra.ui.util.DynamicManagerSignature
import com.sukisu.ultra.ui.util.addDynamicManager
import com.sukisu.ultra.ui.util.getDynamicManagers
import com.sukisu.ultra.ui.util.setDynamicManagersEnabled
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 换包名进行到哪一步。 */
enum class RenameStage {
    BUILDING,
    INSTALLING,
    REGISTERING,
    DONE,
}

/** 换包名结果。 */
internal data class RenameOutcome(
    val newPackage: String,
    val oldPackage: String,
    val apkFile: File,
    val dynamicManagerEnabledByUs: Boolean,
)

/**
 * 把「改包名 → 装新包 → 内核登记」串起来。
 *
 * 顺序不能反：内核的 `dynamic_manager` 需要按包名查 uid，包没装就查不到；
 * 而且 ADD 之后内核会立刻重扫（`track_throne(false)`），验证签名通过才把新包加冕。
 */
internal class PackageRenameCoordinator(private val context: Context) {

    /** 改名前的原版 APK 备份目录。 */
    private fun backupDir(): File = File(context.filesDir, "pkgrename-original").apply { mkdirs() }

    /**
     * 备份当前 APK，便于用户想还原时重新装回原名。
     *
     * @return 备份文件；已存在则直接返回
     */
    fun backupOriginalApk(): File {
        val src = File(context.applicationInfo.sourceDir)
        val dest = File(backupDir(), "${context.packageName}.apk")
        if (dest.isFile && dest.length() == src.length()) return dest
        dest.parentFile?.mkdirs()
        src.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    /**
     * 执行一次换包名。
     *
     * @param onStage 进度回调（在主线程以外调用，UI 自行切线程）
     */
    suspend fun rename(
        newPackage: String = PackageNameUtils.random(),
        onStage: (RenameStage) -> Unit = {},
    ): RenameOutcome {
        val oldPackage = context.packageName
        PackageNameUtils.requireValid(newPackage)
        if (newPackage == oldPackage) throw PackageRenameException("新包名与当前包名相同")

        val key = ManagerKeyProvider.get(context)

        onStage(RenameStage.BUILDING)
        backupOriginalApk()
        val result = withContext(Dispatchers.IO) {
            PackageRenameEngine.buildRenamedApk(
                context = context,
                sourceApk = File(context.applicationInfo.sourceDir),
                oldPackage = oldPackage,
                newPackage = newPackage,
                key = key,
            )
        }

        onStage(RenameStage.INSTALLING)
        PackageRenameInstallBridge.reset()
        PackageRenameInstaller.submit(context, result.apkFile, newPackage)
        val outcome = withTimeoutOrNull(INSTALL_TIMEOUT_MS) {
            PackageRenameInstallBridge.outcomes.first()
        } ?: throw PackageRenameException("安装超时，没有收到系统回调")

        when (outcome) {
            is InstallOutcome.Failure -> throw PackageRenameException("新包安装失败：${outcome.message}")
            is InstallOutcome.Success -> Unit
        }

        onStage(RenameStage.REGISTERING)
        val signature = DynamicManagerSignature(key.certSize, key.certSha256Hex)
        var enabledByUs = false
        withContext(Dispatchers.IO) {
            val state = getDynamicManagers()
            if (!state.enabled) {
                setDynamicManagersEnabled(true)
                enabledByUs = true
            }
            // 不在这里查 PackageManager：应用侧查别的包受「包可见性」限制，可能查不到。
            // ksud 以 root 身份查包，不受限制；刚装完可能还没登记完，所以重试几次。
            var lastError: Throwable? = null
            for (attempt in 0 until 5) {
                try {
                    addDynamicManager(newPackage, signature)
                    lastError = null
                    break
                } catch (t: Throwable) {
                    lastError = t
                    kotlinx.coroutines.delay(400L * (attempt + 1))
                }
            }
            lastError?.let { throw PackageRenameException("向内核登记新包名失败：${it.message}", it) }
        }

        onStage(RenameStage.DONE)
        return RenameOutcome(
            newPackage = newPackage,
            oldPackage = oldPackage,
            apkFile = result.apkFile,
            dynamicManagerEnabledByUs = enabledByUs,
        )
    }

    companion object {
        /** 安装回调的等待上限；用户手动确认时可能要等一会儿。 */
        private const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
