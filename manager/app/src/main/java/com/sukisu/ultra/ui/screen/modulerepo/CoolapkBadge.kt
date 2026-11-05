package com.sukisu.ultra.ui.screen.modulerepo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R

/**
 * 酷安渠道徽章：官方图标 + 圆角裁剪。
 *
 * 图来自酷安官网的 apple-touch-icon（`https://static.coolapk.com/images/apple-touch-icon.png`，
 * 152×152 PNG），放在 `res/drawable-nodpi/ic_coolapk.png`，按 nodpi 原样取用，
 * 由 Compose 按 [size] 缩放，不做密度换算。尺寸和圆角与商店卡的图标块一致，两套主题共用。
 */
@Composable
fun CoolapkBadge(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Image(
        painter = painterResource(R.drawable.ic_coolapk),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp)),
    )
}
