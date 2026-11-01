package org.litvin.license.online

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.Socket
import java.net.http.HttpClient
import java.security.KeyStore
import java.security.Security
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
 * Production gives the store of the bundled runtime and the system stores (`Windows-ROOT` on Windows,
 * `KeychainStore-ROOT` and `KeychainStore` on macOS). The system store is necessary for offices that inspect TLS
 * traffic. The bundled store is necessary because `Windows-ROOT` can miss a root that GitHub uses.
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
         * The system key stores, in the order of the tries:
         * - `Windows-ROOT`: the Windows roots, with the roots that an office adds (module `jdk.crypto.mscapi`).
         * - `KeychainStore-ROOT`: the macOS system roots (from JDK 23).
         * - `KeychainStore`: the certificates of the macOS keychains with a user or administrator trust setting.
         *   An office adds its root here (from JDK 23).
         */
        internal val SYSTEM_STORE_TYPES = listOf("Windows-ROOT", "KeychainStore-ROOT", "KeychainStore")

        /**
         * The bundled store and the system stores, loaded at the first use. A store that does not load goes to the
         * log only (fail open): the request then uses the other stores. With no store, the request uses the default
         * trust of the runtime.
         */
        val production: UpdateTrust by lazy { UpdateTrust(productionStores()) }

        /** The bundled store and each system store of this runtime, without each store that does not load or is empty. */
        internal fun productionStores(available: Set<String> = availableStoreTypes()): List<KeyStore> {
            val systemTypes = systemStoreTypes(available)
            if (systemTypes.isEmpty()) {
                logger.warn { "Update trust: this runtime has no system trust store. The request uses the bundled store only." }
            }
            return listOfNotNull(
                // The certificates of cacerts are not encrypted, so the load needs no password.
                load("bundled store") { KeyStore.getInstance(File(System.getProperty("java.home"), "lib/security/cacerts"), null as CharArray?) },
            ) + systemTypes.mapNotNull { type -> load(type) { KeyStore.getInstance(type).apply { load(null, null) } } }
        }

        /**
         * The types of [SYSTEM_STORE_TYPES] that this runtime has. Each platform has only its own types, so the app
         * does not read `os.name` to select them (the expiry reads no property).
         */
        internal fun systemStoreTypes(available: Set<String>): List<String> =
            SYSTEM_STORE_TYPES.filter { type -> available.any { it.equals(type, ignoreCase = true) } }

        private fun availableStoreTypes(): Set<String> = Security.getAlgorithms("KeyStore")

        private fun load(name: String, block: () -> KeyStore): KeyStore? = try {
            val store = block()
            logger.info { "Update trust: $name has ${store.size()} certificates" }
            // An empty store trusts no certificate. Thus the request does not use it.
            store.takeIf { it.size() > 0 }
        } catch (failure: Exception) {
            logger.warn(failure) { "Update trust: $name does not load. The request uses the other stores." }
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
