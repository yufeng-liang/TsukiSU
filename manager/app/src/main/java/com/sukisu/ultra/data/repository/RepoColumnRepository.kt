package com.sukisu.ultra.data.repository

import com.sukisu.ultra.data.model.RepoColumn

/**
 * 官方专栏索引的地址。
 *
 * 首选 GitHub raw：它不做 CDN 缓存，索引仓合并后立即是最新内容；jsDelivr 的 @main 别名
 * 实测会在合并后十几个小时里继续返回旧文件（2026-11 删步骤时踩过），所以只作为兜底。
 * raw 在国内常见网络下可能连不上，失败后由 [OFFICIAL_COLUMNS_FALLBACK_URLS] 的 CDN 顶上。
 */
const val OFFICIAL_COLUMNS_URL =
    "https://raw.githubusercontent.com/SukiSU-Community/modules-repo/main/json/columns.json"

/**
 * 商店「专栏」的只读数据源。
 *
 * 专栏是可选内容：拉不到时调用方应当整块隐藏专栏区，而不是把它当成页面级错误——模块列表本身
 * 不依赖它。
 */
interface RepoColumnRepository {
    /** 拉取官方专栏；失败原因放在 [Result] 里，不抛。 */
    suspend fun fetchColumns(): Result<List<RepoColumn>>
}
