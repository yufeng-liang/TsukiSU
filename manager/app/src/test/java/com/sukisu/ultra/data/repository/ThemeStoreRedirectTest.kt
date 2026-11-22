package com.sukisu.ultra.data.repository

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.Closeable
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ThemeStoreRedirectTest {
    private class Server(handler: (HttpExchange) -> Unit) : Closeable {
        private val executor = Executors.newCachedThreadPool()
        private val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@Server.executor
            createContext("/") { exchange ->
                try {
                    handler(exchange)
                } finally {
                    exchange.close()
                }
            }
            start()
        }

        val url: String get() = "http://127.0.0.1:${http.address.port}"

        override fun close() {
            http.stop(0)
            executor.shutdownNow()
        }
    }

    private fun HttpExchange.redirect(location: String) {
        responseHeaders.set("Location", location)
        sendResponseHeaders(302, -1)
    }

    private fun HttpExchange.reply(body: String, code: Int = 200) {
        val bytes = body.toByteArray()
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    @Test
    fun crossOriginRedirectChainDropsTokenAndPreservesRange() {
        val sourceToken = AtomicReference<String?>()
        val cnbToken = AtomicReference<String?>()
        val cdnToken = AtomicReference<String?>()
        val cdnRange = AtomicReference<String?>()

        Server { exchange ->
            cdnToken.set(exchange.requestHeaders.getFirst(ThemeStoreRepository.TOKEN_HEADER))
            cdnRange.set(exchange.requestHeaders.getFirst("Range"))
            exchange.responseHeaders.set("Content-Range", "bytes 0-0/4")
            exchange.reply("x", 206)
        }.use { cdn ->
            Server { exchange ->
                cnbToken.set(exchange.requestHeaders.getFirst(ThemeStoreRepository.TOKEN_HEADER))
                exchange.redirect(cdn.url + "/signed-package")
            }.use { cnb ->
                Server { exchange ->
                    sourceToken.set(exchange.requestHeaders.getFirst(ThemeStoreRepository.TOKEN_HEADER))
                    exchange.redirect(cnb.url + "/release-asset")
                }.use { primary ->
                    val client = OkHttpClient.Builder()
                        .protectThemeStoreRedirectCredentials()
                        .build()
                    val request = Request.Builder()
                        .url(primary.url + "/themes/demo.sukisu")
                        .header(ThemeStoreRepository.TOKEN_HEADER, "secret")
                        .header("Range", "bytes=0-0")
                        .build()

                    client.newCall(request).execute().use { response ->
                        assertEquals(206, response.code)
                        assertEquals("x", response.body.string())
                    }
                }
            }
        }

        assertEquals("secret", sourceToken.get())
        assertNull(cnbToken.get())
        assertNull(cdnToken.get())
        assertEquals("bytes=0-0", cdnRange.get())
    }

    @Test
    fun sameOriginRedirectKeepsToken() {
        val finalToken = AtomicReference<String?>()
        Server { exchange ->
            when (exchange.requestURI.path) {
                "/start" -> exchange.redirect("/final")
                else -> {
                    finalToken.set(exchange.requestHeaders.getFirst(ThemeStoreRepository.TOKEN_HEADER))
                    exchange.reply("ok")
                }
            }
        }.use { server ->
            val client = OkHttpClient.Builder()
                .protectThemeStoreRedirectCredentials()
                .build()
            val request = Request.Builder()
                .url(server.url + "/start")
                .header(ThemeStoreRepository.TOKEN_HEADER, "secret")
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("ok", response.body.string())
            }
        }

        assertEquals("secret", finalToken.get())
    }
}
