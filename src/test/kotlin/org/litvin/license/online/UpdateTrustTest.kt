package org.litvin.license.online

import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.litvin.license.time.RunTimeCounter
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.time.Duration
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * E5-S4. The test makes its own two key stores (in place of the bundled store and a system store) and uses a local
 * HTTPS server. It does not change the real Windows store.
 */
class UpdateTrustTest {
    private companion object {
        val certificates = TestCertificates.create()
        val bundledOnly = certificates.ServerKey("bundled-only", "dns:localhost,ip:127.0.0.1")
        val windowsOnly = certificates.ServerKey("windows-only", "dns:localhost,ip:127.0.0.1")
        val untrusted = certificates.ServerKey("untrusted", "dns:localhost,ip:127.0.0.1")
        val otherHost = certificates.ServerKey("other-host", "dns:other.example")

        /** The two stores of production: the bundled store and the Windows store. */
        val trust = UpdateTrust(
            listOf(
                TestCertificates.trustStore(bundledOnly.certificate, otherHost.certificate),
                TestCertificates.trustStore(windowsOnly.certificate),
            ),
        )
    }

    private fun get(server: TestCertificates.ServerKey, trust: UpdateTrust = UpdateTrustTest.trust): Int =
        RawHttpServer(server.serverContext()) { RawHttpServer.response("200 OK") }.use { https ->
            val client = trust.client(java.net.http.HttpClient.Redirect.NEVER, Duration.ofSeconds(5))
            val request = HttpRequest.newBuilder(URI("https://localhost:${https.port}/")).timeout(Duration.ofSeconds(5)).build()
            client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
        }

    @Test
    fun `the request accepts a certificate that only the bundled store trusts`() {
        assertEquals(200, get(bundledOnly))
    }

    @Test
    fun `the request accepts a certificate that only the Windows store trusts`() {
        assertEquals(200, get(windowsOnly))
    }

    @Test
    fun `the request rejects a certificate that neither store trusts`() {
        assertFailsWith<SSLHandshakeException> { get(untrusted) }
    }

    @Test
    fun `the request rejects a trusted certificate for a different host`() {
        assertFailsWith<SSLHandshakeException> { get(otherHost) }
    }

    @Test
    fun `the order of the stores has no effect`() {
        val reversed = UpdateTrust(
            listOf(TestCertificates.trustStore(windowsOnly.certificate), TestCertificates.trustStore(bundledOnly.certificate)),
        )
        assertEquals(200, get(bundledOnly, reversed))
        assertEquals(200, get(windowsOnly, reversed))
    }

    @Test
    fun `a TLS error gives no connection and no server time`() {
        RawHttpServer(untrusted.serverContext()) { RawHttpServer.response("200 OK") }.use { https ->
            val fetcher = HttpRulesFetcher(
                URI("https://localhost:${https.port}/version-policy.json"),
                RunTimeCounter { 0L },
                HttpRulesFetcher.client(trust, Duration.ofSeconds(5)),
                Duration.ofSeconds(5),
            )
            val result = assertIs<RulesFetchResult.NoConnection>(fetcher.fetch())
            assertTrue(result.reason.contains("SSL"), result.reason)
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `production has the bundled store and the Windows store of this runtime`() {
        // Reads the stores only. The test does not change them.
        val stores: List<KeyStore> = UpdateTrust.productionStores()
        assertEquals(2, stores.size)
        assertTrue(stores.all { it.size() > 0 }, "Each store has certificates")
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `production has the bundled store and the macOS system roots of this runtime`() {
        // Reads the stores only. The test does not change them. KeychainStore can be empty, so it is not counted.
        val stores: List<KeyStore> = UpdateTrust.productionStores()
        assertTrue(stores.size >= 2, "Stores: ${stores.map { it.type }}")
        assertTrue(stores.any { it.type == "KeychainStore-ROOT" }, "Stores: ${stores.map { it.type }}")
        assertTrue(stores.all { it.size() > 0 }, "Each store has certificates")
    }

    @Test
    fun `only the system store types of this runtime are tried`() {
        assertEquals(listOf("Windows-ROOT"), UpdateTrust.systemStoreTypes(setOf("PKCS12", "WINDOWS-ROOT", "WINDOWS-MY")))
        assertEquals(
            listOf("KeychainStore-ROOT", "KeychainStore"),
            UpdateTrust.systemStoreTypes(setOf("PKCS12", "KEYCHAINSTORE", "KEYCHAINSTORE-ROOT")),
        )
        assertEquals(emptyList(), UpdateTrust.systemStoreTypes(setOf("PKCS12", "JKS")))
    }

    @Test
    fun `with no system store the bundled store is used`() {
        val stores = UpdateTrust.productionStores(available = setOf("PKCS12"))
        assertEquals(1, stores.size)
        assertTrue(stores.single().size() > 0)
    }
}
