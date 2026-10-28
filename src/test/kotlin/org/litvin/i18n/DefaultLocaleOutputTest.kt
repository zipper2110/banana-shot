package org.litvin.i18n

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.litvin.AssOverlayWriter
import org.litvin.CommentOverlaySpan
import org.litvin.ExportPreset
import org.litvin.ExportPresetsIO
import org.litvin.FFmpegCommandBuilder
import org.litvin.RenderOverlayScript
import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import org.litvin.export.ExportPlanner
import org.litvin.export.ExportRenderPlanRequest
import org.litvin.export.ExportResolution
import org.litvin.export.PassFade
import org.litvin.points.CommentV1
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.shared.util.Timecode
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals

/**
 * B-11 E1-T3: the texts for programs do not depend on the Windows regional format. The test makes the machine outputs
 * with the default locale `en-US` and with another default locale, and compares them: the FFmpeg command, the ASS
 * files, the time codes, and the saved project files. A file that the app saved with one locale opens with the same
 * values with `en-US` (E1-T2).
 *
 * The locales: `de-DE` has a decimal comma, `tr-TR` has a different "i", `ar-EG` and `fa-IR` have other digits,
 * and `th-TH-u-nu-thai` has Thai digits and the Buddhist calendar.
 */
class DefaultLocaleOutputTest {
    @TempDir
    lateinit var dir: File

    @ParameterizedTest
    @ValueSource(strings = ["de-DE", "tr-TR", "ar-EG", "fa-IR", "th-TH-u-nu-thai"])
    fun `the machine outputs are the same with each default locale`(tag: String) {
        val english = withDefaultLocale(Locale.US) { outputs(dir.resolve("en-US")) }
        val other = withDefaultLocale(Locale.forLanguageTag(tag)) { outputs(dir.resolve(tag)) }

        assertEquals(english.keys, other.keys)
        english.forEach { (name, text) -> assertEquals(text, other[name], "$name with the default locale $tag") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["de-DE", "ar-EG"])
    fun `a project saved with one locale opens with the same values with another locale`(tag: String) {
        val other = Locale.forLanguageTag(tag)
        listOf(other to Locale.US, Locale.US to other).forEachIndexed { index, (saveLocale, readLocale) ->
            val projectDir = dir.resolve("project-$tag-$index").apply { mkdirs() }.path
            withDefaultLocale(saveLocale) { save(projectDir) }
            withDefaultLocale(readLocale) {
                assertEquals(manifest(), ManifestIO.read(File(projectDir, "project.trproj").path), "manifest $saveLocale to $readLocale")
                assertEquals(edl(), EdlIO.readForProjectDir(projectDir), "EDL $saveLocale to $readLocale")
                assertEquals(score(), ScoreIO.readForProjectDir(projectDir), "score $saveLocale to $readLocale")
                assertEquals(adjustments(), AdjustmentsIO.readForProjectDir(projectDir), "adjustments $saveLocale to $readLocale")
            }
        }
    }

    /** The machine texts, by name. The paths in the texts are relative, so that each locale gives the same text. */
    private fun outputs(folder: File): Map<String, String> {
        folder.mkdirs()
        val presets = ExportPresetsIO.load()
        val command = FFmpegCommandBuilder.build(
            FFmpegCommandBuilder.BuildParams(
                sourcePath = "input.mp4",
                outputPath = "out.mp4",
                preset = presets[ExportPresetsIO.defaultBalancedIndex(presets)],
                outWidth = 1920,
                outHeight = 1080,
                outputFrameRate = "30000/1001",
                keeps = edl().points,
                subtitlesAssPath = "overlay.ass",
                adjustments = adjustments(),
                sourceWidth = 1920,
                sourceHeight = 1080,
                outputTimeOffsetMs = 1_250,
                fadeIn = PassFade(white = false, durationMs = 350),
                fadeOut = PassFade(white = true, durationMs = 450),
            ),
        )
        val plan = ExportPlanner.buildRenderPlan(
            ExportRenderPlanRequest(
                manifest = manifest(),
                sourcePath = "input.mp4",
                edl = edl(),
                score = score(),
                preset = ExportPreset(id = "balanced", label = "Balanced"),
                resolution = ExportResolution("1080p", 1920, 1080),
                encoderLabel = "H.264 (libx264)",
                idleTrim = true,
                favoriteOnly = false,
                includeScoreboard = true,
                outputPath = "out.mp4",
                includeStatsCard = true,
                includeComments = true,
            ),
        )
        val scoreboard = RenderOverlayScript.writeFor(plan.job, folder.resolve("out.mp4.part"))!!.readText()
        val card = folder.resolve("card.ass").also { AssOverlayWriter.writeStatsCard(it, plan.job.statsCard!!, 1920, 1080) }.readText()
        val comments = folder.resolve("comments.ass").also {
            AssOverlayWriter.write(
                file = it,
                scoreboardSpans = emptyList(),
                commentSpans = listOf(
                    CommentOverlaySpan(1, 1_250, 3_750, "Ace! 1.5 s", "#FFCC00", sizePercent = 135, opacityPercent = 70, rotationDeg = -12),
                ),
                outWidth = 1280,
                outHeight = 720,
            )
        }.readText()
        val times = listOf(0L, 1_234L, 83_500L, 3_666_789L)
        val timecodes = times.joinToString("\n") { "${Timecode.format(it)} ${Timecode.parse(Timecode.format(it))}" } +
            "\n" + listOf("1:23.5", "1:23,5", "01:01:06.789").joinToString("\n") { "$it ${Timecode.parse(it)}" }
        val projectDir = folder.resolve("project").apply { mkdirs() }.path
        save(projectDir)
        val saved = File(projectDir).listFiles()!!.sortedBy { it.name }.associate { "file ${it.name}" to it.readText() }
        return mapOf(
            "ffmpeg" to command.args.joinToString("\n"),
            "scoreboard ASS" to scoreboard,
            "statistics card ASS" to card,
            "comment ASS" to comments,
            "time codes" to timecodes,
            "hex" to MachineFormats.format("#%06X %02x", 0xFFCC00, 0x0A),
        ) + saved
    }

    private fun save(projectDir: String) {
        ManifestIO.write(File(projectDir, "project.trproj").path, manifest())
        EdlIO.writeForProjectDir(projectDir, edl())
        ScoreIO.writeForProjectDir(projectDir, score())
        AdjustmentsIO.writeForProjectDir(projectDir, adjustments())
    }

    private fun points(): List<PointV1> = (1..5).map { PointV1(id = "p$it", startMs = it * 10_000 + 125, endMs = it * 10_000 + 4_375, favorite = it == 2) }

    private fun edl() = EdlV1(
        points = points(),
        comments = listOf(CommentV1(id = 1, startMs = 12_500, durationMs = 2_250, text = "Ace! 1.5 s", sizePercent = 135, opacityPercent = 70, rotationDeg = -12)),
        nextCommentId = 2,
    )

    private fun score(): ScoreV1 {
        val outcomes = points().mapIndexed { index, point -> point.id to if (index % 2 == 0) Outcome.P1 else Outcome.P2 }.toMap()
        return ScoreV1(outcomes = outcomes, rules = MatchRulesV1(deuce = DeuceRule.NO_AD))
    }

    private fun adjustments() = AdjustmentsV1(
        brightness = 1.125f,
        contrast = 0.875f,
        saturation = 1.375f,
        shadows = 0.25f,
        highlights = -0.375f,
        whiteBalance = WhiteBalanceV1(temperature = 0.125f),
        zoom = 1.25f,
        panX = 0.125f,
        panY = -0.0625f,
        rotationDeg = 7.5f,
        backgroundColor = "#102030",
    )

    private fun manifest() = ProjectManifestV1(
        id = "project-id",
        name = "Final 1.5",
        createdAt = "2026-10-20T10:15:30Z",
        lastOpenedAt = "2026-10-21T08:00:00Z",
        sourceVideo = "input.mp4",
    )

    /** Runs [block] with [locale] as the default locale of all categories, and then sets the old locales again. */
    private fun <T> withDefaultLocale(locale: Locale, block: () -> T): T {
        val main = Locale.getDefault()
        val format = Locale.getDefault(Locale.Category.FORMAT)
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val formatLocale = DisplayFormats.formatLocale
        try {
            Locale.setDefault(locale)
            Locale.setDefault(Locale.Category.FORMAT, locale)
            Locale.setDefault(Locale.Category.DISPLAY, locale)
            // The display formats follow the regional format on purpose. They are not machine texts.
            DisplayFormats.formatLocale = Locale.US
            return block()
        } finally {
            Locale.setDefault(main)
            Locale.setDefault(Locale.Category.FORMAT, format)
            Locale.setDefault(Locale.Category.DISPLAY, display)
            DisplayFormats.formatLocale = formatLocale
        }
    }
}
