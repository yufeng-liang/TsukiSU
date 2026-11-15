package com.sukisu.ultra.ui.screen.modulerepo

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.sukisu.ultra.ui.util.customText
import com.sukisu.ultra.ui.util.CustomTextKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.RepoColumn
import com.sukisu.ultra.data.repository.RepoSource
import com.sukisu.ultra.ui.component.ListPopupDefaults
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import com.sukisu.ultra.ui.component.ScrollToTopOnChange
import com.sukisu.ultra.ui.component.SearchStatus
import com.sukisu.ultra.ui.component.dialog.ConfirmDialogHandle
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.markdown.GithubMarkdown
import com.sukisu.ultra.ui.component.miuix.AppCard
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.component.miuix.SearchBarFake
import com.sukisu.ultra.ui.component.miuix.SearchBox
import com.sukisu.ultra.ui.component.miuix.SearchPager
import com.sukisu.ultra.ui.component.miuix.WarningCard
import com.sukisu.ultra.ui.component.miuix.deferredTopPadding
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.theme.backgroundAwareColor
import com.sukisu.ultra.ui.theme.isInDarkTheme
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.CoolapkLinks
import com.sukisu.ultra.ui.util.formatEpochTime
import com.sukisu.ultra.ui.util.formatFileSize
import com.sukisu.ultra.ui.util.formatRepoTime
import com.sukisu.ultra.ui.util.isDownloadAvailable
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import com.sukisu.ultra.ui.util.rememberContentReady
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.ui.component.dialog.ConfirmResult
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.screen.upload.VerifiedAvatar
import com.sukisu.ultra.ui.util.ModuleComments.CommentItem
import com.sukisu.ultra.ui.util.ModuleComments
import com.sukisu.ultra.ui.viewmodel.ModuleCommentsViewModel
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Send
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.ui.text.input.ImeAction
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.RefreshState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ModuleRepoScreenMiuix(
    state: ModuleRepoUiState,
    actions: ModuleRepoActions,
) {
    val searchStatus = state.searchStatus
    val density = LocalDensity.current
    val metaBg = colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    val metaTint = colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
    val sourceBg = colorScheme.secondaryContainer.copy(alpha = 0.6f)
    val sourceTint = colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
    val showSourcesDialog = remember { mutableStateOf(false) }
    // 免责说明：没勾过"承诺"就每次进入本页都弹一次。取值只看进入时的这一份状态，
    // 关掉之后由本地的 showDisclaimer 管着，不再跟着 state 的后续变化走。
    val showDisclaimer = remember { mutableStateOf(!state.disclaimerAccepted) }

    // 搜索框每次输入都会通过 onSearchStatusChange 送进 ViewModel（见 SearchBar），这里原先再回写
    // 一遍是第二条通路，除了让状态多抖一次没有作用，删掉。

    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }

    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val barColor = if (blurActive || hasCustomBackground) Color.Transparent else colorScheme.surface

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = customText(CustomTextKey.PAGE_MODULE_REPO),
                        actions = {
                            val showSortPopup = remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { showSourcesDialog.value = true }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.MoreCircle,
                                    tint = colorScheme.onSurface,
                                    contentDescription = stringResource(R.string.module_repo_manage_sources),
                                )
                            }
                            OverlayListPopup(
                                show = showSortPopup.value,
                                popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                onDismissRequest = { showSortPopup.value = false },
                                content = {
                                    ListPopupColumn {
                                        val sortOptions = listOf(
                                            RepoSort.UPDATED to R.string.module_repos_sort_updated,
                                            RepoSort.CREATED to R.string.module_repos_sort_created,
                                            RepoSort.NAME to R.string.module_repos_sort_name,
                                            RepoSort.STARS to R.string.module_repos_sort_stars,
                                        )
                                        sortOptions.forEachIndexed { index, (order, resId) ->
                                            DropdownImpl(
                                                text = stringResource(resId),
                                                optionSize = sortOptions.size,
                                                isSelected = state.sortOrder == order,
                                                onSelectedIndexChange = {
                                                    actions.onSetSortOrder(order)
                                                    showSortPopup.value = false
                                                },
                                                index = index,
                                            )
                                        }
                                    }
                                })
                            IconButton(
                                onClick = { showSortPopup.value = true },
                                holdDownState = showSortPopup.value
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Sort,
                                    tint = colorScheme.onSurface,
                                    contentDescription = stringResource(R.string.menu_sort),
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = actions.onBack
                            ) {
                                val layoutDirection = LocalLayoutDirection.current
                                Icon(
                                    modifier = Modifier.graphicsLayer {
                                        if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                    },
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior, bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                actions.onSearchStatusChange(searchStatus.copy(offsetY = newOffsetY))
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    actions.onSearchStatusChange(searchStatus.copy(current = SearchStatus.Status.EXPANDING))
                                                }
                                            }
                                        } else Modifier,
                                    ),
                            ) {
                                SearchBarFake(searchStatus.label, dynamicTopPadding)
                            }
                        }
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = actions.onSearchStatusChange,
                defaultResult = {},
                searchBarTopPadding = dynamicTopPadding,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical(),
                ) {
                    // Material 侧同样只有一句「没有匹配的仓库」，两边行为保持一致。
                    if (state.searchResults.isEmpty()) {
                        item(key = "search_empty") {
                            RepoEmptyPrompt(hint = stringResource(R.string.module_repo_search_no_results))
                        }
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                    }
                    items(state.searchResults, key = { "${it.sourceId}|${it.moduleId}" }, contentType = { "module" }) { module ->
                        // 源码状态只有一枚，判据与 Material 侧共用同一个函数。
                        val sourceBadge = repoSourceBadge(module)
                        AppCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            insideMargin = PaddingValues(16.dp),
                            showIndication = true,
                            pressFeedbackType = PressFeedbackType.Sink,
                            onClick = {
                                actions.onOpenRepoDetail(module)
                            }
                        ) {
                            Column {
                                if (module.moduleName.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = module.moduleName,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight(550),
                                            color = colorScheme.onSurface
                                        )
                                        if (module.metamodule) {
                                            Text(
                                                text = "META",
                                                fontSize = 12.sp,
                                                color = metaTint,
                                                modifier = Modifier
                                                    .padding(start = 6.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(metaBg)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight(750),
                                                maxLines = 1
                                            )
                                        }
                                        if (module.zygisk) {
                                            Text(
                                                text = "ZYGISK",
                                                fontSize = 12.sp,
                                                color = metaTint,
                                                modifier = Modifier
                                                    .padding(start = 6.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(metaBg)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight(750),
                                                maxLines = 1
                                            )
                                        }
                                        if (module.sourceName.isNotEmpty()) {
                                            Text(
                                                text = module.sourceName,
                                                fontSize = 12.sp,
                                                color = sourceTint,
                                                modifier = Modifier
                                                    .padding(start = 6.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(sourceBg)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight(750),
                                                maxLines = 1
                                            )
                                        }
                                        if (sourceBadge != null) {
                                            Text(
                                                // 绿勾只给校验通过的两态；灰标没有勾。
                                                text = (if (sourceBadge.isVerified) "✓ " else "") +
                                                    stringResource(repoSourceBadgeLabel(sourceBadge)),
                                                fontSize = 12.sp,
                                                color = if (sourceBadge.isVerified) {
                                                    colorScheme.onPrimary
                                                } else {
                                                    colorScheme.onSurfaceVariantSummary
                                                },
                                                modifier = Modifier
                                                    .padding(start = 6.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (sourceBadge.isVerified) {
                                                            colorScheme.primary
                                                        } else {
                                                            colorScheme.surfaceContainer
                                                        }
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight(750),
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        if (module.stargazerCount > 0) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = MiuixIcons.TopDownloads,
                                                    contentDescription = "stars",
                                                    tint = colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = module.stargazerCount.toString(),
                                                    fontSize = 12.sp,
                                                    color = colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.padding(start = 4.dp)
                                                )
                                            }
                                        }
                                        if (module.likes > 0) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                                                Text(
                                                    text = "♥",
                                                    fontSize = 12.sp,
                                                    color = colorScheme.onSurfaceVariantSummary,
                                                )
                                                Text(
                                                    text = module.likes.toString(),
                                                    fontSize = 12.sp,
                                                    color = colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.padding(start = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (module.moduleId.isNotEmpty()) {
                                    Text(
                                        text = "ID: ${module.moduleId}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight(550),
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                Text(
                                    text = "${stringResource(id = R.string.module_author)}: ${module.authors}",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 1.dp),
                                    fontWeight = FontWeight(550),
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                                if (module.summary.isNotEmpty()) {
                                    Text(
                                        text = module.summary,
                                        fontSize = 14.sp,
                                        color = colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(top = 2.dp),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 4,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        // An empty list means "loading" only until the first fetch settles; after that it
        // means every source failed and the error banner below is the thing to show.
        val isLoading = !state.hasLoadedOnce
        val hadDataOnEntry = remember { state.modules.isNotEmpty() }
        val contentReady = hadDataOnEntry || rememberContentReady()
        val offline = state.offline

        searchStatus.SearchBox {
            val pullToRefreshState = rememberPullToRefreshState()
            val lazyListState = rememberLazyListState()
            val refreshTick = remember { mutableIntStateOf(0) }
            val latestModules = rememberUpdatedState(state.modules)
            val latestRefreshing = rememberUpdatedState(state.isRefreshing)
            // 切换来源筛选也算换了一批要看的模块，跟着回到顶部，而不是留在原来的滚动位置。
            ScrollToTopOnChange(
                lazyListState,
                state.sortOrder,
                state.selectedSourceId,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestModules.value }
            val filterOptions = rememberRepoSourceFilters(
                modules = state.modules,
                sources = state.sources,
                sourceErrors = state.sourceErrors,
            )
            val coolapkSource = coolapkCommunitySource(state.sources)
            // 入口卡没了（源被删掉）又没有第二个筛选项时，这个头就没有内容可放。
            val showHeader = coolapkSource != null || filterOptions.size > 1
            val filteredModules = rememberSourceFilteredModules(state.modules, state.selectedSourceId)
            // 筛选把列表筛空，跟「一个源都没配」是两回事：前者换个筛选就有内容，后者要去加源。
            val filteredEmpty = state.selectedSourceId != null && filteredModules.isEmpty()
            val refreshTexts = listOf(
                stringResource(R.string.refresh_pulling),
                stringResource(R.string.refresh_release),
                stringResource(R.string.refresh_refresh),
                stringResource(R.string.refresh_complete),
            )
            PullToRefresh(
                isRefreshing = state.isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = {
                    actions.onRefresh()
                    refreshTick.intValue++
                },
                refreshTexts = refreshTexts,
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                ),
            ) {
                if (!contentReady || isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (offline || state.error != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(
                                        if (offline) R.string.network_offline
                                        else R.string.module_repo_unavailable
                                    ),
                                    color = colorScheme.onSurfaceVariantSummary,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .fillMaxWidth(),
                                    text = stringResource(R.string.network_retry),
                                    onClick = actions.onRefresh,
                                )
                            }
                        } else if (state.sources.isEmpty()) {
                            // Nothing is configured out of the box, so this is the first thing a
                            // new user sees; it has to lead somewhere instead of just stating it.
                            RepoEmptyPrompt(
                                hint = stringResource(R.string.module_repo_sources_empty),
                                actionLabel = stringResource(R.string.module_repo_add_repo),
                                onAction = { showSourcesDialog.value = true },
                            )
                        } else if (pullToRefreshState.refreshState == RefreshState.Idle) {
                            InfiniteProgressIndicator()
                        }
                    }
                } else {
                    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxHeight()
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding() + 6.dp,
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection)
                            ),
                            overscrollEffect = null,
                        ) {
                            if (showHeader) {
                                // 数据源入口与筛选排在所有内容之前，但仍然是列表的一部分：滚动时一起走。
                                item(key = "source_filter_header") {
                                    MiuixStoreHeader(
                                        coolapkSource = coolapkSource,
                                        filterOptions = filterOptions,
                                        selectedSourceId = state.selectedSourceId,
                                        columns = state.columns,
                                        columnsFailed = state.columnsFailed,
                                        onRetryColumns = actions.onRefresh,
                                        onOpenColumn = actions.onOpenColumn,
                                        onSetSourceEnabled = actions.onSetSourceEnabled,
                                        onSelectSource = actions.onSelectSource,
                                    )
                                }
                            }
                            if (state.sourceErrors.isNotEmpty()) {
                                item(key = "source_errors_banner") {
                                    // 关掉的通知只针对这一次抓取结果，下一次结果落地时会清空。
                                    val visibleErrors = remember(state.sourceErrors, state.dismissedSourceErrors) {
                                        state.sourceErrors.filterValues { message ->
                                            message !in state.dismissedSourceErrors
                                        }
                                    }
                                    val groups = remember(visibleErrors) { groupSourceErrors(visibleErrors) }
                                    Column(
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        groups.forEach { group ->
                                            WarningCard(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp),
                                                title = sourceErrorTitle(group.keys, state.sources),
                                                message = stringResource(
                                                    R.string.module_repo_fetch_failed,
                                                    group.message,
                                                ),
                                                icon = Icons.Rounded.ErrorOutline,
                                                action = {
                                                    IconButton(onClick = { actions.onDismissSourceError(group.message) }) {
                                                        Icon(
                                                            imageVector = MiuixIcons.Close,
                                                            contentDescription = stringResource(R.string.close),
                                                            tint = colorScheme.onSurfaceVariantSummary,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            if (filteredModules.isEmpty()) {
                                item(key = "repo_empty_prompt") {
                                    RepoEmptyPrompt(
                                        hint = if (filteredEmpty) {
                                            stringResource(R.string.module_repo_filter_source_empty)
                                        } else {
                                            repoEmptyHint(repoEmptyReason(state.sources))
                                        },
                                        actionLabel = if (filteredEmpty) {
                                            stringResource(R.string.module_repo_filter_all)
                                        } else {
                                            repoEmptyAction(repoEmptyReason(state.sources))
                                        },
                                        onAction = {
                                            if (filteredEmpty) actions.onSelectSource(null)
                                            else showSourcesDialog.value = true
                                        },
                                    )
                                }
                            }
                            items(items = filteredModules, key = { "${it.sourceId}|${it.moduleId}" }, contentType = { "module" }) { module ->
                                val moduleAuthor = stringResource(id = R.string.module_author)
                                val sourceBadge = repoSourceBadge(module)
                                val latestReleaseTime = remember(module.latestReleaseTime) {
                                    formatRepoTime(module.latestReleaseTime)
                                }

                                AppCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 12.dp),
                                    insideMargin = PaddingValues(16.dp),
                                    showIndication = true,
                                    onClick = { actions.onOpenRepoDetail(module) }) {
                                    Column {
                                        if (module.moduleName.isNotEmpty()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = module.moduleName,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight(550),
                                                    color = colorScheme.onSurface
                                                )
                                                if (module.metamodule) {
                                                    Text(
                                                        text = "META",
                                                        fontSize = 12.sp,
                                                        color = metaTint,
                                                        modifier = Modifier
                                                            .padding(start = 6.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(metaBg)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontWeight = FontWeight(750),
                                                        maxLines = 1
                                                    )
                                                }
                                                if (module.zygisk) {
                                                    Text(
                                                        text = "ZYGISK",
                                                        fontSize = 12.sp,
                                                        color = metaTint,
                                                        modifier = Modifier
                                                            .padding(start = 6.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(metaBg)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontWeight = FontWeight(750),
                                                        maxLines = 1
                                                    )
                                                }
                                                if (module.sourceName.isNotEmpty()) {
                                                    Text(
                                                        text = module.sourceName,
                                                        fontSize = 12.sp,
                                                        color = sourceTint,
                                                        modifier = Modifier
                                                            .padding(start = 6.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(sourceBg)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontWeight = FontWeight(750),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                        if (module.moduleId.isNotEmpty()) {
                                            Text(
                                                text = "ID: ${module.moduleId}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight(550),
                                                color = colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                        Text(
                                            text = "$moduleAuthor: ${module.authors}",
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(bottom = 1.dp),
                                            fontWeight = FontWeight(550),
                                            color = colorScheme.onSurfaceVariantSummary,
                                        )
                                        if (module.summary.isNotEmpty()) {
                                            Text(
                                                text = module.summary,
                                                fontSize = 14.sp,
                                                color = colorScheme.onSurfaceVariantSummary,
                                                modifier = Modifier.padding(top = 2.dp),
                                                overflow = TextOverflow.Ellipsis,
                                                maxLines = 4,
                                            )
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            thickness = 0.5.dp,
                                            color = colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row {
                                                if (module.stargazerCount > 0) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = MiuixIcons.TopDownloads,
                                                            contentDescription = "stars",
                                                            tint = colorScheme.onSurfaceVariantSummary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = module.stargazerCount.toString(),
                                                            fontSize = 12.sp,
                                                            color = colorScheme.onSurfaceVariantSummary,
                                                            modifier = Modifier.padding(start = 4.dp)
                                                        )
                                                    }
                                                }
                                                if (module.likes > 0) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                                                        Text(
                                                            text = "♥",
                                                            fontSize = 12.sp,
                                                            color = colorScheme.onSurfaceVariantSummary,
                                                        )
                                                        Text(
                                                            text = module.likes.toString(),
                                                            fontSize = 12.sp,
                                                            color = colorScheme.onSurfaceVariantSummary,
                                                            modifier = Modifier.padding(start = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.weight(1f))
                                                if (latestReleaseTime.isNotEmpty()) {
                                                    Text(
                                                        text = latestReleaseTime,
                                                        fontSize = 12.sp,
                                                        color = colorScheme.onSurfaceVariantSummary,
                                                        textAlign = TextAlign.End
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(Modifier.height(WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()))
                            }
                        }
                    }
                }
            }
        }
    }

    ManageSourcesDialogMiuix(
        show = showSourcesDialog.value,
        onDismissRequest = { showSourcesDialog.value = false },
        state = state,
        actions = actions,
    )

    RepoDisclaimerDialogMiuix(
        show = showDisclaimer.value,
        onDismiss = { committed ->
            showDisclaimer.value = false
            if (committed) {
                // 勾了"承诺"才落盘；落盘后不再弹。
                actions.onDisclaimerAccepted()
            } else {
                // 没勾就关闭（点外面/按返回）＝不接受：直接退回上一页，本页不再展示。
                actions.onBack()
            }
        },
    )
}

/**
 * 商店页顶部的「酷安社区模块」入口：未开启时是一张带两段式确认的说明卡，开启后降级成一行状态条。
 *
 * 这个源默认关闭，而且只会在首次读列表时种一次，用户从来源管理里删掉之后不会再回来，所以
 * [source] 为 null 就整块不渲染——否则「开启」会是个永远按不动的死控件。
 */
@Composable
private fun MiuixCoolapkCommunityCard(
    source: RepoSource?,
    onSetSourceEnabled: (String, Boolean) -> Unit,
) {
    if (source == null) return

    // 已开启就什么都不显示：来源开关在筛选胶囊下方「来源管理」里也有入口，
    // 商店页不需要常驻一行「已开启 / 关闭」提醒用户自己刚才点过什么。
    if (source.enabled) return

    // 两段式确认：第一次点只是把按钮文案换成「我已了解，确认开启」，第二次点才真的开启，
    // 不额外弹窗。用 enabled 当 key，启用状态一变（不管是在这里还是来源管理里改的）就复位。
    var awaitingConfirm by remember(source.enabled) { mutableStateOf(false) }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        // 一行放完：渠道徽章 + 标题/说明 + 开启按钮；按钮不再独占一行，跟下面模块卡的密度对齐。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoolapkBadge()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.module_repo_coolapk_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.module_repo_coolapk_summary),
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MiuixButton(
                onClick = {
                    if (awaitingConfirm) {
                        awaitingConfirm = false
                        onSetSourceEnabled(source.id, true)
                    } else {
                        awaitingConfirm = true
                    }
                },
                // 高度不能再压到 34dp：miuix Button 的内边距算下来更高，固定 34dp 会把「开启」上下裁掉。
                modifier = Modifier.heightIn(min = 40.dp),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                colors = MiuixButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    text = stringResource(
                        if (awaitingConfirm) R.string.module_repo_coolapk_confirm
                        else R.string.module_repo_coolapk_enable
                    ),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 来源筛选胶囊：「全部」加每个已开启的源，横向滚动一行。
 *
 * Miuix 没有现成的 FilterChip，这里用圆角胶囊自己搭，配色跟主题走。只有一个「全部」的时候
 * 没有筛选的意义，整行不显示。
 */
@Composable
private fun MiuixSourceFilterRow(
    options: List<RepoSourceFilterOption>,
    selectedSourceId: String?,
    onSelectSource: (String?) -> Unit,
) {
    if (options.size <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val selected = option.sourceId == selectedSourceId
            val containerColor = if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh
            val contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceContainerHigh
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(containerColor)
                    .clickable { onSelectSource(option.sourceId) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = option.chipText(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight(550),
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 列表头：入口卡 + 筛选胶囊，作为一个 item 跟着列表滚。 */
@Composable
private fun MiuixStoreHeader(
    coolapkSource: RepoSource?,
    filterOptions: List<RepoSourceFilterOption>,
    selectedSourceId: String?,
    columns: List<RepoColumn>,
    columnsFailed: Boolean,
    onRetryColumns: () -> Unit,
    onOpenColumn: (RepoColumn) -> Unit,
    onSetSourceEnabled: (String, Boolean) -> Unit,
    onSelectSource: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiuixCoolapkCommunityCard(
            source = coolapkSource,
            onSetSourceEnabled = onSetSourceEnabled,
        )
        // 专栏横滑区用 Miuix 自己的一套卡片与按钮，避免 Material 组件在 Miuix 主题下回落成紫色。
        ModuleColumnStripMiuix(
            columns = columns,
            onOpenColumn = onOpenColumn,
            failed = columnsFailed,
            onRetry = onRetryColumns,
        )
        MiuixSourceFilterRow(
            options = filterOptions,
            selectedSourceId = selectedSourceId,
            onSelectSource = onSelectSource,
        )
    }
}

/**
 * Shown whenever there is nothing to list: no source is configured yet, or every configured source
 * failed. Adding a repository is the way out of both, so the prompt leads straight to the dialog
 * instead of only stating that the list is empty.
 */
@Composable
private fun RepoEmptyPrompt(hint: String?, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hint != null) {
            Text(
                text = hint,
                color = colorScheme.onSurfaceVariantSummary,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
        }
        // actionLabel 为空就是不提供出口（搜索无结果、该模块没有可安装产物）。
        if (actionLabel != null) {
            TextButton(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = actionLabel,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun ReadmePage(
    readmeHtml: String?,
    readmeLoaded: Boolean,
    isMarkdown: Boolean,
    innerPadding: PaddingValues, scrollBehavior: ScrollBehavior, backdrop: LayerBackdrop?
) {
    val layoutDirection = LocalLayoutDirection.current
    val isReady = rememberContentReady()
    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item {
                if (readmeLoaded && readmeHtml != null) {
                    var loaded by remember(readmeHtml) { mutableStateOf(false) }
                    val alpha by animateFloatAsState(
                        targetValue = if (loaded) 1f else 0f,
                        animationSpec = tween(durationMillis = 300),
                        label = "MiuixReadmeAlpha",
                    )
                    val placeholderAlpha by animateFloatAsState(
                        targetValue = if (loaded) 0f else 1f,
                        animationSpec = tween(durationMillis = 150),
                        label = "MiuixReadmePlaceholderAlpha",
                    )
                    Box {
                        if (isReady) {
                            Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                                Column {
                                    GithubMarkdown(
                                        content = readmeHtml,
                                        isMarkdown = isMarkdown,
                                        onLoadingChange = { loaded = !it },
                                    )
                                }
                            }
                        }
                        if (placeholderAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .graphicsLayer { this.alpha = placeholderAlpha },
                                contentAlignment = Alignment.Center,
                            ) {
                                InfiniteProgressIndicator()
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ReleasesPage(
    detailReleases: List<ReleaseArg>,
    showNoArchiveHint: Boolean,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    actionIconTint: Color,
    secondaryContainer: Color,
    confirmTitle: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    onInstallModule: (Uri) -> Unit,
    context: Context,
    setPendingDownload: ((() -> Unit)) -> Unit,
    downloads: AssetDownloads,
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            // 索引里根本没有可安装产物时，页面上原来只有一个空列表：没有安装入口，也没有一句解释。
            if (showNoArchiveHint) {
                item(key = "no_installable_archive") {
                    RepoEmptyPrompt(hint = stringResource(R.string.module_repo_no_installable_archive))
                }
            }
            if (detailReleases.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                }
                itemsIndexed(items = detailReleases, key = { index, _ -> "release_$index" }, contentType = { _, _ -> "release" }) { _, rel ->
                    val title = remember(rel.name, rel.tagName) { rel.name.ifBlank { rel.tagName } }
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                        .weight(1f)
                                ) {
                                    Text(
                                        text = title, fontSize = 17.sp, fontWeight = FontWeight(550), color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = rel.tagName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight(550),
                                        color = colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = rel.publishedAt,
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier
                                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                        .align(Alignment.Top)
                                )
                            }
                            AnimatedVisibility(
                                visible = rel.assets.isNotEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    AnimatedVisibility(
                                        visible = rel.descriptionHTML.isNotEmpty(),
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                                                thickness = 0.5.dp,
                                                color = colorScheme.outline.copy(alpha = 0.5f)
                                            )
                                            val descReady = rememberContentReady()
                                            var descLoaded by remember(rel.descriptionHTML) { mutableStateOf(false) }
                                            val descAlpha by animateFloatAsState(
                                                targetValue = if (descLoaded) 1f else 0f,
                                                animationSpec = tween(durationMillis = 300),
                                                label = "MiuixReleaseDescAlpha",
                                            )
                                            val descPlaceholderAlpha by animateFloatAsState(
                                                targetValue = if (descLoaded) 0f else 1f,
                                                animationSpec = tween(durationMillis = 150),
                                                label = "MiuixDescPlaceholderAlpha",
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateContentSize(animationSpec = tween(durationMillis = 300)),
                                            ) {
                                                if (descReady) {
                                                    Box(modifier = Modifier.graphicsLayer { this.alpha = descAlpha }) {
                                                        GithubMarkdown(
                                                            content = rel.descriptionHTML,
                                                            isMarkdown = rel.changelogUrl != null,
                                                            onLoadingChange = { descLoaded = !it },
                                                        )
                                                    }
                                                }
                                                if (descPlaceholderAlpha > 0f) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(72.dp)
                                                            .graphicsLayer { this.alpha = descPlaceholderAlpha },
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        InfiniteProgressIndicator()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                        thickness = 0.5.dp,
                                        color = colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                    rel.assets.forEachIndexed { index, asset ->
                                        val fileName = asset.name
                                        stringResource(R.string.module_downloading)
                                        val sizeText = remember(asset.size) {
                                            formatFileSize(asset.size)
                                        }
                                        val sizeAndDownloads =
                                            remember(sizeText, asset.downloadCount) { "$sizeText · ${asset.downloadCount} downloads" }
                                        val download = downloads.of(asset.downloadUrl)
                                        val isDownloading = download.isDownloading
                                        val progress = download.progress
                                        val downloadedUri = download.uri
                                        val onClickDownload = {
                                            val startText = context.getString(R.string.module_start_downloading, fileName)
                                            setPendingDownload { downloads.start(asset) }
                                            confirmDialog.showConfirm(title = confirmTitle, content = startText)
                                        }
                                        val bottomPadding = if (index == rel.assets.lastIndex) 16.dp else 8.dp
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding)
                                                    .weight(1f)
                                            ) {
                                                Text(
                                                    text = fileName, fontSize = 14.sp, color = colorScheme.onSurface
                                                )
                                                Text(
                                                    text = sizeAndDownloads,
                                                    fontSize = 12.sp,
                                                    color = colorScheme.onSurfaceVariantSummary,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                            if (downloadedUri != null) {
                                                IconButton(
                                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                                                    backgroundColor = secondaryContainer,
                                                    minHeight = 35.dp,
                                                    minWidth = 35.dp,
                                                    onClick = {
                                                        scope.launch {
                                                            if (isDownloadAvailable(downloadedUri)) {
                                                                onInstallModule(downloadedUri)
                                                            } else {
                                                                downloads.forget(asset.downloadUrl)
                                                            }
                                                        }
                                                    },
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Icon(
                                                            modifier = Modifier.size(20.dp),
                                                            imageVector = MiuixIcons.FileDownloads,
                                                            tint = actionIconTint,
                                                            contentDescription = stringResource(R.string.install)
                                                        )
                                                        Text(
                                                            modifier = Modifier.padding(start = 4.dp, end = 2.dp),
                                                            text = stringResource(R.string.install),
                                                            color = actionIconTint,
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 15.sp
                                                        )
                                                    }
                                                }
                                            } else {
                                                IconButton(
                                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                                                    backgroundColor = secondaryContainer,
                                                    minHeight = 35.dp,
                                                    minWidth = 35.dp,
                                                    enabled = !isDownloading,
                                                    onClick = onClickDownload,
                                                ) {
                                                    if (isDownloading) {
                                                        CircularProgressIndicator(
                                                            progress = progress / 100f, size = 20.dp, strokeWidth = 2.dp
                                                        )
                                                    } else {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Icon(
                                                                modifier = Modifier.size(20.dp),
                                                                imageVector = MiuixIcons.FileDownloads,
                                                                tint = actionIconTint,
                                                                contentDescription = stringResource(R.string.download)
                                                            )
                                                            Text(
                                                                modifier = Modifier.padding(start = 4.dp, end = 2.dp),
                                                                text = stringResource(R.string.download),
                                                                color = actionIconTint,
                                                                fontWeight = FontWeight.Medium,
                                                                fontSize = 15.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (index != rel.assets.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                                thickness = 0.5.dp,
                                                color = colorScheme.outline.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoPage(
    module: RepoModuleArg,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    actionIconTint: Color,
    secondaryContainer: Color,
    uriHandler: UriHandler,
    sourceUrl: String,
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            if (module.sourceName.isNotEmpty()) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.module_repo_source_label),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Text(
                            text = module.sourceName,
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
            if (module.alternateSourceNames.isNotEmpty()) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.module_repo_source_also_in),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            module.alternateSourceNames.forEach { name ->
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            if (module.authorsList.isNotEmpty()) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.module_author), modifier = Modifier.padding(top = 6.dp)
                    )
                    AppCard(
                        modifier = Modifier.padding(horizontal = 12.dp), insideMargin = PaddingValues(16.dp)
                    ) {
                        Column {
                            module.authorsList.forEachIndexed { index, author ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = author.name, fontSize = 14.sp, color = colorScheme.onSurface, modifier = Modifier.weight(1f)
                                    )
                                    val clickable = author.link.isNotEmpty()
                                    val tint = if (clickable) actionIconTint else actionIconTint.copy(alpha = 0.35f)
                                    IconButton(
                                        backgroundColor = secondaryContainer,
                                        minHeight = 35.dp,
                                        minWidth = 35.dp,
                                        enabled = clickable,
                                        onClick = {
                                            if (clickable) {
                                                uriHandler.openUri(author.link)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(20.dp),
                                            imageVector = MiuixIcons.Link,
                                            tint = tint,
                                            contentDescription = null
                                        )
                                    }
                                }
                                if (index != module.authorsList.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        thickness = 0.5.dp,
                                        color = colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (sourceUrl.isNotEmpty()) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.module_repos_source_code), modifier = Modifier.padding(top = 6.dp)
                    )
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = sourceUrl,
                                fontSize = 16.sp,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                backgroundColor = secondaryContainer,
                                minHeight = 35.dp,
                                minWidth = 35.dp,
                                onClick = {
                                    uriHandler.openUri(sourceUrl)
                                },
                            ) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = MiuixIcons.Link,
                                    tint = actionIconTint,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            }
            // 源码仓库证据；仓库地址与上面的「源码」重复时不再画一遍。
            sourceEvidenceItemsMiuix(
                evidence = module.repoEvidence,
                includeRepoUrl = module.repoEvidence.repoUrl != sourceUrl,
                onOpenUrl = { uriHandler.openUri(it) },
            )
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/** 详情页的一页（Miuix 版）。酷安条目没有 README 页，页数与顺序都跟着来源变。 */
private enum class RepoDetailPageKindMiuix { README, RELEASES, INFO, COMMENTS }

/**
 * 酷安条目的「信息」页：出处（帖子链接）、上传者（昵称，有 UID 时挂主页入口）、提交时间，
 * 以及源码仓库的校验证据。
 *
 * 没有 GitHub 仓库、星标、README——索引里没有的东西整块不画，而不是画一行空占位。
 * 有仓库证据但没有酷安元数据时也走这一页，所以 [meta] 可空。
 */
@Composable
private fun CoolapkInfoPageMiuix(
    meta: CoolapkModuleMeta?,
    evidence: RepoEvidenceArg,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    actionIconTint: Color,
    secondaryContainer: Color,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val postUrl = meta?.postUrl?.takeIf { it.isNotBlank() }
    val uploaderName = meta?.uploaderName?.takeIf { it.isNotBlank() }
    val uploaderUid = meta?.coolapkUid?.takeIf { it.isNotBlank() }
    val submittedAt = meta?.submittedAt?.takeIf { it.isNotBlank() }
    // 作者与上传者是两个人：作者是写包的人，上传者是把它贴到酷安的人。作者主页由上传者
    // 填写，服务端没有核验过，所以这一块必须把「未验证」写在链接旁边。
    val authorName = meta?.authorName?.takeIf { it.isNotBlank() }
    val authorCoolapkUrl = meta?.authorCoolapkUrl?.takeIf { it.isNotBlank() }
    val authorGithubUrl = meta?.authorGithubUrl?.takeIf { it.isNotBlank() }
    val hasAuthor = authorName != null || authorCoolapkUrl != null || authorGithubUrl != null
    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            if (hasAuthor) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.module_author),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 名字那行始终在：没有链接也要让人知道作者是谁，而不是整块消失。
                            Text(
                                text = authorName ?: stringResource(R.string.module_repo_author_unknown),
                                fontSize = 14.sp,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.module_repo_author_unverified),
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                            authorCoolapkUrl?.let { url ->
                                AuthorLinkRowMiuix(
                                    label = stringResource(R.string.module_repo_author_coolapk),
                                    url = url,
                                    actionIconTint = actionIconTint,
                                    secondaryContainer = secondaryContainer,
                                    onOpenUrl = onOpenUrl,
                                )
                            }
                            authorGithubUrl?.let { url ->
                                AuthorLinkRowMiuix(
                                    label = stringResource(R.string.module_repo_author_github),
                                    url = url,
                                    actionIconTint = actionIconTint,
                                    secondaryContainer = secondaryContainer,
                                    onOpenUrl = onOpenUrl,
                                )
                            }
                        }
                    }
                }
            }
            if (postUrl != null) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.module_repo_coolapk_post),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = postUrl,
                                fontSize = 14.sp,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                backgroundColor = secondaryContainer,
                                minHeight = 35.dp,
                                minWidth = 35.dp,
                                onClick = { onOpenPost(postUrl) },
                            ) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = MiuixIcons.Link,
                                    tint = actionIconTint,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            }
            if (uploaderName != null) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.module_repo_coolapk_uploader),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uploaderName,
                                fontSize = 14.sp,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (uploaderUid != null) {
                                IconButton(
                                    backgroundColor = secondaryContainer,
                                    minHeight = 35.dp,
                                    minWidth = 35.dp,
                                    onClick = { onOpenUser(uploaderUid) },
                                ) {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        imageVector = MiuixIcons.Link,
                                        tint = actionIconTint,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (submittedAt != null) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.module_repo_coolapk_submitted_at),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        // 索引给的是 ISO 时间字符串；若数据侧换成时间戳，这里改走 formatEpochTime。
                        Text(
                            text = formatRepoTime(submittedAt),
                            fontSize = 14.sp,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
            // 出处信息之后接源码仓库的校验证据。
            sourceEvidenceItemsMiuix(evidence = evidence, onOpenUrl = onOpenUrl)
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/** 信息页里的「作者主页」一行：左边平台名与地址，右边打开按钮。 */
@Composable
private fun AuthorLinkRowMiuix(
    label: String,
    url: String,
    actionIconTint: Color,
    secondaryContainer: Color,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = colorScheme.onSurface
            )
            Text(
                text = url,
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            backgroundColor = secondaryContainer,
            minHeight = 35.dp,
            minWidth = 35.dp,
            onClick = { onOpenUrl(url) },
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = MiuixIcons.Link,
                tint = actionIconTint,
                contentDescription = null
            )
        }
    }
}

@SuppressLint("StringFormatInvalid", "DefaultLocale")
@Composable
fun ModuleRepoDetailScreenMiuix(
    state: ModuleRepoDetailUiState,
    actions: ModuleRepoDetailActions,
) {
    val context = LocalContext.current
    val enableBlur = LocalEnableBlur.current
    val actionIconTint = colorScheme.onSurface.copy(alpha = if (isInDarkTheme()) 0.7f else 0.9f)
    val secondaryContainer = colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val module = state.module
    // 酷安社区的条目没有 README，也没有 GitHub 仓库/星标：详情页只留「版本」与「信息」，
    // 「信息」换成出处（帖子链接）、上传者、提交时间这组酷安字段。
    val isCoolapkModule = module.isCoolapkCommunityModule()
    val coolapkMeta = module.coolapkMeta()
    val coolapkOpenFailed = stringResource(R.string.module_repo_coolapk_open_failed)
    val scope = rememberCoroutineScope()
    val confirmTitle = stringResource(R.string.module_install)
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = { pendingDownload?.invoke() })

    // 下载状态挂在页面上（见 AssetDownloads）：版本列表的列表项滚出屏幕就被回收，状态放在项里
    // 会跟着一起消失。
    //
    // 下载完就接着装：那个确认框问的本来就是"要不要装这个模块"，下载只是它的第一步，再让用户
    // 点一次「安装」等于把同一个决定问第二遍。装到设备上是唯一不可逆的一步，仍然由确认框守着。
    //
    // 用页面的 scope 发起跳转：下载完成的回调不属于任何一次组合，列表项被回收、甚至用户已经
    // 退回上一页之后它照样会到。这个 scope 随页面一起取消，于是"用户已经走开"本身就是一道闸门，
    // 不会把人从别处拽进刷入页。
    val downloads = rememberAssetDownloads(
        onCompleted = { uri ->
            scope.launch {
                // 下载目录里的文件可能已经不在了，和手动点「安装」时一样先确认还能读。
                if (isDownloadAvailable(uri)) actions.onInstallModule(uri)
            }
        }
    )

    val scrollBehavior = MiuixScrollBehavior()

    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    val detailBarColor = if (blurActive || hasCustomBackground) Color.Transparent else colorScheme.surface

    val tabReadme = stringResource(R.string.tab_readme)
    val tabReleases = stringResource(R.string.tab_releases)
    val tabInfo = stringResource(R.string.tab_info)
    // 酷安条目没有 README，这一页整个去掉；出处信息与仓库证据都没有时才不摆「信息」页。
    val pages = when {
        isCoolapkModule && (coolapkMeta != null || module.repoEvidence.repoUrl.isNotEmpty()) -> listOf(
            RepoDetailPageKindMiuix.RELEASES,
            RepoDetailPageKindMiuix.INFO,
            RepoDetailPageKindMiuix.COMMENTS,
        )

        isCoolapkModule -> listOf(RepoDetailPageKindMiuix.RELEASES, RepoDetailPageKindMiuix.COMMENTS)
        else -> listOf(
            RepoDetailPageKindMiuix.README,
            RepoDetailPageKindMiuix.RELEASES,
            RepoDetailPageKindMiuix.INFO,
            RepoDetailPageKindMiuix.COMMENTS,
        )
    }
    val tabComments = stringResource(R.string.module_comments_tab)
    val tabs = pages.map { kind ->
        when (kind) {
            RepoDetailPageKindMiuix.README -> tabReadme
            RepoDetailPageKindMiuix.RELEASES -> tabReleases
            RepoDetailPageKindMiuix.INFO -> tabInfo
            RepoDetailPageKindMiuix.COMMENTS -> tabComments
        }
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val tabRowHeight = 40.dp
    val dynamicTopPadding = remember(scrollBehavior) {
        { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val coroutineScope = rememberCoroutineScope()

    AppScaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(color = detailBarColor, title = module.moduleName, scrollBehavior = scrollBehavior, navigationIcon = {
                    IconButton(
                        onClick = actions.onBack
                    ) {
                        val layoutDirection = LocalLayoutDirection.current
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            }, imageVector = MiuixIcons.Back, contentDescription = null, tint = colorScheme.onSurface
                        )
                    }
                }, actions = {
                    // 酷安条目的 webUrl 是从下载地址反推的，打开只是个 zip，不如不摆；
                    // 出处链接在「信息」页里，按帖子给。
                    if (!isCoolapkModule && state.webUrl.isNotEmpty()) {
                        IconButton(
                            onClick = actions.onOpenWebUrl
                        ) {
                            Icon(
                                imageVector = MiuixIcons.HorizontalSplit, contentDescription = null, tint = colorScheme.onBackground
                            )
                        }
                    }
                }, bottomContent = {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp)
                            .deferredTopPadding(dynamicTopPadding)
                    ) {
                        TabRow(
                            tabs = tabs,
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { index ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = PagerNavigationSpringSpec,
                                    )
                                }
                            },
                            colors = TabRowDefaults.tabRowColors(
                                backgroundColor = if (blurActive) Color.Transparent else colorScheme.surface,
                                selectedBackgroundColor = backgroundAwareColor(colorScheme.surfaceContainer),
                            ),
                            height = tabRowHeight,
                        )
                    }
                })
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            overscrollEffect = null,
        ) { page ->
            val innerPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding() + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
            )

            when (pages[page]) {
                RepoDetailPageKindMiuix.README -> ReadmePage(
                    readmeHtml = state.readmeHtml,
                    readmeLoaded = state.readmeLoaded,
                    isMarkdown = module.isMmrl,
                    innerPadding = innerPadding,
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop
                )

                RepoDetailPageKindMiuix.RELEASES -> ReleasesPage(
                    detailReleases = state.detailReleases,
                    showNoArchiveHint = state.showNoArchiveHint(),
                    innerPadding = innerPadding,
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    actionIconTint = actionIconTint,
                    secondaryContainer = secondaryContainer,
                    confirmTitle = confirmTitle,
                    confirmDialog = confirmDialog,
                    scope = scope,
                    onInstallModule = actions.onInstallModule,
                    context = context,
                    setPendingDownload = { pendingDownload = it },
                    downloads = downloads)

                RepoDetailPageKindMiuix.INFO -> if (isCoolapkModule) {
                    CoolapkInfoPageMiuix(
                        meta = coolapkMeta,
                        evidence = module.repoEvidence,
                        innerPadding = innerPadding,
                        scrollBehavior = scrollBehavior,
                        backdrop = backdrop,
                        actionIconTint = actionIconTint,
                        secondaryContainer = secondaryContainer,
                        onOpenPost = { url ->
                            // 装了酷安就进帖子，没装回落网页；两条都不通才提示。
                            if (!CoolapkLinks.openPost(context, url)) {
                                Toast.makeText(context, coolapkOpenFailed, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenUser = { uid ->
                            if (!CoolapkLinks.openUser(context, uid)) {
                                Toast.makeText(context, coolapkOpenFailed, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenUrl = actions.onOpenUrl,
                    )
                } else {
                    val uriHandler = remember(actions) {
                        object : UriHandler {
                            override fun openUri(uri: String) = actions.onOpenUrl(uri)
                        }
                    }
                    InfoPage(
                        module = module,
                        innerPadding = innerPadding,
                        scrollBehavior = scrollBehavior,
                        backdrop = backdrop,
                        actionIconTint = actionIconTint,
                        secondaryContainer = secondaryContainer,
                        uriHandler = uriHandler,
                        sourceUrl = state.sourceUrl,
                    )
                }

                RepoDetailPageKindMiuix.COMMENTS -> CommentsPageMiuix(
                    moduleId = module.moduleId,
                    initialLikeCount = module.likes,
                    innerPadding = innerPadding,
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    actionIconTint = actionIconTint,
                    secondaryContainer = secondaryContainer,
                )
            }
        }
    }
}

/**
 * 「信息」页的源码校验证据块（Miuix 版）：仓库、LICENSE、命中的 Release tag、摘要、
 * 最近校验时间与复验结论收在一张卡里，免得六段标题把页面拉长。
 *
 * 没有仓库时只留一句灰字——空壳比没有更让人困惑；字段缺哪个就跳过哪一行，
 * 免得把「索引没给」画成「作者没填」。
 */
private fun LazyListScope.sourceEvidenceItemsMiuix(
    evidence: RepoEvidenceArg,
    includeRepoUrl: Boolean = true,
    onOpenUrl: (String) -> Unit,
) {
    if (evidence.repoUrl.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.module_repo_no_public_source),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    item {
        SmallTitle(
            text = stringResource(R.string.module_repo_repo_evidence_title),
            modifier = Modifier.padding(top = 6.dp)
        )
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (includeRepoUrl) {
                    EvidenceLineMiuix(
                        label = R.string.module_repo_repo_url,
                        value = evidence.repoUrl,
                        action = {
                            IconButton(
                                minHeight = 35.dp,
                                minWidth = 35.dp,
                                onClick = { onOpenUrl(evidence.repoUrl) },
                            ) {
                                Icon(
                                    modifier = Modifier.size(20.dp),
                                    imageVector = MiuixIcons.Link,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
                if (evidence.repoLicense.isNotEmpty()) {
                    EvidenceLineMiuix(R.string.module_repo_license, evidence.repoLicense)
                }
                if (evidence.matchedTag.isNotEmpty()) {
                    EvidenceLineMiuix(R.string.module_repo_repo_tag, evidence.matchedTag)
                }
                if (evidence.digest.isNotEmpty()) {
                    EvidenceLineMiuix(R.string.module_repo_repo_digest, shortRepoDigest(evidence.digest))
                }
                if (evidence.repoCheckedAt > 0L) {
                    EvidenceLineMiuix(
                        R.string.module_repo_repo_checked_at,
                        formatEpochTime(LocalContext.current, evidence.repoCheckedAt)
                    )
                }
                Text(
                    text = stringResource(
                        if (evidence.repoVerified) {
                            R.string.module_repo_repo_valid
                        } else {
                            R.string.module_repo_repo_stale
                        }
                    ),
                    fontSize = 12.sp,
                    color = if (evidence.repoVerified) colorScheme.primary else colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

/** 证据卡的一行：小号标题 + 值，右侧可选一个打开链接的按钮。 */
@Composable
private fun EvidenceLineMiuix(
    @StringRes label: Int,
    value: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(label),
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariantSummary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
    }
}

/**
 * 详情页的评论区（Miuix 皮肤）：结构同 Material 版的 CommentsPage，只是组件换成 Miuix。
 *
 * 列表走 LazyColumn + AppCard；发布条钉底部（Surface + imePadding）。
 * 未登录/401 时整页换引导卡，验证完点「重试」回来。
 */
@Composable
private fun CommentsPageMiuix(
    moduleId: String,
    /** 索引带来的初始点赞数；进页面后以 VM 的状态为准。 */
    initialLikeCount: Int,
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    actionIconTint: Color,
    secondaryContainer: Color,
) {
    val viewModel: ModuleCommentsViewModel = viewModel(
        key = moduleId,
        initializer = { ModuleCommentsViewModel(moduleId, initialLikeCount) },
    )
    val state by viewModel.uiState.collectAsState()
    val layoutDirection = LocalLayoutDirection.current
    val navigator = LocalNavigator.current

    // 从评论区验证页 pop 回来时 NavDisplay 重组本页：重读会话，令牌到手就清
    // 「去验证」卡并刷新列表；init 那次 readSession 比较不出变化，这里补上。
    LaunchedEffect(Unit) { viewModel.recheckSession() }

    var draft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var deleteTarget by remember { mutableStateOf<CommentItem?>(null) }
    val confirmTitle = stringResource(R.string.module_comments_delete_confirm_title)
    val confirmBody = stringResource(R.string.module_comments_delete_confirm_body)
    val confirmDialog = rememberConfirmDialog()

    // 删除走异步确认框：awaitConfirm 挂起等用户表态，确认后才真正调 VM。
    LaunchedEffect(deleteTarget) {
        if (deleteTarget != null) {
            when (confirmDialog.awaitConfirm(confirmTitle, confirmBody)) {
                ConfirmResult.Confirmed -> deleteTarget?.let { viewModel.delete(it.id) }
                ConfirmResult.Canceled -> Unit
            }
            deleteTarget = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                // 底部多留出发送条的高度：最后一条评论不能被输入框压住。
                bottom = innerPadding.calculateBottomPadding() + 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 头部汇总条：左侧「N 条评论」，右侧点赞胶囊——点赞不再是孤零零一行。
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.module_comments_count, state.comments.size),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    val liked = state.likedModuleIds.contains(moduleId)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (liked) colorScheme.primary else colorScheme.surfaceContainerHigh)
                            .clickable(enabled = state.canInteract && !state.submitting) {
                                viewModel.toggleLike(moduleId, !liked)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (liked) R.string.module_comments_unlike else R.string.module_comments_like
                                ),
                                modifier = Modifier.size(16.dp),
                                tint = if (liked) Color.White else colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = state.likeCount.toString(),
                                fontSize = 13.sp,
                                color = if (liked) Color.White else colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            when {
                state.loading && state.comments.isEmpty() -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(progress = null, size = 24.dp, strokeWidth = 3.dp)
                        }
                    }
                }

                state.loadFailed && state.comments.isEmpty() -> {
                    item(key = "load_failed") {
                        CommentsStateCardMiuix(
                            icon = Icons.Rounded.ErrorOutline,
                            message = stringResource(R.string.module_comments_load_failed),
                            actionText = stringResource(R.string.network_retry),
                            onAction = viewModel::refresh,
                        )
                    }
                }

                state.needVerify -> {
                    item(key = "need_verify") {
                        CommentsStateCardMiuix(
                            icon = Icons.Rounded.Info,
                            message = stringResource(R.string.module_comments_login_required),
                            actionText = stringResource(R.string.module_comments_go_verify),
                            onAction = { navigator.push(Route.CommentVerify) },
                        )
                    }
                }

                else -> {
                    if (state.comments.isEmpty()) {
                        item(key = "empty") {
                            CommentsStateCardMiuix(
                                icon = Icons.AutoMirrored.Outlined.Chat,
                                message = stringResource(R.string.module_comments_empty),
                            )
                        }
                    }
                    itemsIndexed(
                        items = state.comments,
                        key = { _, c -> "comment_" + c.id },
                        contentType = { _, _ -> "comment" },
                    ) { _, comment ->
                        // 楼中楼用左侧引导线挂在同一张卡里，缩进关系一眼可见。
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            CommentRowMiuix(
                                comment = comment,
                                isReply = false,
                                isMine = comment.uid.isNotEmpty() && comment.uid == state.myUid,
                                onReply = { target -> replyTarget = target },
                                onDelete = { deleteTarget = it },
                            )
                            if (comment.replies.isNotEmpty()) {
                                Row(modifier = Modifier.padding(top = 10.dp).height(IntrinsicSize.Min)) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(colorScheme.outline, RoundedCornerShape(1.dp)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        comment.replies.forEach { reply ->
                                            CommentRowMiuix(
                                                comment = reply,
                                                isReply = true,
                                                isMine = reply.uid.isNotEmpty() && reply.uid == state.myUid,
                                                onReply = { target -> replyTarget = target },
                                                onDelete = { deleteTarget = it },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.comments.isNotEmpty() && state.nextCursor.isNotEmpty()) {
                        item(key = "load_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.paging) {
                                    CircularProgressIndicator(progress = null, size = 22.dp, strokeWidth = 2.dp)
                                } else {
                                    TextButton(
                                        text = stringResource(R.string.module_comments_load_more),
                                        onClick = viewModel::loadMore,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 发布条：钉在底部，回复目标变化时占位文案跟着换。
        CommentsComposerMiuix(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            draft = draft,
            onDraftChange = { draft = it },
            replyTarget = replyTarget,
            onCancelReply = { replyTarget = null },
            charLimit = ModuleComments.MAX_COMMENT_LENGTH,
            submitting = state.submitting,
            submitError = state.submitError,
            canInteract = state.canInteract,
            onSend = {
                val target = replyTarget
                viewModel.submit(
                    content = draft,
                    // 回复顶级评论：parent_id = 顶级 id；回复楼中楼：parent_id = 所属顶级 id（B 站模式）。
                    parentId = target?.let { t ->
                        state.comments.firstOrNull { it.id == t.id }?.id
                            ?: state.comments.firstOrNull { top -> top.replies.any { it.id == t.id } }?.id
                    },
                    replyToUid = target?.uid,
                ) { ok ->
                    if (ok) {
                        draft = ""
                        replyTarget = null
                    }
                }
            },
            onGoVerify = { navigator.push(Route.CommentVerify) },
        )
    }
}

/**
 * 一条评论（或楼中楼里的一条回复），Miuix 版。
 *
 * 已删除的顶级评论（content 为空串）：正文换成「已删除」占位，回复保留。
 */
@Composable
private fun CommentRowMiuix(
    comment: CommentItem,
    isReply: Boolean,
    isMine: Boolean,
    onReply: (CommentItem) -> Unit,
    onDelete: (CommentItem) -> Unit,
) {
    val deleted = comment.content.isEmpty()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerifiedAvatar(
                avatarUrl = comment.avatar,
                tint = colorScheme.onSurface,
                placeholder = colorScheme.secondaryContainer,
                avatarSize = if (isReply) 22.dp else 32.dp,
                showMark = false,
            )
            Column {
                // 昵称次级色、时间更弱，把视觉重量留给正文。
                Text(
                    text = comment.nickname.ifBlank { comment.uid },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = formatRepoTime(comment.createdAt),
                    fontSize = 11.sp,
                    color = colorScheme.outline,
                )
            }
            Spacer(Modifier.weight(1f))
            CommentActionTextMiuix(
                text = stringResource(R.string.module_comments_reply),
                color = colorScheme.primary,
                onClick = { onReply(comment) },
            )
            if (isMine && !deleted) {
                CommentActionTextMiuix(
                    text = stringResource(R.string.module_comments_delete),
                    color = colorScheme.error,
                    onClick = { onDelete(comment) },
                )
            }
        }
        if (deleted) {
            Text(
                text = stringResource(R.string.module_comments_deleted),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
        } else {
            val replyPrefix = if (isReply && comment.replyToNickname.isNotBlank()) {
                "@" + comment.replyToNickname + " "
            } else ""
            Text(
                text = replyPrefix + comment.content,
                fontSize = 14.sp,
                color = colorScheme.onSurface,
            )
        }
    }
}

/** 底部发布条（Miuix）：胶囊输入框 + 主色圆形发送钮 + 右下角字数；未登录时整条换成引导行。 */
@Composable
private fun CommentsComposerMiuix(
    modifier: Modifier = Modifier,
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTarget: CommentItem?,
    onCancelReply: () -> Unit,
    charLimit: Int,
    submitting: Boolean,
    submitError: String,
    canInteract: Boolean,
    onSend: () -> Unit,
    onGoVerify: () -> Unit,
) {
    // 全 Miuix 组件与配色：MD3 兜底主题会带出默认紫描边，这里一根都不用。
    Box(modifier = modifier.background(colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!canInteract) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.module_comments_login_required),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.module_comments_go_verify),
                        onClick = onGoVerify,
                    )
                }
                return@Column
            }
            replyTarget?.let { target ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.module_comments_reply_hint,
                            target.nickname.ifBlank { target.uid },
                        ),
                        fontSize = 12.sp,
                        color = colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    CommentActionTextMiuix(
                        text = stringResource(R.string.cancel),
                        color = colorScheme.onSurfaceVariantSummary,
                        onClick = onCancelReply,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = draft,
                    onValueChange = { value -> if (value.length <= charLimit) onDraftChange(value) },
                    modifier = Modifier.weight(1f),
                    label = replyTarget?.let {
                        stringResource(R.string.module_comments_reply_hint, it.nickname.ifBlank { it.uid })
                    } ?: stringResource(R.string.module_comments_hint),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                )
                val canSend = draft.isNotBlank() && !submitting
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (canSend) colorScheme.primary else colorScheme.surfaceContainerHigh)
                        .clickable(enabled = canSend) { onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(progress = null, size = 18.dp, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = stringResource(R.string.module_comments_send),
                            tint = if (canSend) Color.White else colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (submitError.isNotBlank()) {
                    Text(
                        text = submitError,
                        fontSize = 12.sp,
                        color = colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = stringResource(R.string.module_comments_char_count, draft.length, charLimit),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 状态卡（Miuix）：大图标 + 文案 + 可选文字按钮，居中摆在一张 AppCard 里。 */
@Composable
private fun CommentsStateCardMiuix(
    icon: ImageVector,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            if (actionText != null && onAction != null) {
                TextButton(
                    text = actionText,
                    onClick = onAction,
                )
            }
        }
    }
}

/** 紧凑文字操作（Miuix）：替代 TextButton，去掉最小高度让评论卡更紧。 */
@Composable
private fun CommentActionTextMiuix(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = color,
        )
    }
}

