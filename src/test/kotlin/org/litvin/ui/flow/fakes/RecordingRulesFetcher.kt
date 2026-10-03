package org.litvin.ui.flow.fakes

import org.litvin.license.TestExpiry
import org.litvin.license.online.RulesFetchResult
import org.litvin.license.online.RulesFetcher
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The read of the rules file in the UI-flow tests (E7-S4, E8-S9). It records each call. By default it gives
 * "no connection". A test can set a server response, and can hold a read until it calls [release].
 */
class RecordingRulesFetcher : RulesFetcher {
    private val count = AtomicInteger()

    val calls: Int get() = count.get()

    /** The result of the next reads. */
    @Volatile
    var response: () -> RulesFetchResult = { RulesFetchResult.NoConnection("ui flow: no network") }

    @Volatile
    private var gate: CountDownLatch? = null

    /** The next reads wait until [release], for a maximum of 30 seconds. */
    fun hold() {
        gate = CountDownLatch(1)
    }

    fun release() {
        gate?.countDown()
        gate = null
    }

    /** The server answers with [rulesJson] and the server time now. */
    fun online(rulesJson: String = EMPTY_RULES) {
        response = {
            RulesFetchResult.ServerResponse(200, Instant.now(), TestExpiry.COUNTER.millis(), rulesJson.toByteArray())
        }
    }

    fun offline() {
        response = { RulesFetchResult.NoConnection("ui flow: no network") }
    }

    override fun fetch(): RulesFetchResult {
        count.incrementAndGet()
        gate?.await(30, TimeUnit.SECONDS)
        return response()
    }

    companion object {
        const val EMPTY_RULES = """{"schema":1,"rules":[]}"""
    }
}
