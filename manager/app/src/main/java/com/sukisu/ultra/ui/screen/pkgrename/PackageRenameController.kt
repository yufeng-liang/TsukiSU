package com.sukisu.ultra.ui.screen.pkgrename

import android.content.Context
import com.sukisu.ultra.core.pkgrename.PackageNameUtils
import com.sukisu.ultra.core.pkgrename.PackageRenameCoordinator
import com.sukisu.ultra.core.pkgrename.RegistrationFailed
import com.sukisu.ultra.core.pkgrename.RenameJournal
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.isPackageInstalled
import com.sukisu.ultra.ui.util.removeDynamicManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 换包名流程的状态持有者。
 *
 * 整个流程要跑几十秒到几分钟（重打包 39MB 的 APK → 等系统安装器 → 内核登记），
 * 如果把它挂在页面的 rememberCoroutineScope 上，用户一离开页面协程就被取消：
 * 安装会话已经交给系统了，取消不会撤销安装，但随后的登记永远不会执行，
 * 于是新包装上了却拿不到内核权限。所以流程跑在应用级作用域，页面退出不影响它。
 */
internal object PackageRenameController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        PackageRenameUiState(
            currentPackage = ksuApp.packageName,
            targetPackage = PackageNameUtils.random(),
        ),
    )
    val state: StateFlow<PackageRenameUiState> = _state.asStateFlow()

    /** 同一时刻只允许一个流程，重复点击在入口处就被挡掉。 */
    private var job: Job? = null

    private val busy: Boolean get() = job?.isActive == true

    /** 换一个新随机包名。流程进行中不允许改，避免界面显示的名字和实际安装的不一致。 */
    fun reroll() {
        if (busy) return
        _state.value = _state.value.copy(targetPackage = PackageNameUtils.random(), error = null)
    }

    fun start(context: Context) {
        if (busy) return
        val appContext = context.applicationContext
        val target = _state.value.targetPackage
        job = scope.launch {
            _state.value = _state.value.copy(
                busy = true,
                error = null,
                stage = null,
                finishedPackage = null,
                previousPackage = null,
                unregisteredPackage = null,
                dynamicManagerEnabledByUs = false,
            )
            try {
                val outcome = PackageRenameCoordinator(appContext).rename(target) { stage ->
                    _state.value = _state.value.copy(stage = stage)
                }
                _state.value = _state.value.copy(
                    finishedPackage = outcome.newPackage,
                    previousPackage = outcome.oldPackage,
                    dynamicManagerEnabledByUs = outcome.dynamicManagerEnabledByUs,
                )
            } catch (e: RegistrationFailed) {
                // 新包已经装好了，只是没登记上：不要显示成「失败了」。
                _state.value = _state.value.copy(
                    unregisteredPackage = e.newPackage,
                    // 关键：不提供「卸载旧包」入口。旧包还在时它仍是内核认定/可能仍在的管理器，
                    // 卸掉它而新包没被授权，就没有任何进程还能以 root 补登记了。
                    previousPackage = null,
                    error = e.message,
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.value = _state.value.copy(error = t.message ?: t.toString())
            } finally {
                _state.value = _state.value.copy(busy = false, stage = null)
            }
        }
    }

    /** 新包已安装但登记失败时，只重跑登记这一段。 */
    fun retryRegister(context: Context) {
        if (busy) return
        val pkg = _state.value.unregisteredPackage ?: return
        val appContext = context.applicationContext
        job = scope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val enabledByUs = PackageRenameCoordinator(appContext).registerNewPackage(pkg)
                // 登记成功了，那条「待登记」记录再留着会在下次启动时被重复登记一遍。
                RenameJournal.clear(appContext)
                _state.value = _state.value.copy(
                    finishedPackage = pkg,
                    previousPackage = _state.value.currentPackage,
                    unregisteredPackage = null,
                    dynamicManagerEnabledByUs = enabledByUs,
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.value = _state.value.copy(error = t.message ?: t.toString())
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /**
     * 撤掉刚登记的那一条。
     *
     * 登记一直失败时给用户一条退路：撤掉之后新包就是一个普通应用，
     * 不会因为「看起来换名成功、实际没权限」而被误留。
     */
    fun revokeRegister(context: Context) {
        if (busy) return
        val pkg = _state.value.unregisteredPackage ?: return
        val appContext = context.applicationContext
        job = scope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    removeDynamicManager(pkg)
                    // 已经明确放弃，就不能让启动恢复流程下次再把它加回去。
                    RenameJournal.clear(appContext)
                }
                _state.value = _state.value.copy(unregisteredPackage = null)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.value = _state.value.copy(error = t.message ?: t.toString())
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /** 完成或放弃后回到初始状态，方便再换一次。 */
    fun restart() {
        if (busy) return
        RenameJournal.clear(ksuApp)
        _state.value = PackageRenameUiState(
            currentPackage = ksuApp.packageName,
            targetPackage = PackageNameUtils.random(),
        )
    }

    /**
     * 把上次遗留的「已提交安装、未登记」记录读进界面。
     *
     * 启动续登记（[PackageRenameRecovery]）可能在后台静默失败——它没有界面，也没法弹通知。
     * 这里在页面打开时补一次：记录还在就说明新包没登记上，直接进入「已装未登记」卡片，
     * 用户可以自己点重试或撤销，比对着灰掉的功能发呆强。
     */
    fun refreshJournal(context: Context) {
        if (busy || _state.value.unregisteredPackage != null || _state.value.finishedPackage != null) return
        val appContext = context.applicationContext
        val pending = RenameJournal.read(appContext) ?: return
        scope.launch {
            // 用户在安装器上点了取消：journal 已经写下但包根本没装上，
            // 不清掉的话每次进页面都报「已装未登记」，还会反复跑无效的登记尝试
            if (!isPackageInstalled(pending.newPackage)) {
                if (!busy) RenameJournal.clear(appContext)
                return@launch
            }
            if (busy || _state.value.unregisteredPackage != null || _state.value.finishedPackage != null) {
                return@launch
            }
            _state.value = _state.value.copy(
                unregisteredPackage = pending.newPackage,
                previousPackage = pending.oldPackage.ifBlank { null },
            )
        }
    }

    /** 页面上的本地校验失败（如没有「安装未知应用」权限）也走同一个错误位。 */
    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message)
    }
}
