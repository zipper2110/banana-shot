package org.litvin

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The network guard for the test run ("Tests", test isolation, in `build-expiry-spec.md`; E5-S1).
 *
 * JUnit loads this extension for each test class (`META-INF/services` and `junit-platform.properties`). It sets a
 * default [ProxySelector] that records and stops each request to a guarded host. After each test, it fails the
 * test if a request was recorded. The app shows no error for a failed request (fail open), so a selector that only
 * stops the request cannot find the mistake.
 *
 * `HttpClient` reads the default selector when the client is built. Thus, a client that a test builds after the
 * first test class starts uses the guard.
 */
class NetworkGuardExtension : BeforeAllCallback, AfterEachCallback {
    override fun beforeAll(context: ExtensionContext) {
        NetworkGuard.install()
    }

    override fun afterEach(context: ExtensionContext) {
        val requests = NetworkGuard.takeRecorded()
        if (requests.isNotEmpty()) {
            throw AssertionError("The test sent a request to a guarded host. Give the code a fake: $requests")
        }
    }
}

object NetworkGuard {
    /**
     * The host of the rules file (`HttpRulesFetcher.RULES_URL`), and the host of the setup download of "Update and
     * restart". `NetworkGuardExtensionTest` checks that the first host is the host of the rules URL.
     */
    val GUARDED_HOSTS: Set<String> = setOf("raw.githubusercontent.com", "github.com")

    private val recorded = CopyOnWriteArrayList<URI>()

    @Volatile
    private var selector: GuardSelector? = null

    @Synchronized
    fun install() {
        if (selector != null && ProxySelector.getDefault() === selector) return
        selector = GuardSelector(ProxySelector.getDefault(), Stopper.address).also { ProxySelector.setDefault(it) }
    }

    val isInstalled: Boolean get() = selector != null && ProxySelector.getDefault() === selector

    /** The requests to a guarded host since the last call. The list is then empty. */
    fun takeRecorded(): List<URI> {
        val requests = recorded.toList()
        recorded.removeAll(requests.toSet())
        return requests
    }

    private class GuardSelector(private val previous: ProxySelector?, private val stopper: InetSocketAddress) : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            if (uri.host?.lowercase() in GUARDED_HOSTS) {
                recorded += uri
                return listOf(Proxy(Proxy.Type.HTTP, stopper))
            }
            return previous?.select(uri) ?: listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI, address: SocketAddress, failure: IOException) {
            if (address != stopper) previous?.connectFailed(uri, address, failure)
        }
    }

    /**
     * A local "proxy" that closes each connection at once. The request then fails fast. A closed port is slow on
     * Windows, because Windows tries the connection again for about 2 seconds.
     */
    private object Stopper {
        private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val address = InetSocketAddress(InetAddress.getLoopbackAddress(), socket.localPort)

        init {
            Thread({
                while (true) {
                    try {
                        socket.accept().close()
                    } catch (_: IOException) {
                        // Continue. The socket stays open until the test JVM exits.
                    }
                }
            }, "network-guard-stopper").apply { isDaemon = true }.start()
        }
    }
}
