package org.litvin.analytics

/**
 * The facts that features record. Features never see counter keys: [EnabledAnalytics] changes each event into counter
 * changes (`docs/analytics/design.md`, "Counters"). Feature packages can import only [Analytics] and this class with
 * its nested types.
 */
sealed class AnalyticsEvent {
    /**
     * The current tab changed. [byUser] is true only for a click on a sidebar button. A null [tab] is a tab that is
     * not counted (the Test tab).
     */
    data class TabShown(val tab: Tab?, val byUser: Boolean) : AnalyticsEvent()

    /** The main window became the active window, or stopped being it. Only active time counts in `active_s`. */
    data class WindowActive(val active: Boolean) : AnalyticsEvent()

    data object UncaughtError : AnalyticsEvent()
    data object HelpOpened : AnalyticsEvent()
    data object ProjectCreated : AnalyticsEvent()
    data object ProjectOpened : AnalyticsEvent()
    data object VideoOpenFailed : AnalyticsEvent()
    data object PointAdded : AnalyticsEvent()
    data object PointDeleted : AnalyticsEvent()
    data object PointFavorited : AnalyticsEvent()
    data object CommentAdded : AnalyticsEvent()
    data object ScoreRecorded : AnalyticsEvent()
    data object ColorChanged : AnalyticsEvent()
    data object CropRotateChanged : AnalyticsEvent()

    /**
     * The current settings of the app. The app records them at the start and after each change. They are session
     * attributes (`docs/analytics/design.md`, "Attributes"): the summary has the values at the time of the send.
     */
    data class Settings(val theme: Theme, val accent: Accent, val language: Language) : AnalyticsEvent()

    /** The user works with a project of [sport]: a project was created or opened, or the sport of a project changed. */
    data class SportUsed(val sport: Sport) : AnalyticsEvent()

    /** An export job started to run. A job cancelled while it waits in the queue does not start. */
    data class ExportStarted(val encoder: Encoder, val options: Set<ExportOption>, val resolution: Resolution) : AnalyticsEvent()

    /** A started export ended. */
    data class ExportFinished(val encoder: Encoder, val outcome: ExportOutcome) : AnalyticsEvent()

    sealed interface ExportOutcome {
        /** [runMs] is the run time of the job. [videoS] is the length of the output video. */
        data class Completed(val runMs: Long, val videoS: Long) : ExportOutcome
        data class Failed(val reason: FailReason) : ExportOutcome
        /** The user cancelled the running export. */
        data object Cancelled : ExportOutcome
        /** The export was running when the app closed. */
        data object Interrupted : ExportOutcome
    }

    enum class Tab(internal val key: String) {
        PROJECTS("projects"), POINTS("points"), COLORS("colors"), CROP_ROTATE("crop_rotate"), SCORING("scoring"),
        STATS("stats"), EXPORT("export")
    }

    enum class Theme(internal val key: String) { DARK("dark"), MID("mid"), LIGHT("light") }

    /** Only the type of the accent. The app never sends the color. */
    enum class Accent(internal val key: String) { DEFAULT("default"), CUSTOM("custom") }

    /** The language of the app interface. It is not the language of the operating system. */
    enum class Language(internal val key: String) { EN("en") }

    enum class Sport(internal val key: String) { TENNIS("tennis"), PADEL("padel") }

    enum class Encoder(internal val key: String) { SOFTWARE("software"), NVENC("nvenc"), AMF("amf"), QSV("qsv") }

    enum class FailReason(internal val key: String) {
        SOURCE_MISSING("source_missing"), OUTPUT_WRITE("output_write"), PROCESS_START("process_start"),
        FFMPEG_EXIT("ffmpeg_exit"), OTHER("other")
    }

    enum class ExportOption(internal val key: String) {
        SCOREBOARD("scoreboard"), COMMENTS("comments"), STATS_CARD("stats_card"), FAVORITES_ONLY("favorites_only"),
        IDLE_TRIM("idle_trim")
    }

    enum class Resolution(internal val key: String) {
        P720("720"), P1080("1080"), P1440("1440"), P2160("2160"), OTHER("other");

        companion object {
            /** Uses the short side of the frame: after a 90° rotation, a 1080p export has a height of 1920. */
            fun ofFrame(width: Int, height: Int): Resolution = when (minOf(width, height)) {
                720 -> P720
                1080 -> P1080
                1440 -> P1440
                2160 -> P2160
                else -> OTHER
            }
        }
    }
}
