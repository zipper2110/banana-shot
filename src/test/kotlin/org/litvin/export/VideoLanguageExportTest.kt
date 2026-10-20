package org.litvin.export

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import org.litvin.AssOverlayWriter
import org.litvin.ExportPreset
import org.litvin.RenderJob
import org.litvin.RenderOverlayScript
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardTimelineBuilder
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScoreboardLayouts
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.DisplayFormats
import org.litvin.i18n.I18n
import org.litvin.i18n.MessageCatalog
import org.litvin.i18n.Messages
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ProjectManifestV1
import org.litvin.projects.ProjectVideoLanguage
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.MatchRulesV1
import org.litvin.scoring.Outcome
import org.litvin.scoring.ScoreV1
import org.litvin.stats.StatsCard
import org.litvin.stats.StatsReport
import org.litvin.stats.StatsSettingsV1
import org.litvin.videoTextLanguage
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-11 E5-T1, E5-T3, E5-T4, and E4-T9: the export and the preview use the video language. */
class VideoLanguageExportTest {
    @TempDir
    lateinit var dir: File

    @AfterEach
    fun restore() {
        I18n.install(Messages.english())
    }

    @Test
    fun `an export without a selection uses the app language of the time of the export`() {
        val manifest = manifest(videoLanguage = null)
        val score = score()

        val english = plan(score, ProjectVideoLanguage.of(manifest))
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        val spanish = plan(score, ProjectVideoLanguage.of(manifest))

        assertEquals("en", english.job.videoLanguage)
        assertEquals("Player 1", english.overlayTimeline.first().p1Name)
        assertTrue("MATCH STATISTICS" in labels(english.job.statsCard!!.pages))
        assertEquals("es", spanish.job.videoLanguage)
        assertEquals("Jugador 1", spanish.overlayTimeline.first().p1Name)
        assertTrue("ESTADÍSTICAS DEL PARTIDO" in labels(spanish.job.statsCard!!.pages))
    }

    @Test
    fun `the app in Spanish and the video in English give English texts in the ASS files`() {
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        // A project from a Spanish start keeps the Spanish default names and title.
        val score = score()
        assertEquals("Jugador 1", score.player1Name)

        val job = plan(score, ProjectVideoLanguage.of(manifest(videoLanguage = "en"))).job
        val scoreboard = RenderOverlayScript.writeFor(job, dir.resolve("out.mp4.part"))!!.readText()
        val card = dir.resolve("card.ass").also { AssOverlayWriter.writeStatsCard(it, job.statsCard!!, 1920, 1080) }.readText()

        assertTrue("PLAYER 1" in scoreboard, scoreboard)
        assertTrue("YOUR TOURNAMENT OR CLUB" in scoreboard, scoreboard)
        assertTrue("DECIDING POINT" in scoreboard, scoreboard)
        assertFalse("JUGADOR" in scoreboard, scoreboard)
        assertTrue("MATCH STATISTICS" in card, card)
        assertTrue("Points won" in card, card)
        assertFalse("Puntos" in card, card)
    }

    @Test
    fun `a user text does not change in the video`() {
        val score = score().copy(player1Name = "Ana", player2Name = "Bea").let {
            it.copy(scoreboard = it.scoreboard.copy(title = "Liga de verano"))
        }

        val job = plan(score, AppLanguage.ENGLISH).job
        val scoreboard = RenderOverlayScript.writeFor(job, dir.resolve("out.mp4.part"))!!.readText()

        assertTrue("ANA" in scoreboard && "BEA" in scoreboard, scoreboard)
        assertTrue("LIGA DE VERANO" in scoreboard, scoreboard)
    }

    @Test
    fun `a missing video text shows the English text and the export does not fail`() {
        val spanish = MessageCatalog.loadWithParents(AppLanguage.SPANISH_SPAIN) - "video.badge.decidingPoint"
        I18n.install(Messages(AppLanguage.SPANISH_SPAIN, spanish, MessageCatalog.load(AppLanguage.SOURCE)))

        val job = plan(score(), AppLanguage.SPANISH_SPAIN).job
        val scoreboard = RenderOverlayScript.writeFor(job, dir.resolve("out.mp4.part"))!!.readText()

        assertTrue("DECIDING POINT" in scoreboard, scoreboard)
        assertTrue("JUGADOR 1" in scoreboard, scoreboard)
    }

    @Test
    fun `a queued job renders in its language after a change of the app language`() {
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        val queue = SavedRenderQueue(dir.resolve("render-queue.json"))
        queue.save(listOf(plan(score(), AppLanguage.SPANISH_SPAIN).job))

        // The app starts again in English.
        I18n.install(Messages.english())
        val job = queue.load().single()
        val scoreboard = RenderOverlayScript.writeFor(job, dir.resolve("out.mp4.part"))!!.readText()

        assertEquals(AppLanguage.SPANISH_SPAIN, job.videoTextLanguage)
        assertTrue("JUGADOR 1" in scoreboard, scoreboard)
        assertTrue("TU TORNEO O CLUB" in scoreboard, scoreboard)
        assertTrue("PUNTO DECISIVO" in scoreboard, scoreboard)
        assertTrue("ESTADÍSTICAS DEL PARTIDO" in labels(job.statsCard!!.pages))
    }

    @Test
    fun `a queued job with an unknown, a broken, or no language stays and renders in English`() {
        val queue = SavedRenderQueue(dir.resolve("render-queue.json"))
        val job = plan(score(), AppLanguage.ENGLISH).job
        queue.save(listOf(job.copy(id = "eo", videoLanguage = "eo"), job.copy(id = "broken", videoLanguage = "123"), job.copy(id = "old", videoLanguage = null)))
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))

        val loaded = queue.load()

        assertEquals(listOf("eo", "broken", "old"), loaded.map { it.id })
        assertEquals(listOf("eo", "123", null), loaded.map { it.videoLanguage })
        loaded.forEach { saved ->
            assertEquals(AppLanguage.ENGLISH, saved.videoTextLanguage)
            val scoreboard = RenderOverlayScript.writeFor(saved, dir.resolve("${saved.id}.mp4.part"))!!.readText()
            assertTrue("YOUR TOURNAMENT OR CLUB" in scoreboard, scoreboard)
        }
    }

    @Test
    fun `a queue file of the current release loads, and its jobs render in English`() {
        val file = dir.resolve("render-queue.json").apply {
            writeText(
                """
                {"format":1,"jobs":[{"id":"j1","sourcePath":"C:/videos/match.mp4","presetId":"balanced","outWidth":1920,
                "outHeight":1080,"encoderLabel":"H.264 (libx264)","idleTrim":true,"outputPath":"C:/videos/out.mp4",
                "status":"QUEUED"}]}
                """.trimIndent(),
            )
        }

        val job = SavedRenderQueue(file).load().single()

        assertEquals("j1", job.id)
        assertEquals(AppLanguage.ENGLISH, job.videoTextLanguage)
    }

    @Test
    fun `the preview and the export show the same texts for the same video language`() {
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        val score = score()
        val edl = EdlV1(points = points())
        val video = AppLanguage.ENGLISH

        val job = plan(score, video).job
        // The Stats tab makes its preview in the same way (SwingStatsPanel).
        val previewCard = I18n.inVideo(video) {
            StatsCard.pages(StatsCard.content(StatsReport.build(edl, score), StatsSettingsV1()))
        }
        // The Points tab makes its scoreboard in the same way (PointsScoring), from the names that it shows.
        val previewBoard = I18n.inVideo(video) {
            val spans = ScoreboardTimelineBuilder.build(
                points = edl.points, outcomes = score.outcomes, idleTrim = false,
                player1Name = score.player1Name, player2Name = score.player2Name,
                player1ColorHex = score.player1ColorHex, player2ColorHex = score.player2ColorHex,
                rules = score.rules, manualMarks = score.manualMarks(), serverMarks = score.serverMarks, sport = score.sport,
            )
            spans.map { ScoreboardLayouts.scene(ScoreboardComponent.display(it), score.scoreboard) }
        }
        val exportBoard = I18n.inVideo(job.videoTextLanguage) {
            job.overlayTimeline.map { ScoreboardLayouts.scene(ScoreboardComponent.display(it), job.scoreboardSettings) }
        }

        assertEquals(labels(job.statsCard!!.pages), labels(previewCard))
        assertEquals(labels(exportBoard), labels(previewBoard))
        assertTrue("PLAYER 1" in labels(previewBoard))
    }

    @Test
    fun `the statistics card shows the numbers in the video number format`() {
        val saved = DisplayFormats.formatLocale
        try {
            fun card(video: AppLanguage, regional: String): List<String> {
                DisplayFormats.formatLocale = Locale.forLanguageTag(regional)
                val settings = StatsSettingsV1(videoMomentum = false, videoStats = listOf("short_points_won", "average_point"))
                return I18n.inVideo(video) {
                    labels(StatsCard.pages(StatsCard.content(StatsReport.build(EdlV1(points = points()), score()), settings)))
                }
            }

            // Each point is 4.2 s long, so all points are short. Player 1 wins four of seven: 57 %.
            card(AppLanguage.ENGLISH, "en-US").let { assertTrue(it.containsAll(listOf("4.2 s", "57%")), it.toString()) }
            card(AppLanguage.SPANISH_SPAIN, "es-MX").let { assertTrue(it.containsAll(listOf("4.2 s", "57%")), it.toString()) }
            card(AppLanguage.SPANISH_SPAIN, "en-US").let { assertTrue(it.containsAll(listOf("4,2 s", "57\u00A0%")), it.toString()) }
            val arabic = card(AppLanguage.ENGLISH, "ar-EG")
            assertTrue(arabic.containsAll(listOf("4.2 s", "57%")), arabic.toString())
            assertTrue(arabic.joinToString("").none { it.isDigit() && it !in '0'..'9' }, arabic.toString())
        } finally {
            DisplayFormats.formatLocale = saved
        }
    }

    private fun labels(scenes: List<ScoreboardScene>): List<String> =
        scenes.flatMap { scene -> scene.items.filterIsInstance<SceneItem.Label>().map { it.text } }

    /** Seven points. The seventh point starts at 40-40, so it is a deciding point under the no-ad rule. */
    private fun points(): List<PointV1> = (1..7).map { PointV1(id = "p$it", startMs = it * 10_000, endMs = it * 10_000 + 4_200) }

    /** A new project of the current app language: it keeps the default names and title of this language. */
    private fun score(): ScoreV1 {
        val outcomes = points().mapIndexed { index, point -> point.id to if (index % 2 == 0) Outcome.P1 else Outcome.P2 }.toMap()
        return ScoreV1(outcomes = outcomes, rules = MatchRulesV1(deuce = DeuceRule.NO_AD))
    }

    private fun plan(score: ScoreV1, videoLanguage: AppLanguage): ExportRenderPlan = ExportPlanner.buildRenderPlan(
        ExportRenderPlanRequest(
            manifest = manifest(videoLanguage = videoLanguage.code),
            sourcePath = "input.mp4",
            edl = EdlV1(points = points()),
            score = score,
            preset = ExportPreset(id = "balanced", label = "Balanced"),
            resolution = ExportResolution("1080p", 1920, 1080),
            encoderLabel = "H.264 (libx264)",
            idleTrim = false,
            favoriteOnly = false,
            includeScoreboard = true,
            outputPath = dir.resolve("out.mp4").path,
            includeStatsCard = true,
            videoLanguage = videoLanguage,
        ),
    )

    private fun manifest(videoLanguage: String?) = ProjectManifestV1(
        id = "project-id",
        name = "Project",
        createdAt = "2026-10-20T00:00:00Z",
        lastOpenedAt = "2026-10-20T00:00:00Z",
        sourceVideo = "input.mp4",
        videoLanguage = videoLanguage,
    )
}
