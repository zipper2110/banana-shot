package org.litvin.license.online

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.Socket
import java.net.http.HttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * The trust for the HTTPS requests to the update server ("Trust for the HTTPS connection" in
 * `build-expiry-spec.md`; E5-S4). A certificate is accepted if one of [stores] trusts it. No certificate pinning.
 *
 * Production gives the store of the bundled runtime and `Windows-ROOT`. The Windows store is necessary for offices
 * that inspect TLS traffic. The bundled store is necessary because `Windows-ROOT` can miss a root that GitHub uses.
 * Tests give their own stores.
 */
class UpdateTrust(stores: List<KeyStore>) {
    /** With no store, the default trust of the runtime applies. */
    private val trustManagers: List<X509ExtendedTrustManager> =
        if (stores.isEmpty()) listOf(trustManagerOf(null)) else stores.map(::trustManagerOf)

    val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(AnyStoreTrustManager(trustManagers)), null)
    }

    /** A client for the update server. The proxy comes from `java.net.useSystemProxies` in `main`. */
    fun client(redirect: HttpClient.Redirect, connectTimeout: Duration, version: HttpClient.Version? = null): HttpClient =
        HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(connectTimeout)
            .followRedirects(redirect)
            .apply { if (version != null) version(version) }
            .build()

    companion object {
        private val logger = KotlinLogging.logger {}

        /**
         * The bundled store and `Windows-ROOT`, loaded at the first use. A store that does not load goes to the log
         * only (fail open): the request then uses the other store. With no store, the request uses the default
         * trust of the runtime.
         */
        val production: UpdateTrust by lazy { UpdateTrust(productionStores()) }

        /** The bundled store and `Windows-ROOT`, without each store that does not load. */
        internal fun productionStores(): List<KeyStore> = listOfNotNull(
            // The certificates of cacerts are not encrypted, so the load needs no password.
            load("bundled store") { KeyStore.getInstance(File(System.getProperty("java.home"), "lib/security/cacerts"), null as CharArray?) },
            load("Windows-ROOT") { KeyStore.getInstance("Windows-ROOT").apply { load(null, null) } },
        )

        private fun load(name: String, block: () -> KeyStore): KeyStore? = try {
            block().also { logger.info { "Update trust: $name has ${it.size()} certificates" } }
        } catch (failure: Exception) {
            logger.warn(failure) { "Update trust: $name does not load. The request uses the other store." }
            null
        }

        /** A null [store] gives the default trust of the runtime. */
        private fun trustManagerOf(store: KeyStore?): X509ExtendedTrustManager {
            val factory = TrustManagerFactory.getInstance("PKIX")
            factory.init(store)
            return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().single()
        }
    }
}

/**
 * Accepts a server certificate if one of [delegates] accepts it. Each delegate is a PKIX trust manager of the
 * runtime. The [SSLEngine] and [Socket] variants also check the host name, so the composite keeps that check.
 */
private class AnyStoreTrustManager(private val delegates: List<X509ExtendedTrustManager>) : X509ExtendedTrustManager() {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) =
        firstAccepts { it.checkServerTrusted(chain, authType, engine) }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) =
        firstAccepts { it.checkServerTrusted(chain, authType, socket) }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        firstAccepts { it.checkServerTrusted(chain, authType) }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) =
        firstAccepts { it.checkClientTrusted(chain, authType, engine) }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) =
        firstAccepts { it.checkClientTrusted(chain, authType, socket) }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        firstAccepts { it.checkClientTrusted(chain, authType) }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        delegates.flatMap { it.acceptedIssuers.asList() }.distinct().toTypedArray()

    private fun firstAccepts(check: (X509ExtendedTrustManager) -> Unit) {
        var firstFailure: CertificateException? = null
        for (delegate in delegates) {
            try {
                check(delegate)
                return
            } catch (failure: CertificateException) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        throw firstFailure ?: CertificateException("No trust store is available")
    }
}
