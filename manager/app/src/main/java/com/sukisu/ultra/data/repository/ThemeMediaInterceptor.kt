package com.sukisu.ultra.data.repository

import com.sukisu.ultra.ui.util.ThemeMediaCrypto
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Opens CNB media envelopes in place so Coil can decode them like any other
 * image. Requests without the direct marker still receive plaintext from the
 * service, and the magic check leaves unrelated traffic untouched.
 */
object ThemeMediaInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        if (!response.isSuccessful) return response
        // Peek through the buffered source; peeking never consumes bytes, so the
        // original body stays readable when this is not a sealed image.
        val source = try {
            body.source()
        } catch (_: Exception) {
            return response
        }
        val prepared = try {
            source.request(ThemeMediaCrypto.HEADER_SIZE.toLong()) &&
                source.buffer.size >= ThemeMediaCrypto.HEADER_SIZE
        } catch (_: Exception) {
            false
        }
        if (!prepared) return response
        val prefix = ByteArray(ThemeMediaCrypto.HEADER_SIZE)
        for (index in prefix.indices) prefix[index] = source.buffer.get(index.toLong())
        if (!ThemeMediaCrypto.isSealed(prefix)) return response
        val plain = try {
            ThemeMediaCrypto.decrypt(body.bytes())
        } catch (_: Exception) {
            // A key mismatch must surface as a broken image, not as a crash.
            return response
        }
        return response.newBuilder()
            .body(plain.toResponseBody(body.contentType()))
            .build()
    }
}
