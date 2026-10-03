package org.litvin.analytics

/** A monotonic clock. Tests give a fake clock. */
fun interface AnalyticsClock {
    fun nanoTime(): Long
}

object SystemAnalyticsClock : AnalyticsClock {
    override fun nanoTime(): Long = System.nanoTime()
}
