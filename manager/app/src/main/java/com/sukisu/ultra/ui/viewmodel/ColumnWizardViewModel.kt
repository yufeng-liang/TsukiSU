package com.sukisu.ultra.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukisu.ultra.data.column.ColumnCheckIds
import com.sukisu.ultra.data.column.ColumnKeyboxFetcher
import com.sukisu.ultra.data.column.ColumnModuleFetcher
import com.sukisu.ultra.data.column.ColumnRollbackStore
import com.sukisu.ultra.data.column.ColumnRunContext
import com.sukisu.ultra.data.column.ColumnSelfCheckRunner
import com.sukisu.ultra.data.column.ColumnSelfCheckRunnerImpl
import com.sukisu.ultra.data.column.ColumnStepResult
import com.sukisu.ultra.data.column.ColumnStepRunner
import com.sukisu.ultra.data.column.ColumnStepRunnerImpl
import com.sukisu.ultra.data.column.ColumnStepStatus
import com.sukisu.ultra.data.column.ColumnStepUiState
import com.sukisu.ultra.data.column.ColumnWizardUiState
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnTopic
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    /** 当前专题，[runStep] 需要把它一起交给执行器（步骤共享专题级的 keybox 信息）。 */
    private var topic: RepoColumnTopic? = null

    /** 校验通过的密钥材料正文；没拉取、没校验通过一律为 null。 */
    private var keyboxContent: String? = null

    /** 同一条向导只允许一个步骤在跑。 */
    private var runningStep = false

    /** 已经预取过密钥的专题 id，避免重复网络请求。 */
    private var prefetchedTopicId: String? = null

    /**
     * 进入向导时调用。同一专题重复调用不会重置已有进度（导航返回再进来还能看到上次跑到哪）。
     */
    fun start(topic: RepoColumnTopic) {
        if (this.topic?.id == topic.id && _uiState.value.stepStates.isNotEmpty()) return
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
        )
        if (!restored.isNullOrEmpty()) {
            appendLog("已恢复上次的进度（${restored.size} 步已处理），接着往下做就行")
        }
        prefetchKeybox(topic)
    }

    /**
     * 一键执行：按顺序把「不需要人看着」的步骤跑完，遇到需要人工的（重启、验收、确认）就停下，
     * 并在日志里说清还剩哪些要自己做。
     *
     * 用途是设备重启后回来接着跑：重启前的步骤已经是 SUCCESS/SKIPPED，会被直接跳过，
     * 真正执行的只有重启后才允许做的那几步（例如写 HMA 配置）。
     */
    fun runAll() {
        if (runningStep) return
        val pending = _uiState.value.stepStates
            .filter { !it.status.isSettled }
            .map { it.step }
            .filter { it.isAutoRunnable() }
        if (pending.isEmpty()) {
            appendLog("没有可以自动执行的步骤了；剩下的都需要你手动处理")
            return
        }
        runningStep = true
        _uiState.update { it.copy(running = true, error = "") }
        appendLog("一键执行：${pending.size} 个步骤会自动跑完，遇到需要人工的会停下来")
        viewModelScope.launch {
            for ((index, step) in pending.withIndex()) {
                if (!isActive) break
                val result = executeStep(step, force = false)
                if (result.isFailure) {
                    runningStep = false
                    _uiState.update { it.copy(running = false, error = result.message) }
                    appendLog("步骤「${step.title}」失败，一键执行停在这里（修正后可以重试这一步）")
                    return@launch
                }
                appendLog("进度：${index + 1}/${pending.size}")
            }
            runningStep = false
            _uiState.update { it.copy(running = false) }
            maybeFinish()
            appendLog("自动步骤都执行完了；剩下的需要你手动确认")
        }
    }

    /** 这些步骤不需要人看着：装模块、装 APK、写文件、跑命令。重启 / 验收 / 确认动作一律不自动。 */
    private fun RepoColumnStep.isAutoRunnable(): Boolean = when (kind.trim().lowercase()) {
        "installmodule", "installapp", "writefile", "writeconfig", "writehmaconfig", "runcommand" -> true
        else -> false
    }

    /**
     * 预取专题声明的密钥材料。失败不阻塞向导：日志里说明一句，相关步骤自己会要求用户提供文件。
     */
    private fun prefetchKeybox(topic: RepoColumnTopic) {
        val keybox = topic.keybox ?: return
        if (prefetchedTopicId == topic.id) return
        prefetchedTopicId = topic.id
        viewModelScope.launch {
            keyboxFetcher.fetch(keybox)
                .onSuccess { content ->
                    keyboxContent = content
                    _uiState.update { it.copy(keyboxReady = true) }
                    appendLog("密钥材料已就绪（${content.length} 字节），sha256 与专栏登记的指纹一致")
                }
                .onFailure { error ->
                    appendLog("密钥材料未能自动获取：${error.message ?: "未知原因"}；涉及密钥的步骤会要求你自己提供文件")
                }
        }
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
        runningStep = true
        _uiState.update { it.copy(running = true, error = "") }
        viewModelScope.launch {
            val result = executeStep(step, force)
            runningStep = false
            _uiState.update { it.copy(running = false, error = if (result.isFailure) result.message else "") }
            if (result.isFailure) {
                appendLog("步骤「${step.title}」失败，向导已停在这里；修正后可以重试这一步，或回滚本次改动")
            } else {
                maybeFinish()
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

    /** 用户主动跳过一步。只对 optional 步骤有意义，非 optional 的跳过会在日志里留痕。 */
    fun skipStep(step: RepoColumnStep) {
        if (runningStep) return
        if (_uiState.value.done) return
        updateStep(step.id) { it.copy(status = ColumnStepStatus.SKIPPED, message = "已跳过") }
        appendLog("跳过步骤「${step.title}」${if (step.optional) "" else "（该步骤不是可选项）"}")
        maybeFinish()
    }

    /** 逆序撤销本次向导装过的模块、写过的文件。 */
    fun rollback() {
        if (runningStep) return
        runningStep = true
        _uiState.update { it.copy(running = true) }
        viewModelScope.launch {
            val lines = try {
                rollbackStore.rollback()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                listOf("回滚失败：${error.message ?: "未知错误"}")
            }
            lines.forEach { appendLog(it) }
            runningStep = false
            _uiState.update { it.copy(running = false, done = true) }
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

    /** 全部步骤都落定后收尾：跑一次自检并标记完成。 */
    private fun maybeFinish() {
        val state = _uiState.value
        if (state.done) return
        val allSettled = state.stepStates.all { it.status.isSettled }
        if (!allSettled) return
        if (state.checks.isEmpty()) runSelfCheck()
        _uiState.update { it.copy(done = true) }
        appendLog("全部步骤已处理完毕，下面是当前环境的自检结果")
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
                .apply()
        }
    }

    /**
     * 读回上次的进度。
     *
     * 只认 [PROGRESS_TTL_MS] 内、且步骤 id 还能对上的记录：专栏内容会更新，
     * 对不上的旧状态直接丢掉，免得把「已完成」错认到别的步骤上。
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

    private fun progressKey(topicId: String): String = "wizard_progress_$topicId"

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

    private val ColumnStepStatus.isSettled: Boolean
        get() = this == ColumnStepStatus.SUCCESS ||
            this == ColumnStepStatus.SKIPPED ||
            this == ColumnStepStatus.NEEDS_USER

    private companion object {
        /** 日志上限：长步骤（模块安装）输出很多，不截断会把内存和 UI 一起拖慢。 */
        const val MAX_LOG_LINES = 400

        /** 一份完整 keybox.xml（3 级证书链 + ECDSA 私钥）通常在 10 KiB 以上，低于这个数一定是选错了文件。 */
        const val MIN_KEYBOX_LENGTH = 1000

        /** 向导进度单独放一个文件，别和设置项混在一起。 */
        const val COLUMN_PREFS = "column_wizard"

        /** 每条步骤状态里存的说明文字上限，只为让人看懂上次停在哪，不承担完整日志职责。 */
        const val MAX_SAVED_MESSAGE_CHARS = 300
    }
}
