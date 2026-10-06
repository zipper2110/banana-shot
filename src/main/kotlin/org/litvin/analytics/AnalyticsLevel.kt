package org.litvin.analytics

/**
 * The level of the usage statistics (`docs/analytics/design.md`, "Levels").
 *
 * - [ESSENTIAL]: always on. The summary contains only [AnalyticsSchema.ESSENTIAL_KEYS], the session length, and the
 *   active time.
 * - [EXTENDED]: only when the user selects it. The summary can contain all counter keys.
 */
enum class AnalyticsLevel(internal val key: String) {
    ESSENTIAL("essential"),
    EXTENDED("extended");

    /** The counter keys that a summary of this level can contain. */
    internal val counterKeys: Set<String>
        get() = if (this == EXTENDED) AnalyticsSchema.COUNTER_KEYS else AnalyticsSchema.ESSENTIAL_KEYS
}
