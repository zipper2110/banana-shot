package org.litvin

import org.litvin.export.comments.CommentAss
import org.litvin.export.scoreboard.BoardPlacement
import org.litvin.export.scoreboard.ScoreboardAss
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.stats.StatsCard
import org.litvin.stats.StatsCardVideo
import java.io.File
import java.util.Locale

/**
 * Task 3.19 — Helper to generate an ASS subtitles file for the scoreboard overlay.
 *
 * The scoreboard comes from [ScoreboardLayouts] and [ScoreboardAss]. The scoring preview sends the
 * same ASS events to mpv, so the preview and the export look the same.
 * Each span writes all board events with the same start and end, so the board changes atomically
 * at point boundaries. The layer number keeps the drawing order.
 */
object AssOverlayWriter {
    /** Comment events use layers above all scoreboard layers. */
    private const val COMMENT_LAYER = 1000

    /**
     * Write an ASS file using the given video resolution and spans.
     * Fonts, sizes, paddings are scaled relative to 1080p.
     */
    fun write(
        file: File,
        spans: List<OverlaySpan>,
        outWidth: Int,
        outHeight: Int,
        settings: ScoreboardSettingsV1 = ScoreboardSettingsV1(),
    ) = write(
        file = file,
        scoreboardSpans = spans,
        commentSpans = emptyList(),
        outWidth = outWidth,
        outHeight = outHeight,
        settings = settings,
    )

    fun write(
        file: File,
        scoreboardSpans: List<OverlaySpan>,
        commentSpans: List<CommentOverlaySpan>,
        outWidth: Int,
        outHeight: Int,
        settings: ScoreboardSettingsV1 = ScoreboardSettingsV1(),
    ) {
        val playResX = outWidth.coerceAtLeast(16)
        val playResY = outHeight.coerceAtLeast(16)
        val fontFamily = "Arial"

        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.scriptInfo(playResX, playResY)

            // Colors use &HAABBGGRR (AA: 00 opaque, FF transparent)
            val black = assColor(0x000000, 0x00)

            w.appendLine("[V4+ Styles]")
            w.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            // Board events set all of their override tags; this style only supplies neutral values.
            w.appendLine("Style: Board,$fontFamily,20,${assColor(0xFFFFFF, 0x00)},&H000000FF,$black,$black,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,0")
            // Comment events also set all of their override tags (see CommentAss).
            w.appendLine("Style: Comment,$fontFamily,20,${assColor(0xFFFFFF, 0x00)},&H000000FF,$black,$black,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,0")
            w.appendLine()

            w.appendLine("[Events]")
            w.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

            for (s in scoreboardSpans) {
                if (s.endMs <= s.startMs) continue
                val start = toAssTs(s.startMs)
                val end = toAssTs(s.endMs)
                val display = ScoreboardComponent.display(withStructuredState(s))
                val scene = ScoreboardLayouts.scene(display, settings)
                val placement = ScoreboardAss.place(scene, settings, 0.0, 0.0, playResX.toDouble(), playResY.toDouble())
                ScoreboardAss.events(scene, placement).forEachIndexed { layer, text ->
                    w.appendLine("Dialogue: $layer,$start,$end,Board,,0,0,0,,$text")
                }
            }

            for (comment in commentSpans) {
                if (comment.endMs <= comment.startMs || comment.text.isBlank()) continue
                val start = toAssTs(comment.startMs)
                val end = toAssTs(comment.endMs)
                val events = CommentAss.events(
                    comment.text, comment.colorHex, 0.0, 0.0, playResX.toDouble(), playResY.toDouble(), comment.style,
                )
                events.forEachIndexed { index, text ->
                    w.appendLine("Dialogue: ${COMMENT_LAYER + index},$start,$end,Comment,,0,0,0,,$text")
                }
            }
        }
    }

    /**
     * Writes the ASS file of the statistics card pass. The pass video starts at 0, so page i shows
     * from i x [StatsCardVideo.pageDurationMs] to the end of that page.
     */
    fun writeStatsCard(file: File, card: StatsCardVideo, outWidth: Int, outHeight: Int) {
        val playResX = outWidth.coerceAtLeast(16)
        val playResY = outHeight.coerceAtLeast(16)
        // The pages have the aspect ratio of the output, so one scale fills the frame.
        val placement = BoardPlacement(0.0, 0.0, playResY / StatsCard.HEIGHT)
        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.scriptInfo(playResX, playResY)
            w.appendLine("[V4+ Styles]")
            w.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            val black = assColor(0x000000, 0x00)
            w.appendLine("Style: Board,Arial,20,${assColor(0xFFFFFF, 0x00)},&H000000FF,$black,$black,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,0")
            w.appendLine()
            w.appendLine("[Events]")
            w.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            card.pages.forEachIndexed { index, scene ->
                val start = toAssTs(index * card.pageDurationMs)
                val end = toAssTs((index + 1) * card.pageDurationMs)
                ScoreboardAss.events(scene, placement).forEachIndexed { layer, text ->
                    w.appendLine("Dialogue: $layer,$start,$end,Board,,0,0,0,,$text")
                }
            }
        }
    }

    private fun Appendable.scriptInfo(playResX: Int, playResY: Int) {
        appendLine("[Script Info]")
        appendLine("ScriptType: v4.00+")
        appendLine("PlayResX: $playResX")
        appendLine("PlayResY: $playResY")
        appendLine("ScaledBorderAndShadow: yes")
        // Without this header, FFmpeg converts the colors like VSFilter (TV range BT.601).
        // The mpv preview uses the RGB values as they are, so the export must do the same.
        appendLine("YCbCr Matrix: None")
        appendLine()
    }

    /**
     * Returns the span with structured score fields. Old spans have only the state text,
     * so the fields come from parsing the text.
     */
    private fun withStructuredState(s: OverlaySpan): OverlaySpan {
        val structured = s.isTiebreak || s.p1Pts != 0 || s.p2Pts != 0 || s.gamesP1 != 0 || s.gamesP2 != 0 ||
            s.setsP1 != 0 || s.setsP2 != 0 || s.completedSets.isNotEmpty()
        if (structured) return s
        val p = parseState(s.text)
        return s.copy(
            p1Pts = p.p1Pts,
            p2Pts = p.p2Pts,
            gamesP1 = p.gamesP1,
            gamesP2 = p.gamesP2,
            setsP1 = p.setsP1,
            setsP2 = p.setsP2,
            completedSets = p.completedSets,
        )
    }

    // ----- Parsing helpers -----
    private data class State(
        val p1Pts: Int, val p2Pts: Int,
        val gamesP1: Int, val gamesP2: Int,
        val setsP1: Int, val setsP2: Int,
        val completedSets: List<Pair<Int, Int>>,
    )

    private fun parseState(text: String): State {
        // Expected format from ScoreboardTimelineBuilder.stateText()
        // Player 1: pts X, games G, sets S  |  Player 2: pts Y, games G2, sets S2 [a-b, c-d]
        var p1Pts = 0; var p2Pts = 0; var g1 = 0; var g2 = 0; var s1 = 0; var s2 = 0
        val sets = mutableListOf<Pair<Int, Int>>()
        try {
            val sep = "  |  "
            val left = text.substringBefore(sep, text)
            val right = text.substringAfter(sep, "")
            fun nums(s: String): Triple<Int, Int, Int> {
                // returns (pts, games, sets)
                val r = Regex("pts\\s+(\\w+).*?games\\s+(\\d+).*?sets\\s+(\\d+)", RegexOption.IGNORE_CASE)
                val m = r.find(s)
                val ptsStr = m?.groupValues?.getOrNull(1) ?: "0"
                val pts = when (ptsStr.trim().lowercase()) {
                    "0" -> 0
                    "15" -> 1
                    "30" -> 2
                    "40" -> 3
                    "ad" -> 4 // advantage
                    else -> ptsStr.toIntOrNull() ?: 0
                }
                val games = m?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                val setsV = m?.groupValues?.getOrNull(3)?.toIntOrNull() ?: 0
                return Triple(pts, games, setsV)
            }
            val (lp, lg, ls) = nums(left)
            val (rp, rg, rs) = nums(right)
            p1Pts = lp; g1 = lg; s1 = ls
            p2Pts = rp; g2 = rg; s2 = rs
            val br = Regex("\\[(.*)]").find(text)?.groupValues?.getOrNull(1)
            if (!br.isNullOrBlank()) {
                br.split(",").forEach { token ->
                    val t = token.trim()
                    val mm = Regex("(\\d+)\\s*[-:]\\s*(\\d+)").find(t)
                    if (mm != null) {
                        val a = mm.groupValues[1].toInt()
                        val b = mm.groupValues[2].toInt()
                        sets += a to b
                    }
                }
            }
        } catch (_: Throwable) { }
        return State(p1Pts, p2Pts, g1, g2, s1, s2, sets)
    }

    // ----- ASS helpers -----
    private fun toAssTs(ms: Long): String {
        val totalCs = (ms / 10).coerceAtLeast(0)
        val cs = (totalCs % 100).toInt()
        val totalSec = totalCs / 100
        val s = (totalSec % 60).toInt()
        val totalMin = totalSec / 60
        val m = (totalMin % 60).toInt()
        val h = (totalMin / 60).toInt()
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    }

    private fun assColor(rgb: Int, alpha: Int): String {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val aa = alpha.coerceIn(0, 255)
        // &HAABBGGRR
        return String.format(Locale.US, "&H%02X%02X%02X%02X", aa, b, g, r)
    }
}
