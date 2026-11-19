package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归：两个专栏工具必须一直注册在工具表里，参数名就是模型看到的契约，
 * 改名或漏注册会让「隐藏环境」整条链路静默失效。
 */
class AiColumnToolsSpecTest {

    private val specs = AiTools.specsFor(AiConfig())

    @Test
    fun columnToolsAreOffered() {
        val names = specs.map { it.name }
        assertTrue(AiTools.COLUMN_WIZARD_TOOL in names)
        assertTrue(AiTools.COLUMN_WIZARD_RUN_TOOL in names)
    }

    @Test
    fun readToolTakesAnAction() {
        val spec = specs.first { it.name == AiTools.COLUMN_WIZARD_TOOL }
        val params = spec.parameters.toString()
        assertTrue(params.contains("action"))
    }

    @Test
    fun runToolTakesColumnAndTopic() {
        val spec = specs.first { it.name == AiTools.COLUMN_WIZARD_RUN_TOOL }
        val params = spec.parameters.toString()
        assertTrue(params.contains("column_id"))
        assertTrue(params.contains("topic_id"))
        assertTrue(params.contains("step_id"))
    }

    /** 重启后的接续与「需勾选」都必须出现在参数契约里，否则模型永远调不到。 */
    @Test
    fun runToolKnowsResumeAndOptIn() {
        val spec = specs.first { it.name == AiTools.COLUMN_WIZARD_RUN_TOOL }
        val params = spec.parameters.toString()
        assertTrue(params.contains("resume"))
        assertTrue(params.contains("mark_done"))
        assertTrue(params.contains("opt_in"))
        assertTrue(params.contains("overwrite"))
    }

    /** 专栏页入口的定位参数不能丢：丢了模型就只能打开首页。 */
    @Test
    fun openAppPageAcceptsColumnTargets() {
        val spec = specs.first { it.name == "open_app_page" }
        val params = spec.parameters.toString()
        assertTrue(params.contains("column_id"))
        assertTrue(params.contains("topic_id"))
        assertTrue(params.contains("module_column"))
    }

    /**
     * 「本次请求始终允许」认的格：没 scope 就按工具整条，有 scope 只认那一格。
     *
     * 专栏把 scope 绑成「工具 + 专题 + 内容摘要」，所以勾一次只覆盖看过的那一版内容；
     * 换专题或索引改字，key 就变，旧授权不会再命中。
     */
    @Test
    fun approvalGrantKeyIsScopedToWhatTheUserSaw() {
        assertEquals("run_shell", AiApprovalRequest("run_shell", "t", "d").grantKey)
        assertEquals(
            "run_step:column:hide_env/daily_banking#abc123",
            AiApprovalRequest(
                "run_step", "t", "d",
                scope = "run_step:column:hide_env/daily_banking#abc123",
            ).grantKey,
        )
        // 摘要不同 = 内容变了 = 授权不通用
        assertTrue(
            AiApprovalRequest("run_step", "t", "d", scope = "run_step:column:a/b#1").grantKey !=
                AiApprovalRequest("run_step", "t", "d", scope = "run_step:column:a/b#2").grantKey
        )
    }
}
