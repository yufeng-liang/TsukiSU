package com.sukisu.ultra.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableProgressiveBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val topBarStyle: Int = 0,
    val topBarPillFade: Boolean = true,
    val enableNavigationBadge: Boolean,
    val enableSwipeDismiss: Boolean,
    val pagerInterceptionMode: Int,
    val moduleDescriptionMaxLines: Int = 4,
    
    val customBackgroundUri: String = "",
    val customBackgroundUpdatedAt: Long = 0L,
    val dynamicWallpaperPath: String = "",
    val dynamicWallpaperUpdatedAt: Long = 0L,
    val dynamicWallpaperMuted: Boolean = true,
    val dynamicWallpaperFit: Int = 1,
    val dynamicWallpaperZoom: Float = 1f,
    val dynamicWallpaperOffsetX: Float = 0f,
    val dynamicWallpaperOffsetY: Float = 0f,
    val dynamicWallpaperFps: Int = 0,
    val dynamicWallpaperSpeed: Float = 1f,
    val cardBackgroundAlphaPercent: Int = 60,
    val customBackgroundBlurPercent: Int = 50,
    val customBackgroundScrimPercent: Int = -35,
    
    val keyColorOverride: Int? = null,
    val uiMode: UiMode,
    val customTexts: Map<String, String> = emptyMap(),
)
