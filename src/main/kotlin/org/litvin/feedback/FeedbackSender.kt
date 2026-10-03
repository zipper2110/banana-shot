package org.litvin.feedback

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/** The result of one send. */
sealed interface FeedbackSendResult {
    /** The Worker has the report (`201`), or it had it before (`200`). */
    data object Sent : FeedbackSendResult

    data class Failed(val failure: FeedbackFailure) : FeedbackSendResult
}

enum class FeedbackFailure(val canTryAgain: Boolean) {
    /** No response: no network, a timeout, a proxy, or a TLS error. */
    NO_CONNECTION(true),

    /** A `5xx` response. The Worker could not keep or deliver the report. */
    SERVER(true),

    /** `410`: the server does not accept reports now. */
    DISABLED(true),

    /** `429`: too many reports from this network in this hour. */
    RATE_LIMITED(true),

    /** Another response, for example `422`. The same request cannot succeed. */
    REFUSED(false),
}

/** Sends one report. It blocks, so the caller runs it in the background. Tests give a fake. */
fun interface FeedbackSender {
    fun send(report: FeedbackReport): FeedbackSendResult
}

/**
 * Sends a report to the feedback Worker (T1 of B-8). The caller gives [client] with the proxy and the trust of E5-S4.
 * The request contains only the report.
 */
class HttpFeedbackSender(
    private val endpoint: URI,
    private val client: HttpClient,
    private val timeout: Duration = TIMEOUT,
) : FeedbackSender {
    override fun send(report: FeedbackReport): FeedbackSendResult {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(report.toJson(), Charsets.UTF_8))
            .build()
        val status = try {
            client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
        } catch (failure: HttpTimeoutException) {
            logger.warn { "Feedback ${report.reportId}: no response in ${timeout.toSeconds()} s" }
            return FeedbackSendResult.Failed(FeedbackFailure.NO_CONNECTION)
        } catch (failure: IOException) {
            logger.warn { "Feedback ${report.reportId}: ${failure.javaClass.simpleName}: ${failure.message}" }
            return FeedbackSendResult.Failed(FeedbackFailure.NO_CONNECTION)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return FeedbackSendResult.Failed(FeedbackFailure.NO_CONNECTION)
        }
        logger.info { "Feedback ${report.reportId}: HTTP $status" }
        return resultOf(status)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** A report with a log can be some hundred KB, so the timeout is longer than for the rules file. */
        val TIMEOUT: Duration = Duration.ofSeconds(30)

        fun resultOf(status: Int): FeedbackSendResult = when {
            status == 200 || status == 201 -> FeedbackSendResult.Sent
            status == 410 -> FeedbackSendResult.Failed(FeedbackFailure.DISABLED)
            status == 429 -> FeedbackSendResult.Failed(FeedbackFailure.RATE_LIMITED)
            status >= 500 -> FeedbackSendResult.Failed(FeedbackFailure.SERVER)
            else -> FeedbackSendResult.Failed(FeedbackFailure.REFUSED)
        }
    }
}

/** The build setting of the endpoint (T2 of B-8). */
object FeedbackBuildConfig {
    const val ENDPOINT_PROPERTY = "bananashot.feedback.endpoint"
    const val PATH = "/v1/feedback"

    fun endpointFromSystemProperties(): URI? = endpoint(System.getProperty(ENDPOINT_PROPERTY))

    /** The endpoint, or null when [value] is not an `https` URL that ends in [PATH]. Then the form cannot send. */
    fun endpoint(value: String?): URI? = runCatching { value?.trim()?.let(::URI) }.getOrNull()?.takeIf {
        it.scheme == "https" && !it.isOpaque && !it.host.isNullOrEmpty() && it.userInfo == null &&
            it.rawQuery == null && it.rawFragment == null && it.rawPath.endsWith(PATH)
    }
}
