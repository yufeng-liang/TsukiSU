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
 * 「模块与扩展」二级页：模块投稿、分享开关、App Profile 模板、拓展功能、工具、默认卸载与两个删除开关。
 */
@Composable
fun ModuleSettingsScreen(navigator: Navigator) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberSettingsScreenActions(navigator)
    // 从设置搜索或 AI 助手带锚点进来时取一次；消费后置空，避免重组时重复滚动。
    var highlightAnchor by remember { mutableStateOf(SettingsHighlight.consume()) }
    val onHighlightConsumed = { highlightAnchor = null }
    when (LocalUiMode.current) {
        UiMode.Miuix -> ModuleSettingsMiuix(
            uiState = uiState,
            actions = actions,
            onBack = { navigator.pop() },
            highlightAnchor = highlightAnchor,
            onHighlightConsumed = onHighlightConsumed,
        )
        UiMode.Material -> ModuleSettingsMaterial(
            uiState = uiState,
            actions = actions,
            onBack = { navigator.pop() },
            highlightAnchor = highlightAnchor,
            onHighlightConsumed = onHighlightConsumed,
        )
    }
}
