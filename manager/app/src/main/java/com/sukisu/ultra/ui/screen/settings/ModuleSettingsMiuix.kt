package com.sukisu.ultra.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.ModuleContributionPrefs
import com.sukisu.ultra.ui.component.miuix.AppScaffold
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.theme.LocalEnableBlur
import com.sukisu.ultra.ui.theme.backgroundAwareColor
import com.sukisu.ultra.ui.util.BlurredBar
import com.sukisu.ultra.ui.util.CustomTextKey
import com.sukisu.ultra.ui.util.customText
import com.sukisu.ultra.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// ---------------------------------------------------------------------------
// 三个二级页（模块设置 / Root 与安全 / 维护）共用的列表骨架。
// 之所以放在这个文件：它是第一批落地的页面之一，其余两页直接用同包 internal。
// ---------------------------------------------------------------------------

/**
 * 分段位置：同一张卡的相邻行拼起来，只让首段 / 末段带圆角。
 *
 * LazyColumn 里不能把整卡塞进一个 item（AGENTS 第 9 条），所以卡片观感靠这个位置拼：
 * 首段圆上角、末段圆下角、单行四角全圆、中间段方角。
 */
internal enum class MiuixSettingsSegmentPosition { Single, Top, Middle, Bottom }

/** 列表里的一项：可跳转的行（带锚点），或者一句小节标题。 */
internal class MiuixSettingsSlot(
    val key: String,
    val anchor: String? = null,
    val headerRes: Int? = null,
    val position: MiuixSettingsSegmentPosition = MiuixSettingsSegmentPosition.Middle,
    /** 行体自己就是一张卡（例如临时 Root 偏好块），不能再套分角背景。 */
    val selfCard: Boolean = false,
    val row: (@Composable () -> Unit)? = null,
)

/**
 * 追加一张卡：行序即 item 顺序，分段位置在这里算好，页面里不用手写 Top / Bottom。
 *
 * @param rows 卡片里的行，元素是「锚点 to 行体」。
 * @param extra 有条件才出现的末行；vararg 拼不出条件行，所以单独留一个入口。
 */
internal fun MutableList<MiuixSettingsSlot>.miuixSettingsGroup(
    vararg rows: Pair<String, @Composable () -> Unit>,
    extra: Pair<String, @Composable () -> Unit>? = null,
) {
    val all = if (extra == null) rows.toList() else rows.toList() + extra
    all.forEachIndexed { index, (anchor, content) ->
        val position = when {
            all.size == 1 -> MiuixSettingsSegmentPosition.Single
            index == 0 -> MiuixSettingsSegmentPosition.Top
            index == all.lastIndex -> MiuixSettingsSegmentPosition.Bottom
            else -> MiuixSettingsSegmentPosition.Middle
        }
        add(MiuixSettingsSlot(key = anchor, anchor = anchor, position = position, row = content))
    }
}

/** 追加一行自带卡片的设置：行体自己画 AppCard，高亮只能改成描边。 */
internal fun MutableList<MiuixSettingsSlot>.miuixSettingsSelfCardGroup(
    anchor: String,
    row: @Composable () -> Unit,
) {
    add(
        MiuixSettingsSlot(
            key = anchor,
            anchor = anchor,
            position = MiuixSettingsSegmentPosition.Single,
            selfCard = true,
            row = row,
        ),
    )
}

/** 追加一句小节标题：SmallTitle 自带上下留白，这里不再补 Spacer。 */
internal fun MutableList<MiuixSettingsSlot>.miuixSettingsHeader(
    @StringRes titleRes: Int,
) {
    add(MiuixSettingsSlot(key = "header-" + titleRes, headerRes = titleRes))
}

/**
 * 行首图标：miuix 的 Icon 组件，和 SettingPagerMiuix 一样留 6.dp 行距。
 *
 * 图标沿用的是 Material icons：仓库现有的 MiuixIcons 里没有本页这些语义
 * （memory / developer mode / security / policy / adb / verified user …），
 * 换图标只改这里的调用点，不影响布局。禁用态由调用方传 tint。
 */
@Composable
internal fun MiuixSettingsRowIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = colorScheme.onBackground,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.padding(end = 6.dp),
    )
}

/**
 * 一行的分角背景。
 *
 * 高亮沿用 Material 版的配色方案：命中时整段换成 secondaryContainer，220ms 渐变。
 * 形状用 RoundedCornerShape 分角（圆角取卡片默认 16.dp）：AGENTS 第 2 条本来要求 squircle，
 * 但这套 miuix（0.9.4）没有可分角的 squircle 重载可核实，换回 squircle 只需要改这一个函数。
 */
@Composable
internal fun MiuixSettingsSegment(
    position: MiuixSettingsSegmentPosition,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = backgroundAwareColor(
            if (highlighted) colorScheme.secondaryContainer else colorScheme.surfaceContainer,
        ),
        animationSpec = tween(durationMillis = 220),
        label = "miuixSettingsRowContainer",
    )
    val corner = CardDefaults.CornerRadius
    val shape = when (position) {
        MiuixSettingsSegmentPosition.Single -> RoundedCornerShape(corner)
        MiuixSettingsSegmentPosition.Top -> RoundedCornerShape(topStart = corner, topEnd = corner)
        MiuixSettingsSegmentPosition.Bottom -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        MiuixSettingsSegmentPosition.Middle -> RoundedCornerShape(0.dp)
    }
    // 水平 12.dp 是卡片到屏幕边缘的距离；只有整张卡的末段才补 12.dp 底边距，
    // 组内相邻行必须严丝合缝，否则一张卡会被切成好几张漂浮小卡
    val outerSpacing = if (
        position == MiuixSettingsSegmentPosition.Bottom || position == MiuixSettingsSegmentPosition.Single
    ) {
        Modifier.padding(bottom = 12.dp)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(outerSpacing)
            // 先裁再铺底色：段内的点按涟漪也必须被裁进圆角，不然会从方角溢出来
            .clip(shape)
            .background(color = container, shape = shape),
    ) {
        content()
    }
}

/**
 * 一行可跳转设置。
 *
 * requester 必须在这个 item 自己的组合里创建：LazyColumn 是惰性组合的，
 * 目标行还没被组合过时页面级滚动找不到 requester，只能先按已知下标滚过去。
 */
@Composable
internal fun MiuixSettingsRow(
    anchor: String,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
    position: MiuixSettingsSegmentPosition,
    content: @Composable () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    val targeted = highlightAnchor == anchor
    var highlighted by remember { mutableStateOf(false) }

    LaunchedEffect(targeted, anchor) {
        if (!targeted) {
            // 高亮被别的行抢走或已消费：别留一盏不会熄的灯
            highlighted = false
            return@LaunchedEffect
        }
        requester.bringIntoView()
        highlighted = true
        // 高亮固定 2 秒后自动消失，和 Material 版节奏一致
        delay(2_000)
        highlighted = false
        onHighlightConsumed()
    }

    MiuixSettingsSegment(
        position = position,
        highlighted = highlighted,
        modifier = Modifier.bringIntoViewRequester(requester),
    ) {
        content()
    }
}

/**
 * 自带卡片的行（临时 Root 偏好块自己就是一张 AppCard）。
 *
 * 这类行改不了行体底色，所以高亮改成描边，和 Material 版是同一个做法。
 */
@Composable
internal fun MiuixSettingsSelfCardRow(
    anchor: String,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
    content: @Composable () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    val targeted = highlightAnchor == anchor
    var highlighted by remember { mutableStateOf(false) }

    LaunchedEffect(targeted, anchor) {
        if (!targeted) {
            highlighted = false
            return@LaunchedEffect
        }
        requester.bringIntoView()
        highlighted = true
        delay(2_000)
        highlighted = false
        onHighlightConsumed()
    }

    val borderColor by animateColorAsState(
        targetValue = if (highlighted) colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "miuixSettingsSelfCardHighlight",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            // 自带卡片的行没有段落容器替它收尾，这里补上卡片之间的 12 dp 间距
            .padding(bottom = 12.dp)
            .bringIntoViewRequester(requester),
    ) {
        content()
        // 行体顶部自带 12.dp 间距，描边要贴着卡片本身画，不然会框到空白处
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 12.dp, bottom = 12.dp)
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(CardDefaults.CornerRadius),
                ),
        )
    }
}

/**
 * 页面级跳转：先按已知槽位下标滚过去，行自己的 requester 再把行体精确带进视野。
 *
 * 目标行在当前状态下不存在（例如内置 Zygisk 面板被关掉）时直接消费掉高亮，避免反复重试。
 */
@Composable
internal fun MiuixSettingsHighlightScroll(
    listState: LazyListState,
    slots: List<MiuixSettingsSlot>,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
) {
    LaunchedEffect(highlightAnchor) {
        val anchor = highlightAnchor ?: return@LaunchedEffect
        val index = slots.indexOfFirst { it.anchor == anchor }
        if (index < 0) onHighlightConsumed() else listState.scrollToItem(index)
    }
}

/** 把槽位表铺进 LazyColumn：小节标题走 SmallTitle，其余每行单独一个 item。 */
internal fun LazyListScope.miuixSettingsSlots(
    slots: List<MiuixSettingsSlot>,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
) {
    itemsIndexed(items = slots, key = { _, slot -> slot.key }) { _, slot ->
        val headerRes = slot.headerRes
        val anchor = slot.anchor
        val row = slot.row
        when {
            headerRes != null -> SmallTitle(text = stringResource(headerRes))
            anchor != null && row != null && slot.selfCard -> MiuixSettingsSelfCardRow(
                anchor = anchor,
                highlightAnchor = highlightAnchor,
                onHighlightConsumed = onHighlightConsumed,
                content = row,
            )
            anchor != null && row != null -> MiuixSettingsRow(
                anchor = anchor,
                highlightAnchor = highlightAnchor,
                onHighlightConsumed = onHighlightConsumed,
                position = slot.position,
                content = row,
            )
        }
    }
}

/**
 * 模块设置（Miuix 皮肤）。
 *
 * 行内容与 Material 版一一对应（8 行）：模块投稿两行不依赖 root，
 * 其余行只在管理器身份下出现（等价于 Material 版的 KsuIsValid）。
 */
@Composable
internal fun ModuleSettingsMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    onBack: () -> Unit,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val hasCustomBackground = LocalBackgroundImagePath.current.isNotEmpty()
    // 有模糊背景或自定义背景时顶栏必须透明，否则会在背景图上切出一条硬边
    // 设备若不支持实时模糊，透明顶栏会把滚动内容露在标题底下；
    // 因此只在有自定义背景图（确实期望透明）时才透明，其余用不透明 surface
    val barColor = if (hasCustomBackground) Color.Transparent else colorScheme.surface
    val listState = rememberLazyListState()
    // KsuValidCheck 的判据就是 Natives.isManager（version 是 Int，判空恒真），这里只需要布尔值
    val ksuReady = remember { Natives.isManager }

    // 行序即 item 下标：页面级跳转按锚点反查下标，所以生成顺序必须稳定
    val slots: List<MiuixSettingsSlot> = buildList {
        // 模块投稿不依赖 root：没有管理器身份也能看、也能关
        if (!ModuleContributionPrefs.SUSPENDED) {
            miuixSettingsGroup(

                SettingsSection.MODULE_SHARE to {
                    val title = stringResource(R.string.module_upload_after_install_title)
                    SwitchPreference(
                        title = title,
                        summary = stringResource(R.string.module_upload_after_install_summary),
                        startAction = { MiuixSettingsRowIcon(Icons.Rounded.Share, title) },
                        checked = uiState.moduleContributionEnabled,
                        onCheckedChange = actions.onSetModuleContributionEnabled,
                    )
                },
            )
        }
        if (ksuReady) {
            miuixSettingsGroup(
                SettingsSection.PROFILE_TEMPLATES to {
                    val title = customText(CustomTextKey.PAGE_TEMPLATE)
                    ArrowPreference(
                        title = title,
                        summary = stringResource(R.string.settings_profile_template_summary),
                        startAction = { MiuixSettingsRowIcon(Icons.Rounded.Description, title) },
                        onClick = actions.onOpenProfileTemplate,
                    )
                },
            )
            miuixSettingsGroup(
                SettingsSection.EXTENSIONS to {
                    val title = customText(CustomTextKey.PAGE_EXTENSIONS)
                    ArrowPreference(
                        title = title,
                        summary = stringResource(R.string.extension_section_summary),
                        startAction = { MiuixSettingsRowIcon(Icons.Rounded.Memory, title) },
                        onClick = actions.onOpenExtensions,
                    )
                },
            )
            miuixSettingsGroup(
                SettingsSection.TOOLS to {
                    val title = customText(CustomTextKey.PAGE_TOOLS)
                    ArrowPreference(
                        title = title,
                        summary = stringResource(R.string.settings_tools_summary),
                        startAction = { MiuixSettingsRowIcon(Icons.Rounded.DeveloperMode, title) },
                        onClick = { actions.onOpenTools() },
                    )
                },
            )
            miuixSettingsGroup(
                SettingsSection.DEFAULT_UMOUNT_MODULES to {
                    val title = customText(CustomTextKey.SETTING_UMOUNT_MODULES_DEFAULT)
                    SwitchPreference(
                        title = title,
                        summary = stringResource(R.string.settings_umount_modules_default_summary),
                        startAction = { MiuixSettingsRowIcon(Icons.AutoMirrored.Rounded.Rule, title) },
                        checked = uiState.isDefaultUmountModules,
                        onCheckedChange = actions.onSetDefaultUmountModules,
                    )
                },

            )
        }
    }

    // 先把列表按已知下标滚过去，行自己的 requester 再把行体精确带进视野
    MiuixSettingsHighlightScroll(listState, slots, highlightAnchor, onHighlightConsumed)

    AppScaffold(
        containerColor = if (hasCustomBackground) Color.Transparent else colorScheme.surface,
        topBar = {
            BlurredBar(backdrop = backdrop, scrollBehavior = scrollBehavior) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings_group_module),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    // miuix 的返回箭头在 RTL 下要镜像，跟其它页面保持一致
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                // contentPadding 只设顶部：底部留白靠末尾 Spacer + 导航栏内边距
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 12.dp),
                overscrollEffect = null,
            ) {
                miuixSettingsSlots(slots, highlightAnchor, onHighlightConsumed)
                item(key = "bottom-spacer") {
                    Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
                }
            }
        }
    }
}
