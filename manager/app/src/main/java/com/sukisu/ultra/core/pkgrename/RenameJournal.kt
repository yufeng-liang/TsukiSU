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
 * 换包名流程的落盘记录，同一时刻只会存在一种：
 *
 * - [Pending]：「新包已经提交安装、但内核还没登记」。之所以要落盘：提交安装后系统会把安装确认界面
 *   弹到前台，App 退到后台在低内存设备上被杀是常态。一旦进程没了，内存里的等待点、协程、计时器
 *   全部消失，用户看到的是「新包装上了、登记没做、界面回到初始状态」，而且没有任何恢复途径。
 * - [Completed]：「新包已装好并登记，但旧包还装着」。成功卡上的「卸载旧APK」「打开新管理器」原先
 *   只存在内存里，进程被杀后这两个入口永久消失，只剩一个用户记不住的新包名。把结果也落盘，
 *   重进页面还能接着收尾。
 *
 * 两种记录共用一个文件（同一时刻只有一种），靠 state 键区分；没有这个键的老记录按待登记读。
 */
internal object RenameJournal {

    private const val FILE_NAME = "pkgrename-pending.properties"

    /** 记录种类：待登记 / 已完成。 */
    private const val KEY_STATE = "state"
    private const val STATE_PENDING = "pending"
    private const val STATE_COMPLETED = "completed"
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

    /**
     * 一条「已经换完、等用户收尾」的记录。
     *
     * 新包名必须有；旧包名可能为空（老版本记录或字段缺失），界面按「没有卸载入口」处理。
     */
    data class Completed(
        val newPackage: String,
        val oldPackage: String,
    )

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun write(context: Context, pending: Pending) {
        writeProperties(context, encodePending(pending))
    }

    /** 登记成功、进入「等用户收尾」阶段时写入；会覆盖掉同一条待登记记录。 */
    fun writeCompleted(context: Context, completed: Completed) {
        writeProperties(context, encodeCompleted(completed))
    }

    fun read(context: Context): Pending? = readProperties(context)?.let { decodePending(it) }

    fun readCompleted(context: Context): Completed? =
        readProperties(context)?.let { decodeCompleted(it) }

    /**
     * 原子写：同目录先写 .tmp 并 fsync 再改名。
     *
     * 必须 fsync：这条记录存在的意义就是「进程/设备突然断电后还能续登记」，
     * 只靠 rename 落盘可能留下 0 字节文件，读的时候会被当作没有记录直接放弃恢复。
     */
    private fun writeProperties(context: Context, props: Properties) {
        val dest = file(context)
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        java.io.FileOutputStream(tmp).use { out ->
            props.store(out, "pkgrename journal")
            out.fd.sync()
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            error("写入换包名记录失败")
        }
    }

    private fun readProperties(context: Context): Properties? {
        val f = file(context)
        if (!f.isFile) return null
        return runCatching {
            Properties().apply { f.inputStream().use { load(it) } }
        }.getOrNull()
    }

    /** 序列化待登记记录（纯函数，单测直接覆盖）。 */
    internal fun encodePending(pending: Pending): Properties = Properties().apply {
        setProperty(KEY_STATE, STATE_PENDING)
        setProperty(KEY_NEW_PACKAGE, pending.newPackage)
        setProperty(KEY_OLD_PACKAGE, pending.oldPackage)
        setProperty(KEY_APK_PATH, pending.apkPath)
        setProperty(KEY_CERT_SIZE, pending.certSize.toString())
        setProperty(KEY_CERT_HASH, pending.certHash)
    }

    /**
     * 反序列化待登记记录；已完成记录返回 null。
     *
     * 这一步是恢复流程的第一道门：把完成记录当成待登记去重跑登记，会在启动时对刚换好的包
     * 再做一次 ADD，界面还会把「已完成」降级成「已装未登记」。
     */
    internal fun decodePending(props: Properties): Pending? {
        // 老版本写下的记录没有 state 键，按待登记兼容读取。
        if (props.getProperty(KEY_STATE) == STATE_COMPLETED) return null
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

    /** 序列化完成记录（纯函数，单测直接覆盖）。 */
    internal fun encodeCompleted(completed: Completed): Properties = Properties().apply {
        setProperty(KEY_STATE, STATE_COMPLETED)
        setProperty(KEY_NEW_PACKAGE, completed.newPackage)
        setProperty(KEY_OLD_PACKAGE, completed.oldPackage)
    }

    /** 反序列化完成记录；待登记记录或字段缺失返回 null。 */
    internal fun decodeCompleted(props: Properties): Completed? {
        if (props.getProperty(KEY_STATE) != STATE_COMPLETED) return null
        val newPackage = props.getProperty(KEY_NEW_PACKAGE)?.takeIf { it.isNotBlank() } ?: return null
        return Completed(
            newPackage = newPackage,
            oldPackage = props.getProperty(KEY_OLD_PACKAGE).orEmpty(),
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
