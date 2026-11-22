package com.sukisu.ultra.ui.screen.modulerepo

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.ui.util.customText
import com.sukisu.ultra.ui.util.CustomTextKey
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.RepoColumn
import com.sukisu.ultra.data.model.RepoModule
import com.sukisu.ultra.data.repository.RepoSource
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import com.sukisu.ultra.ui.component.ScrollToTopOnChange
import com.sukisu.ultra.ui.component.dialog.ConfirmDialogHandle
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.markdown.GithubMarkdown
import com.sukisu.ultra.ui.component.material.ExpressiveScaffold
import com.sukisu.ultra.ui.component.material.ExpressiveTabRow
import com.sukisu.ultra.ui.component.material.SearchAppBar
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedItemContainer
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.TonalCard
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors
import com.sukisu.ultra.ui.component.statustag.StatusTag
import com.sukisu.ultra.ui.util.CoolapkLinks
import com.sukisu.ultra.ui.util.formatEpochTime
import com.sukisu.ultra.ui.util.formatFileSize
import com.sukisu.ultra.ui.util.formatRepoTime
import com.sukisu.ultra.ui.util.isDownloadAvailable
import com.sukisu.ultra.ui.util.rememberContentReady
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.ui.screen.upload.VerifiedAvatar
import com.sukisu.ultra.ui.component.dialog.ConfirmResult
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.util.ModuleComments.CommentItem
import com.sukisu.ultra.ui.util.ModuleComments
import com.sukisu.ultra.ui.viewmodel.ModuleCommentsViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ModuleRepoScreenMaterial(
    state: ModuleRepoUiState,
    actions: ModuleRepoActions,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullToRefreshState = rememberPullToRefreshState()
    var showSourcesDialog by remember { mutableStateOf(false) }
    // 免责说明：没勾过"承诺"就每次进入本页都弹一次。取值只看进入时的这一份状态，
    // 关掉之后由本地的 showDisclaimer 管着，不再跟着 state 的后续变化走。
    var showDisclaimer by remember { mutableStateOf(!state.disclaimerAccepted) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(text = customText(CustomTextKey.PAGE_MODULE_REPO)) },
                searchText = state.searchStatus.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showSourcesDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.module_repo_manage_sources)
                        )
                    }

                    IconButton(
                        onClick = { showSortMenu = true }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.menu_sort)
                        )

                        DropdownMenuPopup(expanded = showSortMenu, onDismissRequest = {
                            showSortMenu = false
                        }) {
                            val sortOptions = listOf(
                                RepoSort.UPDATED to R.string.module_repos_sort_updated,
                                RepoSort.CREATED to R.string.module_repos_sort_created,
                                RepoSort.NAME to R.string.module_repos_sort_name,
                                RepoSort.STARS to R.string.module_repos_sort_stars,
                            )
                            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                                sortOptions.forEachIndexed { index, (order, resId) ->
                                    SelectableDropdownMenuItem(
                                        text = { Text(stringResource(resId)) },
                                        selected = state.sortOrder == order,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                            actions.onSetSortOrder(order)
                                            showSortMenu = false
                                        },
                                        shapes = MenuDefaults.itemShape(
                                            index = index,
                                            count = sortOptions.size
                                        ),
                                        selectedLeadingIcon = {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                searchContent = { _, closeSearch ->
                    val latestSearchResults = rememberUpdatedState(state.searchResults)
                    ScrollToTopOnChange(
                        searchListState,
                        state.searchStatus.searchText,
                    ) { latestSearchResults.value }
                    RepoModuleList(
                        modules = state.searchResults,
                        listState = searchListState,
                        modifier = Modifier.fillMaxSize(),
                        // 搜索无结果不是「还没配源」，这里没有什么可以添加；地址栏就在上方，再摆一个
                        // 什么都不做的「添加仓库」只会是死控件。
                        emptyHint = stringResource(R.string.module_repo_search_no_results),
                        likeOverrides = state.likeOverrides,
                        likedModuleIds = state.likedModuleIds,
                        onToggleLike = actions.onToggleLike,
                        onModuleClick = {
                            closeSearch()
                            actions.onOpenRepoDetail(it)
                        }
                    )
                }
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        // An empty list means "loading" only until the first fetch settles; after that it
        // means every source failed and the error banner below is the thing to show.
        val isLoading = !state.hasLoadedOnce
        val hadDataOnEntry = remember { state.modules.isNotEmpty() }
        val contentReady = hadDataOnEntry || rememberContentReady()

        if (!contentReady || isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (state.offline || state.error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(
                                if (state.offline) R.string.network_offline
                                else R.string.module_repo_unavailable
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = actions.onRefresh,
                        ) {
                            Text(stringResource(R.string.network_retry))
                        }
                    }
                } else if (state.sources.isEmpty()) {
                    // Nothing is configured out of the box, so this is the first thing a new user
                    // sees; it has to lead somewhere instead of just stating the fact.
                    RepoEmptyPrompt(
                        hint = stringResource(R.string.module_repo_sources_empty),
                        actionLabel = stringResource(R.string.module_repo_add_repo),
                        onAction = { showSourcesDialog = true },
                    )
                } else {
                    LoadingIndicator()
                }
            }
        }
        if (!isLoading && contentReady) {
            val latestModules = rememberUpdatedState(state.modules)
            val latestRefreshing = rememberUpdatedState(state.isRefreshing)
            // 切换来源筛选也算换了一批要看的模块，跟着回到顶部，而不是留在原来的滚动位置。
            ScrollToTopOnChange(
                listState,
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
            val showHeader = coolapkSource != null || filterOptions.size > 1 ||
                state.columns.isNotEmpty() || state.columnsFailed
            val filteredModules = rememberSourceFilteredModules(state.modules, state.selectedSourceId)
            // 筛选把列表筛空，跟「一个源都没配」是两回事：前者换个筛选就有内容，后者要去加源。
            val filteredEmpty = state.selectedSourceId != null && filteredModules.isEmpty()

            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                isRefreshing = state.isRefreshing,
                onRefresh = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    actions.onRefresh()
                    refreshTick.intValue++
                },
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isRefreshing,
                        state = pullToRefreshState,
                    )
                },
            ) {
                RepoModuleList(
                    modules = filteredModules,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    sourceErrors = state.sourceErrors,
                    sources = state.sources,
                    dismissedErrors = state.dismissedSourceErrors,
                    emptyHint = if (filteredEmpty) {
                        stringResource(R.string.module_repo_filter_source_empty)
                    } else {
                        repoEmptyHint(repoEmptyReason(state.sources))
                    },
                    emptyActionLabel = if (filteredEmpty) {
                        stringResource(R.string.module_repo_filter_all)
                    } else {
                        repoEmptyAction(repoEmptyReason(state.sources))
                    },
                    onEmptyAction = {
                        if (filteredEmpty) actions.onSelectSource(null) else showSourcesDialog = true
                    },
                    onDismissSourceError = actions.onDismissSourceError,
                    header = if (showHeader) {
                        {
                            RepoStoreHeader(
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
                    } else null,
                    likeOverrides = state.likeOverrides,
                    likedModuleIds = state.likedModuleIds,
                    onToggleLike = actions.onToggleLike,
                    onModuleClick = actions.onOpenRepoDetail
                )
            }
        }
    }

    ManageSourcesDialogMaterial(
        show = showSourcesDialog,
        onDismissRequest = { showSourcesDialog = false },
        state = state,
        actions = actions,
    )

    RepoDisclaimerDialogMaterial(
        show = showDisclaimer,
        onDismiss = { committed ->
            showDisclaimer = false
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

@Composable
private fun RepoModuleList(
    modules: List<RepoModule>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    sourceErrors: Map<String, String> = emptyMap(),
    sources: List<RepoSource> = emptyList(),
    dismissedErrors: Set<String> = emptySet(),
    emptyHint: String? = null,
    emptyActionLabel: String? = null,
    onEmptyAction: () -> Unit = {},
    onDismissSourceError: (String) -> Unit = {},
    // 商店页顶部的入口卡与来源筛选，跟着列表一起滚；搜索列表不传，搜的就是全部结果。
    header: (@Composable () -> Unit)? = null,
    /** 点赞数覆盖与「我点过」集合：卡片底栏按 moduleId 取，见 ModuleRepoUiState。 */
    likeOverrides: Map<String, Int> = emptyMap(),
    likedModuleIds: Set<String> = emptySet(),
    onToggleLike: (RepoModule) -> Unit = {},
    onModuleClick: (RepoModule) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp + bottomPadding
        ),
    ) {
        if (header != null) {
            // 数据源入口与筛选排在所有内容之前，但仍然是列表的一部分：滚动时一起走，不占死高度。
            item(key = "source_filter_header") { header() }
        }
        if (sourceErrors.isNotEmpty()) {
            item(key = "source_errors_banner") {
                // 关掉的通知只针对这一次抓取结果，下一次结果落地时会清空（见 ModuleRepoViewModel）。
                val visibleErrors = remember(sourceErrors, dismissedErrors) {
                    sourceErrors.filterValues { message -> message !in dismissedErrors }
                }
                val groups = remember(visibleErrors) { groupSourceErrors(visibleErrors) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group ->
                        SourceErrorBanner(
                            keys = group.keys,
                            sources = sources,
                            message = group.message,
                            onDismiss = { onDismissSourceError(group.message) },
                        )
                    }
                }
            }
        }
        if (modules.isEmpty()) {
            item(key = "repo_empty_prompt") {
                RepoEmptyPrompt(hint = emptyHint, actionLabel = emptyActionLabel, onAction = onEmptyAction)
            }
        }
        items(modules, key = { "${it.sourceId}|${it.moduleId}" }, contentType = { "module" }) { module ->
            val latestReleaseTime = remember(module.latestReleaseTime) { formatRepoTime(module.latestReleaseTime) }
            // 源码状态只有一枚：单状态由 repoSourceBadge 收口，界面这里只负责翻文案与配色。
            val sourceBadge = remember(
                module.repoUrl,
                module.repoVerified,
                module.repoCoversLatest,
                module.repoLicense,
            ) { repoSourceBadge(module) }
            // 点赞数优先用本次会话的覆盖值（点赞后立即更新），否则用索引快照。
            val likes = likeOverrides[module.moduleId] ?: module.likes
            val liked = module.moduleId in likedModuleIds

            TonalCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onModuleClick(module) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 14.dp, 16.dp, 6.dp)
                ) {
                    if (module.moduleName.isNotEmpty()) {
                        Text(
                            text = module.moduleName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        )
                    }
                    // 紧凑一行：moduleId 与作者合并，monospace 小字（沿用上游 Material 重设计）
                    if (module.moduleId.isNotEmpty() || module.authors.isNotEmpty()) {
                        Text(
                            text = listOf(module.moduleId, module.authors)
                                .filter { it.isNotEmpty() }
                                .joinToString(", "),
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (module.summary.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = module.summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 4,
                        )
                    }

                    if (module.metamodule || module.zygisk || module.sourceName.isNotEmpty() || sourceBadge != null) {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (module.metamodule) {
                                StatusTag(
                                    "META",
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    backgroundColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (module.zygisk) {
                                StatusTag(
                                    "ZYGISK",
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            }
                            if (module.sourceName.isNotEmpty()) {
                                StatusTag(
                                    module.sourceName,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            }
                            if (sourceBadge != null) {
                                StatusTag(
                                    // 绿勾是「校验通过」的唯一记号；两态灰标没有勾，
                                    // 免得把「最新版未覆盖」也看成已核实。
                                    label = (if (sourceBadge.isVerified) "✓ " else "") +
                                        stringResource(repoSourceBadgeLabel(sourceBadge)),
                                    contentColor = if (sourceBadge.isVerified) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    backgroundColor = if (sourceBadge.isVerified) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = Dp.Hairline)
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 点赞固定在最左，左沿与上方模块名同一条基准线；0 也画，
                        // 位置固定用户才知道这里能点，空心/实心区分已赞。
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = module.moduleId.isNotEmpty()) { onToggleLike(module) }
                                .padding(vertical = 1.dp),
                        ) {
                            Icon(
                                imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (liked) R.string.module_comments_unlike else R.string.module_comments_like
                                ),
                                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = likes.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                        if (module.stargazerCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "stars",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = module.stargazerCount.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (latestReleaseTime.isNotEmpty()) {
                            Text(
                                text = latestReleaseTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * 商店页顶部的「酷安社区模块」入口：未开启时是一张带两段式确认的说明卡，开启后降级成一行状态条。
 *
 * 这个源默认关闭，而且只会在首次读列表时种一次，用户从来源管理里删掉之后不会再回来，所以
 * [source] 为 null 就整块不渲染——否则「开启」会是个永远按不动的死控件。
 */
@Composable
private fun CoolapkCommunityEntryCard(
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

    TonalCard(modifier = Modifier.fillMaxWidth()) {
        // 一行放完：渠道徽章 + 标题/说明 + 开启按钮；按钮不再独占一行，跟下面模块卡的密度对齐。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoolapkBadge()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.module_repo_coolapk_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.module_repo_coolapk_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = {
                    if (awaitingConfirm) {
                        awaitingConfirm = false
                        onSetSourceEnabled(source.id, true)
                    } else {
                        awaitingConfirm = true
                    }
                },
                // 高度给足：M3 按钮的文本行高比 34dp / 38dp 都大，压到那个高度就会把「开启」上下裁掉。
                // 用默认最小高度 40dp + 上下 10dp 内边距，靠 contentPadding 托住文本行高。
                modifier = Modifier.heightIn(min = 40.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // 按钮宽度会被左边说明挤压，这里禁止换行：文案已经压到「开启 / 确认开启」，
                // 单行放得下，不要让它折成两行顶高整张卡。
                Text(
                    text = stringResource(
                        if (awaitingConfirm) R.string.module_repo_coolapk_confirm
                        else R.string.module_repo_coolapk_enable
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * 来源筛选胶囊：「全部」加每个已开启的源，横向滚动一行。
 *
 * [options] 里的数字已经按「这次是否抓取成功」算好，这里只管选中态与点击。只有一个「全部」
 * 的时候没有筛选的意义，整行不显示。
 */
@Composable
private fun RepoSourceFilterRow(
    options: List<RepoSourceFilterOption>,
    selectedSourceId: String?,
    onSelectSource: (String?) -> Unit,
) {
    if (options.size <= 1) return
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.sourceId == selectedSourceId,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onSelectSource(option.sourceId)
                },
                label = {
                    Text(
                        text = option.chipText(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                shape = CircleShape,
                // 选中的胶囊用反色块，跟这一页其它强调块保持同一套配色。
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            )
        }
    }
}

/** 列表头：入口卡 + 筛选胶囊，作为一个 item 跟着列表滚。 */
@Composable
private fun RepoStoreHeader(
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoolapkCommunityEntryCard(source = coolapkSource, onSetSourceEnabled = onSetSourceEnabled)
        // 专栏排在筛选胶囊之前：它是内容入口，筛选是列表工具，工具不该挡在内容前面。
        ModuleColumnStrip(
            columns = columns,
            onOpenColumn = onOpenColumn,
            failed = columnsFailed,
            onRetry = onRetryColumns,
        )
        RepoSourceFilterRow(
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
            Text(text = hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
        // actionLabel 为空就是不提供出口（搜索无结果）；有出口时空态才知道该把人送去哪里。
        if (actionLabel != null) {
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * A source that could not be fetched is reported as an error notice rather than in a module-shaped
 * card, which would read as a module whose content failed to load. Sources that failed the same way
 * share one notice — the title names them, the message is stated once.
 */
@Composable
private fun SourceErrorBanner(
    keys: List<String>,
    sources: List<RepoSource>,
    message: String,
    onDismiss: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onErrorContainer
    TonalCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = sourceErrorTitle(keys, sources),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.module_repo_fetch_failed, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@SuppressLint("StringFormatInvalid", "DefaultLocale")
@Composable
fun ModuleRepoDetailScreenMaterial(
    state: ModuleRepoDetailUiState,
    actions: ModuleRepoDetailActions,
) {
    val module = state.module
    // 酷安社区的条目没有 README，也没有 GitHub 仓库/星标：详情页只留「版本」与「信息」，
    // 「信息」换成出处（帖子链接）、上传者、提交时间这组酷安字段。
    val isCoolapkModule = module.isCoolapkCommunityModule()
    val coolapkMeta = module.coolapkMeta()
    val coolapkOpenFailed = stringResource(R.string.module_repo_coolapk_open_failed)
    val context = LocalContext.current
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = module.moduleName) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    // 酷安条目的 webUrl 是从下载地址反推的，打开只是个 zip，不如不摆；
                    // 出处链接在「信息」页里，按帖子给。
                    if (!isCoolapkModule && state.webUrl.isNotEmpty()) {
                        IconButton(onClick = actions.onOpenWebUrl) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                                contentDescription = null,
                            )
                        }
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val tabReadme = stringResource(R.string.tab_readme)
        val tabReleases = stringResource(R.string.tab_releases)
        val tabInfo = stringResource(R.string.tab_info)
        // 酷安条目没有 README，这一页整个去掉；只要还有出处信息或者源码仓库证据，
        // 「信息」页就值得留——两条都没有时不摆一张空页。
        val pages = when {
            isCoolapkModule && (coolapkMeta != null || module.repoEvidence.repoUrl.isNotEmpty()) -> listOf(
                RepoDetailPageKind.RELEASES,
                RepoDetailPageKind.INFO,
                RepoDetailPageKind.COMMENTS,
            )

            isCoolapkModule -> listOf(RepoDetailPageKind.RELEASES, RepoDetailPageKind.COMMENTS)
            else -> listOf(
                RepoDetailPageKind.README,
                RepoDetailPageKind.RELEASES,
                RepoDetailPageKind.INFO,
                RepoDetailPageKind.COMMENTS,
            )
        }
        val tabComments = stringResource(R.string.module_comments_tab)
        val tabCommentsCount = stringResource(R.string.module_comments_tab_count)
        // 评论数由评论区页上报（首拉完成后才有值，-1 表示还没加载）。
        var commentCount by remember { mutableIntStateOf(-1) }
        val tabs = pages.map { kind ->
            when (kind) {
                RepoDetailPageKind.README -> tabReadme
                RepoDetailPageKind.RELEASES -> tabReleases
                RepoDetailPageKind.INFO -> tabInfo
                RepoDetailPageKind.COMMENTS ->
                    if (commentCount >= 0) tabCommentsCount.format(tabComments, commentCount) else tabComments
            }
        }
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
        val layoutDirection = LocalLayoutDirection.current
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                overscrollEffect = null,
            ) { page ->
                val paddedInnerPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 56.dp + 8.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding()
                            + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 16.dp
                )
                when (pages[page]) {
                    RepoDetailPageKind.README -> ReadmePage(
                        readmeHtml = state.readmeHtml,
                        readmeLoaded = state.readmeLoaded,
                        isMarkdown = module.isMmrl,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior
                    )

                    RepoDetailPageKind.RELEASES -> ReleasesPage(
                        detailReleases = state.detailReleases,
                        showNoArchiveHint = state.showNoArchiveHint(),
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                        confirmTitle = confirmTitle,
                        confirmDialog = confirmDialog,
                        scope = scope,
                        onInstallModule = actions.onInstallModule,
                        context = context,
                        setPendingDownload = { pendingDownload = it },
                        downloads = downloads,
                    )

                    RepoDetailPageKind.INFO -> if (isCoolapkModule) {
                        // 有仓库证据但没有酷安元数据时也走这一页：meta 收窄为可空，
                        // 页里按「有就画、没有就跳过」处理。
                        CoolapkInfoPage(
                            meta = coolapkMeta,
                            evidence = module.repoEvidence,
                            innerPadding = paddedInnerPadding,
                            scrollBehavior = scrollBehavior,
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
                        InfoPage(
                            module = module,
                            innerPadding = paddedInnerPadding,
                            scrollBehavior = scrollBehavior,
                            uriHandler = object : UriHandler {
                                override fun openUri(uri: String) = actions.onOpenUrl(uri)
                            },
                            sourceUrl = state.sourceUrl
                        )
                    }

                    RepoDetailPageKind.COMMENTS -> CommentsPage(
                        moduleId = module.moduleId,
                        initialLikeCount = module.likes,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                        onCommentCountChange = { commentCount = it },
                    )
                }
            }
            ExpressiveTabRow(
                selectedTabIndex = pagerState.currentPage,
                tabs = tabs,
                onTabClick = { scope.launch {
                    pagerState.animateScrollToPage(
                        page = it,
                        animationSpec = PagerNavigationSpringSpec,
                    )
                } },
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            )
        }
    }
}

/** 详情页的一页。酷安条目没有 README 页，页数与顺序都跟着来源变。 */
private enum class RepoDetailPageKind { README, RELEASES, INFO, COMMENTS }

/**
 * 酷安条目的「信息」页：出处（帖子链接）、上传者（昵称，有 UID 时挂主页入口）、提交时间，
 * 以及源码仓库的校验证据。
 *
 * 没有 GitHub 仓库、星标、README——索引里没有的东西整块不画，而不是画一行空占位。
 * 有仓库证据但没有酷安元数据时也走这一页，所以 [meta] 可空。
 */
@Composable
private fun CoolapkInfoPage(
    meta: CoolapkModuleMeta?,
    evidence: RepoEvidenceArg,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding(),
        ),
    ) {
        if (hasAuthor) {
            item {
                val authorItems = buildList<@Composable () -> Unit> {
                    // 名字那行始终在：没有链接也要让人知道作者是谁，而不是整块消失。
                    add {
                        SegmentedListItem(
                            headlineContent = {
                                Text(authorName ?: stringResource(R.string.module_repo_author_unknown))
                            },
                            supportingContent = {
                                Text(stringResource(R.string.module_repo_author_unverified))
                            },
                        )
                    }
                    authorCoolapkUrl?.let { url ->
                        add {
                            SegmentedListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.module_repo_author_coolapk))
                                },
                                supportingContent = { Text(text = url) },
                                trailingContent = { DetailLinkButton(onClick = { onOpenUrl(url) }) },
                            )
                        }
                    }
                    authorGithubUrl?.let { url ->
                        add {
                            SegmentedListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.module_repo_author_github))
                                },
                                supportingContent = { Text(text = url) },
                                trailingContent = { DetailLinkButton(onClick = { onOpenUrl(url) }) },
                            )
                        }
                    }
                }
                SegmentedColumn(
                    title = stringResource(R.string.module_author),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    content = authorItems,
                )
            }
        }
        if (postUrl != null) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.module_repo_coolapk_post),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    content = listOf(
                        {
                            SegmentedListItem(
                                headlineContent = { Text(text = postUrl) },
                                trailingContent = {
                                    DetailLinkButton(onClick = { onOpenPost(postUrl) })
                                },
                            )
                        }
                    ),
                )
            }
        }
        if (uploaderName != null) {
            val uploaderAction: (@Composable () -> Unit)? = uploaderUid?.let { uid ->
                { DetailLinkButton(onClick = { onOpenUser(uid) }) }
            }
            item {
                SegmentedColumn(
                    title = stringResource(R.string.module_repo_coolapk_uploader),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    content = listOf(
                        {
                            SegmentedListItem(
                                headlineContent = { Text(text = uploaderName) },
                                trailingContent = uploaderAction,
                            )
                        }
                    ),
                )
            }
        }
        if (submittedAt != null) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.module_repo_coolapk_submitted_at),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            // 索引给的是 ISO 时间字符串；若数据侧换成时间戳，这里改走 formatEpochTime。
                            SegmentedListItem(headlineContent = { Text(text = formatRepoTime(submittedAt)) })
                        }
                    ),
                )
            }
        }
        // 出处信息之后接源码仓库的校验证据：仓库、LICENSE、命中的 Release 与摘要。
        sourceEvidenceItems(evidence = evidence, onOpenUrl = onOpenUrl)
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** 信息页里的「打开链接」按钮，样式与作者、源码那几行保持一致。 */
@Composable
private fun DetailLinkButton(onClick: () -> Unit) {
    FilledTonalButton(
        modifier = Modifier.defaultMinSize(52.dp, 32.dp),
        onClick = onClick,
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Icons.Outlined.Link,
            contentDescription = null,
        )
    }
}

/**
 * 信息页的「源码校验」证据块：仓库、LICENSE、命中的 Release tag、摘要前 12 位、
 * 最近校验时间与复验结论。
 *
 * 没有仓库的条目只留一句灰字——空壳比没有更让人困惑。字段缺哪个就跳过哪一行，
 * 免得把「索引没给」画成「作者没填」。
 */
private fun LazyListScope.sourceEvidenceItems(
    evidence: RepoEvidenceArg,
    includeRepoUrl: Boolean = true,
    onOpenUrl: (String) -> Unit,
) {
    if (evidence.repoUrl.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.module_repo_no_public_source),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (includeRepoUrl) {
        item {
            EvidenceRow(
                title = R.string.module_repo_repo_url,
                value = evidence.repoUrl,
                onOpenUrl = onOpenUrl,
            )
        }
    }
    if (evidence.repoLicense.isNotEmpty()) {
        item { EvidenceRow(title = R.string.module_repo_license, value = evidence.repoLicense) }
    }
    if (evidence.matchedTag.isNotEmpty()) {
        item { EvidenceRow(title = R.string.module_repo_repo_tag, value = evidence.matchedTag) }
    }
    if (evidence.digest.isNotEmpty()) {
        item {
            EvidenceRow(
                title = R.string.module_repo_repo_digest,
                value = shortRepoDigest(evidence.digest),
            )
        }
    }
    if (evidence.repoCheckedAt > 0L) {
        item {
            EvidenceRow(
                title = R.string.module_repo_repo_checked_at,
                value = formatEpochTime(LocalContext.current, evidence.repoCheckedAt),
            )
        }
    }
    item {
        Text(
            text = stringResource(
                if (evidence.repoVerified) {
                    R.string.module_repo_repo_valid
                } else {
                    R.string.module_repo_repo_stale
                }
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (evidence.repoVerified) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 证据块的一行：标题在上、值在下，右侧可选一个打开链接的按钮。 */
@Composable
private fun EvidenceRow(
    @StringRes title: Int,
    value: String,
    onOpenUrl: ((String) -> Unit)? = null,
) {
    val action: (@Composable () -> Unit)? = onOpenUrl?.let { open ->
        { DetailLinkButton(onClick = { open(value) }) }
    }
    SegmentedColumn(
        title = stringResource(title),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        content = listOf(
            {
                SegmentedListItem(
                    headlineContent = { Text(text = value) },
                    trailingContent = action,
                )
            }
        ),
    )
}

@Composable
private fun ReadmePage(
    readmeHtml: String?,
    readmeLoaded: Boolean,
    isMarkdown: Boolean,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val layoutDirection = LocalLayoutDirection.current
    val isReady = rememberContentReady()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding(),
        ),
    ) {
        item {
            if (readmeLoaded && readmeHtml != null) {
                var loaded by remember(readmeHtml) { mutableStateOf(false) }
                val alpha by animateFloatAsState(
                    targetValue = if (loaded) 1f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "ReadmeAlpha",
                )
                Box {
                    if (isReady) {
                        Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                            GithubMarkdown(
                                content = readmeHtml,
                                isMarkdown = isMarkdown,
                                onLoadingChange = { loaded = !it },
                                containerColor = MaterialTheme.colorScheme.surface,
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = !loaded,
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
                    ) {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ReleasesPage(
    detailReleases: List<ReleaseArg>,
    showNoArchiveHint: Boolean,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    confirmTitle: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    onInstallModule: (Uri) -> Unit,
    context: Context,
    setPendingDownload: ((() -> Unit)) -> Unit,
    downloads: AssetDownloads,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = innerPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // 索引里根本没有可安装产物时，页面上原来只有一个空列表：没有安装入口，也没有一句解释。
        if (showNoArchiveHint) {
            item(key = "no_installable_archive") {
                RepoEmptyPrompt(hint = stringResource(R.string.module_repo_no_installable_archive))
            }
        }
        if (detailReleases.isNotEmpty()) {
            itemsIndexed(
                items = detailReleases,
                key = { index, _ -> "release_$index" },
                contentType = { _, _ -> "release" }
            ) { _, rel ->
                val title = remember(rel.name, rel.tagName) { rel.name.ifBlank { rel.tagName } }
                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    content = buildList<@Composable () -> Unit> {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(text = title) },
                                supportingContent = rel.tagName
                                    .takeIf { it.isNotBlank() && it != title }
                                    ?.let { tag -> { Text(text = tag) } },
                                trailingContent = rel.publishedAt
                                    .takeIf { it.isNotBlank() }
                                    ?.let { date ->
                                        { Text(text = date, style = MaterialTheme.typography.bodyMedium) }
                                    },
                            )
                        }
                        if (rel.descriptionHTML.isNotEmpty()) {
                            add {
                                SegmentedItemContainer {
                                    val descReady = rememberContentReady()
                                    var descLoaded by remember(rel.descriptionHTML) { mutableStateOf(false) }
                                    val descAlpha by animateFloatAsState(
                                        targetValue = if (descLoaded) 1f else 0f,
                                        animationSpec = tween(durationMillis = 300),
                                        label = "ReleaseDescAlpha",
                                    )
                                    val descPlaceholderAlpha by animateFloatAsState(
                                        targetValue = if (descLoaded) 0f else 1f,
                                        animationSpec = tween(durationMillis = 150),
                                        label = "ReleaseDescPlaceholderAlpha",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                                LoadingIndicator()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        rel.assets.forEach { asset ->
                            add {
                                ReleaseAssetSegmentedItem(
                                    asset = asset,
                                    confirmTitle = confirmTitle,
                                    confirmDialog = confirmDialog,
                                    scope = scope,
                                    context = context,
                                    onInstallModule = onInstallModule,
                                    setPendingDownload = setPendingDownload,
                                    downloads = downloads,
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun ReleaseAssetSegmentedItem(
    asset: ReleaseAssetArg,
    confirmTitle: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    context: Context,
    onInstallModule: (Uri) -> Unit,
    setPendingDownload: ((() -> Unit)) -> Unit,
    downloads: AssetDownloads,
) {
    val fileName = asset.name
    val sizeText = remember(asset.size) { formatFileSize(asset.size) }
    val sizeAndDownloads =
        remember(sizeText, asset.downloadCount) { "$sizeText · ${asset.downloadCount} downloads" }
    val download = downloads.of(asset.downloadUrl)
    val onClickDownload = {
        val startText = context.getString(R.string.module_start_downloading, fileName)
        setPendingDownload { downloads.start(asset) }
        confirmDialog.showConfirm(title = confirmTitle, content = startText)
    }

    SegmentedListItem(
        headlineContent = { Text(text = fileName) },
        supportingContent = { Text(text = sizeAndDownloads) },
        trailingContent = {
            val uri = download.uri
            if (uri != null) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            if (isDownloadAvailable(uri)) {
                                onInstallModule(uri)
                            } else {
                                downloads.forget(asset.downloadUrl)
                            }
                        }
                    },
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.InstallMobile,
                        contentDescription = stringResource(R.string.install)
                    )
                    Text(
                        modifier = Modifier.padding(start = 7.dp),
                        text = stringResource(R.string.install),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onClickDownload,
                    enabled = !download.isDownloading,
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    if (download.isDownloading) {
                        CircularWavyProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.download)
                        )
                        Text(
                            modifier = Modifier.padding(start = 7.dp),
                            text = stringResource(R.string.download),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun InfoPage(
    module: RepoModuleArg,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    uriHandler: UriHandler,
    sourceUrl: String,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding(),
        ),
    ) {
        if (module.sourceName.isNotEmpty()) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.module_repo_source_label),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    content = listOf(
                        {
                            SegmentedListItem(
                                headlineContent = { Text(text = module.sourceName) },
                            )
                        }
                    )
                )
            }
        }
        if (module.alternateSourceNames.isNotEmpty()) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.module_repo_source_also_in),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    content = module.alternateSourceNames.map { name ->
                        { SegmentedListItem(headlineContent = { Text(text = name) }) }
                    }
                )
            }
        }
        if (module.authorsList.isNotEmpty()) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.module_author),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    content = module.authorsList.map { author ->
                        {
                            SegmentedListItem(
                                headlineContent = {
                                    Text(
                                        text = author.name,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                                    )
                                },
                                trailingContent = {
                                    FilledTonalButton(
                                        modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                                        onClick = { uriHandler.openUri(author.link) },
                                        contentPadding = ButtonDefaults.TextButtonContentPadding
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(20.dp),
                                            imageVector = Icons.Outlined.Link,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
        if (sourceUrl.isNotEmpty()) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.module_repos_source_code),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf(
                        {
                            SegmentedListItem(
                                headlineContent = {
                                    Text(
                                        text = sourceUrl,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                                    )
                                },
                                trailingContent = {
                                    FilledTonalButton(
                                        modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                                        onClick = { uriHandler.openUri(sourceUrl) },
                                        contentPadding = ButtonDefaults.TextButtonContentPadding
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(20.dp),
                                            imageVector = Icons.Outlined.Link,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                        }
                    )
                )
            }
        }
        // 源码仓库证据；仓库地址与上面的「源码」重复时不再画一遍。
        sourceEvidenceItems(
            evidence = module.repoEvidence,
            includeRepoUrl = module.repoEvidence.repoUrl != sourceUrl,
            onOpenUrl = { uriHandler.openUri(it) },
        )
    }
}

/**
 * 详情页的评论区（Material 皮肤）：列表 + 发布框，一页一个 [ModuleCommentsViewModel]。
 *
 * 数据面：顶级评论 newest first，翻页用游标；楼中楼直接挂在顶级项下面（B 站模式，
 * 回复不再有回复，只有「@谁」）。回复目标只改占位文案，发送内容不带「@」——
 * 服务端按 parent_id / reply_to_uid 归位。
 *
 * 未登录（没有有效会话）输入区换成引导卡：去上传页验证后回来才能发言与点赞。
 * 401 同样落在这张卡上，验证完点「重试」即可，不用退出详情页。
 */
@Composable
internal fun CommentsPage(
    moduleId: String,
    /** 索引带来的初始点赞数；进页面后以 VM 的状态为准。 */
    initialLikeCount: Int,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    /** 已加载评论数变化时上报给详情页（tab 标题「评论 (N)」用）。 */
    onCommentCountChange: (Int) -> Unit = {},
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

    // 已加载条数变化（首拉/翻页/删除）就报给外层，tab 标题跟着变。
    LaunchedEffect(state.comments.size) { onCommentCountChange(state.comments.size) }

    var draft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var deleteTarget by remember { mutableStateOf<CommentItem?>(null) }
    val confirmTitle = stringResource(R.string.module_comments_delete_confirm_title)
    val confirmBody = stringResource(R.string.module_comments_delete_confirm_body)
    val confirmDialog = rememberConfirmDialog()

    // 删除走异步确认框：awaitConfirm 挂起等用户表态，确认后才真正调 VM。
    LaunchedEffect(deleteTarget) {
        if (deleteTarget != null) {
            when (confirmDialog.awaitConfirm(confirmTitle, confirmBody, destructive = true)) {
                ConfirmResult.Confirmed -> deleteTarget?.let { viewModel.delete(it.id) }
                ConfirmResult.Canceled -> Unit
            }
            deleteTarget = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                // 底部多留出发送条的高度：最后一条评论不能被输入框压住。
                bottom = innerPadding.calculateBottomPadding() + 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 头部只留条数：点赞是模块维度的热度，展示与操作都在商店列表卡片的底栏。
            item(key = "header") {
                Text(
                    text = stringResource(R.string.module_comments_count, state.comments.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            when {
                state.loading && state.comments.isEmpty() -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                }

                state.loadFailed && state.comments.isEmpty() -> {
                    item(key = "load_failed") {
                        CommentsStateCardMaterial(
                            icon = Icons.Rounded.ErrorOutline,
                            message = stringResource(R.string.module_comments_load_failed),
                            actionText = stringResource(R.string.network_retry),
                            onAction = viewModel::refresh,
                        )
                    }
                }

                state.needVerify -> {
                    item(key = "need_verify") {
                        CommentsStateCardMaterial(
                            icon = Icons.Rounded.Info,
                            message = stringResource(R.string.module_comments_login_required),
                            actionText = stringResource(R.string.module_comments_go_verify),
                            onAction = { navigator.push(Route.CommentVerify) },
                            filledAction = true,
                        )
                    }
                }

                else -> {
                    if (state.comments.isEmpty()) {
                        item(key = "empty") {
                            CommentsStateCardMaterial(
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
                        // 每条主评论收进一张圆角卡；楼中楼用左侧引导线挂在同一张卡里。
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                CommentRowMaterial(
                                    comment = comment,
                                    isReply = false,
                                    isMine = comment.uid.isNotEmpty() && comment.uid == state.myUid,
                                    onReply = { target -> replyTarget = target },
                                    onDelete = { deleteTarget = it },
                                )
                                if (comment.replies.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(top = 10.dp).height(IntrinsicSize.Min),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            comment.replies.forEach { reply ->
                                                CommentRowMaterial(
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
                    }
                    if (state.comments.isNotEmpty() && state.nextCursor.isNotEmpty()) {
                        item(key = "load_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.paging) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = viewModel::loadMore) {
                                        Text(
                                            stringResource(R.string.module_comments_load_more),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 发布条：钉在底部，回复目标变化时占位文案跟着换。
        CommentsComposerMaterial(
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
 * 一条评论（或楼中楼里的一条回复）。
 *
 * 已删除的顶级评论（content 为空串）：正文换成「已删除」占位，回复保留——
 * 服务端删顶级留回复的约定画在这里。
 */
@Composable
private fun CommentRowMaterial(
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                placeholder = MaterialTheme.colorScheme.surfaceContainerHighest,
                avatarSize = if (isReply) 22.dp else 32.dp,
                showMark = false,
            )
            Column {
                // 昵称次级色、时间更弱，把视觉重量留给正文。
                Text(
                    // 已删除评论服务端只给空昵称；不能回退到 uid，否则界面上是一串数字。
                    text = if (deleted) stringResource(R.string.module_comments_deleted_user) else comment.nickname.ifBlank { comment.uid },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatRepoTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.weight(1f))
            CommentActionTextMaterial(
                text = stringResource(R.string.module_comments_reply),
                color = MaterialTheme.colorScheme.primary,
                onClick = { onReply(comment) },
            )
            if (isMine && !deleted) {
                CommentActionTextMaterial(
                    text = stringResource(R.string.module_comments_delete),
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onDelete(comment) },
                )
            }
        }
        if (deleted) {
            Text(
                text = stringResource(R.string.module_comments_deleted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val replyPrefix = if (isReply && comment.replyToNickname.isNotBlank()) {
                "@" + comment.replyToNickname + " "
            } else ""
            Text(
                text = replyPrefix + comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 底部发布条（Material）：胶囊输入框 + 主色圆形发送钮 + 右下角字数；未登录时整条换成引导行。 */
@Composable
private fun CommentsComposerMaterial(
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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!canInteract) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.module_comments_login_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onGoVerify) {
                        Text(stringResource(R.string.module_comments_go_verify))
                    }
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    CommentActionTextMaterial(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onCancelReply,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 胶囊输入框：填充容器 + 主题色描边（聚焦时变主色），不再出现默认裸描边。
                OutlinedTextField(
                    value = draft,
                    onValueChange = { value -> if (value.length <= charLimit) onDraftChange(value) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = replyTarget?.let {
                                stringResource(R.string.module_comments_reply_hint, it.nickname.ifBlank { it.uid })
                            } ?: stringResource(R.string.module_comments_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank() && !submitting) onSend() }),
                )
                // 主色填充圆形发送钮：发送中换成小圈。
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !submitting,
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = stringResource(R.string.module_comments_send),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (submitError.isNotBlank()) {
                    Text(
                        text = submitError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = stringResource(R.string.module_comments_char_count, draft.length, charLimit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 状态卡（Material）：大图标 + 文案 + 可选操作按钮，居中摆在一张圆角卡里。 */
@Composable
private fun CommentsStateCardMaterial(
    icon: ImageVector,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    filledAction: Boolean = false,
) {
    // 与 Miuix 空态卡对齐：卡片撑满列表宽度、上下留 20dp，不额外加垂直外边距。
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (actionText != null && onAction != null) {
                    if (filledAction) {
                        Button(onClick = onAction) {
                            Text(actionText)
                        }
                    } else {
                        FilledTonalButton(onClick = onAction) {
                            Text(actionText)
                        }
                    }
                }
            }
        }
    }
}

/** 紧凑文字操作（Material）：替代 TextButton，去掉最小高度让评论卡更紧。 */
@Composable
private fun CommentActionTextMaterial(
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
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

