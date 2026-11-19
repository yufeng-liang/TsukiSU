package com.sukisu.ultra.data.column

import android.content.Context
import com.sukisu.ultra.data.backup.sha256Hex
import com.sukisu.ultra.data.model.RepoColumn
import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnStepSource
import com.sukisu.ultra.data.model.RepoColumnTopic
import com.sukisu.ultra.data.repository.RepoColumnRepository
import com.sukisu.ultra.data.repository.RepoColumnRepositoryImpl
import com.sukisu.ultra.ksuApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/** 步骤 kind 里的重启标记；执行器那边的同名常量是私有的，这里按契约字符串对齐。 */
private const val REBOOT_KIND = "reboot"

/**
 * 一步执行的结果，已经收敛成「成功 / 失败 / 需要用户动手 / 正在重启」四态。
 *
 * [needsUser] 为 true 表示这一步得用户自己在设备上做（装 APK、打开第三方 app、自己验收），
 * 调用方必须停下来问用户结果，不能自己编一个「已通过」。
 */
data class ColumnAutomationOutcome(
    val status: ColumnStepStatus,
    val message: String,
    val stepTitle: String = "",
    val log: String = "",
    val needsUser: Boolean = false,
    val rebooting: Boolean = false,
) {
    val ok: Boolean get() = status == ColumnStepStatus.SUCCESS || status == ColumnStepStatus.SKIPPED

    val failed: Boolean get() = status == ColumnStepStatus.FAILED
}

/**
 * 给审批策略用的一小把事实：走「逐步确认」还是「专题批量授权」。
 *
 * [needsOwnConfirmation] 是命令、配置写入、需要勾选的高危步骤；[userAction] 是用户自己动手、
 * 助手不执行命令的步骤；[planDigest] 是当前索引版本下整个专题正文的摘要，索引一改就变。
 */
data class ColumnStepFacts(
    val stepId: String,
    val title: String,
    val kind: String,
    val needsOwnConfirmation: Boolean,
    val userAction: Boolean,
    val optIn: Boolean,
    val planDigest: String,
    val topicSteps: Int,
    val routineSteps: Int,
    /** 会被这次授权一起放行的步骤标题，用来说清「批量」到底含哪几步。 */
    val routineTitles: List<String>,
)

/** 账本快照：最新一条、全部条目、以及「重启后待继续」标记。 */
data class ColumnAutomationStatus(
    val latest: ColumnProgressEntry?,
    val entries: List<ColumnProgressEntry>,
    val pending: ColumnPendingResume?,
)

/**
 * 「隐藏环境」专栏的无 UI 执行内核。
 *
 * 向导页（[com.sukisu.ultra.ui.viewmodel.ColumnWizardViewModel]）与 AI 助手是同一套执行器的两个驱动端：
 * 步骤执行、回滚账本、自检探针、进度汇总都在这层，账本仍然是唯一状态源（见设计文档 Q1=C）。
 * 这里不碰 Compose、不持有 ViewModel，所以可以从任意协程上下文调用。
 *
 * 安全边界：只读动作（[columns]、[status]、[checks]）不触设备写入；写动作（[runStep]、[reboot]、
 * [rollback]）由调用方负责先问用户。
 */
class ColumnAutomationService(
    private val repository: RepoColumnRepository = RepoColumnRepositoryImpl(),
    private val keyboxFetcher: ColumnKeyboxFetcher = ColumnKeyboxFetcher(),
    private val rollbackStore: ColumnRollbackStore = ColumnRollbackStore(),
    private val selfCheckRunner: ColumnSelfCheckRunner = ColumnSelfCheckRunnerImpl(),
) {

    /**
     * 执行器必须惰性创建：[ColumnModuleFetcher] 构造时就会读 [ksuApp]，过早建好会在
     * 没有 Android 运行时（单元测试、纯 JVM 调用）直接抛 UninitializedPropertyAccessException。
     */
    private val stepRunner: ColumnStepRunner by lazy {
        ColumnStepRunnerImpl(
            fetcher = ColumnModuleFetcher(),
            rollback = rollbackStore,
            selfCheck = selfCheckRunner,
        )
    }

    /**
     * 密钥材料按专题缓存：一个专题十几步都会用到同一份 keybox，没必要每步重新下载一遍。
     * 缓存 null 表示「这个专题这次确实取不到」，避免反复超时。
     */
    private val keyboxCache = HashMap<String, String?>()
    private val keyboxMutex = Mutex()

    /** 拉一遍官方索引仓的专栏。失败时错误信息原样带给调用方，AI 侧据此说「拉不到文档」。 */
    suspend fun columns(): Result<List<RepoColumn>> = repository.fetchColumns()

    /** 定位一个专题；找不到时错误信息里带上可用的 id，免得模型瞎猜。 */
    suspend fun resolveTopic(columnId: String, topicId: String): Result<Pair<RepoColumn, RepoColumnTopic>> =
        repository.fetchColumns().mapCatching { columns ->
            val column = columns.firstOrNull { it.id == columnId }
                ?: error("没有这个专栏：" + columnId + "；可用：" + columns.joinToString { it.id })
            val topic = column.topics.firstOrNull { it.id == topicId }
                ?: error(
                    "专栏 " + column.id + " 下没有专题 " + topicId + "；可用：" +
                        column.topics.joinToString { it.id },
                )
            column to topic
        }

    /**
     * 读账本快照，给「继续」「现在到哪了」这类问句用。
     *
     * 读盘本身可能失败（账本损坏、prefs 不可用），这一层不让异常冒到工具调用上：返回空快照，
     * 由调用方说「账本读不出来」，比整条工具报错有用。
     */
    fun status(): ColumnAutomationStatus = runCatching {
        ColumnAutomationStatus(
            latest = ColumnProgressStore.latest(),
            entries = ColumnProgressStore.snapshot(),
            pending = ColumnProgressStore.peekPendingResume(),
        )
    }.getOrElse { ColumnAutomationStatus(null, emptyList(), null) }

    /** 12 项只读自检；红项的具体含义由调用方解释。 */
    suspend fun checks(): List<ColumnCheckResult> = selfCheckRunner.check(ColumnCheckIds.SUPPORTED)

    /**
     * 真正执行一步。
     *
     * [force] 会被透传给执行器，含义是「重做 / 覆盖」：设备上已有的 keybox、已装的模块会被真的改写一遍，
     * 同时跳过本函数的顺序保护。执行器契约（[ColumnRunContext.force]）要求它只能由用户显式点击产生。
     *
     * [optIn] 对应步骤上的「需勾选」：不显式确认就拒绝执行，避免助手替用户答应。
     */
    suspend fun runStep(
        columnId: String,
        topicId: String,
        stepId: String,
        force: Boolean = false,
        optIn: Boolean = false,
    ): ColumnAutomationOutcome {
        val (column, topic) = resolveTopic(columnId, topicId).getOrElse { error ->
            return ColumnAutomationOutcome(ColumnStepStatus.FAILED, error.message ?: "专栏文档拉取失败")
        }
        val step = topic.steps.firstOrNull { it.id == stepId }
            ?: return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "专题 " + topic.id + " 里没有步骤 " + stepId + "；可用：" +
                    topic.steps.joinToString { it.id },
            )
        if (step.kind.trim().lowercase() == REBOOT_KIND) {
            return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "这一步是重启点，必须走重启流程：先写续跑标记再断电，否则回来接不上。",
                stepTitle = step.title,
            )
        }
        if (step.hidden) {
            return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "「" + step.title + "」是隐藏步骤，只在向导里出现：请让用户自己在专栏页点这一项。",
                stepTitle = step.title,
            )
        }
        if (step.optIn && !optIn) {
            return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "「" + step.title + "」需要用户勾选同意（opt_in=true）才能执行；先问清楚，再带 opt_in 调用。",
                stepTitle = step.title,
            )
        }
        val blocking = blockingPredecessor(column.id, topic, step)
        if (blocking != null && !force) {
            return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "顺序没到：前面还有没做的「" + blocking.id + "｜" + blocking.title +
                    "」。先跑它；用户明确要求跳步时才用 force=true。",
                stepTitle = step.title,
            )
        }
        val logs = StringBuilder()
        val result = execute(step, topic, logs, force)
        recordProgress(column, topic, step, result.status)
        return ColumnAutomationOutcome(
            status = result.status,
            message = result.message,
            stepTitle = step.title,
            log = logs.toString(),
            needsUser = result.status == ColumnStepStatus.NEEDS_USER,
        )
    }

    /**
     * 重启后的接续：把重启那一步补记为完成、清掉标记，然后告诉调用方下一步是哪一步。
     *
     * 这里只做记账，不替用户跑下一步——设计上是「被动接续」：用户说「继续」之后，助手用本方法
     * 拿到下一步，再逐条走 [runStep]（每一步仍然各自过一次确认）。
     */
    suspend fun resume(columnId: String, topicId: String): ColumnAutomationOutcome {
        val (column, topic) = resolveTopic(columnId, topicId).getOrElse { error ->
            return ColumnAutomationOutcome(ColumnStepStatus.FAILED, error.message ?: "专栏文档拉取失败")
        }
        val settled = settledSteps(column.id, topic.id).toMutableSet()
        val head = StringBuilder()
        val pending = ColumnProgressStore.peekPendingResume(ksuApp)
        if (pending == null) {
            head.appendLine("账本里没有待继续的重启标记（可能已经接过一次，或设备还没重启完）。")
        } else if (pending.columnId != column.id || pending.topicId != topic.id) {
            head.appendLine(
                "待继续的标记属于另一个专题：" + pending.columnId + "/" + pending.topicId +
                    "。这个专题先按自己的账本继续。",
            )
        } else {
            val rebootStep = topic.steps.firstOrNull { it.id == pending.lastStepId }
            if (rebootStep != null) {
                settled += rebootStep.id
                saveSettled(column.id, topic.id, settled)
                ColumnProgressStore.clearPendingResume(ksuApp, column.id, topic.id)
                recordProgress(column, topic, rebootStep, ColumnStepStatus.SUCCESS)
                head.appendLine("已确认重启完成：" + rebootStep.title)
            } else {
                ColumnProgressStore.clearPendingResume(ksuApp, column.id, topic.id)
                head.appendLine("已清掉重启标记（原来的步骤 " + pending.lastStepId + " 在最新文档里已经不存在了）。")
            }
        }
        val remaining = topic.steps.filter { !it.hidden && it.id !in settled }
        val next = remaining.firstOrNull { !it.optional } ?: remaining.firstOrNull()
        val message = buildString {
            append(head)
            if (next == null) {
                appendLine("这个专题的步骤都已经落定了，没有下一步。")
            } else {
                appendLine("下一步：" + next.id + "｜" + next.title + "（kind=" + next.kind + "）")
                append("kind=reboot 的步骤走 reboot，其余用 run_step。")
            }
        }
        return ColumnAutomationOutcome(
            status = ColumnStepStatus.SUCCESS,
            message = message,
            stepTitle = next?.title ?: "",
            log = message,
        )
    }

    /**
     * 用户回报「这一步我自己做完了」（installapp / askuser 这类）：补记为完成，不执行任何命令。
     *
     * 只记账、不碰设备：助手拿不到这一步的完成证据，只能按用户的话记下来，避免顺序保护把整条链路卡死。
     */
    suspend fun markDone(columnId: String, topicId: String, stepId: String): ColumnAutomationOutcome {
        val (column, topic) = resolveTopic(columnId, topicId).getOrElse { error ->
            return ColumnAutomationOutcome(ColumnStepStatus.FAILED, error.message ?: "专栏文档拉取失败")
        }
        val step = topic.steps.firstOrNull { it.id == stepId }
            ?: return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "专题 " + topic.id + " 里没有步骤 " + stepId,
            )
        recordProgress(column, topic, step, ColumnStepStatus.SUCCESS)
        val settled = settledSteps(column.id, topic.id)
        val remaining = topic.steps.filter { !it.hidden && it.id !in settled }
        val next = remaining.firstOrNull { !it.optional } ?: remaining.firstOrNull()
        val message = buildString {
            append("已记下「")
            append(step.title)
            append("」由用户完成（没有执行任何命令）。")
            if (next != null) append("下一步：" + next.id + "｜" + next.title)
        }
        return ColumnAutomationOutcome(ColumnStepStatus.SUCCESS, message, step.title, message)
    }

    /**
     * 重启：先落续跑标记（同步 commit，失败就不重启），再让执行器断电。
     *
     * 标记的来源写成 [ColumnProgressStore.ORIGIN_AI]：向导看到这种标记只会让路，不会顺手把
     * 「一键执行」自己开起来。
     */
    suspend fun reboot(columnId: String, topicId: String, stepId: String): ColumnAutomationOutcome {
        val (column, topic) = resolveTopic(columnId, topicId).getOrElse { error ->
            return ColumnAutomationOutcome(ColumnStepStatus.FAILED, error.message ?: "专栏文档拉取失败")
        }
        val step = topic.steps.firstOrNull { it.id == stepId }
            ?: return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "专题 " + topic.id + " 里没有步骤 " + stepId,
            )
        if (step.kind.trim().lowercase() != REBOOT_KIND) {
            return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "「" + step.title + "」不是重启步骤（kind=" + step.kind + "），用 run_step 跑它。",
                stepTitle = step.title,
            )
        }
        val marked = ColumnProgressStore.markPendingResume(
            ksuApp,
            column.id,
            topic.id,
            step.id,
            ColumnProgressStore.ORIGIN_AI,
        )
        if (!marked) {
            return ColumnAutomationOutcome(
                ColumnStepStatus.FAILED,
                "续跑标记没能落盘（commit=false），为了不让你重启后回不来，这次没有执行重启。" +
                    "可以让用户手动重启，或先腾一下存储再试。",
                stepTitle = step.title,
            )
        }
        recordPendingReboot(column, topic, step)
        val logs = StringBuilder()
        val result = execute(step, topic, logs, false)
        return ColumnAutomationOutcome(
            status = result.status,
            message = result.message,
            stepTitle = step.title,
            log = logs.toString(),
            rebooting = result.status == ColumnStepStatus.SUCCESS,
        )
    }

    /**
     * 逆序撤销本次装过的模块、写过的文件。失败条目留在账本里等人工处理。
     *
     * 只有 [ColumnRollbackReport.complete] 为真才清账：回滚没做完还清账，等于把「设备上还有东西」
     * 这件事从账本里抹掉，用户下次点回滚会得到「没有需要撤销的改动」。
     */
    suspend fun rollback(columnId: String, topicId: String): ColumnAutomationOutcome {
        val report = try {
            rollbackStore.selectScope(columnId, topicId)
            rollbackStore.rollback()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ColumnRollbackReport(listOf("回滚失败：" + (error.message ?: "未知错误")), complete = false)
        }
        if (report.complete) {
            ColumnProgressStore.clear(ksuApp, columnId, topicId)
            ColumnProgressStore.clearPendingResume(ksuApp, columnId, topicId)
            saveSettled(columnId, topicId, emptySet())
        }
        val message = report.messages.joinToString("；").ifBlank { "没有需要撤销的改动" } +
            if (report.complete) "" else "（回滚没做完，账本保持原样；处理完再试一次）"
        return ColumnAutomationOutcome(
            status = if (report.complete) ColumnStepStatus.SUCCESS else ColumnStepStatus.FAILED,
            message = message,
            log = report.messages.joinToString("\n"),
        )
    }

    /** 记下用户选的档位（专题 id 就是档位），只写偏好，不动设备。 */
    fun selectTier(columnId: String, topicId: String, tier: String) {
        prefs().edit().putString(tierKey(columnId, topicId), tier.trim()).apply()
    }

    /** 读回档位偏好；没选过返回 null。 */
    fun selectedTier(columnId: String, topicId: String): String? =
        prefs().getString(tierKey(columnId, topicId), null)

    /**
     * 给确认卡用的一行说明：把这一步**真正会做什么**写给人看，而不是只报 id。
     *
     * AI 侧在执行前必须把这段展示给用户，否则「同意」只是一句空话。
     */
    suspend fun stepBrief(columnId: String, topicId: String, stepId: String): String {
        val (_, topic) = resolveTopic(columnId, topicId).getOrElse { error ->
            return error.message ?: "找不到这个步骤"
        }
        val step = topic.steps.firstOrNull { it.id == stepId }
            ?: return "专题 " + topic.id + " 里没有步骤 " + stepId
        return buildBrief(step, topic)
    }

    /**
     * 审批要用的事实；专题拉不到时返回 null，调用方退回「每步都问、且不许记住」的保守策略。
     */
    suspend fun stepFacts(columnId: String, topicId: String, stepId: String): ColumnStepFacts? {
        val (_, topic) = resolveTopic(columnId, topicId).getOrNull() ?: return null
        val step = topic.steps.firstOrNull { it.id == stepId } ?: return null
        val visible = topic.steps.filter { !it.hidden }
        val routine = visible.filter { !needsOwnConfirmation(it) && !isUserAction(it) }
        return ColumnStepFacts(
            stepId = step.id,
            title = step.title,
            kind = step.kind,
            needsOwnConfirmation = needsOwnConfirmation(step),
            userAction = isUserAction(step),
            optIn = step.optIn,
            planDigest = planDigest(topic),
            topicSteps = visible.size,
            routineSteps = routine.size,
            routineTitles = routine.map { it.title },
        )
    }

    /** 命令、配置写入、带 confirm 的步骤：每一步都要人看着，不能用专题授权兜。 */
    private fun needsOwnConfirmation(step: RepoColumnStep): Boolean =
        step.confirm.isNotBlank() || step.optIn || step.kind.trim().lowercase() in COMMAND_KINDS

    /** 用户自己动手的步骤：助手不执行命令，只回报结果。 */
    private fun isUserAction(step: RepoColumnStep): Boolean =
        step.kind.trim().lowercase() in USER_ACTION_KINDS

    /**
     * 整个专题正文的摘要，作为批量授权的作用域。
     *
     * 覆盖命令、写入路径、写入内容、取材来源与版本：索引改一个字，摘要就变，用户之前那次
     * 「本次请求始终允许」自动失效——不会出现「看过旧命令、实际跑的是新命令」。
     */
    private fun planDigest(topic: RepoColumnTopic): String {
        val canonical = buildString {
            topic.steps.forEach { step ->
                append(step.id).append('|').append(step.kind).append('|').append(step.title).append('|')
                append(step.command).append('|').append(step.filePath).append('|')
                append(step.fileContent).append('|').append(step.source.kind).append('|')
                append(step.source.repo).append('|').append(step.source.pinnedVersion).append('|')
                append(step.source.sha256).append('|').append(step.source.moduleId).append('|')
                append(step.source.packageName).append('|').append(step.note).append('\n')
            }
        }
        return canonical.toByteArray(Charsets.UTF_8).sha256Hex().take(16)
    }

    /** 通用 key 后缀：档位按专栏+专题各存一份，换专栏不互相覆盖。 */
    private fun tierKey(columnId: String, topicId: String): String =
        "selected_tier_" + columnId + "_" + topicId

    private fun prefs() = ksuApp.getSharedPreferences(ColumnProgressStore.PREFS, Context.MODE_PRIVATE)

    /**
     * 顺序保护：目标步骤之前还有没落定、非可选、非隐藏的步骤时，返回那一步。
     *
     * 隐藏步骤不参与：它只在向导里出现，助手根本跑不了，让它卡住只会把整条链路堵死。
     */
    private fun blockingPredecessor(
        columnId: String,
        topic: RepoColumnTopic,
        step: RepoColumnStep,
    ): RepoColumnStep? {
        val settled = settledSteps(columnId, topic.id)
        val index = topic.steps.indexOfFirst { it.id == step.id }
        if (index <= 0) return null
        return topic.steps.subList(0, index).firstOrNull { candidate ->
            !candidate.hidden && !candidate.optional && candidate.id !in settled
        }
    }

    /** 结果落成一句人话：标题 + 类型 + 会改什么。超长内容截断，免得把确认框撑爆。 */
    private fun buildBrief(step: RepoColumnStep, topic: RepoColumnTopic): String = buildString {
        append(step.title)
        append("（")
        append(topic.title)
        append(" · kind=")
        append(step.kind)
        append("）")
        when {
            step.command.isNotBlank() -> {
                appendLine()
                append("将执行命令：")
                append(step.command.trim().take(BRIEF_CHARS))
            }
            step.filePath.isNotBlank() -> {
                appendLine()
                append("将写入文件：")
                append(step.filePath)
                if (step.filePath.contains(KEYBOX_PATH_HINT, ignoreCase = true)) {
                    append("（会写入密钥材料；设备上已有合法 keybox 时默认跳过）")
                }
            }
            step.source.kind.isNotBlank() -> {
                appendLine()
                append("来源：")
                append(describeSource(step.source))
            }
        }
        if (step.note.isNotBlank()) {
            appendLine()
            append("说明：")
            append(step.note.trim().take(BRIEF_CHARS))
        }
        if (step.confirm.isNotBlank()) {
            appendLine()
            append("提示：")
            append(step.confirm.trim().take(BRIEF_CHARS))
        }
    }

    /** 取材来源说人话：上游仓库带版本，商店模块带 id，其他原样。 */
    private fun describeSource(source: RepoColumnStepSource): String = when (source.kind) {
        "upstream" -> source.repo.ifBlank { "上游仓库" } +
            if (source.pinnedVersion.isBlank()) "" else " @ " + source.pinnedVersion
        "store" -> "商店模块 " + source.moduleId.ifBlank { "（未写模块 id）" }
        else -> source.kind.ifBlank { "未注明来源" }
    }.take(BRIEF_CHARS)

    /** 统一收敛异常：协程取消继续往上抛，其余都当成一步失败，不让向导/对话被异常打断。 */
    private suspend fun execute(
        step: RepoColumnStep,
        topic: RepoColumnTopic,
        logs: StringBuilder,
        force: Boolean,
    ): ColumnStepResult {
        val keybox = ensureKeybox(topic)
        val scope = currentCoroutineContext()
        val context = ColumnRunContext(
            keyboxContent = keybox,
            log = { line -> appendTail(logs, line + "\n") },
            // 取消必须真的生效：向导那边会 cancel 协程，执行器的长任务（下载）要能停下来。
            isActive = { scope.isActive },
            force = force,
        )
        val result = try {
            stepRunner.run(step, topic, context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ColumnStepResult(
                status = ColumnStepStatus.FAILED,
                message = "执行异常：" + (error.message ?: "未知错误"),
            )
        }
        // 执行器自己那份日志比我们的回调更完整（带「已截断」标记）：取尾巴并进来，失败原因别丢。
        if (result.log.isNotBlank()) {
            appendTail(logs, "\n[执行器日志]\n" + result.log.takeLast(STEP_LOG_TAIL_CHARS))
        }
        return result
    }

    /** 只保留尾部：调用方看的是「最后发生了什么」，留头部等于把失败现场扔掉。 */
    private fun appendTail(builder: StringBuilder, text: String) {
        builder.append(text)
        val overflow = builder.length - MAX_LOG_CHARS
        if (overflow > 0) builder.delete(0, overflow)
    }

    /**
     * 取密钥材料，按专题缓存一次。
     *
     * 顺序是「先索引里钉了 sha256 的直链，再退公开源」：公开源没有摘要可比，能少用就少用。
     * 取不到不算失败——写密钥那一步会自己要求用户选文件。
     */
    private suspend fun ensureKeybox(topic: RepoColumnTopic): String? {
        if (!topicNeedsKeybox(topic)) return null
        keyboxMutex.withLock {
            if (keyboxCache.containsKey(topic.id)) return keyboxCache[topic.id]
        }
        val declared = topic.keybox
        val fromDeclared = if (declared != null) keyboxFetcher.fetch(declared).getOrNull() else null
        val fetched = fromDeclared ?: keyboxFetcher.fetchAuto().getOrNull()
        keyboxMutex.withLock { keyboxCache[topic.id] = fetched }
        return fetched
    }

    /** 专题是否真的会用到密钥材料：声明了直链，或有步骤正文带 keybox 占位。 */
    private fun topicNeedsKeybox(topic: RepoColumnTopic): Boolean =
        topic.keybox != null || topic.steps.any { it.fileContent.contains("\${keybox}") }

    /**
     * 把这一步记进主页卡片的进度汇总。
     *
     * [settle] 为 false 用于「重启前」：标记已经写了，但这一步还没完成，账本要如实停在它身上。
     */
    private fun recordProgress(
        column: RepoColumn,
        topic: RepoColumnTopic,
        step: RepoColumnStep,
        status: ColumnStepStatus,
        settle: Boolean = true,
    ) {
        val settled = settledSteps(column.id, topic.id).toMutableSet()
        if (settle && (status == ColumnStepStatus.SUCCESS || status == ColumnStepStatus.SKIPPED)) {
            settled += step.id
        }
        if (settle) saveSettled(column.id, topic.id, settled)
        val remaining = topic.steps.filter { it.id !in settled }
        val next = remaining.firstOrNull { !it.optional } ?: remaining.firstOrNull()
        runCatching {
            ColumnProgressStore.record(
                ksuApp,
                ColumnProgressEntry(
                    columnId = column.id,
                    topicId = topic.id,
                    topicTitle = topic.title,
                    lastActiveAt = System.currentTimeMillis(),
                    settledSteps = topic.steps.count { it.id in settled },
                    totalSteps = topic.steps.size,
                    done = next == null,
                    nextStepTitle = next?.title ?: "",
                    nextStepId = next?.id ?: "",
                ),
            )
        }
    }

    /** 重启前把「停在重启这一步」写进汇总账本：回来 resume 才能知道该把哪一步补记为完成。 */
    private fun recordPendingReboot(column: RepoColumn, topic: RepoColumnTopic, step: RepoColumnStep) {
        recordProgress(column, topic, step, ColumnStepStatus.NEEDS_USER, settle = false)
    }

    private fun settledSteps(columnId: String, topicId: String): Set<String> {
        val raw = prefs().getString(settledKey(columnId, topicId), null) ?: return emptySet()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                val id = array.optString(index)
                if (id.isNotEmpty()) add(id)
            }
        }
    }

    private fun saveSettled(columnId: String, topicId: String, settled: Set<String>) {
        val array = JSONArray()
        settled.forEach { array.put(it) }
        runCatching {
            prefs().edit().putString(settledKey(columnId, topicId), array.toString()).apply()
        }
    }

    private fun settledKey(columnId: String, topicId: String): String =
        "ai_settled_" + columnId + "_" + topicId

    private companion object {
        /** 日志上限：助手侧只播报摘要，留太多只会把上下文塞满。 */
        const val MAX_LOG_CHARS = 4000

        /** 执行器日志的尾巴长度：失败原因通常就在最后几行。 */
        const val STEP_LOG_TAIL_CHARS = 2000

        /** 确认卡里单段说明的字数上限。 */
        const val BRIEF_CHARS = 260

        /** 路径里出现这个词就提示「这是密钥文件」。 */
        const val KEYBOX_PATH_HINT = "keybox"

        /** 必须逐步确认的 kind：命令直接进 root shell，配置写入会改系统行为。 */
        val COMMAND_KINDS = setOf("runcommand", "writeconfig", "writehmaconfig")

        /** 用户自己动手、助手不执行命令的 kind。 */
        val USER_ACTION_KINDS = setOf("installapp", "askuser")
    }
}
