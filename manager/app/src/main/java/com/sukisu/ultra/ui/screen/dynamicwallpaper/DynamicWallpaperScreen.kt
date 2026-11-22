package com.sukisu.ultra.ui.screen.dynamicwallpaper

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.background.DynamicWallpaperBackground
import com.sukisu.ultra.ui.component.background.DynamicWallpaperFit
import com.sukisu.ultra.ui.component.background.DynamicWallpaperTarget
import com.sukisu.ultra.ui.component.background.DynamicWallpaperTransform
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.theme.LocalBackgroundImagePath
import com.sukisu.ultra.ui.util.HomeLayout
import com.sukisu.ultra.ui.util.HomeStatusStyle
import com.sukisu.ultra.ui.util.ImageStorage
import com.sukisu.ultra.ui.viewmodel.SettingsViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class DynamicWallpaperPreviewUiState(
    val previewPath: String? = null,
    val previewUpdatedAt: Long = 0L,
    val muted: Boolean = true,
    val kind: ImageStorage.DynamicWallpaperKind? = null,
    val sizeBytes: Long = 0L,
    val hasStaged: Boolean = false,
    val hasSaved: Boolean = false,
    val busy: Boolean = false,
    val rendererFailed: Boolean = false,
    val rendererError: String = "",
    val showRemoveConfirm: Boolean = false,
    val transform: DynamicWallpaperTransform = DynamicWallpaperTransform.Default,
    val fps: Int = 0,
    val speed: Float = 1f,
    val isCard: Boolean = false,
    val cardLayout: HomeLayout = HomeLayout.CLASSIC,
    val cardWidthFraction: Float = HomeStatusStyle.WIDTH_DEFAULT,
    val cardHeightScale: Float = HomeStatusStyle.SCALE_DEFAULT,
)

@Immutable
data class DynamicWallpaperPreviewActions(
    val onBack: () -> Unit = {},
    val onPick: () -> Unit = {},
    val onApply: () -> Unit = {},
    val onDiscardStaged: () -> Unit = {},
    val onRequestRemove: () -> Unit = {},
    val onDismissRemove: () -> Unit = {},
    val onConfirmRemove: () -> Unit = {},
    val onSetMuted: (Boolean) -> Unit = {},
    val onRetryRenderer: () -> Unit = {},
    val onRendererError: (String) -> Unit = {},
    val onTransformChange: (DynamicWallpaperTransform) -> Unit = {},
    val onSelectFit: (DynamicWallpaperFit) -> Unit = {},
    val onSetFps: (Int) -> Unit = {},
    val onSetSpeed: (Float) -> Unit = {},
)

@Composable
fun DynamicWallpaperScreen(card: Boolean = false) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SettingsViewModel>()
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val target = if (card) DynamicWallpaperTarget.CARD else DynamicWallpaperTarget.APP

    val savedPath = (if (card) settings.dynamicHomeCardPath else settings.dynamicWallpaperPath)
        .takeIf { path -> path.isNotBlank() && File(path).isFile }
    val savedUpdatedAt = if (card) {
        settings.dynamicHomeCardUpdatedAt
    } else {
        settings.dynamicWallpaperUpdatedAt
    }
    val savedTransform = if (card) {
        DynamicWallpaperTransform(
            fit = DynamicWallpaperFit.fromValue(settings.dynamicHomeCardFit),
            zoom = settings.dynamicHomeCardZoom,
            offsetX = settings.dynamicHomeCardOffsetX,
            offsetY = settings.dynamicHomeCardOffsetY,
        )
    } else {
        DynamicWallpaperTransform(
            fit = DynamicWallpaperFit.fromValue(settings.dynamicWallpaperFit),
            zoom = settings.dynamicWallpaperZoom,
            offsetX = settings.dynamicWallpaperOffsetX,
            offsetY = settings.dynamicWallpaperOffsetY,
        )
    }.sanitized()

    var stagedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rendererFailed by remember { mutableStateOf(false) }
    var rendererError by remember { mutableStateOf("") }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var transform by remember(target) { mutableStateOf(savedTransform) }

    // A process death can restore the path after the cache was cleared.
    LaunchedEffect(stagedPath) {
        val staged = stagedPath
        if (staged != null && !File(staged).isFile) stagedPath = null
    }

    // The cache can be trimmed while this screen is open; a staged path without a file must
    // fall back to the applied wallpaper and must not block picking another one.
    val stagedFile = stagedPath?.let(::File)?.takeIf { it.isFile }
    val previewFile = stagedFile ?: savedPath?.let(::File)?.takeIf { it.isFile }
    val previewPath = previewFile?.absolutePath
    val stagedActive = stagedFile != null && stagedPath == stagedFile.absolutePath
    val kind = previewFile?.let { ImageStorage.dynamicWallpaperKind(it.absolutePath) }
    val previewUpdatedAt = if (stagedActive) {
        previewFile?.lastModified() ?: 0L
    } else {
        savedUpdatedAt
    }

    // Copying or promoting a large file must finish before the screen can disappear.
    BackHandler(enabled = busy) { }
    BackHandler(enabled = !busy) {
        viewModel.setDynamicWallpaperTransform(target, transform)
        navigator.pop()
    }

    LaunchedEffect(previewPath, previewUpdatedAt) {
        rendererFailed = false
        rendererError = ""
    }
    // Pinch/pan changes are cheap in memory; persist them once the gesture settles.
    LaunchedEffect(transform) {
        if (transform != savedTransform) {
            delay(300.milliseconds)
            viewModel.setDynamicWallpaperTransform(target, transform)
        }
    }
    // Backing out of the preview never touches the applied wallpaper, but the staged copy is
    // no longer needed and can be hundreds of megabytes.
    DisposableEffect(stagedPath) {
        val staged = stagedPath
        onDispose { ImageStorage.discardStagedDynamicWallpaper(staged) }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        viewModel.stageDynamicWallpaper(context, uri) { result ->
            busy = false
            when (result) {
                is ImageStorage.DynamicWallpaperResult.Ready -> {
                    stagedPath = result.path
                    rendererFailed = false
                    rendererError = ""
                }

                ImageStorage.DynamicWallpaperResult.TooLarge -> {
                    // Staging clears the cache before it copies, so the previous preview is
                    // already gone; drop the path so the applied wallpaper shows through.
                    stagedPath = null
                    rendererFailed = false
                    rendererError = ""
                    Toast.makeText(
                        context,
                        R.string.dynamic_wallpaper_too_large,
                        Toast.LENGTH_LONG,
                    ).show()
                }

                ImageStorage.DynamicWallpaperResult.UnsupportedFormat -> {
                    stagedPath = null
                    rendererFailed = false
                    rendererError = ""
                    Toast.makeText(
                        context,
                        R.string.dynamic_wallpaper_unsupported,
                        Toast.LENGTH_LONG,
                    ).show()
                }

                ImageStorage.DynamicWallpaperResult.Failed -> {
                    stagedPath = null
                    rendererFailed = false
                    rendererError = ""
                    Toast.makeText(
                        context,
                        R.string.dynamic_wallpaper_import_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun discardStaged() {
        ImageStorage.discardStagedDynamicWallpaper(stagedPath)
        stagedPath = null
        rendererFailed = false
        rendererError = ""
    }

    val actions = DynamicWallpaperPreviewActions(
        onBack = {
            viewModel.setDynamicWallpaperTransform(target, transform)
            navigator.pop()
        },
        onPick = { picker.launch(arrayOf("video/mp4", "image/webp")) },
        onApply = {
            val staged = stagedPath
            if (staged != null && !busy) {
                busy = true
                viewModel.setDynamicWallpaperTransform(target, transform)
                viewModel.commitDynamicWallpaper(context, staged, target) { applied ->
                    busy = false
                    if (applied) {
                        stagedPath = null
                        navigator.pop()
                    } else {
                        Toast.makeText(
                            context,
                            if (card) {
                                R.string.dynamic_home_card_apply_failed
                            } else {
                                R.string.dynamic_wallpaper_apply_failed
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        },
        onDiscardStaged = { if (!busy) discardStaged() },
        onRequestRemove = { if (!busy) showRemoveConfirm = true },
        onDismissRemove = { showRemoveConfirm = false },
        onConfirmRemove = {
            showRemoveConfirm = false
            if (!busy) {
                busy = true
                ImageStorage.discardStagedDynamicWallpaper(stagedPath)
                stagedPath = null
                viewModel.clearDynamicWallpaper(context, target) {
                    busy = false
                    rendererFailed = false
                }
            }
        },
        onSetMuted = viewModel::setDynamicWallpaperMuted,
        onRetryRenderer = {
            rendererFailed = false
            rendererError = ""
        },
        onRendererError = { reason ->
            android.util.Log.w("DynamicWallpaper", "card preview failed: $reason")
            rendererError = reason
            rendererFailed = true
        },
        onTransformChange = { transform = it },
        onSelectFit = { fit ->
            transform = transform.copy(
                fit = fit,
                zoom = if (fit == DynamicWallpaperFit.CUSTOM) transform.zoom else 1f,
                offsetX = if (fit == DynamicWallpaperFit.CUSTOM) transform.offsetX else 0f,
                offsetY = if (fit == DynamicWallpaperFit.CUSTOM) transform.offsetY else 0f,
            )
        },
        onSetFps = viewModel::setDynamicWallpaperFps,
        onSetSpeed = viewModel::setDynamicWallpaperSpeed,
    )

    val state = DynamicWallpaperPreviewUiState(
        previewPath = previewPath,
        previewUpdatedAt = previewUpdatedAt,
        muted = settings.dynamicWallpaperMuted,
        kind = kind,
        sizeBytes = previewFile?.length() ?: 0L,
        hasStaged = stagedActive,
        hasSaved = savedPath != null,
        busy = busy,
        rendererFailed = rendererFailed,
        rendererError = rendererError,
        showRemoveConfirm = showRemoveConfirm,
        transform = transform,
        fps = settings.dynamicWallpaperFps,
        speed = settings.dynamicWallpaperSpeed,
        isCard = card,
        cardLayout = settings.homeLayout,
        cardWidthFraction = settings.homeStatusWidthFraction,
        cardHeightScale = settings.homeStatusScale,
    )

    val adjustActive = state.transform.fit == DynamicWallpaperFit.CUSTOM &&
        state.previewPath != null && !state.busy

    Box(modifier = Modifier.fillMaxSize()) {
        // The card editor renders its own card-shaped stage inside the control layout; only the
        // app-wide wallpaper is previewed full screen.
        if (!state.isCard && state.previewPath != null && !state.rendererFailed) {
            key(state.previewPath, state.previewUpdatedAt) {
                DynamicWallpaperBackground(
                    path = state.previewPath,
                    muted = state.muted,
                    transform = state.transform,
                    fps = state.fps,
                    speed = state.speed,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f),
                    onError = { reason ->
                        android.util.Log.w("DynamicWallpaper", "preview failed: $reason")
                        rendererError = reason
                        rendererFailed = true
                    },
                )
            }
        }
        if (!state.isCard && adjustActive) {
            // Sits above the live media but below the controls, so empty areas can zoom and pan
            // while the top bar and the panel keep receiving their own gestures.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.5f)
                    .pointerInput(state.transform.fit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newZoom = (transform.zoom * zoom)
                                .coerceIn(
                                    DynamicWallpaperTransform.ZOOM_MIN,
                                    DynamicWallpaperTransform.ZOOM_MAX,
                                )
                            val dx = if (size.width > 0) pan.x / (size.width * 0.5f) else 0f
                            val dy = if (size.height > 0) pan.y / (size.height * 0.5f) else 0f
                            transform = transform.copy(
                                zoom = newZoom,
                                offsetX = (transform.offsetX + dx).coerceIn(-1f, 1f),
                                offsetY = (transform.offsetY + dy).coerceIn(-1f, 1f),
                            )
                        }
                    }
            )
        }
        Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
            when (LocalUiMode.current) {
                // Keep the control surfaces opaque on the preview page; they sit over live media,
                // not over the app's shared background, so the card alpha setting must not apply.
                UiMode.Miuix -> CompositionLocalProvider(LocalBackgroundImagePath provides "") {
                    DynamicWallpaperScreenMiuix(state, actions)
                }
                UiMode.Material -> CompositionLocalProvider(LocalBackgroundImagePath provides "") {
                    DynamicWallpaperScreenMaterial(state, actions)
                }
            }
        }
    }
}
