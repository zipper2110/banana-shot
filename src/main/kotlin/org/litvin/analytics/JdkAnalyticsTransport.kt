package org.litvin.analytics

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Sends a summary with the JDK HTTP client. [client] makes the client at the first send, on the analytics thread.
 * The `app` package gives a client with the proxy of the system and the trust of E5-S4. The client follows no
 * redirect. The transport never reads the response body.
 */
class JdkAnalyticsTransport(
    private val endpoint: URI,
    private val timeout: Duration = TIMEOUT,
    client: () -> HttpClient,
) : AnalyticsTransport {
    private val client by lazy(client)

    override fun post(body: String): Int {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
