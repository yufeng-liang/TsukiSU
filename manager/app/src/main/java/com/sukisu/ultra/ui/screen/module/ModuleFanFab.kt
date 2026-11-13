package com.sukisu.ultra.ui.screen.module

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 模块页的扇形悬浮按钮（Material 与 Miuix 两套界面共用这一份实现）。
 *
 * 整块浮层铺满屏幕：最底下是全屏遮罩，上面是两枚动作和悬浮按钮。它必须挂在页面的最外层
 * （包住整个 Scaffold），否则遮罩只能盖住内容区，顶部标题栏和底部导航栏露在外面会很割裂。
 *
 * 角度口径：0° 指向正右，90° 向上，180° 指向正左。两枚动作的**正中心**钉在同一个圆弧上——
 * 把胶囊右边缘贴按钮的话，靠上的那一枚右边会空出一块，像扇子缺了角。
 *
 * 时序：点动作先播收起动画，收回之后再真正跳转/弹选择器，否则页面切换会把动画掐断，
 * 返回时还会因为残留状态抖一下。
 */
private const val FAB_HALF_DP = 28f // 悬浮按钮 56dp 的一半，作为偏移基准

@Composable
fun ModuleFanFabHost(
    expanded: Boolean,
    installLabel: String,
    uploadLabel: String,
    installAngle: Float,
    uploadAngle: Float,
    radius: Dp,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onUpload: () -> Unit,
    bottomPadding: Dp,
    endPadding: Dp,
    itemContainerColor: Color,
    itemContentColor: Color,
    iconTint: Color,
    fabContainerColor: Color,
    fabContentColor: Color,
    fabIcon: ImageVector,
    installIcon: ImageVector,
    uploadIcon: ImageVector,
) {
    // 收起动画播完再执行动作，避免「跳转把动画掐断、返回时页面抖一下」。
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(expanded, pending) {
        val action = pending
        if (!expanded && action != null) {
            delay(200)
            pending = null
            action()
        }
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.32f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "module-fan-scrim",
    )
    val progress = updateTransition(targetState = expanded, label = "module-fan")

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩始终存在（只改透明度），避免中途销毁重建导致返回时闪一下。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .then(
                    if (expanded) {
                        Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } }
                    } else {
                        Modifier
                    }
                )
        )
        // 这一层要铺满整屏：Compose 只在自己节点的边界内做命中测试，若父容器只有按钮那么大，
        // 画到外面的胶囊虽然看得见却点不到（这正是之前点「投稿模块」没反应的原因）。
        Box(modifier = Modifier.fillMaxSize()) {
            FanAction(
                progress = progress,
                index = 0,
                count = 2,
                angle = installAngle,
                radius = radius,
                endPadding = endPadding,
                bottomPadding = bottomPadding,
                label = installLabel,
                icon = installIcon,
                onClick = {
                    pending = onInstall
                    onDismiss()
                },
                containerColor = itemContainerColor,
                contentColor = itemContentColor,
                iconTint = iconTint,
            )
            FanAction(
                progress = progress,
                index = 1,
                count = 2,
                angle = uploadAngle,
                radius = radius,
                endPadding = endPadding,
                bottomPadding = bottomPadding,
                label = uploadLabel,
                icon = uploadIcon,
                onClick = {
                    pending = onUpload
                    onDismiss()
                },
                containerColor = itemContainerColor,
                contentColor = itemContentColor,
                iconTint = iconTint,
            )
            // 单击 = 展开/收起扇形菜单；长按 = 跳过菜单直接进刷入流程（弹 ZIP 选择器）。
            // Surface 的 onClick 版本不支持长按，所以这里用无点击的 Surface 承载外观，
            // 交互交给里层 Box 的 combinedClickable（自带涟漪）。
            val haptics = LocalHapticFeedback.current
            Surface(
                shape = CircleShape,
                color = fabContainerColor,
                contentColor = fabContentColor,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endPadding, bottom = bottomPadding)
                    .size(56.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = onToggle,
                            onLongClick = {
                                // 长按直达刷入：先给一次触感反馈，再收起菜单（若开着）并直接调起选择器。
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                pending = null
                                onDismiss()
                                onInstall()
                            },
                        ),
                ) {
                    Icon(
                        imageVector = fabIcon,
                        contentDescription = installLabel,
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(rememberFanIconRotation(expanded)),
                    )
                }
            }
        }
    }
}

/** 加号 → 叉号的旋转角，交给调用方贴在图标上。 */
@Composable
fun rememberFanIconRotation(expanded: Boolean): Float {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
        label = "module-fan-icon",
    )
    return rotation
}

@Composable
private fun FanAction(
    progress: Transition<Boolean>,
    index: Int,
    count: Int,
    angle: Float,
    radius: Dp,
    endPadding: Dp,
    bottomPadding: Dp,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    iconTint: Color,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val t by progress.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(
                    durationMillis = 260,
                    delayMillis = index * 40,
                    easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.12f),
                )
            } else {
                // 收起时反序：离按钮最远的那一枚先收回，视觉上像「卷起来」。
                tween(
                    durationMillis = 150,
                    delayMillis = (count - 1 - index) * 40,
                    easing = FastOutLinearInEasing,
                )
            }
        },
        label = "module-fan-item-$index",
    ) { open -> if (open) 1f else 0f }

    val radians = Math.toRadians(angle.toDouble())
    val radiusPx = with(density) { radius.toPx() }
    val dx = (radiusPx * cos(radians)).toFloat()
    val dy = (radiusPx * sin(radians)).toFloat()
    val anchorPx = with(density) { FAB_HALF_DP.dp.toPx() }
    val endPx = with(density) { endPadding.toPx() }
    val bottomPx = with(density) { bottomPadding.toPx() }
    // 目标位置是「胶囊中心落在圆弧上」，所以扣掉自身一半宽高；原点取悬浮按钮中心
    //（按钮中心 = 屏幕右下角再减掉外边距和按钮半径）。
    val targetX = dx + size.width / 2f - anchorPx - endPx
    val targetY = size.height / 2f - anchorPx - bottomPx - dy

    Box(modifier = Modifier.fillMaxSize()) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 5.dp,
        modifier = Modifier
            // 必须固定在右下角，否则在铺满全屏的父容器里会从左上角起算偏移，胶囊直接飞到屏幕外。
            .align(Alignment.BottomEnd)
            .height(52.dp)
            .onSizeChanged { size = it }
            .offset {
                IntOffset((targetX * t).roundToInt(), (targetY * t).roundToInt())
            }
            .graphicsLayer {
                alpha = t
                val scale = 0.88f + 0.12f * t
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 18.dp, end = 10.dp),
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 12.dp),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    }
}
