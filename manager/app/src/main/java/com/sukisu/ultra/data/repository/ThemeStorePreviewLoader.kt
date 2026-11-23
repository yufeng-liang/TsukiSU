package com.sukisu.ultra.data.repository

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import com.sukisu.ultra.ksuApp
import okhttp3.Dispatcher

/**
 * Dedicated Coil loader for theme-store grid previews.
 *
 * Only preview cards use this loader, and its OkHttp dispatcher allows four
 * in-flight requests. A finished preview frees a slot for the next card, so a
 * fast scroll cannot fan out dozens of image downloads at once. Detail
 * screenshots keep using the app's normal loader, which has its own dispatcher
 * and therefore starts immediately while the grid's requests are paused.
 */
object ThemeStorePreviewLoader {
    const val MAX_PARALLEL_PREVIEWS = 4

    @Volatile
    private var loader: ImageLoader? = null

    @Volatile
    private var sharedCache: MemoryCache? = null

    fun get(context: Context): ImageLoader {
        loader?.let { return it }
        return synchronized(this) {
            loader ?: build(context.applicationContext).also { loader = it }
        }
    }

    /**
     * The single [MemoryCache] shared between this loader and the app's default loader
     * (see [KernelSUApplication.newImageLoader]). A cover decoded for a grid card therefore
     * shows instantly in the detail film strip and pager instead of being decoded a second
     * time. Sizing it at the Coil default keeps the combined footprint at one normal cache
     * rather than two separate ones.
     */
    fun sharedMemoryCache(context: Context): MemoryCache =
        sharedCache ?: synchronized(this) {
            sharedCache
                ?: MemoryCache.Builder(context).maxSizePercent(0.25).build().also { sharedCache = it }
        }

    private fun build(context: Context): ImageLoader {
        val dispatcher = Dispatcher().apply {
            maxRequests = MAX_PARALLEL_PREVIEWS
            maxRequestsPerHost = MAX_PARALLEL_PREVIEWS
        }
        val client = ksuApp.okhttpClient.newBuilder()
            .addInterceptor(ThemeMediaInterceptor)
            .protectThemeStoreRedirectCredentials()
            .dispatcher(dispatcher)
            .build()
        return ImageLoader.Builder(context)
            // Shares the app's default memory cache so decoded covers are reusable across the
            // grid and the detail screen; the shared disk cache still stores the raw files.
            .memoryCache { sharedMemoryCache(context) }
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
