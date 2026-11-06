package com.sukisu.ultra.ui.util

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.sukisu.ultra.ai.api.AiHomeCardBounds
import kotlinx.coroutines.launch

/** 卡片到页面的容器变换时长上限（毫秒）。 */
private const val ENTER_STIFFNESS = 180f
private const val EXIT_STIFFNESS = 240f

/**
 * AI 聊天页的容器变换（Container Transform）。
 *
 * 不依赖 nav 的过渡：聊天页路由使用 [androidx.compose.animation.NavTransitions.None] 瞬时切换，
 * 由这里把整页从首页卡片的矩形“长”出来、收起时再缩回去。做法是一个可动画的裁剪面板：
 * 面板的 offset/size/圆角/底色从卡片矩形插值到全屏，页面内容按最终尺寸布局后等比缩放到面板
 * 尺寸（Crop 语义，绝不非等比拉伸），所以既不会把文字压扁，也不会出现“卡片和页面是两个东西”。
 */
@Composable
fun AiChatContainer(
    backEnabled: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    chat: @Composable (requestClose: () -> Unit) -> Unit,
) {
    val card = AiHomeCardBounds
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val pageBackground = MaterialTheme.colorScheme.background
    val cardBackground = MaterialTheme.colorScheme.surfaceContainerHigh

    LaunchedEffect(Unit) {
        // 先让首页卡片那一帧真正上屏，避免冷启动时面板起点算在旧的 bounds 上。
        withFrameNanos { }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = ENTER_STIFFNESS),
        )
    }

    val requestClose: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = EXIT_STIFFNESS),
                )
                onClose()
            }
        }
    }

    BackHandler(enabled = backEnabled, onBack = requestClose)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullWidth = maxWidth
        val fullHeight = maxHeight
        val p = progress.value.coerceIn(0f, 1f)

        val cardWidth = fullWidth * card.width
        val cardHeight = fullHeight * card.height
        val cardLeft = fullWidth * (card.centerX - card.width / 2f)
        val cardTop = fullHeight * (card.centerY - card.height / 2f)

        val panelWidth = lerp(cardWidth, fullWidth, p)
        val panelHeight = lerp(cardHeight, fullHeight, p)
        val panelLeft = lerp(cardLeft, 0.dp, p)
        val panelTop = lerp(cardTop, 0.dp, p)
        val corner = lerp(28.dp, 0.dp, p)
        val containerColor = lerp(cardBackground, pageBackground, p)
        // 面板刚起步时让位给首页那张真实卡片（它就在下面）：只让面板随展开淡入，
        // 这样不会出现“卡片旁边又多出一块颜色不一样的板”。
        val panelAlpha = (p / 0.3f).coerceIn(0f, 1f)
        // 内容晚一点出现：前段让面板像卡片一样“长开”，后段才露出页面内容。
        val contentAlpha = ((p - 0.18f) / 0.42f).coerceIn(0f, 1f)
        // Crop 语义：等比放大到刚好盖住面板，多出来的部分被裁剪，避免任何非等比拉伸。
        val contentScale = maxOf(
            panelWidth.value / fullWidth.value,
            panelHeight.value / fullHeight.value,
        ).coerceAtMost(1f)

        Box(
            modifier = Modifier
                .offset(x = panelLeft, y = panelTop)
                .size(width = panelWidth, height = panelHeight)
                .clip(RoundedCornerShape(corner))
                .graphicsLayer { alpha = panelAlpha }
                .background(containerColor),
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(fullWidth, fullHeight)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = contentScale
                        scaleY = contentScale
                        alpha = contentAlpha
                    },
            ) {
                chat(requestClose)
            }
        }
    }
}
