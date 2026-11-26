package com.sukisu.ultra.data.repository

import com.sukisu.ultra.ksuApp
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File

/**
 * CNB raw prefixes learned from theme-store redirects, keyed by store base URL.
 *
 * A store that mirrors its assets hands out a 302 to CNB during the first request;
 * that address is stable for the whole store, so remembering it lets every later
 * package download and every theme image skip the store hop. The entry point can be
 * a cross-border CDN, where that hop costs a TLS handshake per request.
 *
 * Kept in `filesDir` rather than the cache: trimming the cache must not cost the next
 * request another redirect round trip.
 */
object ThemeCnbPrefixes {
    /** Asset segments a store serves below its base; the first one ends the base URL. */
    private val ASSET_SEGMENTS = listOf("/previews/", "/screenshots/", "/avatars/")

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val lock = Any()

    @Volatile
    private var cached: Map<String, String>? = null

    private val file: File by lazy { File(ksuApp.filesDir, "theme_store/cnb-prefix.json") }

    fun get(base: String): String? = synchronized(lock) { read()[base] }

    fun remember(base: String, prefix: String) {
        mutate { it + (base to prefix) }
    }

    /** Forgets [base]; the next request falls back to the store and learns it again. */
    fun forget(base: String) {
        mutate { it - base }
    }

    /**
     * Store base URL for a theme asset URL: everything before the first asset segment.
     *
     * `https://store.example/previews/a.webp` yields `https://store.example/`, and a
     * store mounted under a path keeps that path (`https://host/site/previews/...`).
     */
    fun baseOf(url: HttpUrl): String? {
        val path = url.encodedPath
        val index = ASSET_SEGMENTS
            .map { path.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()
            ?: return null
        return buildString {
            append(url.scheme).append("://").append(url.host)
            if (url.port != HttpUrl.defaultPort(url.scheme)) append(':').append(url.port)
            append(path, 0, index).append('/')
        }
    }

    /** A store asset that can be fetched from CNB directly instead of through the store. */
    data class Offload(val base: String, val url: String)

    /** Direct CNB URL for [url], or null while the store's prefix is still unknown. */
    fun offloadTarget(url: HttpUrl): Offload? {
        val base = baseOf(url) ?: return null
        val prefix = get(base) ?: return null
        val tail = url.toString().removePrefix(base)
        if (tail.isEmpty() || tail == url.toString()) return null
        return Offload(base = base, url = prefix + tail)
    }

    /**
     * Store base and CNB prefix implied by a redirect from [original] to [final], or null
     * when the redirect does not describe an asset on a CNB host.
     *
     * Compared segment by segment: CNB percent-encodes hyphens (`a%2Dv1.webp`), so a plain
     * string suffix check would never match.
     */
    internal fun derivePrefix(original: HttpUrl, final: HttpUrl): Pair<String, String>? {
        val base = baseOf(original) ?: return null
        val baseSegments = base.toHttpUrlOrNull()?.pathSegments.orEmpty().filter { it.isNotEmpty() }
        val tail = original.pathSegments.filter { it.isNotEmpty() }.drop(baseSegments.size)
        if (tail.isEmpty()) return null
        val prefix = prefixForTail(final, tail) ?: return null
        return base to prefix
    }

    /**
     * CNB prefix that serves a path ending in [tail] (the catalog-relative segments), or
     * null when [target] is not a CNB host or does not end with that path.
     */
    internal fun prefixForTail(target: HttpUrl, tail: List<String>): String? {
        if (!isCnbHost(target.host)) return null
        if (tail.isEmpty()) return null
        val segments = target.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < tail.size || segments.takeLast(tail.size) != tail) return null
        val prefixPath = segments.dropLast(tail.size).joinToString("/")
        val port = if (target.port == HttpUrl.defaultPort(target.scheme)) "" else ":${target.port}"
        return buildString {
            append(target.scheme).append("://").append(target.host).append(port).append('/')
            if (prefixPath.isNotEmpty()) append(prefixPath).append('/')
        }
    }

    /** Records what a redirect revealed; returns true when a new prefix was stored. */
    fun learnFromRedirect(original: HttpUrl, final: HttpUrl): Boolean {
        val (base, prefix) = derivePrefix(original, final) ?: return false
        if (get(base) == prefix) return false
        remember(base, prefix)
        return true
    }

    private fun isCnbHost(host: String): Boolean =
        host == "cnb.cool" || host.endsWith(".cnb.cool")

    private fun mutate(block: (Map<String, String>) -> Map<String, String>) = synchronized(lock) {
        val current = read()
        val next = runCatching { block(current) }.getOrDefault(current)
        if (next == current) return@synchronized
        cached = next
        runCatching {
            if (next.isEmpty()) {
                file.delete()
            } else {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writeText(json.encodeToString(serializer, next))
                file.delete()
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            }
        }
    }

    private fun read(): Map<String, String> {
        cached?.let { return it }
        val loaded = runCatching {
            if (!file.isFile) return@runCatching emptyMap()
            json.decodeFromString(serializer, file.readText())
        }.getOrDefault(emptyMap())
        cached = loaded
        return loaded
    }
}

/**
 * Fetches theme media from the CNB prefix a store redirect revealed, and learns the
 * prefix from store redirects that still happen.
 *
 * Media is sealed with the `SKM1` envelope on CNB; [ThemeMediaInterceptor] already
 * opens it, so only the address has to change. The store credential and the media
 * marker are dropped from the rewritten request — CNB needs neither, and the store
 * token must never leave the store.
 *
 * A rewritten request that fails forgets the prefix and falls back to the store, so a
 * moved mirror or a rotated repository heals itself.
 */
internal object ThemeStoreMediaOffload : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        val offload = ThemeCnbPrefixes.offloadTarget(request.url)
        if (offload != null) {
            val direct = request.newBuilder()
                .url(offload.url)
                .removeHeader(ThemeStoreRepository.TOKEN_HEADER)
                .removeHeader(ThemeStoreRepository.MEDIA_DIRECT_HEADER)
                .build()
            val rewritten = runCatching { chain.proceed(direct) }.getOrNull()
            if (rewritten != null && rewritten.isSuccessful) return rewritten
            rewritten?.close()
            ThemeCnbPrefixes.forget(offload.base)
        }

        val response = chain.proceed(request)
        ThemeCnbPrefixes.learnFromRedirect(request.url, response.request.url)
        return response
    }
}
