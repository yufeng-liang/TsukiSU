package com.sukisu.ultra.ui.screen.pkgrename

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.core.pkgrename.PackageNameUtils
import com.sukisu.ultra.core.pkgrename.PackageRenameCoordinator
import com.sukisu.ultra.core.pkgrename.RenameStage
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import kotlinx.coroutines.launch

/** 换包名界面状态。 */
internal data class PackageRenameUiState(
    val currentPackage: String = "",
    val targetPackage: String = "",
    val stage: RenameStage? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** 换成功后新包的包名。 */
    val finishedPackage: String? = null,
    /** 换成功后旧包的包名，用于提示卸载。 */
    val previousPackage: String? = null,
)

internal data class PackageRenameActions(
    val onBack: () -> Unit = {},
    val onReroll: () -> Unit = {},
    val onStart: () -> Unit = {},
    /** 打开系统卸载界面，把旧包名那一份卸掉。 */
    val onUninstallOld: () -> Unit = {},
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
fun PackageRenameScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var target by remember { mutableStateOf(PackageNameUtils.random()) }
    var stage by remember { mutableStateOf<RenameStage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf<String?>(null) }
    var previous by remember { mutableStateOf<String?>(null) }

    val actions = PackageRenameActions(
        onBack = navigator::pop,
        onUninstallOld = {
            // 旧包就是当前运行的这个包：换名成功后它还装着，用户留着会同时有两个管理器。
            previous?.let { pkg ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        },
        onReroll = { if (!busy) target = PackageNameUtils.random() },
        onStart = {
            if (!busy) {
                // 装另一个包名属于「安装未知应用」，没有这个权限时 PackageInstaller 会话会直接失败。
                // 老系统（< 8.0）没有这个开关，跳过。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    val opened = runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + context.packageName),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.isSuccess
                    if (!opened) {
                        error = context.getString(R.string.pkg_rename_need_install_permission)
                    }
                } else scope.launch {
                    busy = true
                    error = null
                    finished = null
                    previous = null
                    try {
                        val outcome = PackageRenameCoordinator(context).rename(target) { stage = it }
                        finished = outcome.newPackage
                        previous = outcome.oldPackage
                    } catch (t: Throwable) {
                        error = t.message ?: t.toString()
                    } finally {
                        busy = false
                        stage = null
                    }
                }
            }
        },
    )

    val state = PackageRenameUiState(
        currentPackage = ksuApp.packageName,
        targetPackage = target,
        stage = stage,
        busy = busy,
        error = error,
        finishedPackage = finished,
        previousPackage = previous,
    )

    when (LocalUiMode.current) {
        UiMode.Material -> PackageRenameMaterial(state, actions)
        UiMode.Miuix -> PackageRenameMiuix(state, actions)
    }
}
