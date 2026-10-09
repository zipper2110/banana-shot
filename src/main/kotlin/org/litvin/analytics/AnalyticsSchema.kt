package org.litvin.analytics

/**
 * The session summary (`docs/analytics/design.md`, "Wire format" and "Counters"). The Worker has the same closed key
 * lists. A test checks both lists against `analytics-contract/v1/counter-keys.json`.
 */
object AnalyticsSchema {
    /** Version 2 adds the `level` field. The Worker refuses version 1 with 410. Version 3 adds `attributes`. */
    const val SCHEMA_VERSION = 3
    /** Version 2 tells about the attributes. The Worker accepts notice version 2 only with schema version 3. */
    const val NOTICE_VERSION = 2
    const val MAX_COUNTER_VALUE = 1_000_000
    /** 7 days. `duration_s` and `active_s` are clamped to this value. */
    const val MAX_SECONDS = 604_800
    const val MAX_SNAPSHOT = 1_000_000

    const val UNCLEAN_EXIT = "unclean_exit"
    const val UNCAUGHT_ERROR = "uncaught_error"

    /** The keys of the essential level. All other keys need the extended level. */
    val ESSENTIAL_KEYS: Set<String> = buildSet {
        add(UNCLEAN_EXIT)
        add(UNCAUGHT_ERROR)
        listOf("1", "2_5", "6_20", "21p").forEach { add("session_n_$it") }
    }

    val COUNTER_KEYS: Set<String> = buildSet {
        addAll(ESSENTIAL_KEYS)
        AnalyticsEvent.Tab.entries.forEach { add(tabOpened(it)) }
        AnalyticsEvent.Tab.entries.forEach { add(tabSeconds(it)) }
        addAll(listOf(
            "project_created", "project_opened", "video_open_failed", "point_added", "point_deleted",
            "point_favorited", "comment_added", "score_recorded", "color_changed", "crop_rotate_changed", "help_opened",
        ))
        listOf("started", "completed", "failed", "cancelled", "interrupted", "run_s", "video_s").forEach { result ->
            AnalyticsEvent.Encoder.entries.forEach { add("export_${result}_${it.key}") }
        }
        AnalyticsEvent.FailReason.entries.forEach { add("export_fail_${it.key}") }
        AnalyticsEvent.ExportOption.entries.forEach { add("export_opt_${it.key}") }
        AnalyticsEvent.Resolution.entries.forEach { add("export_res_${it.key}") }
    }

    /**
     * The closed lists of the session attributes (`docs/analytics/design.md`, "Attributes"). Only an extended summary
     * contains them. A test checks the lists against `analytics-contract/v1/attributes.json`.
     */
    val ATTRIBUTE_VALUES: Map<String, List<String>> = linkedMapOf(
        THEME to AnalyticsEvent.Theme.entries.map { it.key },
        ACCENT to AnalyticsEvent.Accent.entries.map { it.key },
        LANGUAGE to AnalyticsEvent.Language.entries.map { it.key },
        SPORT to AnalyticsEvent.Sport.entries.map { it.key },
    )

    /** Attributes with an array of values. */
    val ARRAY_ATTRIBUTES: Set<String> = setOf(SPORT)

    const val THEME = "theme"
    const val ACCENT = "accent"
    const val LANGUAGE = "language"
    const val SPORT = "sport"

    fun tabOpened(tab: AnalyticsEvent.Tab) = "tab_${tab.key}"
    fun tabSeconds(tab: AnalyticsEvent.Tab) = "tab_s_${tab.key}"

    /** The key of the number of this analytics session on this install. */
    fun sessionBucket(number: Int): String = "session_n_" + when {
        number <= 1 -> "1"
        number <= 5 -> "2_5"
        number <= 20 -> "6_20"
        else -> "21p"
    }

    /** The counter key of an event that only adds 1, or null for an event with more data. */
    internal fun simpleKey(event: AnalyticsEvent): String? = when (event) {
        AnalyticsEvent.UncaughtError -> UNCAUGHT_ERROR
        AnalyticsEvent.HelpOpened -> "help_opened"
        AnalyticsEvent.ProjectCreated -> "project_created"
        AnalyticsEvent.ProjectOpened -> "project_opened"
        AnalyticsEvent.VideoOpenFailed -> "video_open_failed"
        AnalyticsEvent.PointAdded -> "point_added"
        AnalyticsEvent.PointDeleted -> "point_deleted"
        AnalyticsEvent.PointFavorited -> "point_favorited"
        AnalyticsEvent.CommentAdded -> "comment_added"
        AnalyticsEvent.ScoreRecorded -> "score_recorded"
        AnalyticsEvent.ColorChanged -> "color_changed"
        AnalyticsEvent.CropRotateChanged -> "crop_rotate_changed"
        is AnalyticsEvent.TabShown, is AnalyticsEvent.WindowActive, is AnalyticsEvent.ExportStarted,
        is AnalyticsEvent.ExportFinished, is AnalyticsEvent.Settings, is AnalyticsEvent.SportUsed -> null
    }
}
