package com.sukisu.ultra.ui.screen.upload

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R

/**
 * 提交栏左边那一句「现在能不能传」。
 *
 * 按优先级：
 *  1. 能传 → 「可以上传」
 *  2. 归属被服务端判成别人的 → **服务端原话**（[ModuleUploadRules.ownershipBlockReason]），
 *     服务端没给话时用本地化兜底句。它压在「还差 N 项」前面，是因为那一条不是用户动手
 *     能解决的：显示「还差 1 项」会让他找一个永远不存在的待办。
 *  3. 已经传成功 / 正在传 / 正在查 / 已收录 → 各给一句专门的话；取人机校验令牌的准备阶段
 *     另有一句（见 [preparing]），那一句不能借「正在上传」。
 *     以前这几种一律落到「还差 N 项」，而 N 只数用户能动手的那几项，于是出现
 *     「还差 0 项才能上传」而按钮是灰的。
 *  4. 其余 → 「还差 N 项才能上传」。
 *
 * 只在这里拼一次，Material 与 Miuix 两套界面共用：文案口径分叉过一次（两屏各写各的），
 * 这次不再给它分叉的机会。
 */
@Composable
fun submitHint(
    canSubmit: Boolean,
    blockers: List<ModuleUploadRules.SubmitBlocker>,
    ownershipBlock: String,
    preparing: Boolean = false,
): String = when {
    canSubmit -> stringResource(R.string.module_upload_ready)

    // 取令牌那一段：请求还没发出去，说「正在上传」是假的。
    preparing -> stringResource(R.string.module_upload_preparing_submit)

    ModuleUploadRules.SubmitBlocker.OWNERSHIP in blockers ->
        ownershipBlock.ifEmpty { stringResource(R.string.module_upload_ownership_not_mine_summary) }

    ModuleUploadRules.SubmitBlocker.ALREADY_SUBMITTED in blockers ->
        stringResource(R.string.module_upload_submit_success)

    ModuleUploadRules.SubmitBlocker.SUBMITTING in blockers ->
        stringResource(R.string.module_upload_submitting)

    ModuleUploadRules.SubmitBlocker.CHECKING in blockers ->
        stringResource(R.string.module_upload_checking)

    ModuleUploadRules.SubmitBlocker.RECORDED in blockers ->
        stringResource(R.string.module_upload_status_recorded_summary)

    // 来源两条一起说：只说「还差一项」会让用户不知道该填哪一栏。
    ModuleUploadRules.SubmitBlocker.ORIGIN in blockers ->
        stringResource(R.string.module_upload_origin_required)

    ModuleUploadRules.SubmitBlocker.REPO in blockers ->
        stringResource(R.string.module_upload_repo_invalid)

    ModuleUploadRules.SubmitBlocker.AUTHOR_COOLAPK in blockers ->
        stringResource(R.string.module_upload_author_coolapk_invalid)

    ModuleUploadRules.SubmitBlocker.AUTHOR_GITHUB in blockers ->
        stringResource(R.string.module_upload_author_github_invalid)

    ModuleUploadRules.SubmitBlocker.SOURCE in blockers ->
        stringResource(R.string.module_upload_source_invalid)

    else -> stringResource(
        R.string.module_upload_blockers,
        ModuleUploadRules.actionableBlockerCount(blockers),
    )
}
