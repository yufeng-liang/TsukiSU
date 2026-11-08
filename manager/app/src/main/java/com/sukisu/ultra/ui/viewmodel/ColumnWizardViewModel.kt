package com.sukisu.ultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukisu.ultra.data.column.ColumnCheckIds
import com.sukisu.ultra.data.column.ColumnKeyboxFetcher
import com.sukisu.ultra.data.column.ColumnModuleFetcher
import com.sukisu.ultra.data.column.ColumnRollbackStore
import com.sukisu.ultra.data.column.ColumnHealthReport
import com.sukisu.ultra.data.column.ColumnPreflight
import com.sukisu.ultra.data.column.ColumnPreflightAction
import com.sukisu.ultra.data.column.ColumnPreflightItem
import com.sukisu.ultra.data.column.ColumnProgressEntry
import com.sukisu.ultra.data.column.ColumnProgressStore
import com.sukisu.ultra.data.column.ColumnRootIo
import com.sukisu.ultra.data.column.buildColumnHealthReport
import com.sukisu.ultra.data.column.ColumnRunContext
import com.sukisu.ultra.data.column.ColumnSelfCheckRunner
import com.sukisu.ultra.data.column.ColumnSelfCheckRunnerImpl
import com.sukisu.ultra.data.column.ColumnStepResult
import com.sukisu.ultra.data.column.ColumnStepRunner
import com.sukisu.ultra.data.column.ColumnStepRunnerImpl
import com.sukisu.ultra.data.column.ColumnStepStatus
import com.sukisu.ultra.data.column.ColumnStepUiState
import com.sukisu.ultra.data.column.ColumnWizardUiState
import com.sukisu.ultra.data.column.isValidColumnModuleId
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnTopic
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * 专栏执行向导的 ViewModel。
 *
 * 这里只做状态编排，设备操作全部交给 [ColumnStepRunner] / [ColumnSelfCheckRunner] /
 * [ColumnRollbackStore]。三条硬约束：
 * 1. 同一时刻只允许一步在跑（[runningStep] 守卫），避免两条 root 命令抢同一个文件；
 * 2. 任何一步失败就停在原地，不自动跑下一步，也不替用户「重试一下试试」；
 * 3. 回滚账本必须与执行器共用同一个 [ColumnRollbackStore] 实例，否则记录不上账。
 *
 * @param keyboxFetcher 密钥材料现拉器；专题不需要 keybox 时不会被调用
 * @param rollbackStore 回滚账本，同时注入给执行器，保证记账与回滚看到的是同一份记录
 */
class ColumnWizardViewModel(
    private val keyboxFetcher: ColumnKeyboxFetcher = ColumnKeyboxFetcher(),
    private val rollbackStore: ColumnRollbackStore = ColumnRollbackStore(),
    private val stepRunner: ColumnStepRunner = ColumnStepRunnerImpl(
        fetcher = ColumnModuleFetcher(),
        rollback = rollbackStore,
    ),
    private val selfCheck: ColumnSelfCheckRunner = ColumnSelfCheckRunnerImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColumnWizardUiState())
    val uiState: StateFlow<ColumnWizardUiState> = _uiState.asStateFlow()

    /** 体检是否在跑：重复进专题不该并发开两条 root shell。 */
    private var healthRunning = false

    /** 当前专题所属的专栏 id；进度 key 带上它，避免不同专栏里的同名专题互相串进度。 */
    private var columnId: String? = null

    /** 当前专题，[runStep] 需要把它一起交给执行器（步骤共享专题级的 keybox 信息）。 */
    private var topic: RepoColumnTopic? = null

    /** 校验通过的密钥材料正文；没拉取、没校验通过一律为 null。 */
    private var keyboxContent: String? = null

    /** 同一条向导只允许一个步骤在跑。 */
    private var runningStep = false

    /**
     * 重启倒计时期间用户的选择：true = 立刻重启，false = 稍后自己重启。
     *
     * 只有 [confirmRebootBeforeRunning] 在等的时候非空。
     */
    private var rebootDecision: CompletableDeferred<Boolean>? = null

    /** 已经预取过密钥的专题 id，避免重复网络请求。 */
    private var prefetchedTopicId: String? = null

    /** 密钥预取任务；续跑 / 单步执行前先等它完成，避免「密钥还没拉到」导致步骤失败。 */
    private var keyboxJob: Job? = null

    /**
     * 进入向导时调用。同一专栏下的同一专题重复调用不会重置已有进度（导航返回再进来还能看到上次跑到哪）。
     *
     * 进度按「专栏 id + 专题 id」区分：不同专栏里可能有同名专题（如 self-check），
     * 只按 topicId 存会让两个专栏互相覆盖进度。
     */
    fun start(columnId: String, topic: RepoColumnTopic) {
        if (this.columnId == columnId && this.topic?.id == topic.id && _uiState.value.stepStates.isNotEmpty()) return
        this.columnId = columnId
        this.topic = topic
        this.keyboxContent = null
        val restored = restoreProgress(topic.id)
        _uiState.value = ColumnWizardUiState(
            topicId = topic.id,
            stepStates = topic.steps.map { step ->
                val saved = restored?.get(step.id)
                ColumnStepUiState(
                    step = step,
                    status = saved?.first ?: ColumnStepStatus.PENDING,
                    message = saved?.second.orEmpty(),
                )
            },
            // 体检要跑 root 探针，先置为扫描中，让界面从第一帧就显示骨架卡。
            healthScanning = true,
        )
        val savedOptedIn = runCatching {
            progressPrefs().getStringSet(optedKey(topic.id), null)
        }.getOrNull()
        if (!savedOptedIn.isNullOrEmpty()) {
            _uiState.update { it.copy(optedIn = savedOptedIn.toSet()) }
        }
        if (!restored.isNullOrEmpty()) {
            appendLog("已恢复上次的进度（${restored.size} 步已处理），接着往下做就行")
        }
        keyboxJob = prefetchKeybox(topic)
        // 恢复出来的进度可能已经全部落定（上次做完就退出了），这时要立刻进入完成态，
        // 否则自检与回滚这两个入口永远不会出现。这条路径不弹庆祝：用户只是重新进来看一眼。
        maybeFinish(allowCelebrate = false)
        // SUCCESS 只是上次的记录；设备可能已经刷机或卸载，根目录复核要异步做，不能阻塞主线程。
        verifyRestoredInstallSteps()
        // 存量用户检测：设备上可能早就配好了一套（甚至用别的工具配的），进专题先看一眼现状。
        runHealthCheck()
        // 检测器是否已经装过：装过就不用再下载一次、界面也该按「已安装」来说话。
        detectInstalledApps(topic)
        // 上次一键执行跑到一半被重启打断：设备回来了就接着跑，用户不用再点一次。
        resumePendingRunIfAny()
    }

    /**
     * 重启后自动续跑。
     *
     * 标记是全局的一份，只有「专栏 + 专题」都与当前进入的一致时才续跑：用户在别的专题里
     * 不该被上一处的重启拖走。已全部落定、或标记过期（见 [ColumnProgressStore.peekPendingResume]）
     * 时直接清掉标记，不再执行。
     */
    private fun resumePendingRunIfAny() {
        val pending = ColumnProgressStore.peekPendingResume(ksuApp) ?: return
        if (pending.columnId != columnId || pending.topicId != topic?.id) return
        if (_uiState.value.done) {
            ColumnProgressStore.clearPendingResume(ksuApp)
            return
        }
        // 重启前最后执行的那一步（通常就是 reboot 自己）已经随这次重启完成：补记为 SUCCESS。
        // 少了这一步，恢复出来的 PENDING 会让 runAll 再次排入重启 → 再次重启 → 再次恢复，死循环。
        if (pending.lastStepId.isNotEmpty()) {
            val item = _uiState.value.stepStates.firstOrNull { it.step.id == pending.lastStepId }
            if (item != null && !item.status.isSettled) {
                updateStep(pending.lastStepId) {
                    it.copy(status = ColumnStepStatus.SUCCESS, message = "设备已重启（自动续跑）")
                }
                appendLog("上次重启前执行的「${item.step.title}」已随重启完成")
            }
        }
        appendLog("检测到上次一键执行被重启打断，接着往下跑")
        viewModelScope.launch {
            // 等密钥预取完成再续跑：否则需要 keybox 的步骤可能因为「还没拉到密钥」直接失败。
            keyboxJob?.join()
            runAll()
        }
    }

    /**
     * 扫一遍专题里 `installApp` 步骤声明的包名，看设备上是不是已经装了。
     *
     * 索引里写包名的步骤才能被提前判断（见 [com.sukisu.ultra.data.model.RepoColumnStepSource.packageName]）；
     * 没写包名的仍然只有执行时才可能知道。结果只进 [ColumnWizardUiState.installedApps]，不改善步骤状态。
     */
    private fun detectInstalledApps(topic: RepoColumnTopic) {
        val declared = topic.steps
            .filter { it.kind.trim().equals("installApp", ignoreCase = true) }
            .map { it.source.packageName.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (declared.isEmpty()) return
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { declared.filter(::packageInstalled).toSet() }
            if (installed.isEmpty()) return@launch
            _uiState.update { it.copy(installedApps = it.installedApps + installed) }
            appendLog("设备上已经装了：${installed.joinToString("、")}，这一步可以直接跳过")
        }
    }

    /**
     * 进专题时跑一次设备体检。
     *
     * 九项探针全是只读的（存在性检查、字符设备读取），不写任何文件；结果只用来在顶部提示
     * 「你之前好像已经配过一部分」，绝不因此自动跳过任何步骤——探针看不到「配置对不对」。
     */
    fun runHealthCheck() {
        if (healthRunning) return
        healthRunning = true
        _uiState.update { it.copy(healthScanning = true) }
        viewModelScope.launch {
            try {
                val results = selfCheck.check(ColumnCheckIds.ALL)
                _uiState.update { it.copy(health = buildColumnHealthReport(ColumnCheckIds.ALL, results)) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                appendLog("设备体检没跑成：${error.message ?: "未知错误"}（不影响后续步骤）")
            } finally {
                healthRunning = false
                // 无论成功还是失败都要结束扫描态，否则骨架会一直转。
                _uiState.update { it.copy(healthScanning = false) }
            }
        }
    }

    /**
     * 「一键执行」的入口：先预检，把「将跳过 / 将覆盖 / 将新增 / 需手动」列清楚。
     *
     * 有覆盖动作就停在确认框上，让用户看见要改什么；没有覆盖动作直接开跑。
     * 存量用户最容易被这一步伤到：他手上的 keybox、denylist 开关会被静默覆盖，而备份他并不知道在哪。
     */
    fun requestRunAll() {
        if (runningStep) return
        val steps = topic?.steps.orEmpty()
        if (steps.isEmpty()) return
        viewModelScope.launch {
            val plan = try {
                buildPreflight(steps)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                appendLog("预检失败（${error.message ?: "未知错误"}），按普通流程继续")
                runAll()
                return@launch
            }
            if (plan.needsConfirmation) {
                _uiState.update { it.copy(preflight = plan) }
            } else {
                runAll()
            }
        }
    }

    /** 用户在预检框上点了「继续」。 */
    fun confirmPreflight() {
        _uiState.update { it.copy(preflight = null) }
        runAll()
    }

    /** 用户在预检框上点了「取消」。 */
    fun dismissPreflight() {
        _uiState.update { it.copy(preflight = null) }
        appendLog("已取消，没有执行任何步骤")
    }

    /**
     * 生成预检清单。只读操作，全部在 IO 线程上跑，避免每步开一次 su 卡住主线程。
     */
    private suspend fun buildPreflight(steps: List<RepoColumnStep>): ColumnPreflight =
        withContext(Dispatchers.IO) {
            val state = _uiState.value
            val items = ArrayList<ColumnPreflightItem>()
            for (step in steps) {
                val status = state.stepStates.firstOrNull { it.step.id == step.id }?.status
                if (status?.isSettled == true) continue
                if (step.optIn && step.id !in state.optedIn) continue
                if (!step.isAutoRunnable()) {
                    items += ColumnPreflightItem(
                        stepId = step.id,
                        title = step.title,
                        action = ColumnPreflightAction.MANUAL,
                        reason = if (step.optional) "可选项，需要你自己决定" else "需要你手动完成",
                    )
                    continue
                }
                val verdict = when (step.kind.trim().lowercase()) {
                    "installmodule" -> {
                        val id = step.source.moduleId.trim()
                        if (isValidColumnModuleId(id) && ColumnRootIo.isDirectory("/data/adb/modules/$id")) {
                            ColumnPreflightAction.SKIP to "模块 $id 已安装，会跳过"
                        } else {
                            ColumnPreflightAction.NEW to "会下载并安装"
                        }
                    }
                    "installapp" -> {
                        // 真实执行（ColumnStepRunnerImpl / detectInstalledApps）读的是 packageName，
                        // 预检必须用同一字段；旧数据可能只填了 moduleId，所以留作兜底。
                        val pkg = step.source.packageName.trim().ifEmpty { step.source.moduleId.trim() }
                        if (pkg.isNotEmpty() && packageInstalled(pkg)) {
                            ColumnPreflightAction.SKIP to "应用已安装，会跳过"
                        } else {
                            ColumnPreflightAction.NEW to "会下载并交给系统安装器"
                        }
                    }
                    "writefile" -> {
                        val path = step.filePath.trim()
                        val current = runCatching { ColumnRootIo.readText(path) }.getOrNull()
                        when {
                            current == null -> ColumnPreflightAction.NEW to "会新建 $path"
                            step.fileContent.contains("\${keybox}") ->
                                ColumnPreflightAction.OVERWRITE to "会覆盖 $path（先备份到回滚账本）"
                            current == step.fileContent -> ColumnPreflightAction.SKIP to "内容一致，会跳过"
                            else -> ColumnPreflightAction.OVERWRITE to "会覆盖 $path（先备份到回滚账本）"
                        }
                    }
                    "writehmaconfig" -> {
                        val hasConfig = runCatching {
                            ColumnRootIo.exec("ls -d /data/misc/hide_my_applist_* 2>/dev/null").isSuccess
                        }.getOrDefault(false)
                        if (hasConfig) {
                            ColumnPreflightAction.OVERWRITE to "会在现有 HMA 配置上合并（保留你已有的模板与目标）"
                        } else {
                            ColumnPreflightAction.NEW to "会写入新的 HMA 配置"
                        }
                    }
                    "runcommand" -> ColumnPreflightAction.NEW to "会执行一条命令"
                    else -> ColumnPreflightAction.MANUAL to "需要你手动完成"
                }
                items += ColumnPreflightItem(
                    stepId = step.id,
                    title = step.title,
                    action = verdict.first,
                    reason = verdict.second,
                )
            }
            ColumnPreflight(items = items)
        }

    private fun packageInstalled(packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        ksuApp.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)


    /**
     * 复核恢复出来的 installModule 成功记录。
     *
     * 上次的 SUCCESS 只说明「当时装上了」：用户可能刷过机、手动卸载过，或者模块被别的工具清掉。
     * 这里用 root 看一眼 `/data/adb/modules/<id>`：目录不在就退回 PENDING 让用户重做；
     * root 不可用或模块 id 无法校验时没法下结论，只在文案上标注「上次记录」，不冒充当前事实。
     */
    private fun verifyRestoredInstallSteps() {
        val candidates = _uiState.value.stepStates.filter {
            it.status == ColumnStepStatus.SUCCESS &&
                it.step.kind.trim().equals("installModule", ignoreCase = true)
        }
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            val verdicts = withContext(Dispatchers.IO) {
                // ColumnRootIo 会把异常收敛成 isSuccess=false，先单独探一次 root，
                // 否则「目录不存在」和「没有 root」会混成同一种结果。
                val rootAvailable = ColumnRootIo.exec("true").isSuccess
                candidates.map { item ->
                    val moduleId = item.step.source.moduleId.trim()
                    val verdict = if (!rootAvailable || !isValidColumnModuleId(moduleId)) {
                        RestoreVerdict.Unverifiable(moduleId)
                    } else if (ColumnRootIo.isDirectory("/data/adb/modules/$moduleId")) {
                        RestoreVerdict.Installed
                    } else {
                        RestoreVerdict.Missing(moduleId)
                    }
                    item.step.id to verdict
                }
            }
            // 复核跑在 IO 线程上时用户可能已经重做并成功，落回状态前再确认一次，
            // 别把刚拿到的 SUCCESS 又覆盖成 PENDING（或给新说明追加旧记录标注）。
            val currentStatuses = _uiState.value.stepStates.associate { it.step.id to it.status }
            var downgraded = false
            verdicts.forEach { (stepId, verdict) ->
                if (currentStatuses[stepId] != ColumnStepStatus.SUCCESS) return@forEach
                when (verdict) {
                    RestoreVerdict.Installed -> Unit
                    is RestoreVerdict.Missing -> {
                        updateStep(stepId) {
                            it.copy(
                                status = ColumnStepStatus.PENDING,
                                message = "上次记录为已完成，但设备上找不到模块「${verdict.moduleId}」，请重做这一步",
                            )
                        }
                        appendLog("恢复进度：模块「${verdict.moduleId}」已不在 /data/adb/modules 下，该步骤退回待执行")
                        downgraded = true
                    }
                    is RestoreVerdict.Unverifiable -> {
                        updateStep(stepId) { it.copy(message = markAsStale(it.message)) }
                        appendLog(
                            "恢复进度：无法复核模块「${verdict.moduleId.ifEmpty { "（未知）" }}」的安装状态" +
                                "（root 不可用或模块 id 无法校验），保留上次记录并标注",
                        )
                    }
                }
            }
            if (downgraded) {
                // 有步骤其实没做成，之前的完成态不再成立，底部操作条要重新出现。
                _uiState.update { it.copy(done = false) }
            }
        }
    }

    /** 无法复核时只能在文案上说明「这是旧记录」，避免用户把上次结论当成现在的设备状态。 */
    private fun markAsStale(message: String): String =
        if (message.contains(STALE_RECORD_NOTE)) message else message + STALE_RECORD_NOTE

    /**
     * 一键执行：按顺序把「不需要人看着」的步骤跑完，遇到需要人工的（重启、验收、确认）就停下，
     * 并在日志里说清还剩哪些要自己做。
     *
     * 用途是设备重启后回来接着跑：重启前的步骤已经是 SUCCESS/SKIPPED，会被直接跳过，
     * 真正执行的只有重启后才允许做的那几步（例如写 HMA 配置）。
     */
    fun runAll() {
        if (runningStep) return
        val steps = topic?.steps.orEmpty()
        if (steps.isEmpty()) return
        val state = _uiState.value
        val planned = ArrayList<RepoColumnStep>()
        var stopReason: String? = null
        for (step in steps) {
            val status = state.stepStates.firstOrNull { it.step.id == step.id }?.status
            // 已经处理过的直接跳过（SUCCESS / SKIPPED）
            if (status?.isSettled == true) continue
            // NEEDS_USER 是「等你动手」：前面的步骤没处理完，后面的步骤不能越过它继续跑，
            // 否则一键执行会假装成功、把没装好的东西留到最后自检才暴露。
            if (status == ColumnStepStatus.NEEDS_USER) {
                stopReason = "「${step.title}」需要你手动处理后点「我已处理」"
                break
            }
            if (step.optIn && step.id !in state.optedIn) continue
            if (step.optional) {
                stopReason = "「${step.title}」是可选项，装不装由你决定"
                break
            }
            if (!step.isAutoRunnable()) {
                stopReason = "「${step.title}」需要你手动做"
                break
            }
            if (step.confirm.isNotBlank()) {
                stopReason = "「${step.title}」需要你单独确认"
                break
            }
            planned += step
        }
        if (planned.isEmpty()) {
            appendLog(stopReason ?: "没有可以自动执行的步骤了；剩下的都需要你手动处理")
            maybeFinish()
            return
        }
        runningStep = true
        _uiState.update { it.copy(running = true, error = "") }
        appendLog("一键执行：本次自动处理 ${planned.size} 个步骤")
        viewModelScope.launch {
            try {
                // 等密钥预取完成再开始，避免需要 keybox 的步骤因为「还没拉到」直接失败。
                keyboxJob?.join()
                for ((index, step) in planned.withIndex()) {
                    if (!isActive) break
                    // 重启会杀掉进程：先把「这个专题还没跑完」落盘，设备回来后 start() 会自动续上。
                    // 放在执行之前写，是因为 reboot 一旦生效就没有机会再写任何东西了。
                    if (step.kind.trim().equals("reboot", ignoreCase = true)) {
                        val runningColumn = columnId
                        val runningTopic = topic
                        if (runningColumn != null && runningTopic != null) {
                            // lastStepId 记下 reboot 自己：回来后补记为成功，不会被再次排入。
                            ColumnProgressStore.markPendingResume(ksuApp, runningColumn, runningTopic.id, step.id)
                        }
                        // 手机突然黑屏重启会吓到人：先倒计时并在界面上说清楚「回来后会自动接着跑」。
                        if (!confirmRebootBeforeRunning()) {
                            appendLog("已暂停：等你手动重启设备。回来后打开 App 会自动接着跑剩下的步骤")
                            return@launch
                        }
                    }
                    val result = executeStep(step, force = false)
                    if (result.isFailure) {
                        _uiState.update { it.copy(error = result.message) }
                        appendLog("步骤「${step.title}」失败，一键执行停在这里（修正后可以重试这一步）")
                        return@launch
                    }
                    // NEEDS_USER 不是成功：必须停下来等用户处理，不能继续跑后面的步骤。
                    if (result.status == ColumnStepStatus.NEEDS_USER) {
                        appendLog("「${step.title}」需要你手动处理后点「我已处理」，一键执行先停在这里")
                        return@launch
                    }
                    // 重启命令已经发出去：进程马上会被杀掉，后面的步骤要等设备回来后由续跑逻辑接着做。
                    if (step.kind.trim().equals("reboot", ignoreCase = true) &&
                        result.status == ColumnStepStatus.SUCCESS
                    ) {
                        appendLog("设备正在重启，回来后会自动接着跑剩下的步骤")
                        return@launch
                    }
                    appendLog("进度：${index + 1}/${planned.size}")
                }
                maybeFinish()
                appendLog(
                    if (stopReason != null) "自动步骤都执行完了；接下来：$stopReason"
                    else "自动步骤都执行完了；剩下的需要你手动确认",
                )
            } finally {
                runningStep = false
                _uiState.update { it.copy(running = false) }
            }
        }
    }

    /**
     * 这些步骤不需要人看着：装模块、装 APK、写文件、跑命令、重启。
     *
     * 重启也算自动：一键执行的语义就是「点一下，剩下我自己走完」。执行重启前会写一条
     * [ColumnProgressStore.markPendingResume] 标记，设备回来后自动接着跑后面的步骤
     * （见 [start]）。验收 / 确认动作仍然一律不自动——那需要人看结果。
     */
    private fun RepoColumnStep.isAutoRunnable(): Boolean = when (kind.trim().lowercase()) {
        "installmodule", "installapp", "writefile", "writeconfig", "writehmaconfig", "runcommand", "reboot" -> true
        else -> false
    }

    /**
     * 预取密钥材料：先试自动源，失败再试专题里登记的地址，都失败才让用户自己选文件。
     *
     * 失败不阻塞向导——日志里说明一句，涉及密钥的步骤会自己要求用户提供文件。
     */
    private fun prefetchKeybox(topic: RepoColumnTopic): Job? {
        if (prefetchedTopicId == topic.id) return keyboxJob
        val hasKeyboxStep = topic.steps.any { it.fileContent.contains("\${keybox}") }
        if (topic.keybox == null && !hasKeyboxStep) return null
        prefetchedTopicId = topic.id
        keyboxJob = viewModelScope.launch {
            val auto = keyboxFetcher.fetchAuto()
            auto.onSuccess { content ->
                // 用户在等待期间自己选过文件，就以用户的为准：自动源没有摘要校验，不能静默顶掉手选的
                if (keyboxContent != null) return@onSuccess
                keyboxContent = content
                _uiState.update { it.copy(keyboxReady = true) }
                appendLog("已自动获取密钥材料（${content.length} 字节，来源：MeowDump 的 GitHub Release 镜像）")
                return@launch
            }
            var reason = auto.exceptionOrNull()?.message
            val declared = topic.keybox
            if (declared != null) {
                keyboxFetcher.fetch(declared).onSuccess { content ->
                    if (keyboxContent != null) return@onSuccess
                    keyboxContent = content
                    _uiState.update { it.copy(keyboxReady = true) }
                    appendLog("密钥材料已就绪（${content.length} 字节），sha256 与专栏登记的指纹一致")
                    return@launch
                }.onFailure { reason = it.message }
            }
            appendLog("没能自动获取密钥材料：${reason ?: "未知原因"}；涉及密钥的步骤会要求你自己选择文件")
        }
        return keyboxJob
    }

    /**
     * 用户从本地选了一份 keybox.xml。
     *
     * 商店不保存、不转存：内容只在内存里留给这次向导用。返回 null 表示接受，否则是给用户看的拒绝原因。
     * 只做形状校验（是 PEM 证书链 + 带私钥），不判断密钥"能不能过检测"——那是 Google 服务器说了算的。
     */
    fun provideKeybox(content: String): String? {
        val text = content.trim()
        if (text.length < MIN_KEYBOX_LENGTH) {
            return "这个文件太小了（${text.length} 字符），看起来不是完整的 keybox.xml"
        }
        if (!text.contains("BEGIN CERTIFICATE")) {
            return "没找到证书段（BEGIN CERTIFICATE），这不是 keybox.xml"
        }
        if (!text.contains("PRIVATE KEY")) {
            return "没找到私钥段（PRIVATE KEY），这不是 keybox.xml"
        }
        keyboxContent = text
        _uiState.update { it.copy(keyboxReady = true) }
        appendLog("已载入本地密钥材料（${text.length} 字符），只用于本次向导，商店不会保存")
        return null
    }

    /**
     * 执行一步。失败时把原因写到该步骤上并停止，用户修正后可以再点这一行重试。
     *
     * [force] 是用户明确点「升级 / 重做」时置位的：只有它会绕过「这座模块已经装过」的跳过，
     * 向导自己永远不会主动置位。
     */
    fun runStep(step: RepoColumnStep, force: Boolean = false) {
        if (runningStep) return
        // 已经做成的步骤再点一次就是重装模块、重写文件：向导里每一步都可能是一轮下载或一次
        // reboot，误触的代价太高。这里直接忽略（要重做请先回滚整次改动，再重新走一遍）。
        val done = _uiState.value.stepStates
            .firstOrNull { it.step.id == step.id }
            ?.status == ColumnStepStatus.SUCCESS
        if (done && !force) return
        // 用户主动点了某个「可选安装」步骤，就等同于勾选它，不再要求先去复选框打勾。
        if (step.optIn && step.id !in _uiState.value.optedIn) toggleOptIn(step.id)
        runningStep = true
        _uiState.update { it.copy(running = true, error = "") }
        viewModelScope.launch {
            try {
                // 密钥还在预取时先等一等：否则需要 keybox 的步骤会拿着 null 直接失败。
                keyboxJob?.join()
                // 重启步骤同样要先倒计时：单步执行也不能让手机毫无征兆地黑屏。
                if (step.kind.trim().equals("reboot", ignoreCase = true)) {
                    val runningColumn = columnId
                    val runningTopic = topic
                    if (runningColumn != null && runningTopic != null) {
                        ColumnProgressStore.markPendingResume(ksuApp, runningColumn, runningTopic.id, step.id)
                    }
                    if (!confirmRebootBeforeRunning()) {
                        appendLog("已暂停：等你手动重启设备")
                        return@launch
                    }
                }
                val result = executeStep(step, force)
                _uiState.update { it.copy(error = if (result.isFailure) result.message else "") }
                if (result.isFailure) {
                    appendLog("步骤「${step.title}」失败，向导已停在这里；修正后可以重试这一步，或回滚本次改动")
                } else {
                    maybeFinish()
                }
            } finally {
                runningStep = false
                _uiState.update { it.copy(running = false) }
            }
        }
    }

    /** 真正执行一步并把结果写回状态；[runStep] 与 [runAll] 共用。异常一律收敛成失败结果。 */
    private suspend fun CoroutineScope.executeStep(step: RepoColumnStep, force: Boolean): ColumnStepResult {
        val topic = this@ColumnWizardViewModel.topic
            ?: return ColumnStepResult(ColumnStepStatus.SKIPPED, "向导还没启动")
        updateStep(step.id) { it.copy(status = ColumnStepStatus.RUNNING, message = "", upgrade = null) }
        val context = ColumnRunContext(
            keyboxContent = keyboxContent,
            log = { line -> appendLog("[${step.title}] $line") },
            isActive = { isActive },
            force = force,
        )
        val result = try {
            stepRunner.run(step, topic, context)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            ColumnStepResult(
                status = ColumnStepStatus.FAILED,
                message = "执行异常：${error.message ?: "未知错误"}",
            )
        }
        updateStep(step.id) {
            it.copy(status = result.status, message = result.message, upgrade = result.upgrade)
        }
        if (result.log.isNotBlank()) appendLog(result.log)
        return result
    }

    /**
     * 勾选 / 取消勾选一个「可选安装」步骤。
     *
     * 这类步骤（目前是自检检测器）默认一个都不装：商店不替用户决定装哪几个，勾了才会进一键执行。
     */
    fun toggleOptIn(stepId: String) {
        _uiState.update { state ->
            val next = if (stepId in state.optedIn) state.optedIn - stepId else state.optedIn + stepId
            // 完成之后又勾上新的可选步骤：完成状态不再成立，底部的「一键执行」要能重新出现
            state.copy(optedIn = next, done = if (stepId in next) false else state.done)
        }
        saveProgress()
    }

    /** 用户主动跳过一步。只对 optional 步骤有意义，非 optional 的跳过会在日志里留痕。 */
    fun skipStep(step: RepoColumnStep) {
        if (runningStep) return
        if (_uiState.value.done) return
        // 非可选项不许跳过：跳过等于「假装做了」，重启和验收会被糊弄过去，最后自检一片红。
        if (!step.optional) {
            appendLog("「${step.title}」不是可选项，不能跳过；确实做过了请点「标记为已完成」")
            return
        }
        // 状态标签已经显示「已跳过」，这里不再重复写一遍。
        updateStep(step.id) { it.copy(status = ColumnStepStatus.SKIPPED, message = "") }
        appendLog("跳过步骤「${step.title}」")
        maybeFinish()
    }

    /** 用户按提示自己做完了（重启回来、装好了 APK、自己确认过），把这一步标成完成。 */
    fun markDone(step: RepoColumnStep) {
        if (runningStep) return
        // 状态标签同样已显示完成，不再重复。
        updateStep(step.id) { it.copy(status = ColumnStepStatus.SUCCESS, message = "") }
        appendLog("「${step.title}」已标记为完成")
        maybeFinish()
    }

    /** 逆序撤销本次向导装过的模块、写过的文件。 */
    fun rollback() {
        if (runningStep) return
        runningStep = true
        _uiState.update { it.copy(running = true) }
        viewModelScope.launch {
            try {
                val lines = try {
                    rollbackStore.rollback()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    listOf("回滚失败：${error.message ?: "未知错误"}")
                }
                lines.forEach { appendLog(it) }
                // 回滚是「回到开始前」，不是「结束」：进度要清掉、步骤要复位，用户才能重新走一遍。
                val topicId = _uiState.value.topicId
                runCatching { progressPrefs().edit().remove(progressKey(topicId)).apply() }
                runCatching { ColumnProgressStore.clear(ksuApp, columnId.orEmpty(), topicId) }
                // 回滚之后不该再自动续跑，否则重启回来会把刚撤掉的步骤又跑一遍
                runCatching { ColumnProgressStore.clearPendingResume(ksuApp) }
                prefetchedTopicId = null
                _uiState.update { state ->
                    state.copy(
                        done = false,
                        error = "",
                        checks = emptyList(),
                        stepStates = state.stepStates.map {
                            it.copy(status = ColumnStepStatus.PENDING, message = "", upgrade = null)
                        },
                    )
                }
                appendLog("已回滚到开始前的状态，可以重新执行")
            } finally {
                runningStep = false
                _uiState.update { it.copy(running = false) }
            }
        }
    }

    /** 手动重跑自检。步骤全部处理完时会自动跑一次。 */
    fun runSelfCheck() {
        viewModelScope.launch {
            val checks = selfCheck.check(ColumnCheckIds.ALL)
            _uiState.update { it.copy(checks = checks) }
            checks.forEach { result ->
                appendLog("自检 ${result.id}：${if (result.passed) "通过" else "未通过"} — ${result.detail}")
            }
        }
    }

    /**
     * 全部步骤都落定后收尾：跑一次自检并标记完成。
     *
     * [allowCelebrate] 为 true 时同时置位 [ColumnWizardUiState.celebrate]，界面弹一次庆祝 overlay。
     * 只有「刚刚真的跑完最后一步」的路径才传 true；重新进页面恢复出的完成态不弹。
     */
    private fun maybeFinish(allowCelebrate: Boolean = true) {
        val state = _uiState.value
        if (state.done) return
        val allSettled = state.stepStates.all { item ->
            item.status.isSettled || (item.step.optIn && item.step.id !in state.optedIn)
        }
        if (!allSettled) return
        if (state.checks.isEmpty()) runSelfCheck()
        // 走到这里说明这个专题已经没有待办：重启续跑标记可以清掉了。
        ColumnProgressStore.clearPendingResume(ksuApp)
        _uiState.update { it.copy(done = true, celebrate = allowCelebrate) }
        appendLog("全部步骤已处理完毕，下面是当前环境的自检结果")
    }

    /** 用户关掉庆祝 overlay。 */
    fun dismissCelebration() {
        _uiState.update { it.copy(celebrate = false) }
    }

    /**
     * 重启前先给用户一个倒计时，别让手机毫无征兆地黑屏。
     *
     * 返回 true = 时间到、按原计划重启；false = 用户选「稍后我自己重启」，调用方要停下本次
     * 一键执行。续跑标记在调用前已经写过（[ColumnProgressStore.markPendingResume]），
     * 用户手动重启后打开 App 会由 [resumePendingRunIfAny] 自动接着跑。
     */
    private suspend fun confirmRebootBeforeRunning(): Boolean {
        val decision = CompletableDeferred<Boolean>()
        rebootDecision = decision
        try {
            var left = REBOOT_COUNTDOWN_SECONDS
            _uiState.update { it.copy(rebootCountdown = left) }
            appendLog("${left} 秒后重启设备；回来后会自动接着跑剩下的步骤，不用再点一次")
            while (left > 0) {
                // 每秒看一次用户有没有点按钮；没点就继续倒数。
                val answered = withTimeoutOrNull(1_000L) { decision.await() }
                if (answered != null) return answered
                left -= 1
                _uiState.update { it.copy(rebootCountdown = left) }
            }
            return true
        } finally {
            // 协程被取消（用户退出页面/ViewModel 销毁）时也必须复位，否则倒计时卡片
            // 会永远停在界面上，rebootDecision 也会一直挂着一个再也等不到结果的 deferred。
            rebootDecision = null
            _uiState.update { it.copy(rebootCountdown = -1) }
        }
    }

    /** 倒计时卡片上的「立即重启」。 */
    fun rebootNow() {
        rebootDecision?.complete(true)
    }

    /** 倒计时卡片上的「稍后我自己重启」。 */
    fun rebootLater() {
        rebootDecision?.complete(false)
    }

    private fun updateStep(stepId: String, transform: (ColumnStepUiState) -> ColumnStepUiState) {
        _uiState.update { state ->
            state.copy(
                stepStates = state.stepStates.map { item ->
                    if (item.step.id == stepId) transform(item) else item
                },
            )
        }
        saveProgress()
    }

    /**
     * 把进度落到 SharedPreferences。
     *
     * 向导里最常见的动作就是「装完模块重启」，重启后 ViewModel 没了、内存状态全丢，
     * 用户得从头再点一遍。这里只存「步骤 id → 状态 + 一句话」，不存日志也不存下载好的文件。
     */
    private fun saveProgress() {
        val state = _uiState.value
        if (state.topicId.isEmpty() || state.stepStates.isEmpty()) return
        val array = JSONArray()
        state.stepStates.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.step.id)
                    .put("status", item.status.name)
                    .put("message", item.message.take(MAX_SAVED_MESSAGE_CHARS)),
            )
        }
        runCatching {
            progressPrefs().edit()
                .putString(progressKey(state.topicId), array.toString())
                .putStringSet(optedKey(state.topicId), state.optedIn.toSet())
                .apply()
        }
        // 汇总一条给主页卡片用：主页不该为了画一张卡去解析每个专题的步骤明细。
        val currentTopic = topic ?: return
        val settled = state.stepStates.count { it.status.isSettled }
        val next = state.stepStates.firstOrNull {
            !it.status.isSettled && (!it.step.optIn || it.step.id in state.optedIn)
        }
        runCatching {
            ColumnProgressStore.record(
                ksuApp,
                ColumnProgressEntry(
                    columnId = columnId.orEmpty(),
                    topicId = currentTopic.id,
                    topicTitle = currentTopic.title,
                    lastActiveAt = System.currentTimeMillis(),
                    settledSteps = settled,
                    totalSteps = state.stepStates.size,
                    done = state.done,
                    nextStepTitle = next?.step?.title.orEmpty(),
                    nextStepId = next?.step?.id.orEmpty(),
                ),
            )
        }
    }

    /**
     * 读回上次的进度。
     *
     * 只认步骤 id 还能对上的记录：专栏内容会更新，对不上的旧状态直接丢掉，
     * 免得把「已完成」错认到别的步骤上。
     */
    private fun restoreProgress(topicId: String): Map<String, Pair<ColumnStepStatus, String>>? {
        val raw = runCatching { progressPrefs().getString(progressKey(topicId), null) }.getOrNull()
            ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isEmpty()) continue
                    put(
                        id,
                        item.optString("status").toColumnStepStatus() to item.optString("message"),
                    )
                }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun progressPrefs() = ksuApp.getSharedPreferences(COLUMN_PREFS, android.content.Context.MODE_PRIVATE)

    /**
     * 进度 key 统一在这里拼：必须同时带专栏 id 和专题 id，同名专题（如 self-check）才不会串。
     */
    private fun wizardKey(prefix: String, topicId: String): String =
        "${prefix}_${columnId.orEmpty()}_$topicId"

    private fun progressKey(topicId: String): String = wizardKey("wizard_progress", topicId)

    /** 勾选状态单独存一份：进度是按步骤记的，勾选是「用户想装什么」的偏好，跨进度保留。 */
    private fun optedKey(topicId: String): String = wizardKey("wizard_opted", topicId)

    /** 只认已知状态名；RUNNING 之类的中间态一律回落到 PENDING（重启后它确实没在跑了）。 */
    private fun String.toColumnStepStatus(): ColumnStepStatus = when (this) {
        ColumnStepStatus.SUCCESS.name -> ColumnStepStatus.SUCCESS
        ColumnStepStatus.SKIPPED.name -> ColumnStepStatus.SKIPPED
        ColumnStepStatus.NEEDS_USER.name -> ColumnStepStatus.NEEDS_USER
        ColumnStepStatus.FAILED.name -> ColumnStepStatus.FAILED
        else -> ColumnStepStatus.PENDING
    }

    private fun appendLog(line: String) {
        if (line.isBlank()) return
        _uiState.update { state ->
            state.copy(log = (state.log + line).takeLast(MAX_LOG_LINES))
        }
    }

    /**
     * 只把「真的做完了」算落定。NEEDS_USER 是「等你动手」——重启还没做、安装器还开着、
     * 还要你自己看结果，把它算成完成会让用户在一次都没重启的情况下看到「全部处理完毕」。
     */
    private val ColumnStepStatus.isSettled: Boolean
        get() = this == ColumnStepStatus.SUCCESS || this == ColumnStepStatus.SKIPPED

    /** 恢复进度复核结果：确实装着 / 已经不在了 / root 不可用或 id 非法，无法确认。 */
    private sealed interface RestoreVerdict {
        object Installed : RestoreVerdict
        data class Missing(val moduleId: String) : RestoreVerdict
        data class Unverifiable(val moduleId: String) : RestoreVerdict
    }

    private companion object {
        /** 重启前的倒计时秒数：够用户看清提示，又不至于等太久。 */
        const val REBOOT_COUNTDOWN_SECONDS = 10

        /** 日志上限：长步骤（模块安装）输出很多，不截断会把内存和 UI 一起拖慢。 */
        const val MAX_LOG_LINES = 400

        /** 一份完整 keybox.xml（3 级证书链 + ECDSA 私钥）通常在 10 KiB 以上，低于这个数一定是选错了文件。 */
        const val MIN_KEYBOX_LENGTH = 1000

        /** 向导进度单独放一个文件，别和设置项混在一起。 */
        const val COLUMN_PREFS = "column_wizard"

        /** 每条步骤状态里存的说明文字上限，只为让人看懂上次停在哪，不承担完整日志职责。 */
        const val MAX_SAVED_MESSAGE_CHARS = 300

        /** 无法复核设备状态时追加在旧说明后的提示；重复恢复时不重复追加。 */
        const val STALE_RECORD_NOTE = "（上次记录，设备状态可能已变）"
    }
}
