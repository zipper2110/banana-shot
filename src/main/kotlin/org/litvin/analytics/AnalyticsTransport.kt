package org.litvin.analytics

import java.io.IOException

/** Sends one session summary. [EnabledAnalytics] calls it only on its own daemon thread. */
fun interface AnalyticsTransport {
    /** Returns the HTTP status. Throws [IOException] when there is no response. */
    @Throws(IOException::class, InterruptedException::class)
    fun post(body: String): Int
}
