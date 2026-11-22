package com.sukisu.ultra.ui.component.background

import androidx.compose.runtime.Immutable

/** Which surface a dynamic wallpaper is applied to. */
enum class DynamicWallpaperTarget {
    /** The app-wide background behind every page. */
    APP,

    /** The home status card background. */
    CARD,
}

/** How a dynamic wallpaper is fitted into its viewport. */
enum class DynamicWallpaperFit(val value: Int) {
    /** Whole medium visible, letterboxed where the aspect ratios differ. */
    FIT(0),

    /** Medium covers the viewport, cropped on the overflowing axis. */
    FILL(1),

    /** Cover plus a user zoom/pan, stored separately. */
    CUSTOM(2);

    val jsName: String
        get() = when (this) {
            FIT -> "fit"
            FILL -> "fill"
            CUSTOM -> "custom"
        }

    companion object {
        fun fromValue(value: Int): DynamicWallpaperFit =
            entries.firstOrNull { it.value == value } ?: FILL
    }
}

/**
 * Saved geometry for one dynamic wallpaper surface.
 *
 * [zoom] multiplies the cover scale (1 = cover) and only applies to [DynamicWallpaperFit.CUSTOM].
 * [offsetX]/[offsetY] are normalized -1..1 where ±1 pans the medium to the edge of the viewport;
 * they are ignored by the other modes.
 */
@Immutable
data class DynamicWallpaperTransform(
    val fit: DynamicWallpaperFit = DynamicWallpaperFit.FILL,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun sanitized(): DynamicWallpaperTransform = if (fit == DynamicWallpaperFit.CUSTOM) {
        copy(
            zoom = zoom.coerceIn(ZOOM_MIN, ZOOM_MAX),
            offsetX = offsetX.coerceIn(-1f, 1f),
            offsetY = offsetY.coerceIn(-1f, 1f),
        )
    } else {
        copy(zoom = 1f, offsetX = 0f, offsetY = 0f)
    }

    companion object {
        const val ZOOM_MIN = 1f
        const val ZOOM_MAX = 4f

        val Default = DynamicWallpaperTransform()
    }
}
