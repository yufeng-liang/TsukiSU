package com.sukisu.ultra.data.column

/**
 * 「设备上现在到底配了多少」的体检结果。
 *
 * 注意这只回答「文件/目录在不在」，回答不了「配置对不对」：HMA 的探针只能看到
 * `/data/misc/*hide_my_applist*/` 存在，看不出里面是黑名单还是白名单模板；keybox 只能
 * 验结构，验不了它有没有被 Google 吊销。所以这份报告的用途是**给用户一个提示**，
 * 不能当成「已完成」的判据去自动跳过整个专题。
 */
data class ColumnHealthCheck(
    val id: String,
    val passed: Boolean,
    /** 探针给的一句话，例如「已检测到 KernelSU（/data/adb/ksu）」。 */
    val detail: String,
)

data class ColumnHealthReport(
    val checks: List<ColumnHealthCheck>,
    val scannedAt: Long,
) {
    val passedCount: Int get() = checks.count { it.passed }

    val totalCount: Int get() = checks.size

    fun passed(id: String): Boolean = checks.firstOrNull { it.id == id }?.passed == true

    /**
     * 日用级（过银行/支付）的达标判据：root 管理器 + Zygisk 加载器 + LSPosed + HMA-OSS
     * + 应用列表隐藏 + 排除策略 + 关 ADB。
     */
    val dayLevelReady: Boolean
        get() = passed(ColumnCheckIds.ROOT_MANAGER) &&
            passed(ColumnCheckIds.ZYGISK_NEXT) &&
            passed(ColumnCheckIds.LSPOSED) &&
            passed(ColumnCheckIds.HMA_OSS) &&
            passed(ColumnCheckIds.HMA_CONFIG) &&
            passed(ColumnCheckIds.DENYLIST) &&
            passed(ColumnCheckIds.ADB)

    /** 三绿（过 Play Integrity）的达标判据：日用级 + TEE 模拟 + PIF + 密钥材料有效。 */
    val integrityLevelReady: Boolean
        get() = dayLevelReady &&
            passed(ColumnCheckIds.TEESIMULATOR) &&
            passed(ColumnCheckIds.PIF) &&
            passed(ColumnCheckIds.KEYBOX)
}

/** 把自检结果按专题用到的 checkId 裁成一份报告，顺序沿用传入的 id 顺序。 */
fun buildColumnHealthReport(
    ids: List<String> = ColumnCheckIds.ALL,
    results: List<ColumnCheckResult>,
): ColumnHealthReport {
    val byId = results.associateBy { it.id }
    val checks = ids.map { id ->
        val result = byId[id]
        ColumnHealthCheck(
            id = id,
            passed = result?.passed == true,
            detail = result?.detail.orEmpty(),
        )
    }
    return ColumnHealthReport(checks = checks, scannedAt = System.currentTimeMillis())
}
