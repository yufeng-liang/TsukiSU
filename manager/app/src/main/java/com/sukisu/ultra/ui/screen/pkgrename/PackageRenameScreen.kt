package com.sukisu.ultra.ui.screen.pkgrename

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.sukisu.ultra.R
import com.sukisu.ultra.core.pkgrename.RenameStage
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Route

/** 换包名界面状态。 */
internal data class PackageRenameUiState(
    val currentPackage: String = "",
    val targetPackage: String = "",
    val stage: RenameStage? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** 换成功（且已登记）后新包的包名。 */
    val finishedPackage: String? = null,
    /** 新包已装好但没登记上时的包名；此时不能卸载旧包。 */
    val unregisteredPackage: String? = null,
    /** 旧包的包名，用于提示卸载。 */
    val previousPackage: String? = null,
    /** 这次流程是否替用户打开了内核的动态管理器开关。 */
    val dynamicManagerEnabledByUs: Boolean = false,
)

internal data class PackageRenameActions(
    val onBack: () -> Unit = {},
    val onReroll: () -> Unit = {},
    val onStart: () -> Unit = {},
    /** 打开系统卸载界面，把旧包名那一份卸掉。 */
    val onUninstallOld: () -> Unit = {},
    /** 已安装未登记时，只重跑登记。 */
    val onRetryRegister: () -> Unit = {},
    /** 撤销刚登记的那一条。 */
    val onRevokeRegister: () -> Unit = {},
    /** 打开新装好的管理器。 */
    val onOpenNew: () -> Unit = {},
    /** 跳到「动态管理器」页，核对登记状态。 */
    val onOpenManagers: () -> Unit = {},
    /** 回到未开始状态，再换一次。 */
    val onRestart: () -> Unit = {},
)

/** 换包名进度的文案。 */
@Composable
internal fun renameStageLabel(stage: RenameStage): String = when (stage) {
    RenameStage.BUILDING -> stringResource(R.string.pkg_rename_stage_building)
    RenameStage.INSTALLING -> stringResource(R.string.pkg_rename_stage_installing)
    RenameStage.REGISTERING -> stringResource(R.string.pkg_rename_stage_registering)
    RenameStage.DONE -> stringResource(R.string.pkg_rename_stage_done)
}

@Composable
fun PackageRenameScreen(onBackEnabledChange: (Boolean) -> Unit = {}) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val state by PackageRenameController.state.collectAsState()

    // 流程进行中禁止返回：安装会话已交给系统，此时退出只会留下「装了没登记」的孤儿包。
    // 上次改名死在「已装未登记」时，启动续登记可能已经静默失败，这里把它摆到界面上
    LaunchedEffect(Unit) { PackageRenameController.refreshJournal(context) }
    LaunchedEffect(state.busy) { onBackEnabledChange(!state.busy) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        // isBackEnabled = true 表示「拦截并消费返回」。流程进行中才该拦，空闲时要放行。
        isBackEnabled = state.busy,
        onBackCompleted = {},
    )

    val actions = PackageRenameActions(
        // 顶栏返回按钮也要守同一道门：安装/登记中退出只会在后台留一个没登记的孤儿包。
        onBack = { if (!state.busy) navigator.pop() },
        onReroll = { PackageRenameController.reroll() },
        onStart = start@{
            if (state.busy) return@start
            // 装另一个包名属于「安装未知应用」，没有这个权限时 PackageInstaller 会话会直接失败。
            // 老系统（< 8.0）没有这个开关，跳过。
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            if (needsPermission) {
                val opened = runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.packageName),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (!opened) {
                    PackageRenameController.reportError(
                        context.getString(R.string.pkg_rename_need_install_permission),
                    )
                }
            } else {
                PackageRenameController.start(context)
            }
        },
        onUninstallOld = {
            state.previousPackage?.let { pkg ->
                val opened = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (!opened) {
                    PackageRenameController.reportError(
                        context.getString(R.string.pkg_rename_uninstall_failed, pkg),
                    )
                }
            }
        },
        onRetryRegister = { PackageRenameController.retryRegister(context) },
        onRevokeRegister = { PackageRenameController.revokeRegister(context) },
        onOpenNew = {
            // 用显式组件名：API 30+ 的包可见性限制下查不到别人，但显式启动不受影响。
            // 组件类名是源码命名空间 com.sukisu.ultra.*，不随包名替换而变。
            state.finishedPackage?.let { pkg ->
                val intent = Intent()
                    .setComponent(ComponentName(pkg, "com.sukisu.ultra.ui.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(intent) }.isFailure) {
                    PackageRenameController.reportError(
                        context.getString(R.string.pkg_rename_open_new_failed, pkg),
                    )
                }
            }
        },
        onOpenManagers = { navigator.push(Route.DynamicManagers) },
        onRestart = { PackageRenameController.restart() },
    )

    val viewState = state.copy(currentPackage = ksuApp.packageName)

    when (LocalUiMode.current) {
        UiMode.Material -> PackageRenameMaterial(viewState, actions)
        UiMode.Miuix -> PackageRenameMiuix(viewState, actions)
    }
}
