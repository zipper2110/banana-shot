package org.litvin.license.online

import org.litvin.license.VersionRulesParse
import org.litvin.license.VersionRulesParser
import org.litvin.license.time.RunTimeCounter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E5-S3. A local server gives the exact headers. No test reads GitHub. */
class HttpRulesFetcherTest {
    private val date = "Sat, 03 Oct 2026 10:00:00 GMT"
    private val dateInstant = Instant.parse("2026-10-03T10:00:00Z")
    private val validFile = """{"schema":1,"rules":[]}""".toByteArray()

    private fun fetcher(url: String, timeout: Duration = Duration.ofSeconds(5)) = HttpRulesFetcher(
        URI(url),
        RunTimeCounter { 42_000L },
        HttpRulesFetcher.client(UpdateTrust(emptyList()), timeout),
        timeout,
    )

    private fun fetch(response: ByteArray?, timeout: Duration = Duration.ofSeconds(5)): Pair<RulesFetchResult, RawHttpServer> {
        val server = RawHttpServer { response }
        return server.use { fetcher("http://127.0.0.1:${it.port}/version-policy.json", timeout).fetch() to it }
    }

    @Test
    fun `the app waits a maximum of 10 seconds`() {
        assertEquals(Duration.ofSeconds(10), HttpRulesFetcher.TIMEOUT)
    }

    @Test
    fun `a valid file gives the body, the server time, and the counter value at the response`() {
        val (result, _) = fetch(RawHttpServer.response("200 OK", listOf("Date: $date"), validFile))

        val response = assertIs<RulesFetchResult.ServerResponse>(result)
        assertEquals(200, response.statusCode)
        assertEquals(dateInstant, response.serverTime)
        assertEquals(42_000L, response.counterAtResponse)
        assertContentEquals(validFile, response.body)
    }

    @Test
    fun `a 404 gives a server time and no body`() {
        val (result, _) = fetch(RawHttpServer.response("404 Not Found", listOf("Date: $date"), "404: Not Found".toByteArray()))

        val response = assertIs<RulesFetchResult.ServerResponse>(result)
        assertEquals(404, response.statusCode)
        assertEquals(dateInstant, response.serverTime)
        assertNull(response.body)
    }

    @Test
    fun `a redirect gives a server time, and the fetcher does not follow it`() {
        RawHttpServer { RawHttpServer.response("200 OK", listOf("Date: Sun, 01 Jan 2040 00:00:00 GMT"), validFile) }.use { other ->
            val (result, _) = fetch(
                RawHttpServer.response("302 Found", listOf("Date: $date", "Location: http://127.0.0.1:${other.port}/moved.json")),
            )

            val response = assertIs<RulesFetchResult.ServerResponse>(result)
            assertEquals(302, response.statusCode)
            assertEquals(dateInstant, response.serverTime)
            assertNull(response.body)
            assertTrue(other.requests.isEmpty(), "The host of the redirect got no request")
        }
    }

    @Test
    fun `a file that is not valid and a file with an unknown schema give a server time`() {
        for (body in listOf("not json", """{"schema":2}""")) {
            val (result, _) = fetch(RawHttpServer.response("200 OK", listOf("Date: $date"), body.toByteArray()))
            val response = assertIs<RulesFetchResult.ServerResponse>(result)
            assertEquals(dateInstant, response.serverTime, body)
            assertContentEquals(body.toByteArray(), response.body)
        }
    }

    @Test
    fun `the fetcher stops the read after 64 KB, so the parser finds a body that is too large`() {
        val (result, _) = fetch(RawHttpServer.response("200 OK", listOf("Date: $date"), ByteArray(200 * 1024) { ' '.code.toByte() }))

        val body = assertIs<RulesFetchResult.ServerResponse>(result).body!!
        assertEquals(VersionRulesParser.MAX_BODY_BYTES + 1, body.size)
        assertIs<VersionRulesParse.NotValid>(VersionRulesParser.parse(body))
    }

    @Test
    fun `a body of exactly 64 KB arrives in full`() {
        val padded = validFile + ByteArray(VersionRulesParser.MAX_BODY_BYTES - validFile.size) { ' '.code.toByte() }
        val (result, _) = fetch(RawHttpServer.response("200 OK", listOf("Date: $date"), padded))

        val body = assertIs<RulesFetchResult.ServerResponse>(result).body!!
        assertContentEquals(padded, body)
        assertIs<VersionRulesParse.Valid>(VersionRulesParser.parse(body))
    }

    @Test
    fun `the server time is Date plus Age`() {
        val (result, _) = fetch(RawHttpServer.response("200 OK", listOf("Date: $date", "Age: 120"), validFile))

        assertEquals(dateInstant.plusSeconds(120), assertIs<RulesFetchResult.ServerResponse>(result).serverTime)
    }

    @Test
    fun `an Age that is not a number, below 0, or above 3600 is ignored`() {
        assertEquals(dateInstant.plusSeconds(3600), HttpRulesFetcher.serverTime(date, "3600"))
        assertEquals(dateInstant, HttpRulesFetcher.serverTime(date, "0"))
        assertEquals(dateInstant, HttpRulesFetcher.serverTime(date, "abc"))
        assertEquals(dateInstant, HttpRulesFetcher.serverTime(date, "-5"))
        assertEquals(dateInstant, HttpRulesFetcher.serverTime(date, "3601"))
        assertEquals(dateInstant, HttpRulesFetcher.serverTime(date, null))
    }

    @Test
    fun `a response with no valid Date gives no server time`() {
        assertNull(HttpRulesFetcher.serverTime(null, "120"))
        assertNull(HttpRulesFetcher.serverTime("yesterday", null))

        val (result, _) = fetch(RawHttpServer.response("200 OK", body = validFile))
        val response = assertIs<RulesFetchResult.ServerResponse>(result)
        assertNull(response.serverTime)
        assertContentEquals(validFile, response.body)
    }

    @Test
    fun `a server that does not answer gives no connection after the timeout`() {
        val started = System.nanoTime()
        val (result, server) = fetch(null, Duration.ofMillis(300))

        assertIs<RulesFetchResult.NoConnection>(result)
        assertEquals(1, server.requests.size)
        val elapsed = Duration.ofNanos(System.nanoTime() - started)
        assertTrue(elapsed < Duration.ofSeconds(5), "The fetcher stopped after $elapsed")
    }

    @Test
    fun `a failed connection gives no connection`() {
        val closedPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

        assertIs<RulesFetchResult.NoConnection>(fetcher("http://127.0.0.1:$closedPort/version-policy.json").fetch())
    }

    @Test
    fun `the request sends no user ID and no analytics data`() {
        val (_, server) = fetch(RawHttpServer.response("200 OK", listOf("Date: $date"), validFile))

        val request = server.requests.single()
        assertEquals("GET /version-policy.json HTTP/1.1", request.requestLine)
        val allowed = setOf("host", "user-agent", "connection", "content-length")
        assertTrue(request.headers.keys.all { it.lowercase() in allowed }, "Headers: ${request.headers}")
    }
}
