package org.litvin.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.TreeMap

/**
 * The thread-safe counters of one analytics session. A key must be in [AnalyticsSchema.COUNTER_KEYS]. Each value
 * stops at [AnalyticsSchema.MAX_COUNTER_VALUE].
 *
 * Time counters (`tab_s_<tab>`, `export_run_s_<enc>`) collect nanoseconds and show whole seconds. Thus short times
 * add up correctly.
 */
internal class SessionCounters {
    private val counts = HashMap<String, Long>()
    private val nanos = HashMap<String, Long>()

    @Synchronized
    fun add(key: String, value: Long = 1) {
        require(key in AnalyticsSchema.COUNTER_KEYS) { "Unknown counter key" }
        if (value > 0) counts.merge(key, value, Math::addExact)
    }

    @Synchronized
    fun addNanos(key: String, value: Long) {
        require(key in AnalyticsSchema.COUNTER_KEYS) { "Unknown counter key" }
        if (value > 0) nanos.merge(key, value, Math::addExact)
    }

    /** Deletes all counters with a key that is not in [keys]. */
    @Synchronized
    fun retainOnly(keys: Set<String>) {
        counts.keys.retainAll(keys)
        nanos.keys.retainAll(keys)
    }

    /** The counters with a value above 0, sorted by key. */
    @Synchronized
    fun values(): Map<String, Int> {
        val result = TreeMap<String, Int>()
        counts.forEach { (key, value) -> result[key] = clamp(value) }
        nanos.forEach { (key, value) -> clamp(value / NANOS_PER_SECOND).takeIf { it > 0 }?.let { result[key] = it } }
        return result
    }

    private fun clamp(value: Long): Int = value.coerceAtMost(AnalyticsSchema.MAX_COUNTER_VALUE.toLong()).toInt()

    companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}

/** One session summary as the app sends it to `POST /v1/session`. */
internal data class SessionSummary(
    val sessionId: String,
    val level: AnalyticsLevel,
    val appVersion: String,
    val osFamily: String,
    val snapshot: Int,
    val final: Boolean,
    val durationS: Int,
    val activeS: Int,
    val counters: Map<String, Int>,
    /** The session attributes: a text or a list of texts for each key. Null at the essential level. */
    val attributes: Map<String, Any>? = null,
) {
    fun toJson(): String = mapper.writeValueAsString(mapper.createObjectNode().apply {
        put("schema_version", AnalyticsSchema.SCHEMA_VERSION)
        put("notice_version", AnalyticsSchema.NOTICE_VERSION)
        put("level", level.key)
        put("session_id", sessionId)
        put("app_version", appVersion)
        put("os_family", osFamily)
        put("snapshot", snapshot)
        put("final", final)
        put("duration_s", durationS)
        put("active_s", activeS)
        putObject("counters").apply { counters.forEach { (key, value) -> put(key, value) } }
        attributes?.let { values ->
            putObject("attributes").apply {
                values.forEach { (key, value) ->
                    when (value) {
                        is List<*> -> putArray(key).apply { value.forEach { add(it.toString()) } }
                        else -> put(key, value.toString())
                    }
                }
            }
        }
    })

    private companion object {
        val mapper = ObjectMapper()
    }
}
