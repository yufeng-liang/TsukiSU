package com.sukisu.ultra.ai.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * 首页 AI 入口卡片在窗口中的几何信息（归一化到 0..1）。
 *
 * 卡片每帧通过 onGloballyPositioned 写入，导航过渡（Container Transform）读取它，
 * 把目标页面从卡片的矩形缩放展开，返回时再缩回卡片。
 *
 * 存归一化比例而不是像素，避免 Compose 坐标与 NavDisplay 的 layoutSize 在
 * 分辨率覆盖（wm size）下单位不一致。
 */
object AiHomeCardBounds {
    var centerX: Float by mutableStateOf(0.5f)
        private set
    var centerY: Float by mutableStateOf(0.5f)
        private set
    var width: Float by mutableStateOf(1f)
        private set
    var height: Float by mutableStateOf(1f)
        private set
    var valid: Boolean by mutableStateOf(false)
        private set

    fun update(rect: Rect, rootWidth: Float, rootHeight: Float) {
        if (rootWidth <= 0f || rootHeight <= 0f) return
        centerX = rect.center.x / rootWidth
        centerY = rect.center.y / rootHeight
        width = rect.width / rootWidth
        height = rect.height / rootHeight
        valid = true
    }

    fun clear() {
        valid = false
    }
}
