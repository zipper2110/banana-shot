package org.litvin.analytics

/**
 * The crash of the previous app session (B-67). The app reads it from the crash marker and gives it to the first
 * analytics session of the process. The summary sends only the keys of closed lists (`crash_<kind>`,
 * `crash_lib_<library>`, `crash_at_<activity>`), never a text of the crash.
 *
 * @param library null when there is no JVM crash file.
 * @param activities what the app did at the crash. Empty means [Activity.IDLE].
 */
data class AnalyticsCrash(
    val kind: Kind,
    val library: Library?,
    val activities: Set<Activity>,
) {
    enum class Kind(val key: String) { NATIVE("native"), MEMORY("memory"), FREEZE("freeze"), UNKNOWN("unknown") }

    enum class Library(val key: String) {
        COREVIDEO("corevideo"), GRAPHICS("graphics"), MPV("mpv"), FFMPEG("ffmpeg"), JVM("jvm"), SYSTEM("system"), OTHER("other"),
    }

    enum class Activity(val key: String) { EXPORT("export"), PLAYBACK("playback"), SLEEP_WAKE("sleep_wake"), IDLE("idle") }
}
