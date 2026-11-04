package com.sukisu.ultra.ai

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片输入（多模态）在三种 wire format 下的请求体形状。
 *
 * 用本地 HttpServer 抓真实请求体，这样不必把私有序列化函数暴露成 internal：
 * 断言的是发出去的那段 JSON，而不是中间对象。
 */
class AiApiImageTest {

    private var server: HttpServer? = null
    private val lastBody = AtomicReference("")

    @After
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    private fun startServer(reply: String): Int {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/") { exchange ->
            lastBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            val bytes = reply.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        return http.address.port
    }

    private fun config(port: Int, format: AiProviderFormat) = AiConfig(
        endpoint = "http://127.0.0.1:$port",
        model = "test-model",
        format = format,
        streaming = false,
    )

    private fun sentPayload(): JsonObject = Json.parseToJsonElement(lastBody.get()).jsonObject

    private fun sentMessages(): JsonArray = sentPayload().requireArray("messages")

    private fun JsonObject.requireArray(key: String): JsonArray =
        requireNotNull(this[key] as? JsonArray) { "expected array at '$key', got ${this[key]}" }

    private fun JsonObject.string(key: String): String =
        requireNotNull((this[key] as? JsonPrimitive)?.content) { "expected string at '$key'" }

    @Test
    fun openAiWrapsImageInContentParts() = runBlocking {
        val port = startServer("""{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""")
        AiApiClient.complete(
            config = config(port, AiProviderFormat.OPENAI),
            messages = listOf(
                AiApiMessage("user", "look at this", images = listOf(AiImage("image/png", "AAAA"))),
            ),
        )
        val message = sentMessages()[0]!!.jsonObject
        assertEquals("user", message.string("role"))
        val parts = message.requireArray("content")
        assertEquals(2, parts.size)
        // 文本在前、图片在后（OpenAI 示例里的顺序）。
        assertEquals("text", parts[0]!!.jsonObject.string("type"))
        assertEquals("look at this", parts[0]!!.jsonObject.string("text"))
        assertEquals("image_url", parts[1]!!.jsonObject.string("type"))
        assertEquals(
            "data:image/png;base64,AAAA",
            parts[1]!!.jsonObject["image_url"]!!.jsonObject.string("url"),
        )
    }

    @Test
    fun openAiKeepsPlainStringWithoutImages() = runBlocking {
        val port = startServer("""{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""")
        AiApiClient.complete(
            config = config(port, AiProviderFormat.OPENAI),
            messages = listOf(
                AiApiMessage("user", "hi"),
                AiApiMessage(
                    role = "assistant",
                    toolCalls = listOf(AiToolCall("call_1", "get_environment", "{\"a\":1}")),
                ),
            ),
        )
        val messages = sentMessages()
        // 不带图的消息仍然是纯字符串，形状不变。
        assertTrue(messages[0]!!.jsonObject["content"] is JsonPrimitive)
        assertEquals("hi", messages[0]!!.jsonObject.string("content"))
        // 带 toolCalls 的 assistant 消息维持原形状：content 为 null + tool_calls 数组。
        val assistant = messages[1]!!.jsonObject
        assertTrue(assistant["content"] is JsonNull)
        val call = assistant.requireArray("tool_calls")[0]!!.jsonObject
        assertEquals("call_1", call.string("id"))
        assertEquals("get_environment", call["function"]!!.jsonObject.string("name"))
    }

    @Test
    fun openAiAssistantImagesAreIgnored() = runBlocking {
        val port = startServer("""{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""")
        AiApiClient.complete(
            config = config(port, AiProviderFormat.OPENAI),
            messages = listOf(
                AiApiMessage(
                    role = "assistant",
                    content = "done",
                    images = listOf(AiImage("image/png", "AAAA")),
                    toolCalls = listOf(AiToolCall("call_1", "get_environment", "{}")),
                ),
            ),
        )
        val assistant = sentMessages()[0]!!.jsonObject
        // assistant 不接受混图：既不能变成 part 数组，也不能冒出 images 字段。
        assertEquals("done", assistant.string("content"))
        assertNull(assistant["images"])
    }

    @Test
    fun anthropicPutsImagesBeforeTextAndKeepsThemSeparate() = runBlocking {
        val port = startServer("""{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}""")
        AiApiClient.complete(
            config = config(port, AiProviderFormat.ANTHROPIC),
            messages = listOf(
                AiApiMessage("system", "be brief"),
                AiApiMessage("user", "first"),
                AiApiMessage("user", "with image", images = listOf(AiImage("image/jpeg", "BBBB"))),
                AiApiMessage("user", "third"),
            ),
        )
        val messages = sentMessages()
        // 带图消息独立成条：不能被上一条 user 吞掉，也不再吸收下一条 user。
        assertEquals(3, messages.size)
        messages.forEach { assertEquals("user", it.jsonObject.string("role")) }

        val first = messages[0]!!.jsonObject.requireArray("content")
        assertEquals(1, first.size)
        assertEquals("first", first[0]!!.jsonObject.string("text"))

        val withImage = messages[1]!!.jsonObject.requireArray("content")
        assertEquals(2, withImage.size)
        // Anthropic 推荐同一条消息内图片在前、文本在后。
        assertEquals("image", withImage[0]!!.jsonObject.string("type"))
        val source = withImage[0]!!.jsonObject["source"]!!.jsonObject
        assertEquals("base64", source.string("type"))
        assertEquals("image/jpeg", source.string("media_type"))
        assertEquals("BBBB", source.string("data"))
        assertEquals("text", withImage[1]!!.jsonObject.string("type"))
        assertEquals("with image", withImage[1]!!.jsonObject.string("text"))

        val third = messages[2]!!.jsonObject.requireArray("content")
        assertEquals("third", third[0]!!.jsonObject.string("text"))
    }

    @Test
    fun ollamaSendsBareBase64InImages() = runBlocking {
        val port = startServer("""{"message":{"content":"ok"},"done":true}""")
        AiApiClient.complete(
            config = config(port, AiProviderFormat.OLLAMA),
            messages = listOf(
                AiApiMessage("user", "what is this", images = listOf(AiImage("image/png", "CCCC"))),
            ),
        )
        val message = sentMessages()[0]!!.jsonObject
        // content 仍是纯字符串，图片走独立的 images 数组，元素是裸 base64（无 data: 前缀、无 mime）。
        assertEquals("what is this", message.string("content"))
        val images = message.requireArray("images")
        assertEquals(1, images.size)
        assertEquals("CCCC", (images[0] as JsonPrimitive).content)
    }

    @Test
    fun ollamaToolMessagesDoNotCarryImages() = runBlocking {
        val port = startServer("""{"message":{"content":"ok"},"done":true}""")
        AiApiClient.complete(
            config = config(port, AiProviderFormat.OLLAMA),
            messages = listOf(
                AiApiMessage(
                    role = "tool",
                    content = "result",
                    images = listOf(AiImage("image/png", "DDDD")),
                    toolCallId = "call_1",
                ),
            ),
        )
        val message = sentMessages()[0]!!.jsonObject
        assertEquals("result", message.string("content"))
        assertNull(message["images"])
    }

    @Test
    fun imagesBeyondThePerMessageLimitAreDropped() = runBlocking {
        val port = startServer("""{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""")
        val five = (1..5).map { AiImage("image/png", "IMG$it") }
        AiApiClient.complete(
            config = config(port, AiProviderFormat.OPENAI),
            messages = listOf(AiApiMessage("user", "many", images = five)),
        )
        val parts = sentMessages()[0]!!.jsonObject.requireArray("content")
        // 1 个 text part + 最多 4 个图片 part。
        assertEquals(5, parts.size)
        val urls = parts.drop(1).map { it.jsonObject["image_url"]!!.jsonObject.string("url") }
        assertEquals(
            listOf(
                "data:image/png;base64,IMG1",
                "data:image/png;base64,IMG2",
                "data:image/png;base64,IMG3",
                "data:image/png;base64,IMG4",
            ),
            urls,
        )
    }

    @Test
    fun oversizedAndEmptyImagesAreDropped() = runBlocking {
        val port = startServer("""{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""")
        val tooBig = "A".repeat(4 * 1024 * 1024 + 1)
        AiApiClient.complete(
            config = config(port, AiProviderFormat.OPENAI),
            messages = listOf(
                AiApiMessage(
                    role = "user",
                    content = "still works",
                    images = listOf(AiImage("image/png", tooBig), AiImage("image/png", "")),
                ),
            ),
        )
        // 两图都被丢弃后回到纯字符串形状，请求本身不能失败。
        val message = sentMessages()[0]!!.jsonObject
        assertTrue(message["content"] is JsonPrimitive)
        assertEquals("still works", message.string("content"))
    }
}
