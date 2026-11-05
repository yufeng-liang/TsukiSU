package com.sukisu.ultra.ui.screen.modulerepo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 酷安品牌绿。 */
private val CoolapkGreen = Color(0xFF11B37E)

/**
 * 酷安渠道徽章：绿色圆角方块 + 白色「酷」字。
 *
 * 没有引入官方 logo 位图（仓库里没有这份资源，也不便随便塞第三方商标图），
 * 用绿底白字做渠道标识。尺寸和圆角与商店卡的图标块一致，两套主题共用。
 */
@Composable
fun CoolapkBadge(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(CoolapkGreen),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "酷",
            color = Color.White,
            fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
