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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        _uiState.value = ColumnWizardUiState(
            topicId = topic.id,
            stepStates = topic.steps.map { ColumnStepUiState(step = it) },
        )
        prefetchKeybox(topic)
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
     */
    fun runStep(step: RepoColumnStep) {
        val topic = this.topic ?: return
        if (runningStep) return
        runningStep = true
        updateStep(step.id) { it.copy(status = ColumnStepStatus.RUNNING, message = "") }
        _uiState.update { it.copy(running = true, error = "") }
        viewModelScope.launch {
            val context = ColumnRunContext(
                keyboxContent = keyboxContent,
                log = { line -> appendLog("[${step.title}] $line") },
                isActive = { isActive },
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
            updateStep(step.id) { it.copy(status = result.status, message = result.message) }
            if (result.log.isNotBlank()) appendLog(result.log)
            runningStep = false
            _uiState.update { it.copy(running = false, error = if (result.isFailure) result.message else "") }
            if (result.isFailure) {
                appendLog("步骤「${step.title}」失败，向导已停在这里；修正后可以重试这一步，或回滚本次改动")
            } else {
                maybeFinish()
            }
        }
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
    }
}
