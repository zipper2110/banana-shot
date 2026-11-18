package org.litvin.export.scoreboard

import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.ScoreboardDisplay
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import org.litvin.scoring.DeuceRule
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import org.litvin.scoring.Sport
import org.litvin.ui.commons.ScoreboardSceneImage
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders a sheet of scoreboard previews to a PNG file. Use it to look at a style change without the app.
 * The boards come from the same [ScoreboardLayouts] scenes as the export, drawn by [ScoreboardSceneImage].
 *
 * Each row is one style. Each column is one variant: see [ScoreboardPreviewSheet.variants].
 *
 * Run it from the repository root:
 * ```
 * mvn -q test-compile exec:java -Dexec.classpathScope=test -Dapp.mainClass=org.litvin.export.scoreboard.ScoreboardPreviewMainKt -Dexec.args="--styles=STADIUM,STADIUM_COMPACT"
 * ```
 * Options:
 * - `--out=<file.png>`: the output file. The default is `target/scoreboard-preview/scoreboards.png`.
 * - `--styles=<ID,ID>`: the [ScoreboardStyleId] names. The default is all styles.
 * - `--scale=<number>`: the board scale. 1.0 is the size on a 1080p video. The default is 1.0.
 * - `--language=<code>`: the language of the video texts, for example `ru`. The default is English.
 */
fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val options = args.associate { arg ->
        require(arg.startsWith("--") && "=" in arg) { "Unknown argument: $arg. Use --out=, --styles=, --scale= or --language=." }
        arg.removePrefix("--").substringBefore('=') to arg.substringAfter('=')
    }
    val unknown = options.keys - setOf("out", "styles", "scale", "language")
    require(unknown.isEmpty()) { "Unknown options: $unknown. Use --out=, --styles=, --scale= or --language=." }
    val out = File(options["out"] ?: "target/scoreboard-preview/scoreboards.png")
    val styles = options["styles"]?.split(',')?.map { ScoreboardStyleId.valueOf(it.trim().uppercase()) }
        ?: ScoreboardStyleId.entries
    val scale = options["scale"]?.toDouble() ?: 1.0
    options["language"]?.let { code ->
        val language = requireNotNull(AppLanguage.ofCode(code)) { "Unknown language: $code. Use one of ${AppLanguage.entries.map { it.code }}." }
        I18n.install(Messages.of(language))
    }

    out.absoluteFile.parentFile.mkdirs()
    ImageIO.write(ScoreboardPreviewSheet.render(styles, scale), "png", out)
    println("Scoreboard previews: ${out.absolutePath}")
}

/** A sheet of scoreboard previews: one row for each style and one column for each [Variant]. */
object ScoreboardPreviewSheet {
    /** One board on the sheet: the score and the settings. [settings] changes the default settings of the style. */
    class Variant(
        val label: String,
        val display: ScoreboardDisplay,
        val settings: (ScoreboardSettingsV1) -> ScoreboardSettingsV1,
    )

    /** A score in the third set: 7–6 with a tiebreak (8–6), 4–6, 2–1 in games and 30–40. Player 2 serves. */
    private val fullNames = ScoreboardComponent.display(
        OverlaySpan(
            startMs = 0L,
            endMs = 1L,
            text = "",
            p1Name = "Alexander Zverev",
            p2Name = "Alexander Bublik",
            p1ColorHex = "#2B2B2B",
            p2ColorHex = "#00AFCA",
            p1Pts = 2,
            p2Pts = 3,
            gamesP1 = 2,
            gamesP2 = 1,
            completedSets = listOf(7 to 6, 4 to 6),
            completedSetTiebreaks = listOf(8 to 6, null),
            server = 2,
        ),
    )

    /** Padel teams at a golden point, without completed sets. */
    private val padelTeams = ScoreboardComponent.display(
        OverlaySpan(
            startMs = 0L,
            endMs = 1L,
            text = "",
            p1Name = "Lee / Kim",
            p2Name = Sport.PADEL.defaultSideName(2),
            p1Pts = 3,
            p2Pts = 3,
            gamesP1 = 3,
            gamesP2 = 4,
            server = 1,
            badge = Sport.PADEL.decidingPointBadge(DeuceRule.NO_AD),
        ),
    )

    /** Tennis at a deciding point (no-ad), with the default names. The badge is the longest badge in most languages. */
    private val tennisDecidingPoint = ScoreboardComponent.display(
        OverlaySpan(
            startMs = 0L,
            endMs = 1L,
            text = "",
            p1Name = Sport.TENNIS.defaultSideName(1),
            p2Name = Sport.TENNIS.defaultSideName(2),
            p1Pts = 3,
            p2Pts = 3,
            gamesP1 = 5,
            gamesP2 = 5,
            completedSets = listOf(6 to 4),
            server = 2,
            badge = Sport.TENNIS.decidingPointBadge(DeuceRule.NO_AD),
        ),
    )

    /** The final score of a best of 3 match: 6–3, 6–7 with a tiebreak (5–7), 4–6. No points and no serve show. */
    private val matchEnded = ScoreboardComponent.display(
        OverlaySpan(
            startMs = 0L,
            endMs = 1L,
            text = "",
            p1Name = "Dale | Devin",
            p2Name = "Andrew | Dennis",
            setsP1 = 1,
            setsP2 = 2,
            completedSets = listOf(6 to 3, 6 to 7, 4 to 6),
            completedSetTiebreaks = listOf(null, 5 to 7, null),
            server = 1,
            matchEnded = true,
        ),
    )

    val variants = listOf(
        Variant("Title and credit", fullNames) { it.copy(title = "Round 4 | Stadium court") },
        Variant("No title, no credit", fullNames) { it.copy(showTitle = false, showAppCredit = false) },
        Variant("Padel, golden point, no colors", padelTeams) { it.copy(showTitle = false, showAppCredit = false, showPlayerColors = false) },
        Variant("Default title, deciding point", tennisDecidingPoint) { it.copy(title = ScoreboardSettingsV1.DEFAULT_TITLE) },
        Variant("Match ended", matchEnded) { it.copy(title = "Westgate - Men's 6.5 practice") },
    )

    private const val GAP = 24
    private const val LABEL_H = 28
    private val BACKGROUND = Color(0x2E5E3A)
    private val TEXT = Color(0xE8F0EA)

    fun render(styles: List<ScoreboardStyleId>, scale: Double, variants: List<Variant> = this.variants): BufferedImage {
        val boards = styles.map { style ->
            variants.map { variant ->
                val settings = variant.settings(ScoreboardSettingsV1(style = style))
                ScoreboardSceneImage.render(ScoreboardLayouts.scene(variant.display, settings), scale)
            }
        }
        val columnW = variants.indices.map { column -> boards.maxOf { it[column].width } }
        val rowH = boards.map { row -> row.maxOf { it.height } }
        val width = GAP + columnW.sumOf { it + GAP }
        val height = LABEL_H + GAP + rowH.sumOf { LABEL_H + it + GAP }

        val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = BACKGROUND
        g.fillRect(0, 0, width, height)
        g.color = TEXT
        g.font = Font("Segoe UI", Font.PLAIN, 15)
        var x = GAP
        variants.forEachIndexed { column, variant ->
            g.drawString(variant.label, x, LABEL_H)
            x += columnW[column] + GAP
        }
        var y = LABEL_H + GAP
        g.font = Font("Segoe UI", Font.BOLD, 17)
        styles.forEachIndexed { index, style ->
            g.drawString(style.title, GAP, y + LABEL_H - 8)
            y += LABEL_H
            x = GAP
            boards[index].forEachIndexed { column, board ->
                g.drawImage(board, x, y, null)
                x += columnW[column] + GAP
            }
            y += rowH[index] + GAP
        }
        g.dispose()
        return sheet
    }
}
