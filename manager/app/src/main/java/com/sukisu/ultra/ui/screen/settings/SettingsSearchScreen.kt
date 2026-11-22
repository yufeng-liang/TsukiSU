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
 * 设置搜索页。命中项分两种去处：落在一级页的（外观与语言等）回到设置 Tab 并滚动，
 * 其余 push 目标二级页并把锚点交给它高亮。这里刻意用 push：返回键能回到搜索结果继续点下一条。
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
            // 一级页不换页：回到设置 Tab 并滚到那一行
            mainViewModel.openMainPage(MainPagerConfig.LAST_PAGE_INDEX, entry.anchor)
            navigator.pop()
        } else {
            // 其余页先存锚点再 push：目标页创建时 consume 一次，自己做滚动与高亮
            SettingsHighlight.request(entry.anchor)
            navigator.push(settingsRouteFor(entry.page))
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
