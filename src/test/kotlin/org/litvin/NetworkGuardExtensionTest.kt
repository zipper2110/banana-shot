package org.litvin

import org.junit.jupiter.api.extension.ExtensionContext
import org.litvin.license.online.HttpRulesFetcher
import java.io.IOException
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** E5-S1. Each test that makes a guarded request takes the record, so the guard does not fail the test. */
class NetworkGuardExtensionTest {
    @Test
    fun `the test run loads the guard automatically`() {
        assertTrue(NetworkGuard.isInstalled)
    }

    @Test
    fun `the guard covers the host of the rules file`() {
        assertTrue(URI(HttpRulesFetcher.RULES_URL).host in NetworkGuard.GUARDED_HOSTS)
    }

    @Test
    fun `the guard records and stops an HttpClient request to the host of the rules file`() {
        val uri = URI("https://raw.githubusercontent.com/zipper2110/banana-shot/master/release/version-policy.json")
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

        val started = System.nanoTime()
        assertFailsWith<IOException> {
            client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.discarding())
        }

        assertEquals(listOf(uri), NetworkGuard.takeRecorded())
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(3), "The guard stops the request fast")
    }

    @Test
    fun `the guard records and stops a URLConnection request`() {
        val url = URL("https://github.com/zipper2110/banana-shot/releases/latest")

        assertFailsWith<IOException> {
            (url.openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }).getInputStream().close()
        }

        assertEquals(listOf(url.toURI()), NetworkGuard.takeRecorded())
    }

    @Test
    fun `after a test with a guarded request the extension fails the test`() {
        java.net.ProxySelector.getDefault().select(URI("https://raw.githubusercontent.com/x"))
        // The extension does not read the context.
        val context = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ExtensionContext::class.java)) { _, _, _ ->
            throw UnsupportedOperationException()
        } as ExtensionContext

        val failure = assertFailsWith<AssertionError> { NetworkGuardExtension().afterEach(context) }

        assertTrue(failure.message!!.contains("raw.githubusercontent.com"), failure.message)
        assertTrue(NetworkGuard.takeRecorded().isEmpty(), "The extension takes the record")
    }

    @Test
    fun `a request to another host does not use the guard`() {
        assertEquals(listOf(java.net.Proxy.NO_PROXY), java.net.ProxySelector.getDefault().select(URI("http://127.0.0.1:1/")))
        assertTrue(NetworkGuard.takeRecorded().isEmpty())
    }
}
