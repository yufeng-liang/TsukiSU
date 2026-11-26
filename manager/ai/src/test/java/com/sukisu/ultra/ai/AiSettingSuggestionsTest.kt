package com.sukisu.ultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** suggest_setting 的键白名单回归：只允许 4 个低风险布尔开关。 */
class AiSettingSuggestionsTest {

    private val lowRiskKeys = setOf(
        "allowWebAccess",
        "allowThemeRead",
        "allowModuleAccess",
        "allowExtensionAccess",
    )

    private val highRiskKeys = listOf(
        "allowRootAccess",
        "allowSuperuserAccess",
        "allowThemeWrite",
        "allowExtensionWrite",
        "requireConfirmation",
        "allowCodeExecution",
    )

    @Test
    fun supportedSettingKeysAreExactlyTheFourLowRiskSwitches() {
        assertEquals(lowRiskKeys, AiTools.supportedSettingKeys())
    }

    @Test
    fun onlyLowRiskKeysAreBooleanSettings() {
        lowRiskKeys.forEach { key ->
            assertTrue("$key should be a boolean setting", AiSettings.isBooleanSetting(key))
        }
        highRiskKeys.forEach { key ->
            assertFalse("$key must not be writable via suggest_setting", AiSettings.isBooleanSetting(key))
        }
        assertFalse(AiSettings.isBooleanSetting("unknown"))
    }
}
