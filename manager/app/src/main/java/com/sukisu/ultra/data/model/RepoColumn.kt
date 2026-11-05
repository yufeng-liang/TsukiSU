package com.sukisu.ultra.data.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 商店「专栏」：按功能盘点的模块组合，例如「隐藏环境必刷」。
 *
 * 数据来自索引仓的 `json/columns.json`，随模块索引一起发布，改内容不用发版。这里的字段只做原样
 * 承载，不含任何执行语义——P0 阶段专栏只读展示，装什么、装不装都由用户自己一步步确认。
 */
@Immutable
@Parcelize
data class RepoColumn(
    /** 全局唯一的专栏 id，界面拿它做路由参数与本地进度记录的键。 */
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    /** 主题色提示，取值 root / hide / integrity / tool；空表示索引没给，由界面自行取默认色。 */
    val accent: String,
    /** 图标白名单 key，取值 shield / list / key / tool；空表示没给。 */
    val icon: String,
    /**
     * 排序权重，越小越靠前。
     *
     * 索引里没写就是 [Int.MAX_VALUE]：解析结果已经按它排好序，这里再留一个「缺省排最后」的值，
     * 免得界面自己按 order 排一遍时把没填 order 的专栏顶到最前面。
     */
    val order: Int = Int.MAX_VALUE,
    val topics: List<RepoColumnTopic>,
    /**
     * 「怎么选」引导：给完全不懂的用户看的档位说明，一条一档。
     *
     * 文案由索引维护（改文案不用发版）；App 只按顺序渲染。空列表表示索引没给，界面就不显示这一块。
     */
    val guide: List<RepoColumnGuide> = emptyList(),
) : Parcelable

/** 专栏顶部的一条「怎么选」说明，例如「日用级：只想让银行 App 能用，选这个」。 */
@Immutable
@Parcelize
data class RepoColumnGuide(
    /** 档位，取值 day / integrity / game，与 [RepoColumnTopic.tier] 对齐；空表示只是普通说明。 */
    val tier: String,
    val title: String,
    /** 人话说明：装什么、能过什么、代价是什么。 */
    val body: String,
) : Parcelable

/** 专栏里的一个专题，例如「过银行 / 金融类 App」。 */
@Immutable
@Parcelize
data class RepoColumnTopic(
    val id: String,
    val title: String,
    val summary: String,
    /** 风险等级，取值 low / medium / high；解析器对缺失值一律降级成 high（宁可多给一次警告）。 */
    val riskLevel: String,
    /** 覆盖的隐藏层，例如 `["L1", "L2", "L5"]`。 */
    val layers: List<String>,
    /** 前置条件的人话描述；只做展示与预检提示，不阻塞用户继续。 */
    val requires: List<String>,
    /** 这个专题配什么设备、什么时候验过；缺失时是全空对象，[freshness] 会自然落到 GRAY。 */
    val evidence: RepoColumnEvidence,
    /** 按顺序执行的步骤；P0 只展示，P1 起由向导逐步执行。 */
    val steps: List<RepoColumnStep>,
    /** 怎么自己确认专题做完生效了，给用户看的。 */
    val verifyHints: List<String>,
    /** 这个专题需要的密钥材料；为空表示不需要（过银行、藏管理器都不需要）。 */
    val keybox: RepoColumnKeybox? = null,
    /**
     * 档位，取值 day / integrity / game / tool。
     *
     * 只是给人看的分类标签（界面上一个徽章），不参与执行；空表示索引没给。
     */
    val tier: String = "",
) : Parcelable

/**
 * 专题用到的密钥材料（keybox）来源。
 *
 * 商店**不内置、不托管、不托管代理**这份材料：只登记「从哪拿、拿到的应该是多大的哈希、什么时候
 * 还验过」。真正使用前现拉现校验，校验不过就中止——我们只对「字节与登记时一致」负责，不对
 * 「这份密钥今天还能不能通过检测」负责，后者随时可能被 Google 吊销。
 */
@Immutable
@Parcelize
data class RepoColumnKeybox(
    /** 直链地址；必须是 https。 */
    val url: String,
    /** 整文件 sha256，小写十六进制；空表示没有登记，此时不下载。 */
    val sha256: String,
    /** 登记人最后一次确认这个地址还能拿到同样字节的日期，`yyyy-MM-dd`。 */
    val lastVerifiedAt: String,
    /** 来源说明，例如「Telegram 群流出，社区自行验证」。 */
    val providerNote: String,
    /** 给用户看的免责声明。 */
    val disclaimer: String,
) : Parcelable

/** 专题的证据：谁、在哪台机器上、什么时候验过。没有证据的专题不该被当成「已确认可用」。 */
@Immutable
@Parcelize
data class RepoColumnEvidence(
    /** 验证日期，`yyyy-MM-dd`；解析不出来时 [ageDays] 返回 [Int.MAX_VALUE]。 */
    val verifiedAt: String,
    /**
     * 证据的口径说明，例如「依据 2026-08 的社区实测帖」。
     *
     * 单独一个字段是因为 [verifiedAt] 是那份实测帖的日期，不是「商店最近一次核对」的日期：
     * 直接把它算成「N 天前验证」会让用户以为这套配置 N 天没更新过。
     */
    val label: String = "",
    val device: String,
    val kernel: String,
    /** 证据出处，例如「酷安 @泛彼柏舟y 73287937」。 */
    val source: String,
    val note: String,
) : Parcelable

/** 专题里的一步。P0 只读展示；P1 起由向导按 [kind] 分派执行。 */
@Immutable
@Parcelize
data class RepoColumnStep(
    val id: String,
    val title: String,
    /**
     * 动作类型，取值 installModule / writeConfig / writeFile / runCommand / verify / reboot / askUser。
     *
     * 解析器把未知或缺失的类型归到 `askUser`：那是最保守的一种，含义是「只提示用户、什么都不做」，
     * 界面照着它画也不会替用户动设备。
     */
    val kind: String,
    /** 可选步骤，失败或跳过都不影响专题结论。 */
    val optional: Boolean,
    val note: String,
    val source: RepoColumnStepSource,
    /**
     * `runCommand` 要执行的 shell 命令；其他类型忽略。
     *
     * 命令由索引仓人工维护、随专栏一起发布，等于「远程下发的可执行内容」——所以执行前必须让用户
     * 逐字看到它（界面会把原文展示出来），不做任何自动执行。
     */
    val command: String = "",
    /** `writeFile` / `writeConfig` 的落地路径，必须是以 `/` 开头的绝对路径。 */
    val filePath: String = "",
    /**
     * `writeFile` / `writeConfig` 要写入的正文。
     *
     * 支持 `${keybox}` 占位符：运行时替换成现拉并校验通过的那份密钥材料。除这个占位符外不做任何
     * 变量展开——少一种展开就少一种「数据里藏着命令」的可能。
     */
    val fileContent: String = "",
    /** `verify` 步骤引用的自检项 id（见 `ColumnSelfCheck`）；为空表示只提示用户自查。 */
    val checkId: String = "",
    /**
     * 高风险步骤的二次确认文案；非空时界面必须先弹一次确认才能执行。
     *
     * 留空不等于「免确认」：卸载、重启、写系统属性这类动作由执行器按 kind 自己兜底，不靠索引填。
     */
    val confirm: String = "",
    /**
     * 官方渠道链接（https），非空时界面在步骤行上给一个「打开」按钮。
     *
     * 用来兜住「官方没有可编程直链、只能人去点」的东西：春秋检测只通过 QQ 群与迅雷网盘发放，
     * 商店既不能代下也不能代装，但可以把官方入口摆到用户面前，省得他去找野包。
     */
    val link: String = "",
    /**
     * 默认不装、由用户勾选后才执行的步骤。
     *
     * 用在「装什么工具由用户自己定」的地方（例如自检检测器）：向导不会把它算进一键执行，
     * 界面上给一个勾选框，勾了才会跑，没勾就当作已跳过——不勾不是失败。
     */
    val optIn: Boolean = false,
) : Parcelable

/** 一个步骤的取材来源：上游 GitHub 仓库，或商店索引里已有的模块。 */
@Immutable
@Parcelize
data class RepoColumnStepSource(
    /** 取值 upstream / store；空表示索引没给。 */
    val kind: String,
    /** 上游仓库的 `owner/repo`，[kind] 为 upstream 时有意义。 */
    val repo: String,
    /** release 资源名的通配，例如 `Zygisk-Next-*.zip`。 */
    val assetPattern: String,
    val pinnedVersion: String,
    /** 整包 sha256，小写十六进制；空表示索引没给（P0 不下载，只展示）。 */
    val sha256: String,
    /** 商店索引里的模块 id，[kind] 为 store 时有意义。 */
    val moduleId: String,
    /**
     * `installApp` 步骤要装的 App 包名（索引里手写）。
     *
     * 存在的意义是**执行前**就能判断「已经装了吗」：APK 的包名本来只能从下载下来的安装包里读，
     * 而春秋那种包有 50 MB，为了判断一句「已经装了」先下 50 MB 太浪费。索引写死包名后，
     * 执行器在下载前先用 PackageManager 查一次，已装直接跳过。空串表示索引没写，退回老行为
     * （下载后读包名再判断）。
     */
    val packageName: String = "",
    /**
     * 模块自带的 `update.json` 地址，[kind] 为 updateJson 时有意义。
     *
     * 这类模块不在 GitHub 发版（LSPosed 的 2.x 只走 Telegram 频道），但官方提供了 updateJson：
     * 里面写着当前版本号与 zip 直链。商店读它就能永远装最新版，不用等人改索引。
     */
    val url: String = "",
) : Parcelable

/** 证据的时效档位：越旧越不该被读成「现在还能用」。 */
enum class EvidenceFreshness {
    /** 90 天内验过。 */
    GREEN,

    /** 91–180 天前验过：还能参考，但版本可能已经变了。 */
    YELLOW,

    /** 超过 180 天，或日期根本解析不出来：只算历史记录。 */
    GRAY,
}

/** 新鲜度阈值（天）。放在这里而不是散在界面里，是为了「多久算旧」只有一个定义。 */
private const val FRESH_WITHIN_DAYS = 90
private const val STALE_AFTER_DAYS = 180

/**
 * 证据从验证那天到今天过了多少天；日期解析失败返回 [Int.MAX_VALUE]。
 *
 * 用本地时区的「今天」而不是 24 小时的整数倍：证据是按天写的，跨不跨天才是人能感知的差别。
 * 比今天还晚的日期同样返回 [Int.MAX_VALUE]——那说明数据写错了（或设备时钟错得离谱），
 * 宁可标成「算不出来」，也不能把一个错日期显示成新鲜。
 */
fun RepoColumnEvidence.ageDays(now: Long = System.currentTimeMillis()): Int {
    val verified = runCatching { LocalDate.parse(verifiedAt.trim()) }.getOrNull()
        ?: return Int.MAX_VALUE
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(verified, today)
    return when {
        days < 0L -> Int.MAX_VALUE
        days > Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
        else -> days.toInt()
    }
}

/**
 * 证据的时效档位：≤90 天 GREEN，91–180 天 YELLOW，更旧（或日期无效）GRAY。
 */
fun RepoColumnEvidence.freshness(now: Long = System.currentTimeMillis()): EvidenceFreshness {
    val age = ageDays(now)
    return when {
        age <= FRESH_WITHIN_DAYS -> EvidenceFreshness.GREEN
        age <= STALE_AFTER_DAYS -> EvidenceFreshness.YELLOW
        else -> EvidenceFreshness.GRAY
    }
}
