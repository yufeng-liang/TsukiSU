package com.sukisu.ultra.core.pkgrename

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/** 一次安装的结果。 */
internal sealed class InstallOutcome {
    data class Success(val packageName: String) : InstallOutcome()
    data class Failure(val message: String) : InstallOutcome()
}

/**
 * 安装结果的中转站，按会话号（sessionId）对号入座。
 *
 * `PackageInstaller` 的结果只能靠 PendingIntent 广播拿回来，而广播接收器由系统实例化，
 * 拿不到协程；所以这里用「会话号 → 一次性等待点」的表把结果交回协调器。
 *
 * 按会话号而不是单播流：即使收到的是上一次会话的陈旧回调，也不会被当成本次成功。
 */
internal object PackageRenameInstallBridge {

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallOutcome>>()

    /** 取（必要时创建）某个会话的等待点。 */
    fun await(sessionId: Int): CompletableDeferred<InstallOutcome> =
        pending.computeIfAbsent(sessionId) { CompletableDeferred() }

    fun publish(sessionId: Int, outcome: InstallOutcome) {
        if (sessionId < 0) return
        pending.computeIfAbsent(sessionId) { CompletableDeferred() }.complete(outcome)
    }

    /** 会话结束（成功或失败）后清掉等待点，避免表越积越大。 */
    fun discard(sessionId: Int) {
        pending.remove(sessionId)
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

        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> {
                val pkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
                PackageRenameInstallBridge.publish(sessionId, InstallOutcome.Success(pkg))
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
                PackageRenameInstallBridge.publish(sessionId, InstallOutcome.Failure(message))
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.sukisu.ultra.action.PKG_RENAME_INSTALL_RESULT"
        const val EXTRA_TARGET_PACKAGE = "com.sukisu.ultra.extra.TARGET_PACKAGE"
        const val EXTRA_SESSION_ID = "com.sukisu.ultra.extra.SESSION_ID"
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
                    .putExtra(PkgRenameInstallReceiver.EXTRA_SESSION_ID, sessionId)
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
