package com.sukisu.ultra.core.pkgrename

import android.content.Context
import com.sukisu.ultra.ui.util.DynamicManagerSignature
import com.sukisu.ultra.ui.util.addDynamicManager
import com.sukisu.ultra.ui.util.getDynamicManagers
import com.sukisu.ultra.ui.util.setDynamicManagersEnabled
import java.io.File
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「新包已经提交安装、但内核还没登记」这件事的落盘记录。
 *
 * 之所以要落盘：提交安装后系统会把安装确认界面弹到前台，App 退到后台在低内存设备上被杀是常态。
 * 一旦进程没了，内存里的等待点、协程、计时器全部消失，用户看到的是「新包装上了、登记没做、
 * 界面回到初始状态」，而且没有任何自动恢复途径。
 */
internal object RenameJournal {

    private const val FILE_NAME = "pkgrename-pending.properties"

    private const val KEY_NEW_PACKAGE = "newPackage"
    private const val KEY_OLD_PACKAGE = "oldPackage"
    private const val KEY_APK_PATH = "apkPath"
    private const val KEY_CERT_SIZE = "certSize"
    private const val KEY_CERT_HASH = "certHash"

    /** 一条待登记的记录。 */
    data class Pending(
        val newPackage: String,
        val oldPackage: String,
        val apkPath: String,
        val certSize: Int,
        val certHash: String,
    )

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun write(context: Context, pending: Pending) {
        val props = Properties().apply {
            setProperty(KEY_NEW_PACKAGE, pending.newPackage)
            setProperty(KEY_OLD_PACKAGE, pending.oldPackage)
            setProperty(KEY_APK_PATH, pending.apkPath)
            setProperty(KEY_CERT_SIZE, pending.certSize.toString())
            setProperty(KEY_CERT_HASH, pending.certHash)
        }
        val dest = file(context)
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        // 必须 fsync：这条记录存在的意义就是「进程/设备突然断电后还能续登记」，
        // 只靠 rename 落盘可能留下 0 字节文件，read() 会当作没有记录直接放弃恢复。
        java.io.FileOutputStream(tmp).use { out ->
            props.store(out, "pkgrename pending")
            out.fd.sync()
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            error("写入换包名待登记记录失败")
        }
    }

    fun read(context: Context): Pending? {
        val f = file(context)
        if (!f.isFile) return null
        val props = runCatching {
            Properties().apply { f.inputStream().use { load(it) } }
        }.getOrNull() ?: return null
        val newPackage = props.getProperty(KEY_NEW_PACKAGE) ?: return null
        val apkPath = props.getProperty(KEY_APK_PATH) ?: return null
        val certHash = props.getProperty(KEY_CERT_HASH) ?: return null
        val certSize = props.getProperty(KEY_CERT_SIZE)?.toIntOrNull() ?: return null
        return Pending(
            newPackage = newPackage,
            oldPackage = props.getProperty(KEY_OLD_PACKAGE).orEmpty(),
            apkPath = apkPath,
            certSize = certSize,
            certHash = certHash,
        )
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}

/**
 * 启动时的幂等补齐：上次改名流程如果死在「已提交安装、还没登记」这一步，这里接着做完。
 *
 * ADD 在内核侧是「同名整条替换」，重复执行没有副作用，所以不需要额外的去重判断。
 * 不查 PackageManager：应用侧查别的包受包可见性限制，可能查不到；ksud 以 root 查，
 * 包没装好时 ADD 会失败，这里重试几次即可。
 */
internal object PackageRenameRecovery {

    /** 启动时的重试间隔；系统刚装完包时 packages.list 可能还没刷新。 */
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1500L

    fun recoverAsync(context: Context) {
        val pending = RenameJournal.read(context) ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            if (!File(pending.apkPath).isFile) {
                RenameJournal.clear(context)
                return@launch
            }
            runCatching { getDynamicManagers().let { if (!it.enabled) setDynamicManagersEnabled(true) } }
            val signature = DynamicManagerSignature(pending.certSize, pending.certHash)
            repeat(ATTEMPTS) { attempt ->
                if (runCatching { addDynamicManager(pending.newPackage, signature) }.isSuccess) {
                    RenameJournal.clear(context)
                    return@launch
                }
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }
    }
}
