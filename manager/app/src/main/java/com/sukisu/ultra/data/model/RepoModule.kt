package com.sukisu.ultra.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Author(
    val name: String,
    val link: String,
)

@Immutable
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

@Immutable
data class MmrlVersion(
    val version: String,
    val versionCode: Long,
    val timestamp: Long,
    /** ISO-8601 UTC rendering of [timestamp], empty when unknown. */
    val time: String,
    val zipUrl: String,
    /** Same path on the repository's own host, for indexes whose asset host has moved. */
    val zipUrlFallback: String?,
    val changelogUrl: String?,
    val size: Long,
    /**
     * 服务端在源码仓库里给这一版找到证据时记下的 Release tag，形如 `v1.2.3`；
     * 从 `checksums.txt` 命中时是 `checksums.txt@<branch>`。空表示这一版没有证据。
     */
    val repoTag: String = "",
    /** 整包哈希，`sha256:` + 64 位小写十六进制；空表示索引没给。 */
    val repoDigest: String = "",
    /** 这一版是机器人从仓库自动发布的。 */
    val auto: Boolean = false,
    /** 自动发布走的通道，`repo` 或 `update-json`；只在 [auto] 为 true 时出现。 */
    val autoVia: String = "",
)

/** Extra data carried by modules from MMRL-format repositories. */
@Immutable
data class MmrlModuleInfo(
    val readmeUrl: String?,
    val supportUrl: String?,
    val versions: List<MmrlVersion>,
)

/**
 * Extra data carried by modules from a KernelSU v1 registry (`modules.kernelsu.org`).
 *
 * Such an index lists only the newest version and no download address at all: the releases live in
 * a document per module, which is read when the module is opened rather than for every module on
 * every refresh.
 */
@Immutable
data class RegistryModuleInfo(
    /** Site root the module's paths are relative to, ending with a slash. */
    val baseUrl: String,
    /** Absolute address of the module's detail document, which carries its releases. */
    val detailUrl: String,
)

@Immutable
data class RepoModule(
    val moduleId: String,
    val moduleName: String,
    val authors: String,
    val authorList: List<Author>,
    val summary: String,
    val metamodule: Boolean,
    val zygisk: Boolean = false,
    val stargazerCount: Int,
    val updatedAt: String,
    val createdAt: String,
    val latestRelease: String,
    val latestReleaseTime: String,
    val latestVersionCode: Long,
    val latestAsset: ReleaseAsset?,
    val sourceId: String = "",
    val sourceName: String = "",
    /**
     * 酷安社区索引里记的出处：上传者当时贴的帖子地址、昵称、UID 与提交时间。
     *
     * 其它来源的索引没有这四个键，一律留空——详情页据此判断要不要画「出处」那一套。
     */
    val source: String = "",
    val uploader: String = "",
    val coolapkUid: String = "",
    /** 提交时间，epoch 毫秒或秒都由索引给；0 表示索引没带这个字段。 */
    val submittedAt: Long = 0L,
    /**
     * 作者本人主页，由上传者填写、归一化后的地址；空表示没填或填得不合法。
     *
     * 与 [uploader] 是两个人：作者是写在 module.prop 里的那一位，上传者是把包贴到索引里的人。
     * 这两个链接只代表「上传者声称这是作者」，没有做过任何验证，所以界面必须一并标注，
     * 否则读者会把「有人填了」当成「已经核实」。
     */
    val authorCoolapkUrl: String = "",
    val authorGithubUrl: String = "",
    /**
     * 作者公开的源码仓库，归一化后的 `https://github.com/<owner>/<repo>`。
     *
     * 空表示索引里没有这个字段（旧索引、或作者没填）。App 只按「有没有」判断，
     * 不把空串当成「校验失败」——没填和校验不过要分开说。
     */
    val repoUrl: String = "",
    /** 仓库校验是否通过；false 表示没验过或最近一次没验过。 */
    val repoVerified: Boolean = false,
    /** 最近一次校验的时刻，epoch 毫秒；0 表示没校验过。 */
    val repoCheckedAt: Long = 0L,
    /** SPDX 许可证标识；空表示没有 LICENSE 或识别不出（不是「无许可证」的断言）。 */
    val repoLicense: String = "",
    /**
     * 版本号最大的那一版是否已被仓库证据覆盖。
     *
     * 索引显式给了布尔就按它；没给时按「最新版有没有 repoTag」派生——旧索引只有
     * repoUrl 而没有逐版证据时，不该让列表谎称「最新版未覆盖」。
     */
    val repoCoversLatest: Boolean = false,
    /**
     * Names of the other repositories that publish this same module and lost the merge, so a
     * deduplicated list can still say where else the module is available.
     */
    val alternateSourceNames: List<String> = emptyList(),
    val mmrl: MmrlModuleInfo? = null,
    val registry: RegistryModuleInfo? = null,
)
