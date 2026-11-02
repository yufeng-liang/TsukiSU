package com.sukisu.ultra.ui.screen.modulerepo

import androidx.annotation.StringRes
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.MmrlVersion
import com.sukisu.ultra.data.model.RepoModule

/**
 * 列表卡片上那枚「源码状态」标记的状态。
 *
 * 这里只是判据，不含任何文案：Material 与 Miuix 两套界面各自把枚举翻成字符串资源，
 * 单测直接断言枚举，不会因为改一句文案就红。契约（§4）要求单状态——同一枚模块
 * 只显示一条，而不是「开源」和「最新版未覆盖」并排打架。
 */
enum class RepoSourceBadge {
    /** 仓库校验通过，最新版也被证据覆盖，且仓库带 LICENSE。 */
    OPEN_SOURCE,

    /** 仓库校验通过、最新版也被覆盖，但没读到 LICENSE——只说「公开源码」，不谎称开源。 */
    PUBLIC_SOURCE,

    /** 仓库校验通过，但最新版的包没在仓库里找到对应证据。 */
    LATEST_NOT_COVERED,

    /** 有仓库地址，但最近一次校验没通过（或不曾通过）。 */
    STALE_VERIFICATION,
}

/**
 * 该显示哪一枚标记；null 表示不显示。
 *
 * 没有 repoUrl 就是 null——「没填仓库」不该在列表上冒出一枚灰标，
 * 详情页会用一句「作者未提供公开源码」把这件事说清楚。
 */
fun repoSourceBadge(module: RepoModule): RepoSourceBadge? {
    if (module.repoUrl.isEmpty()) return null
    if (!module.repoVerified) return RepoSourceBadge.STALE_VERIFICATION
    if (!module.repoCoversLatest) return RepoSourceBadge.LATEST_NOT_COVERED
    return if (module.repoLicense.isNotEmpty()) RepoSourceBadge.OPEN_SOURCE else RepoSourceBadge.PUBLIC_SOURCE
}

/**
 * 详情页证据块用的那一版：最新一个带证据（repoTag 或 repoDigest）的版本。
 *
 * 不直接取 [RepoModule] 的 `latestRelease`：那一版可能恰好没有证据，
 * 而较早的一版有——证据块要展示的是「我们手上确实核对过的那一版」。
 */
fun evidenceVersion(module: RepoModule): MmrlVersion? =
    module.mmrl?.versions?.firstOrNull { it.repoTag.isNotEmpty() || it.repoDigest.isNotEmpty() }

/**
 * 证据块里的短哈希：去掉 `sha256:` 前缀取前 12 位。
 *
 * 只用来给人眼做比对；完整哈希在索引里，复制整串的需求由链接/详情承担。
 */
fun shortRepoDigest(digest: String): String =
    digest.removePrefix("sha256:").take(12)

/** 校验通过的两态才配绿勾；其余两态是「有仓库但证据不足」，只用灰标。 */
val RepoSourceBadge.isVerified: Boolean
    get() = this == RepoSourceBadge.OPEN_SOURCE || this == RepoSourceBadge.PUBLIC_SOURCE

/** 标记文案；两套界面共用一个映射，免得 Material 说「开源」而 Miuix 说别的。 */
@StringRes
fun repoSourceBadgeLabel(badge: RepoSourceBadge): Int = when (badge) {
    RepoSourceBadge.OPEN_SOURCE -> R.string.module_repo_badge_open_source
    RepoSourceBadge.PUBLIC_SOURCE -> R.string.module_repo_badge_public_source
    RepoSourceBadge.LATEST_NOT_COVERED -> R.string.module_repo_badge_latest_not_covered
    RepoSourceBadge.STALE_VERIFICATION -> R.string.module_repo_badge_stale
}

/**
 * 把索引条目里与源码仓库有关的字段收成导航参数。
 *
 * 详情页只显示命中的那个版本的 tag 与摘要，没必要把整份 versions 塞进 Bundle。
 */
fun RepoModule.repoEvidenceArg(): RepoEvidenceArg {
    val version = evidenceVersion(this)
    return RepoEvidenceArg(
        repoUrl = repoUrl,
        repoVerified = repoVerified,
        repoCheckedAt = repoCheckedAt,
        repoLicense = repoLicense,
        repoCoversLatest = repoCoversLatest,
        matchedTag = version?.repoTag.orEmpty(),
        digest = version?.repoDigest.orEmpty(),
    )
}
