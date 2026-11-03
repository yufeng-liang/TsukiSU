package com.sukisu.ultra.data.repository

import com.sukisu.ultra.data.model.RepoColumn

/**
 * 官方专栏索引的地址。
 *
 * 和「酷安社区模块」源一样走 jsDelivr：`raw.githubusercontent.com` 在国内常见网络下连不上，
 * 而专栏是给这些用户看的。内容由索引仓 `SukiSU-Community/modules-repo` 的 `json/columns.json`
 * 随模块索引一起重建发布，改专栏内容不用发 App 版本。
 */
const val OFFICIAL_COLUMNS_URL =
    "https://cdn.jsdelivr.net/gh/SukiSU-Community/modules-repo@main/json/columns.json"

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
