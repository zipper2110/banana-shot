package org.litvin.ui.tabs.points.ui

import org.litvin.i18n.MachineFormats

/**
 * The text of a fixed scoreboard time (B-63): `mm:ss`. The minutes can be more than 59, for example "75:20".
 * The parse also accepts `h:mm:ss` and seconds only.
 */
internal object ScoreboardTimeText {
    /** [ms] as `mm:ss`. The seconds are rounded down. */
    fun format(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000
        return MachineFormats.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    /** The time of [text] in ms, or null when the text is not a time. */
    fun parse(text: String): Long? {
        val clean = MachineFormats.asciiDigits(text).trim()
        if (!CLOCK.matches(clean)) return null
        val parts = clean.split(":").map { it.toLongOrNull() ?: return null }
        val seconds = when (parts.size) {
            1 -> parts[0]
            2 -> if (parts[1] > 59) return null else parts[0] * 60 + parts[1]
            else -> if (parts[1] > 59 || parts[2] > 59) return null else (parts[0] * 60 + parts[1]) * 60 + parts[2]
        }
        return seconds * 1000
    }

    private val CLOCK = Regex("""[0-9]{1,5}(:[0-9]{1,2}){0,2}""")
}
