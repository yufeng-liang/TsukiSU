package com.sukisu.ultra.core.pkgrename

import android.content.Context
import com.sukisu.ultra.ui.util.DynamicManagerSignature
import com.sukisu.ultra.ui.util.addDynamicManager
import com.sukisu.ultra.ui.util.getDynamicManagers
import com.sukisu.ultra.ui.util.setDynamicManagersEnabled
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
    /** 改名前那份 APK 的备份：万一新包用不了，靠它装回原名。 */
    val backupApk: File,
    val dynamicManagerEnabledByUs: Boolean,
)

/**
 * 新包已经装进系统，但没能登记进内核。
 *
 * 这是最危险的中间态：新包此刻没有内核权限，而旧包装着时仍可能被内核当成管理器。
 * UI 必须把它和「整次换名失败」区分开，否则用户会按提示卸载旧包，最后两份都用不了。
 */
internal class RegistrationFailed(
    val newPackage: String,
    val apkFile: File,
    cause: Throwable,
) : Exception(cause.message, cause)

/**
 * 把「改包名 → 装新包 → 内核登记」串起来。
 *
 * 顺序不能反：内核的 `dynamic_manager` 需要按包名查 uid，包没装就查不到；
 * ADD 之后内核会按 uid 立刻授权（`ksu_manager_uid_authorized`），
 * 条目里的证书哈希目前只做记录，不参与授权判定。
 */
internal class PackageRenameCoordinator(private val context: Context) {

    /** 改名前的原版 APK 备份目录。 */
    private fun backupDir(): File = File(context.filesDir, "pkgrename-original").apply { mkdirs() }

    /**
     * 备份当前 APK，便于用户想还原时重新装回原名。
     *
     * 先写 `.tmp` 再改名，避免上次写到一半留下的同长度残片被当成有效备份。
     *
     * @return 备份文件；已存在且长度一致则直接复用
     */
    fun backupOriginalApk(): File {
        val src = File(context.applicationInfo.sourceDir)
        val dest = File(backupDir(), "${context.packageName}.apk")
        if (dest.isFile && dest.length() == src.length()) return dest

        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.delete()
        src.inputStream().use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
        if (tmp.length() != src.length()) {
            tmp.delete()
            throw PackageRenameException("备份当前 APK 失败：写入长度与源文件不一致")
        }
        if (dest.exists() && !dest.delete()) {
            tmp.delete()
            throw PackageRenameException("备份当前 APK 失败：无法替换旧备份")
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            throw PackageRenameException("备份当前 APK 失败：无法重命名备份文件")
        }
        return dest
    }

    /** 备份目录里只保留当前包名那一份。 */
    private fun pruneBackups() {
        val keep = "${context.packageName}.apk"
        backupDir().listFiles()?.forEach { if (it.name != keep) it.delete() }
    }

    /**
     * 执行一次换包名。
     *
     * @param onStage 进度回调：在调用方线程上同步回调（UI 从主线程发起，因此天然在主线程）
     */
    suspend fun rename(
        newPackage: String = PackageNameUtils.random(),
        onStage: (RenameStage) -> Unit = {},
    ): RenameOutcome {
        val oldPackage = context.packageName
        PackageNameUtils.requireValid(newPackage)
        if (newPackage == oldPackage) throw PackageRenameException("新包名与当前包名相同")

        // 首次调用会现场生成 RSA 2048，必须在 IO 线程做；registerNewPackage 里那段本来就在 IO 里。
        val key = withContext(Dispatchers.IO) { ManagerKeyProvider.get(context) }

        // 预检：旧内核/旧 ksud 没有 manager 子命令时，新的包一旦装上就再也登记不了，
        // 而那时旧包还在、新包永远是孤儿。所以宁可什么都不做，先确认登记能力存在。
        withContext(Dispatchers.IO) {
            runCatching { getDynamicManagers() }
        }.exceptionOrNull()?.let {
            throw PackageRenameException(
                "当前内核或 ksud 不支持动态管理器登记，已中止（没有安装新包）：${it.message}",
                it,
            )
        }

        // 每次改名都是一个新随机名，产物与备份都是 39MB 级别，先清掉上一次的。
        withContext(Dispatchers.IO) {
            PackageRenameEngine.pruneOutputs(context)
            pruneBackups()
        }

        onStage(RenameStage.BUILDING)
        val backup = withContext(Dispatchers.IO) { backupOriginalApk() }
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
        val sessionId = withContext(Dispatchers.IO) {
            // 先落盘再提交：进程在安装过程中被杀时，启动后靠这条记录把登记补上。
            RenameJournal.write(
                context,
                RenameJournal.Pending(
                    newPackage = newPackage,
                    oldPackage = oldPackage,
                    apkPath = result.apkFile.absolutePath,
                    certSize = key.certSize,
                    certHash = key.certSha256Hex,
                ),
            )
            try {
                PackageRenameInstaller.submit(context, result.apkFile, newPackage)
            } catch (t: Throwable) {
                RenameJournal.clear(context)
                throw t
            }
        }

        val outcome = withTimeoutOrNull(INSTALL_TIMEOUT_MS) {
            PackageRenameInstallBridge.await(sessionId).await()
        }
            ?: run {
                // 超时后这条会话的等待点不会再有人消费，留着只会在下次换名时被误当成结果。
                PackageRenameInstallBridge.discard(sessionId)
                throw PackageRenameException(
                    "安装超时，没有收到系统回调；新包可能已装上，重新进入本页会自动继续登记",
                )
            }
        PackageRenameInstallBridge.discard(sessionId)

        when (outcome) {
            is InstallOutcome.Failure -> {
                RenameJournal.clear(context)
                throw PackageRenameException("新包安装失败：${outcome.message}")
            }

            is InstallOutcome.Success -> {
                // 身份由 sessionId 路由保证（PackageRenameInstallBridge 按会话分派回调）。
                // 回调里的包名是我们自己 putExtra 写进去的，比对它只会得到恒真结果，别当防线。
            }
        }

        onStage(RenameStage.REGISTERING)
        val enabledByUs = try {
            registerNewPackage(newPackage)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            throw RegistrationFailed(newPackage, result.apkFile, t)
        }
        RenameJournal.clear(context)

        onStage(RenameStage.DONE)
        return RenameOutcome(
            newPackage = newPackage,
            oldPackage = oldPackage,
            apkFile = result.apkFile,
            backupApk = backup,
            dynamicManagerEnabledByUs = enabledByUs,
        )
    }

    /**
     * 打开动态管理器开关（如果关着）并把包名登记进内核，然后回读校验。
     *
     * ADD 之后内核按 uid 授权（`ksu_manager_uid_authorized`），条目里的证书哈希只做记录，
     * 所以这里校验「条目存在 + 证书信息一致」，而不是 verified 位。
     *
     * @return 这次调用是否替用户打开了动态管理器开关
     */
    suspend fun registerNewPackage(newPackage: String): Boolean = withContext(Dispatchers.IO) {
        val key = ManagerKeyProvider.get(context)
        val signature = DynamicManagerSignature(key.certSize, key.certSha256Hex)
        var enabledByUs = false
        try {
            val state = getDynamicManagers()
            if (!state.enabled) {
                setDynamicManagersEnabled(true)
                enabledByUs = true
            }
            registerWithRetry(newPackage, signature)

            // 回读校验：登记不生效时宁可报错，也不能让用户按提示把旧包卸掉。
            val after = getDynamicManagers()
            if (!after.enabled) throw PackageRenameException("动态管理器开关没有打开，登记不生效")
            val entry = after.entries.firstOrNull { it.packageName == newPackage }
                ?: throw PackageRenameException("登记后回读不到 $newPackage 这一条")
            if (entry.certSize != key.certSize ||
                !entry.hash.equals(key.certSha256Hex, ignoreCase = true)
            ) {
                throw PackageRenameException("回读到的证书信息与内置密钥不一致")
            }
            // 内核真正用来授权的是 appid（ksu_dynamic_manager_uid_registered 只比 uid），
            // 条目里的 uid 由 ksud 从 packages.list 查出来。查错/查到旧记录时上面几项全都通过，
            // 内核却不认新包，所以这里必须再断言 uid 既不是无效值也不是旧包的 appid。
            val oldAppid = context.applicationInfo.uid % 100000
            if (entry.uid == 0 || entry.uid == oldAppid) {
                throw PackageRenameException("内核条目 uid=${entry.uid} 与预期不符（旧包 appid=$oldAppid）")
            }
            enabledByUs
        } catch (t: Throwable) {
            // 这个开关是内核全局姿态：打开后表里任何已登记 uid 都能拿 manager 权限。
            // enabledByUs 只会在「原本关着、本次打开」时为真，所以失败时关回去就是恢复用户原状态；
            // 表里还有别人的条目也不能例外，否则等于把用户没开的权限留在开启状态。
            if (enabledByUs && t !is CancellationException) {
                withContext(NonCancellable) {
                    runCatching { setDynamicManagersEnabled(false) }
                }
            }
            throw t
        }
    }

    /** ksud 刚装完包时 packages.list 可能还没刷新，所以失败要重试几次。 */
    private suspend fun registerWithRetry(newPackage: String, signature: DynamicManagerSignature) {
        var lastError: Throwable? = null
        for (attempt in 0 until REGISTER_ATTEMPTS) {
            try {
                addDynamicManager(newPackage, signature)
                return
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                lastError = t
                // ksud 是「ioctl 写内核内存 → persist 写状态文件」两步，persist 失败时内核其实已经
                // 认了这条。当成登记失败会让用户去卸旧包，重启后新包反而丢权限，所以先回读确认。
                if (isRegisteredInKernel(newPackage)) {
                    val persisted = runCatching { addDynamicManager(newPackage, signature) }.isSuccess
                    if (!persisted) {
                        throw PackageRenameException(
                            "内核已经接受登记，但状态文件写入失败；重启后会丢失，先别卸载旧包，稍后重试即可",
                            t,
                        )
                    }
                    return
                }
                delay(400L * (attempt + 1))
            }
        }
        throw PackageRenameException("向内核登记新包名失败：${lastError?.message}", lastError)
    }

    /** 内核侧是否已经有这条登记（不论状态文件有没有写成功）。 */
    private suspend fun isRegisteredInKernel(newPackage: String): Boolean =
        runCatching { getDynamicManagers().entries.any { it.packageName == newPackage } }.getOrDefault(false)

    companion object {
        /** 安装回调的等待上限；用户手动确认时可能要等一会儿。 */
        private const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L

        private const val REGISTER_ATTEMPTS = 5
    }
}
