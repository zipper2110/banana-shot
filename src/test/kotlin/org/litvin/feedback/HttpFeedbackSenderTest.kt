package org.litvin.feedback

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.litvin.license.online.TestCertificates
import org.litvin.license.online.UpdateTrust
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T2 of B-8. A local server answers. No test sends a request to a real host. */
class HttpFeedbackSenderTest {
    private data class Received(val method: String, val path: String, val contentType: String?, val body: String)

    private val received = CopyOnWriteArrayList<Received>()
    private val report = FeedbackReport(
        UUID.fromString("00000000-0000-4000-8000-000000000401"), FeedbackTopic.IDEA, "An idea", null,
        FeedbackSystemInfo("1.0.0", "Windows 11", "10.0", "21.0.4"), null, "log text\n",
    )

    private fun record(exchange: HttpExchange) {
        received += Received(
            exchange.requestMethod,
            exchange.requestURI.path,
            exchange.requestHeaders.getFirst("Content-Type"),
            exchange.requestBody.readBytes().toString(Charsets.UTF_8),
        )
    }

    private fun serve(status: Int, block: (URI) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            record(exchange)
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
        try {
            block(URI("http://127.0.0.1:${server.address.port}/v1/feedback"))
        } finally {
            server.stop(0)
        }
    }

    private fun sendWith(status: Int): FeedbackSendResult {
        var result: FeedbackSendResult? = null
        serve(status) { result = HttpFeedbackSender(it, HttpClient.newHttpClient(), Duration.ofSeconds(5)).send(report) }
        return result!!
    }

    @Test
    fun `201 and 200 mean that the author has the report`() {
        assertEquals(FeedbackSendResult.Sent, sendWith(201))
        assertEquals(FeedbackSendResult.Sent, sendWith(200))
    }

    @Test
    fun `each other status gives its failure`() {
        mapOf(
            410 to FeedbackFailure.DISABLED,
            429 to FeedbackFailure.RATE_LIMITED,
            503 to FeedbackFailure.SERVER,
            500 to FeedbackFailure.SERVER,
            422 to FeedbackFailure.REFUSED,
            413 to FeedbackFailure.REFUSED,
            404 to FeedbackFailure.REFUSED,
        ).forEach { (status, failure) -> assertEquals(FeedbackSendResult.Failed(failure), sendWith(status), "HTTP $status") }
    }

    @Test
    fun `only a refused report cannot succeed with a new try`() {
        assertEquals(listOf(FeedbackFailure.REFUSED), FeedbackFailure.entries.filter { !it.canTryAgain })
    }

    @Test
    fun `the request is a JSON POST of the report to the endpoint`() {
        sendWith(201)
        val request = received.single()
        assertEquals("POST", request.method)
        assertEquals("/v1/feedback", request.path)
        assertTrue(request.contentType!!.startsWith("application/json"))
        assertEquals(report.toJson(), request.body)
        assertTrue(FeedbackContract.isValid(request.body))
    }

    @Test
    fun `no server gives no connection`() {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val result = HttpFeedbackSender(URI("http://127.0.0.1:$port/v1/feedback"), HttpClient.newHttpClient(), Duration.ofSeconds(5)).send(report)
        assertEquals(FeedbackSendResult.Failed(FeedbackFailure.NO_CONNECTION), result)
    }

    @Test
    fun `a server that does not answer gives no connection after the timeout`() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { Thread.sleep(5_000) }
        server.start()
        try {
            val sender = HttpFeedbackSender(URI("http://127.0.0.1:${server.address.port}/v1/feedback"), HttpClient.newHttpClient(), Duration.ofMillis(300))
            assertEquals(FeedbackSendResult.Failed(FeedbackFailure.NO_CONNECTION), sender.send(report))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `HTTPS works with the trust of E5-S4 and fails with a certificate that no store trusts`() {
        val certificates = TestCertificates.create()
        val key = certificates.ServerKey("feedback", "dns:localhost,ip:127.0.0.1")
        val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.httpsConfigurator = HttpsConfigurator(key.serverContext())
        server.createContext("/") { exchange ->
            record(exchange)
            exchange.sendResponseHeaders(201, -1)
            exchange.close()
        }
        server.start()
        try {
            val url = URI("https://localhost:${server.address.port}/v1/feedback")
            fun client(trust: UpdateTrust) = trust.client(HttpClient.Redirect.NEVER, Duration.ofSeconds(5))
            val trusted = UpdateTrust(listOf(TestCertificates.trustStore(key.certificate)))
            assertEquals(FeedbackSendResult.Sent, HttpFeedbackSender(url, client(trusted)).send(report))
            val other = certificates.ServerKey("other", "dns:localhost")
            val untrusted = UpdateTrust(listOf(TestCertificates.trustStore(other.certificate)))
            assertEquals(FeedbackSendResult.Failed(FeedbackFailure.NO_CONNECTION), HttpFeedbackSender(url, client(untrusted)).send(report))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `the endpoint must be an https URL that ends in v1 feedback`() {
        assertEquals(URI("https://feedback.example.test/v1/feedback"), FeedbackBuildConfig.endpoint("https://feedback.example.test/v1/feedback"))
        assertEquals(URI("https://example.test/api/v1/feedback"), FeedbackBuildConfig.endpoint(" https://example.test/api/v1/feedback "))
        listOf(
            null, "", "not a url", "http://feedback.example.test/v1/feedback", "https://feedback.example.test/v1/events/batch",
            "https://feedback.example.test/v1/feedback/", "https://user@feedback.example.test/v1/feedback",
            "https://feedback.example.test/v1/feedback?x=1", "https://feedback.example.test/v1/feedback#x", "https:///v1/feedback",
        ).forEach { assertNull(FeedbackBuildConfig.endpoint(it), it.toString()) }
    }
}
