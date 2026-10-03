package org.litvin.license.online

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.license.VersionRulesParser
import org.litvin.license.time.RunTimeCounter
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/** The result of one read of the rules file ("Version rules file" and "Time and the clock" in `build-expiry-spec.md`). */
sealed interface RulesFetchResult {
    /** No server response: no network, a timeout, or a TLS error. No server time. */
    data class NoConnection(val reason: String) : RulesFetchResult

    /**
     * A response from the host of the rules file, with any HTTP status. A 404 and a redirect are also server
     * responses.
     *
     * @property serverTime `Date` plus a valid `Age`. Null only when the response has no valid `Date` header.
     * @property counterAtResponse the [RunTimeCounter] value when the response arrived. `TimeEngine.serverTime`
     *   uses it for the anchor.
     * @property body the body of an HTTP 200 response, at most [VersionRulesParser.MAX_BODY_BYTES] + 1 bytes (so the
     *   parser can see a body that is too large). Null for each other status.
     */
    class ServerResponse(
        val statusCode: Int,
        val serverTime: Instant?,
        val counterAtResponse: Long,
        val body: ByteArray?,
    ) : RulesFetchResult {
        override fun toString(): String =
            "ServerResponse(statusCode=$statusCode, serverTime=$serverTime, counterAtResponse=$counterAtResponse, " +
                "body=${body?.size?.let { "$it bytes" }})"
    }
}

/** One read of the rules file. Tests give a fake. */
fun interface RulesFetcher {
    fun fetch(): RulesFetchResult
}

/**
 * The read of the rules file with `HttpClient` (E5-S3). It waits a maximum of [timeout] in total and stops the read
 * of the body after the limit. It does not follow redirects: the server time must always come from the host of the
 * rules file. The request sends no user ID and no analytics data.
 */
class HttpRulesFetcher(
    private val url: URI,
    private val counter: RunTimeCounter,
    private val client: HttpClient = client(UpdateTrust.production),
    private val timeout: Duration = TIMEOUT,
) : RulesFetcher {
    override fun fetch(): RulesFetchResult = read().also { logger.info { "Rules file read: $it" } }

    private fun read(): RulesFetchResult {
        val request = HttpRequest.newBuilder(url).GET().timeout(timeout).build()
        val counterAtResponse = AtomicLong()
        val handler = HttpResponse.BodyHandler<ByteArray?> { info ->
            counterAtResponse.set(counter.millis())
            if (info.statusCode() == 200) {
                LimitedBodySubscriber(VersionRulesParser.MAX_BODY_BYTES + 1)
            } else {
                HttpResponse.BodySubscribers.replacing(null)
            }
        }
        val future = client.sendAsync(request, handler)
        val response = try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            return RulesFetchResult.NoConnection("no response in ${timeout.toMillis()} ms")
        } catch (failure: ExecutionException) {
            val cause = failure.cause ?: failure
            return RulesFetchResult.NoConnection("${cause.javaClass.simpleName}: ${cause.message}")
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            return RulesFetchResult.NoConnection("interrupted")
        }
        return RulesFetchResult.ServerResponse(
            statusCode = response.statusCode(),
            serverTime = serverTime(response.headers().firstValue("Date").orElse(null), response.headers().firstValue("Age").orElse(null)),
            counterAtResponse = counterAtResponse.get(),
            body = response.body(),
        )
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** The app waits a maximum of 10 seconds for the file. */
        val TIMEOUT: Duration = Duration.ofSeconds(10)

        /** The largest `Age` that the app uses. A larger value cannot move the time far forward. */
        const val MAX_AGE_SECONDS = 3600L

        /** The raw GitHub URL of `release/version-policy.json` on `master`. Only the production setup uses it (E7-S3). */
        const val RULES_URL = "https://raw.githubusercontent.com/zipper2110/banana-shot/master/release/version-policy.json"

        /** No redirects, and HTTP/1.1: the file is small, and a test server can answer it. */
        fun client(trust: UpdateTrust, timeout: Duration = TIMEOUT): HttpClient =
            trust.client(HttpClient.Redirect.NEVER, timeout, HttpClient.Version.HTTP_1_1)

        /**
         * `Date` plus `Age`. GitHub keeps the file in a cache, and `Age` is the time in seconds in the cache. An `Age`
         * that is not a number from 0 to [MAX_AGE_SECONDS] is ignored. No valid `Date` gives null.
         */
        fun serverTime(date: String?, age: String?): Instant? {
            val dateTime = try {
                date?.let { ZonedDateTime.parse(it.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            } catch (failure: DateTimeParseException) {
                logger.warn { "The Date header of the rules file is not valid: $date" }
                null
            } ?: return null
            val ageSeconds = age?.trim()?.toLongOrNull()?.takeIf { it in 0..MAX_AGE_SECONDS } ?: 0L
            return dateTime.plusSeconds(ageSeconds)
        }
    }
}

/**
 * Keeps the first [limit] bytes of the body, then stops the read. The rest of the body does not arrive.
 */
private class LimitedBodySubscriber(private val limit: Int) : HttpResponse.BodySubscriber<ByteArray?> {
    private val result = CompletableFuture<ByteArray?>()
    private val bytes = ByteArrayOutputStream()
    private var subscription: Flow.Subscription? = null

    override fun getBody(): CompletableFuture<ByteArray?> = result

    override fun onSubscribe(subscription: Flow.Subscription) {
        this.subscription = subscription
        subscription.request(Long.MAX_VALUE)
    }

    override fun onNext(item: List<ByteBuffer>) {
        if (result.isDone) return
        for (buffer in item) {
            val count = minOf(buffer.remaining(), limit - bytes.size())
            val chunk = ByteArray(count)
            buffer.get(chunk)
            bytes.write(chunk)
            if (bytes.size() >= limit) {
                result.complete(bytes.toByteArray())
                subscription?.cancel()
                return
            }
        }
    }

    override fun onError(throwable: Throwable) {
        result.completeExceptionally(throwable)
    }

    override fun onComplete() {
        result.complete(bytes.toByteArray())
    }
}
