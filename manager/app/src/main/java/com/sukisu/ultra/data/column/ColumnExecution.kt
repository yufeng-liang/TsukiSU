package com.sukisu.ultra.data.column

import com.sukisu.ultra.data.model.RepoColumnStep
import com.sukisu.ultra.data.model.RepoColumnTopic

/**
 * 专栏专题的执行契约。
 *
 * 这里只放「向导」与「执行器」之间必须共享的词汇：状态、结果、自检项 id。具体怎么装模块、怎么写文件
 * 由 [ColumnStepRunner] 的实现决定，界面只认本文件里的类型——两边可以并行改而不互相踩。
 *
 * 一条贯穿全文件的原则：**任何一步都不做「替用户做决定」的兜底**。不认识的动作、拿不到的密钥、
 * 校验不过的字节，一律停下来报错，宁可让用户多点一次，也不猜。
 */

/** 一步的执行状态。 */
enum class ColumnStepStatus {
    /** 还没轮到。 */
    PENDING,

    /** 正在跑。 */
    RUNNING,

    /** 跑完了，成功。 */
    SUCCESS,

    /** 跑完了，失败。 */
    FAILED,

    /** 用户主动跳过，或 [RepoColumnStep.optional] 步骤被跳过。 */
    SKIPPED,

    /** 需要用户先看一眼再决定（确认弹窗、扫码、手动重启）。 */
    NEEDS_USER,
}

/**
 * 「已装但上游有新版」的信息。
 *
 * 只在 [ColumnStepStatus.SKIPPED] 上出现：说明这一步没重装，但设备上那份不是最新的。界面据此
 * 给一个「升级」按钮，用户点了才带 [ColumnRunContext.force] 重跑这一步。
 */
data class ColumnUpgrade(
    val moduleId: String,
    /** 设备上 module.prop 写的版本；读不到时是「未知版本」。 */
    val installedVersion: String,
    /** 上游给的最新版本标签。 */
    val latestVersion: String,
)

/** 一步跑完的结果。 */
data class ColumnStepResult(
    val status: ColumnStepStatus,
    /** 一句话结论，界面直接显示。 */
    val message: String = "",
    /** 完整输出（安装器 stdout/stderr、命令回显），失败时用来定位。 */
    val log: String = "",
    /** 已装且上游有新版时非空；其余情况为 null。 */
    val upgrade: ColumnUpgrade? = null,
) {
    val isFailure: Boolean get() = status == ColumnStepStatus.FAILED
}

/**
 * 执行一步时能拿到的上下文。
 *
 * [keyboxContent] 是**已经现拉并校验过 sha256** 的密钥材料正文；没有校验通过的绝不会出现在这里，
 * 执行器也就没法在「没校验」的情况下写出一份密钥。
 */
class ColumnRunContext(
    /** 校验通过的密钥材料正文；专题不需要或还没拉取时为 null。 */
    val keyboxContent: String? = null,
    /** 执行过程中的实时输出，界面拿去滚动展示。 */
    val log: (String) -> Unit = {},
    /** 协程被取消时返回 false；长任务（下载）应轮询它主动退出。 */
    val isActive: () -> Boolean = { true },
    /**
     * 用户明确点了「升级 / 重做」。
     *
     * 执行器平时对「已经在设备上的东西」一律跳过（幂等），只有这个标记能让它真的覆盖一遍。
     * 它只能由用户点击产生，向导不会自己置位。
     */
    val force: Boolean = false,
)

/** 单步执行器：把 [step] 落到设备上。实现必须自己处理失败，不抛异常给向导。 */
interface ColumnStepRunner {
    suspend fun run(
        step: RepoColumnStep,
        topic: RepoColumnTopic,
        ctx: ColumnRunContext,
    ): ColumnStepResult
}

/**
 * 回滚账本：只记「这次做了什么」，回滚时逆序撤销。
 *
 * 不承诺把设备恢复原状——用户原本装了什么、改过什么，我们不知道也不该猜。能保证的是：本次装的模块
 * 卸掉、本次写的文件恢复成动之前的字节（原本不存在就删掉）。
 */
interface ColumnRollback {
    /** 记一笔「本次装了哪个模块」，回滚时按 moduleId 卸载。 */
    suspend fun recordInstalledModule(moduleId: String)

    /** 记一笔「本次要写哪个文件」，回滚时恢复原内容。 */
    suspend fun recordWrittenFile(path: String)

    /** 逆序撤销全部记录；返回逐条结果，给用户看。 */
    suspend fun rollback(): List<String>
}

/** 自检项的固定 id。索引仓里的 `verify` 步骤只能引用这里出现过的值，写错就当成「只提示用户」。 */
object ColumnCheckIds {
    const val ROOT_MANAGER = "root-manager"
    const val ZYGISK_NEXT = "zygisk-next"
    const val DENYLIST = "denylist-policy"
    const val HMA_CONFIG = "hma-config"
    const val TARGET_LIST = "target-list"
    const val KEYBOX = "keybox-valid"
    const val HYBRID_MOUNT = "hybrid-mount"
    const val PATHMASK = "pathmask"
    const val ADB = "adb-disabled"

    /** 全部自检项，顺序即界面展示顺序：底座 → 隐藏 → 密钥 → 收尾。 */
    val ALL = listOf(
        ROOT_MANAGER,
        ZYGISK_NEXT,
        HYBRID_MOUNT,
        HMA_CONFIG,
        DENYLIST,
        PATHMASK,
        TARGET_LIST,
        KEYBOX,
        ADB,
    )
}

/** 一个自检项的结果。 */
data class ColumnCheckResult(
    val id: String,
    val passed: Boolean,
    /** 一句话说明，例如「ZygiskNext 已安装（1.5.0）」或「未检测到」。 */
    val detail: String,
)

/** 需要跑自检的东西：一组 id，返回同样顺序的结果。 */
interface ColumnSelfCheckRunner {
    suspend fun check(ids: List<String>): List<ColumnCheckResult>
}

/**
 * 向导每一屏的状态。界面只读这个，不直接碰执行器。
 */
data class ColumnWizardUiState(
    val topicId: String = "",
    val stepStates: List<ColumnStepUiState> = emptyList(),
    /** 正在跑某一步（含密钥拉取、模块下载）。 */
    val running: Boolean = false,
    /** 已经跑完（成功或用户中止）。 */
    val done: Boolean = false,
    /** 本次执行累计的日志行，最新的在最后。 */
    val log: List<String> = emptyList(),
    /** 全局错误（例如密钥拉取失败导致整个专题无法继续）。 */
    val error: String = "",
    /** 自检结果，跑完全部步骤后填。 */
    val checks: List<ColumnCheckResult> = emptyList(),
    /**
     * 本次是否已经有可用的密钥材料。
     *
     * 来源只有两种：专题在索引里登记了直链（预取并核对 sha256），或用户自己选了一个本地
     * keybox.xml。商店不保存、不转存这两条路径上的任何字节。
     */
    val keyboxReady: Boolean = false,
    /**
     * 用户勾选要装的「可选安装」步骤（[RepoColumnStep.optIn]）。
     *
     * 默认空集合 = 一个都不装。勾选只影响一键执行与逐步执行是否放行，不代表商店推荐装什么。
     */
    val optedIn: Set<String> = emptySet(),
    /** 非空时界面弹「执行前预检」确认框；确认或取消后清空。 */
    val preflight: ColumnPreflight? = null,
    /** 进专题时跑一次的设备体检结果，用来在顶部提示「你之前已经配过」。 */
    val health: ColumnHealthReport? = null,
)

/** 预检时对某一步的判定，决定「一键执行」要不要把它列给用户看。 */
enum class ColumnPreflightAction {
    /** 设备上已经有了，执行器会直接跳过。 */
    SKIP,

    /** 会覆盖已有内容（执行器会先备份到回滚账本）。 */
    OVERWRITE,

    /** 新增，当前设备上没有可冲突的东西。 */
    NEW,

    /** 需要用户自己动手：装 APK、重启、去官方页面取包。 */
    MANUAL,
}

data class ColumnPreflightItem(
    val stepId: String,
    val title: String,
    val action: ColumnPreflightAction,
    /** 一句话说明为什么这样判定，直接展示给用户。 */
    val reason: String,
)

/**
 * 一键执行前的预检结果。
 *
 * 存在的理由：存量用户的设备上往往已经有 keybox、HMA 配置、denylist 开关，直接跑会静默覆盖。
 * 这里把「将跳过 / 将覆盖 / 将新增 / 需手动」四类列清楚，让用户在按下去之前就知道会发生什么。
 */
data class ColumnPreflight(
    val items: List<ColumnPreflightItem> = emptyList(),
) {
    val skips: List<ColumnPreflightItem> get() = items.filter { it.action == ColumnPreflightAction.SKIP }

    val overwrites: List<ColumnPreflightItem> get() = items.filter { it.action == ColumnPreflightAction.OVERWRITE }

    val news: List<ColumnPreflightItem> get() = items.filter { it.action == ColumnPreflightAction.NEW }

    val manuals: List<ColumnPreflightItem> get() = items.filter { it.action == ColumnPreflightAction.MANUAL }

    /** 有覆盖动作就必须让用户点头，否则「一键」会变成「一键改掉你不知道的东西」。 */
    val needsConfirmation: Boolean get() = overwrites.isNotEmpty()
}

/** 单步在界面上的状态。 */
data class ColumnStepUiState(
    val step: RepoColumnStep,
    val status: ColumnStepStatus = ColumnStepStatus.PENDING,
    val message: String = "",
    /** 已装但上游有新版时非空，界面据此给「升级」按钮。 */
    val upgrade: ColumnUpgrade? = null,
)
