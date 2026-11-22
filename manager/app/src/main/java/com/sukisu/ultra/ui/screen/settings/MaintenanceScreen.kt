package com.sukisu.ultra.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.navigation3.Navigator
import com.sukisu.ultra.ui.viewmodel.SettingsViewModel

/**
 * 「诊断与维护」二级页：GitHub 下载加速、备份与恢复、发送日志、WebView 调试、卸载、关于。
 */
@Composable
fun MaintenanceScreen(navigator: Navigator) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberSettingsScreenActions(navigator)
    // 从设置搜索或 AI 助手带锚点进来时取一次；消费后置空，避免重组时重复滚动。
    var highlightAnchor by remember { mutableStateOf(SettingsHighlight.consume(SettingsPage.MAINTENANCE)) }
    val onHighlightConsumed = { highlightAnchor = null }
    SettingsAnchorRegistry {
        when (LocalUiMode.current) {
            UiMode.Miuix -> MaintenanceMiuix(
                uiState = uiState,
                actions = actions,
                onBack = { navigator.pop() },
                highlightAnchor = highlightAnchor,
                onHighlightConsumed = onHighlightConsumed,
            )
            UiMode.Material -> MaintenanceMaterial(
                uiState = uiState,
                actions = actions,
                onBack = { navigator.pop() },
                highlightAnchor = highlightAnchor,
                onHighlightConsumed = onHighlightConsumed,
            )
        }
    }
}
