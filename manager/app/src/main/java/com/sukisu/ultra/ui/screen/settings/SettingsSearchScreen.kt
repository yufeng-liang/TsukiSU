package com.sukisu.ultra.ui.screen.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.navigation3.Navigator
import com.sukisu.ultra.ui.viewmodel.MainActivityViewModel
import com.sukisu.ultra.ui.viewmodel.MainPagerConfig

/**
 * 设置搜索页。命中项分两种去处：落在一级页的（外观与语言）回到设置 Tab 并滚动，
 * 其余用 replace 换成目标二级页，这样返回栈是「设置 → 目标页」而不是「设置 → 搜索 → 目标页」。
 */
@Composable
fun SettingsSearchScreen(navigator: Navigator) {
    val activityOwner = LocalActivity.current as? ViewModelStoreOwner
    val mainViewModel: MainActivityViewModel = if (activityOwner != null) {
        viewModel(viewModelStoreOwner = activityOwner)
    } else {
        viewModel()
    }
    val onPick: (SettingsSearchEntry) -> Unit = { entry ->
        if (entry.page == SettingsPage.APPEARANCE) {
            mainViewModel.openMainPage(MainPagerConfig.LAST_PAGE_INDEX, entry.anchor)
            navigator.pop()
        } else {
            SettingsHighlight.request(entry.anchor)
            navigator.replace(settingsRouteFor(entry.page))
        }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingsSearchMiuix(
            entries = SettingsSearchIndex.entries,
            onBack = { navigator.pop() },
            onPick = onPick,
        )
        UiMode.Material -> SettingsSearchMaterial(
            entries = SettingsSearchIndex.entries,
            onBack = { navigator.pop() },
            onPick = onPick,
        )
    }
}
