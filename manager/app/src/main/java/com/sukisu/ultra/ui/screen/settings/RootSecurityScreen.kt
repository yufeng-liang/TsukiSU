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
 * 「Root 与安全」二级页：临时 Root、SU 兼容、内核级卸载、四种伪装、SU 日志、ADB Root、Shell 限制、软重启、内置 Zygisk、更换包名、自动越狱。
 */
@Composable
fun RootSecurityScreen(navigator: Navigator) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberSettingsScreenActions(navigator)
    // 从设置搜索或 AI 助手带锚点进来时取一次；消费后置空，避免重组时重复滚动。
    var highlightAnchor by remember { mutableStateOf(SettingsHighlight.consume()) }
    val onHighlightConsumed = { highlightAnchor = null }
    when (LocalUiMode.current) {
        UiMode.Miuix -> RootSecurityMiuix(
            uiState = uiState,
            actions = actions,
            onBack = { navigator.pop() },
            highlightAnchor = highlightAnchor,
            onHighlightConsumed = onHighlightConsumed,
        )
        UiMode.Material -> RootSecurityMaterial(
            uiState = uiState,
            actions = actions,
            onBack = { navigator.pop() },
            highlightAnchor = highlightAnchor,
            onHighlightConsumed = onHighlightConsumed,
        )
    }
}
