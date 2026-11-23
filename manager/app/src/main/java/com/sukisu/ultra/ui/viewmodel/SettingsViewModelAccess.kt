package com.sukisu.ultra.ui.viewmodel

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 全应用共用一个 Activity 作用域的 [SettingsViewModel]。
 *
 * 各页面以前直接写 viewModel<SettingsViewModel>()，拿到的是当前导航条目（NavBackStackEntry）
 * 作用域的实例：每进一次二级页都会新建一份 ViewModel 并重跑一遍 refresh()（十几次 root shell
 * 查询），于是开关先按默认值渲染、随后才被真实值改写，看起来就是「一进来先关再跳开」；
 * 「卸载」这类依赖运行状态的入口也要等结果回来才出现。
 *
 * 统一挂到 Activity 上之后，整次会话只加载一次，跨页复用同一份状态，页面切换不再触发重读。
 */
@Composable
fun sharedSettingsViewModel(): SettingsViewModel {
    val activityOwner = LocalActivity.current as? ViewModelStoreOwner
    return if (activityOwner != null) {
        viewModel(viewModelStoreOwner = activityOwner)
    } else {
        viewModel()
    }
}
