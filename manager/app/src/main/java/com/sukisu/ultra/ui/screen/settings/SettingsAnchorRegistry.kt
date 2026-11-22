package com.sukisu.ultra.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * 本页「已组合出来的锚点」登记表（Material 二级页用）。
 *
 * Material 二级页是 Column + verticalScroll，没有 Miuix 那样的槽位下标表，锚点指向的行在当前
 * 状态下可能根本没渲染（例如整页包在 KsuIsValid 里）。行组合时把锚点登记进来，页面级兜底
 * 再判断被点名的锚点到底有没有人认领。
 *
 * 用 CompositionLocal 承载而不是给 AnchorHighlight 加参数：三个页面加起来二十多个调用点，
 * 传参只会给每行添一条无意义的样板。
 */
internal val LocalSettingsAnchorRegistry = compositionLocalOf<MutableSet<String>> { mutableSetOf() }

/**
 * 页面级兜底：等行先登记（最多 10 帧），再检查被点名的锚点有没有人认领。
 *
 * 没人认领 = 本页当前没有这一行（条件不满足，或锚点属于别的页面）。此时直接消费高亮，
 * 否则 highlightAnchor 会一直非空：要么等条件翻转时突然亮起来，要么再也滚不动。
 */
@Composable
internal fun SettingsAnchorFallback(
    registered: MutableSet<String>,
    highlightAnchor: String?,
    onHighlightConsumed: () -> Unit,
) {
    LaunchedEffect(highlightAnchor) {
        val anchor = highlightAnchor ?: return@LaunchedEffect
        // 行在组合的 apply 阶段才登记，这里让出一帧再查才看得到它们；
        // 最多等 10 帧，容忍首帧 uiState 还没到齐（builtinZygisk 之类条件行晚一帧出现）
        var waited = 0
        while (anchor !in registered && waited < 10) {
            withFrameNanos { }
            waited++
        }
        if (anchor !in registered) onHighlightConsumed()
    }
}

/**
 * 页面级容器：为当前页提供一张锚点登记表。
 *
 * 套在分发壳（ModuleSettingsScreen 之类）的 when 外面，两套皮肤里的 AnchorHighlight 都读同一张表；
 * 这样页面级兜底不必给每行加参数，页面 body 也不用再套一层缩进。
 */
@Composable
internal fun SettingsAnchorRegistry(content: @Composable () -> Unit) {
    val registered = remember { mutableSetOf<String>() }
    CompositionLocalProvider(LocalSettingsAnchorRegistry provides registered) { content() }
}
