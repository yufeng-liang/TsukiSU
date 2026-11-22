@file:Suppress("DEPRECATION")

package com.sukisu.ultra.ui.component.background

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sukisu.ultra.ui.util.ImageStorage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DynamicWallpaper"
private const val BRIDGE_NAME = "DWBridge"

/**
 * Renders an MP4 or animated WebP behind the app through a WebGL canvas in a WebView.
 *
 * Both formats go through the same upload path: the current frame is drawn into a
 * screen-sized 2D scratch canvas, uploaded as a texture, and presented by a full-screen
 * WebGL quad. The video element is never attached to the DOM, so no media overlay surface
 * can sit above Compose content. [onError] fires when the media or GPU renderer cannot start;
 * callers should fall back to the static poster in that case.
 *
 * [onFrameSnapshot] receives throttled, low-resolution copies of the composed frame. Hosts use
 * them as a safe bitmap backdrop for blur bars: sampling the live WebView surface itself crashes
 * some MIUI/HyperOS WebViews, while these frames were already drawn by our own canvas.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun DynamicWallpaperBackground(
    path: String,
    muted: Boolean,
    transform: DynamicWallpaperTransform = DynamicWallpaperTransform.Default,
    fps: Int = 0,
    speed: Float = 1f,
    modifier: Modifier = Modifier,
    blurPercent: Int = 0,
    onError: (String) -> Unit = {},
    onFrameSnapshot: ((Bitmap) -> Unit)? = null,
) {
    val context = LocalContext.current
    val kind = remember(context, path) {
        ImageStorage.dynamicWallpaperKind(path)
    } ?: return
    val assetPath = remember(context, path) {
        ImageStorage.dynamicWallpaperAssetPath(context, path)
    } ?: return
    val server = remember(path, kind) {
        runCatching {
            DynamicWallpaperServer(
                backgroundsDir = ImageStorage.dynamicWallpaperBackgroundsDir(context),
                previewDir = ImageStorage.dynamicWallpaperPreviewDir(context),
            )
        }.getOrNull()
    }
    if (server == null) {
        LaunchedEffect(path) { onError("server-start") }
        return
    }
    DisposableEffect(server) {
        onDispose { server.close() }
    }
    // "dynamic/name" or "dynamic-preview/name" from ImageStorage, mapped onto the server routes.
    val mediaRelative = when {
        assetPath.startsWith("dynamic-preview/") ->
            "preview/" + assetPath.removePrefix("dynamic-preview/")

        assetPath.startsWith("dynamic/") ->
            "applied/" + assetPath.removePrefix("dynamic/")

        else -> null
    }
    if (mediaRelative == null) {
        LaunchedEffect(path) { onError("asset-path") }
        return
    }
    val mediaUrl = server.origin + mediaRelative
    val htmlTemplate = remember(context) {
        context.assets.open("dynamic_wallpaper.html")
            .bufferedReader()
            .use { it.readText() }
    }
    val mutedState = rememberUpdatedState(muted)
    val blurState = rememberUpdatedState(blurPercent.coerceIn(0, 100) / 100f)
    val transformState = rememberUpdatedState(transform.sanitized())
    val fpsState = rememberUpdatedState(fps)
    val speedState = rememberUpdatedState(speed)
    val onErrorState = rememberUpdatedState(onError)
    val onFrameSnapshotState = rememberUpdatedState(onFrameSnapshot)
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snapshotBusy = remember { AtomicBoolean(false) }
    val snapshotExecutor = remember {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dw-frame-snapshot").apply { isDaemon = true }
        }
    }
    DisposableEffect(snapshotExecutor) {
        onDispose { snapshotExecutor.shutdownNow() }
    }
    val isVideo = kind == ImageStorage.DynamicWallpaperKind.MP4

    val bridge = remember {
        DynamicWallpaperBridge(
            onReadyCallback = {
                mainHandler.post {
                    val view = webViewRef.value ?: return@post
                    runCatching {
                        view.evaluateJavascript(
                            dynamicWallpaperStateScript(
                                mutedState.value,
                                blurState.value,
                                transformState.value,
                                fpsState.value,
                                speedState.value,
                            ) + dynamicWallpaperSnapshotScript(
                                onFrameSnapshotState.value != null,
                            ),
                            null,
                        )
                    }
                }
            },
            onErrorCallback = { reason ->
                mainHandler.post { onErrorState.value(reason) }
            },
            onFrameCallback = { dataUrl ->
                // Decode off the JS bridge thread and keep at most one decode in flight. The
                // page throttles itself; this only guards against a slow decoder falling behind.
                if (onFrameSnapshotState.value != null &&
                    snapshotBusy.compareAndSet(false, true)
                ) {
                    runCatching {
                        snapshotExecutor.execute {
                            val bitmap = decodeDynamicWallpaperFrame(dataUrl)
                            snapshotBusy.set(false)
                            if (bitmap != null) {
                                mainHandler.post {
                                    // A wallpaper swap disposes this renderer; never let its
                                    // last in-flight frame overwrite the new one.
                                    if (!snapshotExecutor.isShutdown) {
                                        onFrameSnapshotState.value?.invoke(bitmap)
                                    }
                                }
                            }
                        }
                    }.onFailure { snapshotBusy.set(false) }
                }
            },
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val view = webViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    view.onResume()
                    runCatching {
                        view.evaluateJavascript("window.DW && window.DW.resume();", null)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Let the page stop its video before the renderer is paused, otherwise
                    // playback (and audio) can outlive the visible window.
                    runCatching {
                        view.evaluateJavascript("window.DW && window.DW.pause();") {
                            view.onPause()
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    key(path, kind) {
        AndroidView<View>(
            modifier = modifier,
            factory = { viewContext ->
                runCatching {
                    // The page and the media share one loopback http origin, so the canvas is not
                    // tainted by the wallpaper frame and WebView's media stack gets normal URLs.
                    server.setPageHtml(
                        buildDynamicWallpaperHtml(
                            template = htmlTemplate,
                            sourceUrl = mediaUrl,
                            isVideo = isVideo,
                            muted = mutedState.value,
                            blur = blurState.value,
                            transform = transformState.value,
                            fps = fpsState.value,
                            speed = speedState.value,
                        )
                    )
                    createDynamicWallpaperWebView(
                        context = viewContext,
                        pageUrl = server.pageUrl,
                        bridge = bridge,
                        currentMuted = { mutedState.value },
                        currentBlur = { blurState.value },
                        currentTransform = { transformState.value },
                        currentFps = { fpsState.value },
                        currentSpeed = { speedState.value },
                        onFailure = { reason -> onErrorState.value(reason) },
                    )
                }.getOrElse { error ->
                    // A missing or disabled WebView package must not take the app down; report
                    // the failure so callers fall back to the static poster.
                    Log.w(TAG, "dynamic wallpaper WebView could not be created", error)
                    mainHandler.post {
                        onErrorState.value(
                            "webview-create: ${error.message ?: error.javaClass.simpleName}"
                        )
                    }
                    View(viewContext)
                }.also { view ->
                    if (view is WebView) webViewRef.value = view
                }
            },
            update = { view ->
                if (view is WebView) {
                    runCatching {
                        view.evaluateJavascript(
                            dynamicWallpaperStateScript(
                                mutedState.value,
                                blurState.value,
                                transformState.value,
                                fpsState.value,
                                speedState.value,
                            ) + dynamicWallpaperSnapshotScript(
                                onFrameSnapshotState.value != null,
                            ),
                            null,
                        )
                    }
                }
            },
            onRelease = { view ->
                if (view is WebView) {
                    if (webViewRef.value === view) webViewRef.value = null
                    destroyDynamicWallpaperWebView(view)
                }
            },
        )
    }
}

private class DynamicWallpaperBridge(
    private val onReadyCallback: () -> Unit,
    private val onErrorCallback: (String) -> Unit,
    private val onFrameCallback: (String) -> Unit,
) {
    @JavascriptInterface
    fun onReady() {
        onReadyCallback()
    }

    @JavascriptInterface
    fun onError(reason: String) {
        onErrorCallback(reason)
    }

    @JavascriptInterface
    fun onFrame(dataUrl: String) {
        onFrameCallback(dataUrl)
    }
}

/** Decodes the page's small data-URL frame copy; null when the payload is not a valid image. */
private fun decodeDynamicWallpaperFrame(dataUrl: String): Bitmap? = runCatching {
    val separator = dataUrl.indexOf(',')
    if (separator <= 0) {
        null
    } else {
        val bytes = Base64.decode(dataUrl.substring(separator + 1), Base64.DEFAULT)
        if (bytes.isEmpty()) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}.getOrNull()

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun createDynamicWallpaperWebView(
    context: Context,
    pageUrl: String,
    bridge: DynamicWallpaperBridge,
    currentMuted: () -> Boolean,
    currentBlur: () -> Float,
    currentTransform: () -> DynamicWallpaperTransform,
    currentFps: () -> Int,
    currentSpeed: () -> Float,
    onFailure: (String) -> Unit,
): WebView = WebView(context).apply {
    setBackgroundColor(Color.TRANSPARENT)
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    isNestedScrollingEnabled = false
    isClickable = false
    isFocusable = false
    isFocusableInTouchMode = false
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    // The wallpaper must never take a tap away from the page above it.
    setOnTouchListener { _, _ -> true }

    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = false
        databaseEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_NO_CACHE
        textZoom = 100
        offscreenPreRaster = true
        loadsImagesAutomatically = true
        setGeolocationEnabled(false)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
    }

    addJavascriptInterface(bridge, BRIDGE_NAME)
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            Log.d(
                TAG,
                "${message.messageLevel()} ${message.message()} " +
                    "(${message.sourceId()}:${message.lineNumber()})",
            )
            return true
        }
    }
    webViewClient = createDynamicWallpaperWebViewClient(
        currentMuted = currentMuted,
        currentBlur = currentBlur,
        currentTransform = currentTransform,
        currentFps = currentFps,
        currentSpeed = currentSpeed,
        onFailure = onFailure,
    )

    loadUrl(pageUrl)
}

private fun createDynamicWallpaperWebViewClient(
    currentMuted: () -> Boolean,
    currentBlur: () -> Float,
    currentTransform: () -> DynamicWallpaperTransform,
    currentFps: () -> Int,
    currentSpeed: () -> Float,
    onFailure: (String) -> Unit,
): WebViewClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    // The API-26 subclass is only loaded where RenderProcessGoneDetail exists.
    DynamicWallpaperWebViewClientApi26(
        currentMuted = currentMuted,
        currentBlur = currentBlur,
        currentTransform = currentTransform,
        currentFps = currentFps,
        currentSpeed = currentSpeed,
        onFailure = onFailure,
    )
} else {
    DynamicWallpaperWebViewClient(
        currentMuted = currentMuted,
        currentBlur = currentBlur,
        currentTransform = currentTransform,
        currentFps = currentFps,
        currentSpeed = currentSpeed,
        onFailureCallback = onFailure,
    )
}

// The renderer-crash override lives in DynamicWallpaperWebViewClientApi26. Referencing
// RenderProcessGoneDetail here would break verification on API 24/25, where the type does
// not exist, so the check is intentionally satisfied by the subclass only.
@SuppressLint("MissingOnRenderProcessGone")
private open class DynamicWallpaperWebViewClient(
    private val currentMuted: () -> Boolean,
    private val currentBlur: () -> Float,
    private val currentTransform: () -> DynamicWallpaperTransform,
    private val currentFps: () -> Int,
    private val currentSpeed: () -> Float,
    protected val onFailureCallback: (String) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = true

    override fun onPageFinished(view: WebView, url: String) {
        runCatching {
            view.evaluateJavascript(
                dynamicWallpaperStateScript(
                    currentMuted(),
                    currentBlur(),
                    currentTransform(),
                    currentFps(),
                    currentSpeed(),
                ),
                null,
            )
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) onFailureCallback("page-error")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private class DynamicWallpaperWebViewClientApi26(
    currentMuted: () -> Boolean,
    currentBlur: () -> Float,
    currentTransform: () -> DynamicWallpaperTransform,
    currentFps: () -> Int,
    currentSpeed: () -> Float,
    onFailure: (String) -> Unit,
) : DynamicWallpaperWebViewClient(
    currentMuted,
    currentBlur,
    currentTransform,
    currentFps,
    currentSpeed,
    onFailure,
) {
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        // Returning true keeps the crash from killing the host process; drop the surface so
        // the static poster underneath becomes visible.
        onFailureCallback("renderer-gone")
        (view.parent as? ViewGroup)?.removeView(view)
        destroyDynamicWallpaperWebView(view)
        return true
    }
}

private fun destroyDynamicWallpaperWebView(view: WebView) {
    runCatching {
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.loadUrl("about:blank")
        view.destroy()
    }
}

private fun dynamicWallpaperStateScript(
    muted: Boolean,
    blur: Float,
    transform: DynamicWallpaperTransform,
    fps: Int,
    speed: Float,
): String {
    val geometry = transform.sanitized()
    return "window.DW && (window.DW.setMuted(${if (muted) "true" else "false"}), " +
        "window.DW.setBlur(${String.format(Locale.US, "%.3f", blur)}), " +
        "window.DW.setTransform(" +
        "'${geometry.fit.jsName}', " +
        "${String.format(Locale.US, "%.3f", geometry.zoom)}, " +
        "${String.format(Locale.US, "%.3f", geometry.offsetX)}, " +
        "${String.format(Locale.US, "%.3f", geometry.offsetY)}" +
        "), window.DW.setPlayback($fps, ${String.format(Locale.US, "%.3f", speed)}));"
}

private fun dynamicWallpaperSnapshotScript(enabled: Boolean): String =
    "window.DW && window.DW.setFrameSnapshots(${if (enabled) "true" else "false"});"

private fun buildDynamicWallpaperHtml(
    template: String,
    sourceUrl: String,
    isVideo: Boolean,
    muted: Boolean,
    blur: Float,
    transform: DynamicWallpaperTransform,
    fps: Int,
    speed: Float,
): String {
    val geometry = transform.sanitized()
    return template
        .replace("@SOURCE@", sourceUrl)
        .replace("@MODE@", if (isVideo) "video" else "image")
        .replace("@MUTED@", if (muted) "true" else "false")
        .replace("@BLUR@", String.format(Locale.US, "%.3f", blur))
        .replace("@FIT@", geometry.fit.jsName)
        .replace("@ZOOM@", String.format(Locale.US, "%.3f", geometry.zoom))
        .replace("@OFFSET_X@", String.format(Locale.US, "%.3f", geometry.offsetX))
        .replace("@OFFSET_Y@", String.format(Locale.US, "%.3f", geometry.offsetY))
        .replace("@FPS@", fps.coerceIn(0, 120).toString())
        .replace("@SPEED@", String.format(Locale.US, "%.3f", speed.coerceIn(0.25f, 2f)))
}

/**
 * Serves the renderer page and the wallpaper over loopback HTTP.
 *
 * WebView's media stack does not reliably route `<video>` requests through
 * `shouldInterceptRequest`, and a partial response returned from there is not always accepted
 * by the player. A real HTTP server on 127.0.0.1 gives the player a normal URL with Range
 * support, so MP4 playback and seeking behave like on any web page. The socket binds to
 * loopback only, the path carries a random per-instance token, and only the two wallpaper
 * directories are exposed.
 */
private class DynamicWallpaperServer(
    private val backgroundsDir: File,
    private val previewDir: File,
) : Closeable {
    private val token = UUID.randomUUID().toString().replace("-", "")
    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))

    /** Base URL ending with the token slash, e.g. `http://127.0.0.1:41234/<token>/`. */
    val origin: String = "http://127.0.0.1:${serverSocket.localPort}/$token/"
    val pageUrl: String = origin + "index.html"

    // Written on the main thread before loadUrl(); read from connection threads.
    @Volatile
    private var pageHtml: String = ""

    @Volatile
    private var closed = false

    private val connections = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()

    init {
        Thread({
            while (!closed) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: IOException) {
                    break
                } catch (_: SecurityException) {
                    break
                }
                connections.add(socket)
                Thread({
                    runCatching { handle(socket) }
                    connections.remove(socket)
                }, "dw-wallpaper-http").apply { isDaemon = true }.start()
            }
        }, "dw-wallpaper-accept").apply { isDaemon = true }.start()
    }

    fun setPageHtml(html: String) {
        pageHtml = html
    }

    override fun close() {
        closed = true
        runCatching { serverSocket.close() }
        // An abandoned range response can block on write; close the sockets to unblock them.
        connections.forEach { connection -> runCatching { connection.close() } }
        connections.clear()
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 10_000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "HEAD")) {
                writeEmpty(output, "405 Method Not Allowed")
                return
            }
            val includeBody = parts[0] == "GET"
            var rangeHeader: String? = null
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).equals("Range", ignoreCase = true)) {
                    rangeHeader = line.substring(colon + 1).trim()
                }
            }
            val path = parts[1].substringBefore('?').substringBefore('#')
            val tokenPrefix = "/$token/"
            if (!path.startsWith(tokenPrefix)) {
                writeEmpty(output, "404 Not Found")
                return
            }
            val relative = path.removePrefix(tokenPrefix)
            when {
                relative == "index.html" -> writePage(output, includeBody)
                relative.startsWith("applied/") ->
                    writeMedia(
                        output,
                        backgroundsDir,
                        relative.removePrefix("applied/"),
                        rangeHeader,
                        includeBody,
                    )
                relative.startsWith("preview/") ->
                    writeMedia(
                        output,
                        previewDir,
                        relative.removePrefix("preview/"),
                        rangeHeader,
                        includeBody,
                    )
                else -> writeEmpty(output, "404 Not Found")
            }
            output.flush()
        }
    }

    private fun writePage(output: OutputStream, includeBody: Boolean) {
        val body = pageHtml.toByteArray(Charsets.UTF_8)
        if (body.isEmpty()) {
            writeEmpty(output, "503 Service Unavailable")
            return
        }
        writeHeaders(
            output,
            status = "200 OK",
            mime = "text/html; charset=utf-8",
            length = body.size.toLong(),
            extra = listOf("Cache-Control" to "no-store"),
        )
        if (includeBody) output.write(body)
    }

    private fun writeMedia(
        output: OutputStream,
        directory: File,
        name: String,
        rangeHeader: String?,
        includeBody: Boolean,
    ) {
        val safeName = name.isNotEmpty() && name.all { character ->
            character.isLetterOrDigit() || character == '_' || character == '-' || character == '.'
        }
        if (!safeName ||
            name.contains("..") ||
            ImageStorage.DynamicWallpaperKind.fromFileName(name) == null
        ) {
            Log.w(TAG, "rejected media path: $name")
            writeEmpty(output, "403 Forbidden")
            return
        }
        val file = File(directory, name)
        val insideDirectory = runCatching {
            file.parentFile?.canonicalFile == directory.canonicalFile
        }.getOrDefault(false)
        if (!file.isFile || !insideDirectory) {
            Log.w(TAG, "missing media: $name")
            writeEmpty(output, "404 Not Found")
            return
        }
        val total = file.length()
        if (total <= 0L) {
            writeEmpty(output, "404 Not Found")
            return
        }
        val mime = ImageStorage.DynamicWallpaperKind.fromFileName(name)?.mimeType
            ?: "application/octet-stream"
        val range = rangeHeader?.let { parseRange(it, total) }
        if (rangeHeader != null && range == null) {
            writeHeaders(
                output,
                status = "416 Range Not Satisfiable",
                mime = mime,
                length = 0L,
                extra = listOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes */$total",
                ),
            )
            return
        }
        val start = range?.first ?: 0L
        val end = range?.second ?: (total - 1L)
        val length = end - start + 1L
        val headers = mutableListOf("Accept-Ranges" to "bytes", "Cache-Control" to "no-store")
        if (range != null) {
            headers += "Content-Range" to "bytes $start-$end/$total"
        }
        Log.d(TAG, "serve ${file.name} range=$rangeHeader")
        writeHeaders(
            output,
            status = if (range != null) "206 Partial Content" else "200 OK",
            mime = mime,
            length = length,
            extra = headers,
        )
        if (!includeBody) return
        try {
            RandomAccessFile(file, "r").use { randomAccess ->
                randomAccess.seek(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0L) {
                    val read = randomAccess.read(
                        buffer,
                        0,
                        minOf(buffer.size.toLong(), remaining).toInt(),
                    )
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read.toLong()
                }
            }
        } catch (_: IOException) {
            Log.d(TAG, "media stream closed early")
        }
    }

    private fun writeHeaders(
        output: OutputStream,
        status: String,
        mime: String,
        length: Long,
        extra: List<Pair<String, String>>,
    ) {
        val builder = StringBuilder(256)
        builder.append("HTTP/1.1 ").append(status).append("\r\n")
        builder.append("Content-Type: ").append(mime).append("\r\n")
        builder.append("Content-Length: ").append(length).append("\r\n")
        builder.append("Connection: close\r\n")
        for ((name, value) in extra) {
            builder.append(name).append(": ").append(value).append("\r\n")
        }
        builder.append("\r\n")
        output.write(builder.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun writeEmpty(output: OutputStream, status: String) {
        writeHeaders(
            output,
            status = status,
            mime = "text/plain; charset=utf-8",
            length = 0L,
            extra = emptyList(),
        )
    }

    private fun readAsciiLine(input: InputStream): String? {
        val builder = StringBuilder(96)
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) return builder.toString()
            if (byte == '\r'.code) continue
            if (builder.length >= 8 * 1024) return null
            builder.append(byte.toChar())
        }
    }

    private fun parseRange(header: String, total: Long): Pair<Long, Long>? {
        val value = header.trim()
        if (!value.startsWith("bytes=", ignoreCase = true)) return null
        val separator = value.indexOf('=')
        if (separator < 0) return null
        val ranges = value.substring(separator + 1).split(',')
        if (ranges.size != 1) return null
        val part = ranges[0].trim()
        val dash = part.indexOf('-')
        if (dash < 0) return null
        val startText = part.substring(0, dash).trim()
        val endText = part.substring(dash + 1).trim()
        return try {
            if (startText.isEmpty()) {
                val suffix = endText.toLong()
                if (suffix <= 0L) return null
                val start = (total - suffix).coerceAtLeast(0L)
                start to (total - 1L)
            } else {
                val start = startText.toLong()
                val end = if (endText.isEmpty()) total - 1L else endText.toLong()
                if (start !in 0L..<total) return null
                val boundedEnd = end.coerceAtMost(total - 1L)
                if (boundedEnd < start) return null
                start to boundedEnd
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
