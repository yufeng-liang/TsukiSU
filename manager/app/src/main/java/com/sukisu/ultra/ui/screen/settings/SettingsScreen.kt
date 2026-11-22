package com.sukisu.ultra.ui.screen.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.navigation3.Navigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.viewmodel.MainActivityViewModel
import com.sukisu.ultra.ui.viewmodel.MainPagerConfig
import com.sukisu.ultra.ui.viewmodel.SettingsViewModel

/**
 * 锚点所在的页面 → 对应的路由。一级页（外观与语言）不换页，其余各自 push 到二级页。
 */
internal fun settingsRouteFor(page: SettingsPage): Route = when (page) {
    SettingsPage.APPEARANCE -> Route.Settings
    SettingsPage.MODULE -> Route.ModuleSettings
    SettingsPage.ROOT_SECURITY -> Route.RootSecurity
    SettingsPage.MAINTENANCE -> Route.Maintenance
}

/**
 * 设置项的所有回调。一级页与三个二级页共用这一份，避免每个页面各拼一遍。
 */
@Composable
fun rememberSettingsScreenActions(navigator: Navigator): SettingsScreenActions {
    val context = LocalContext.current
    val viewModel = viewModel<SettingsViewModel>()
    return SettingsScreenActions(
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onSetCheckModuleUpdate = viewModel::setCheckModuleUpdate,
        onSetAutoDeleteModuleZip = viewModel::setAutoDeleteModuleZip,
        onSetAutoDeleteUpdateApk = viewModel::setAutoDeleteUpdateApk,
        onOpenTheme = { navigator.push(Route.ColorPalette()) },
        onOpenThemeStore = { navigator.push(Route.ThemeStore()) },
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(if (index == 0) UiMode.Miuix.value else UiMode.Material.value)
        },
        onOpenProfileTemplate = { navigator.push(Route.AppProfileTemplate) },
        onSetLanguage = { tag -> viewModel.setLanguage(context, tag) },
        onSetSuCompatMode = viewModel::setSuCompatMode,
        onSetKernelUmountEnabled = viewModel::setKernelUmountEnabled,
        onSetSelinuxHideEnabled = viewModel::setSelinuxHideEnabled,
        onSetAvcSpoofEnabled = viewModel::setAvcSpoofEnabled,
        onSetMountHideEnabled = viewModel::setMountHideEnabled,
        onSetSulogEnabled = viewModel::setSulogEnabled,
        onSetAdbRootEnabled = viewModel::setAdbRootEnabled,
        onSetShellSuAdbOnlyEnabled = viewModel::setShellSuAdbOnlyEnabled,
        onSetDefaultUmountModules = viewModel::setDefaultUmountModules,
        onSetEnableWebDebugging = viewModel::setEnableWebDebugging,
        onSetAutoJailbreak = viewModel::setAutoJailbreak,
        onSetModuleContributionEnabled = viewModel::setModuleContributionEnabled,
        onSetUseSoftReboot = viewModel::setUseSoftReboot,
        onSetBuiltinZygisk = viewModel::setBuiltinZygisk,
        onDismissBuiltinZygiskFailure = viewModel::dismissBuiltinZygiskFailure,
        onOpenModuleUpload = { navigator.push(Route.ModuleUpload) },
        onOpenAbout = { navigator.push(Route.About) },
        onOpenTools = { navigator.push(Route.Tool) },
        onOpenSusfsConfig = { navigator.push(Route.SuSFS()) },
        onOpenBackup = { navigator.push(Route.Backup) },
        onOpenPackageRename = { navigator.push(Route.PackageRename) },
        onOpenExtensions = { navigator.push(Route.Extensions) },
        onOpenBuiltinZygisk = { navigator.push(Route.BuiltinZygisk) },
        onSetProxyMode = viewModel::setProxyMode,
        onSetProxyPinnedId = viewModel::setProxyPinnedId,
        onTestProxies = { viewModel.testProxies(force = true) },
        onEnsureProxyTested = viewModel::ensureProxyTested,
        onOpenProxySettings = { navigator.push(Route.ProxySettings) },
        onOpenModuleSettings = { navigator.push(Route.ModuleSettings) },
        onOpenRootSecurity = { navigator.push(Route.RootSecurity) },
        onOpenMaintenance = { navigator.push(Route.Maintenance) },
        onOpenSearch = { navigator.push(Route.SettingsSearch) },
    )
}

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<SettingsViewModel>()
    val activityOwner = LocalActivity.current as? ViewModelStoreOwner
    val mainViewModel: MainActivityViewModel = if (activityOwner != null) {
        viewModel(viewModelStoreOwner = activityOwner)
    } else {
        viewModel()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pageRequest by mainViewModel.pageRequest.collectAsStateWithLifecycle()
    val rawRequest = pageRequest?.takeIf {
        it.page == MainPagerConfig.LAST_PAGE_INDEX && isCurrentPage
    }
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    // 锚点可能指向二级页。这里先解析，命中二级页就直接跳过去并把锚点交给它，
    // 否则请求会挂在设置页永远消费不到（push 之后 isCurrentPage 已是 false）。
    LaunchedEffect(rawRequest?.token) {
        val request = rawRequest ?: return@LaunchedEffect
        val anchor = SettingsSection.normalize(request.section)
        val target = SettingsSection.pageFor(anchor)
        if (target != SettingsPage.APPEARANCE) {
            SettingsHighlight.request(anchor)
            mainViewModel.consumePageRequest(request.token)
            navigator.push(settingsRouteFor(target))
        }
    }

    // 只有落在一级页的锚点才继续往下传，二级页的已经在上面的 effect 里消化掉。
    val sectionRequest = rawRequest?.takeIf {
        SettingsSection.pageFor(SettingsSection.normalize(it.section)) == SettingsPage.APPEARANCE
    }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            viewModel.refresh()
            // 代理列表原本只在 ViewModel init 时读一次，远程下发项变了就得杀进程才能刷新；
            // 每次切回设置页顺带重读一遍，空态文案里的「重进本页」才真的成立。
            viewModel.refreshProxyEntries()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    val actions = rememberSettingsScreenActions(navigator)

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            sectionRequest = sectionRequest,
            onSectionConsumed = { token -> mainViewModel.consumePageRequest(token) },
        )
        UiMode.Material -> SettingPagerMaterial(
            uiState = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
            sectionRequest = sectionRequest,
            onSectionConsumed = { token -> mainViewModel.consumePageRequest(token) },
        )
    }
}
