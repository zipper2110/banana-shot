package org.litvin.analytics

import java.util.concurrent.CopyOnWriteArrayList

/** Records the events of a feature for its tests. */
class RecordingAnalytics : Analytics {
    val events = CopyOnWriteArrayList<AnalyticsEvent>()

    override fun record(event: AnalyticsEvent) {
        events += event
    }

    fun count(event: AnalyticsEvent): Int = events.count { it == event }
}
