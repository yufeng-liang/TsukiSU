package com.sukisu.ultra.core.pkgrename

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 一次安装的结果。 */
internal sealed class InstallOutcome {
    data class Success(val packageName: String) : InstallOutcome()
    data class Failure(val message: String) : InstallOutcome()
}

/**
 * 安装结果的中转站。
 *
 * `PackageInstaller` 的结果只能通过 PendingIntent 广播拿回来，而广播接收器是系统实例化的，
 * 没法直接拿到协程；所以这里用一个进程内单播把结果交回 [PackageRenameCoordinator]。
 */
internal object PackageRenameInstallBridge {
    // replay = 1：安装回调可能在协调器开始收集之前就到达（commit 与 first() 之间没有原子性），
    // 留一格重放缓存才不会漏掉结果。
    private val _outcomes = MutableSharedFlow<InstallOutcome>(replay = 1, extraBufferCapacity = 4)
    val outcomes = _outcomes.asSharedFlow()

    fun publish(outcome: InstallOutcome) {
        _outcomes.tryEmit(outcome)
    }

    /** 每次提交新会话前清掉上一次的结果，避免读到陈旧结论。 */
    fun reset() {
        _outcomes.resetReplayCache()
    }
}

/**
 * 换包名 APK 的安装结果接收器。
 *
 * 与 `CiPackageInstallReceiver` 的区别：那个是自更新（同一个包名、同签名），
 * 这个装的是**另一个包名**的新包，所以不能复用它写死的 Toast 与包名假设。
 */
class PkgRenameInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return

        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> {
                val pkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
                PackageRenameInstallBridge.publish(InstallOutcome.Success(pkg))
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // 系统要求用户点一下确认，把确认界面拉起来；最终结果会再次回调到本接收器
                @Suppress("DEPRECATION")
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "安装失败（未知原因）"
                PackageRenameInstallBridge.publish(InstallOutcome.Failure(message))
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.sukisu.ultra.action.PKG_RENAME_INSTALL_RESULT"
        const val EXTRA_TARGET_PACKAGE = "com.sukisu.ultra.extra.TARGET_PACKAGE"
    }
}

/** 负责把改名后的 APK 装进系统。 */
internal object PackageRenameInstaller {

    /**
     * 提交一次安装会话。
     *
     * @return 会话 id；失败抛异常
     */
    fun submit(context: Context, apk: File, targetPackage: String): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(targetPackage)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("pkgrename.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, PkgRenameInstallReceiver::class.java)
                    .setAction(PkgRenameInstallReceiver.ACTION_INSTALL_RESULT)
                    .putExtra(PkgRenameInstallReceiver.EXTRA_TARGET_PACKAGE, targetPackage)
                val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
                val pending = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or mutable,
                )
                session.commit(pending.intentSender)
                committed = true
            }
        } finally {
            if (!committed) {
                runCatching { installer.abandonSession(sessionId) }
            }
        }
        return sessionId
    }
}
