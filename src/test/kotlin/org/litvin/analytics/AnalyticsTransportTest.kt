package org.litvin.analytics

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnalyticsTransportTest {
    @Test
    fun `posts the JSON body and returns the status`() = withServer(status = 204) { uri, requests ->
        val transport = JdkAnalyticsTransport(uri) { HttpClient.newHttpClient() }
        assertEquals(204, transport.post("""{"snapshot":0}"""))
        val request = requests.poll(2, TimeUnit.SECONDS)!!
        assertEquals("POST /v1/session application/json {\"snapshot\":0}", request)
    }

    @Test
    fun `returns 410 as a status`() = withServer(status = 410) { uri, _ ->
        assertEquals(410, JdkAnalyticsTransport(uri) { HttpClient.newHttpClient() }.post("{}"))
    }

    @Test
    fun `does not follow a redirect of the given client`() = withServer(status = 307) { uri, _ ->
        val client = { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build() }
        assertEquals(307, JdkAnalyticsTransport(uri, client = client).post("{}"))
    }

    @Test
    fun `throws IOException when there is no response in time`() = withServer(status = 204, delayMs = 2_000) { uri, _ ->
        val transport = JdkAnalyticsTransport(uri, Duration.ofMillis(200)) { HttpClient.newHttpClient() }
        assertFailsWith<IOException> { transport.post("{}") }
    }

    @Test
    fun `makes the client only at the first send`() {
        var made = 0
        JdkAnalyticsTransport(URI("https://analytics.example.test/v1/session")) { made++; HttpClient.newHttpClient() }
        assertEquals(0, made)
    }

    private fun withServer(status: Int, delayMs: Long = 0, block: (URI, LinkedBlockingQueue<String>) -> Unit) {
        val requests = LinkedBlockingQueue<String>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            requests.put("${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Content-Type")} $body")
            if (delayMs > 0) Thread.sleep(delayMs)
            if (status == 307) exchange.responseHeaders.add("Location", "https://analytics.example.test/other")
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
        try {
            block(URI("http://127.0.0.1:${server.address.port}/v1/session"), requests)
        } finally {
            server.stop(0)
        }
    }
}
