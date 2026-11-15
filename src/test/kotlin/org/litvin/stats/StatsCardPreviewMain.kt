package org.litvin.stats

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.scoring.Ending
import org.litvin.scoring.ErrorKind
import org.litvin.scoring.Outcome
import org.litvin.scoring.PointDetailV1
import org.litvin.scoring.ScoringMode
import org.litvin.scoring.ScoreV1
import org.litvin.ui.commons.ScoreboardSceneImage
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

/**
 * Renders the pages of the statistics card of the video to a PNG file. Use it to look at a card change without the app.
 * The pages come from the same [StatsCard] scenes as the export, drawn by [ScoreboardSceneImage] on a green background.
 *
 * Run it from the repository root:
 * ```
 * mvn -q test-compile exec:java -Dexec.classpathScope=test -Dapp.mainClass=org.litvin.stats.StatsCardPreviewMainKt -Dexec.args="--rows=points_won,games_won"
 * ```
 * Options:
 * - `--out=<file.png>`: the output file. The default is `target/stats-card-preview/stats-card.png`.
 * - `--rows=<key,key>`: the [MatchStat] keys of the rows. The default is the default rows of the video.
 * - `--momentum=<true|false>`: add the momentum chart page. The default is false.
 * - `--compact=<true|false>`: use the compact card. The default is false.
 * - `--groupTitles=<true|false>`: show the group titles. The default is false.
 * - `--light=<true|false>`: use the light card. The default is the dark card.
 * - `--style=<key>`: the [CardStyle.key] of the card style. The default is the classic style.
 * - `--advanced=<true|false>`: use a sample match with point details and server marks. The default is false.
 * - `--scale=<number>`: the page scale. 1.0 is the size on a 1080p video. The default is 0.5.
 * - `--language=<code>`: the language of the video texts, for example `ru`. The default is English.
 */
fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val known = setOf("out", "rows", "momentum", "compact", "groupTitles", "style", "light", "advanced", "scale", "language")
    val options = args.associate { arg ->
        require(arg.startsWith("--") && "=" in arg) { "Unknown argument: $arg. Use ${known.joinToString { "--$it=" }}." }
        arg.removePrefix("--").substringBefore('=') to arg.substringAfter('=')
    }
    val unknown = options.keys - known
    require(unknown.isEmpty()) { "Unknown options: $unknown. Use ${known.joinToString { "--$it=" }}." }
    val out = File(options["out"] ?: "target/stats-card-preview/stats-card.png")
    val rows = options["rows"]?.split(',')?.map { it.trim() } ?: MatchStat.defaultVideoKeys
    val scale = options["scale"]?.toDouble() ?: 0.5
    options["language"]?.let { code ->
        val language = requireNotNull(AppLanguage.ofCode(code)) { "Unknown language: $code. Use one of ${AppLanguage.entries.map { it.code }}." }
        I18n.install(Messages.of(language))
    }

    val settings = StatsSettingsV1(
        videoStats = rows,
        videoMomentum = options["momentum"].toBoolean(),
        compactCard = options["compact"].toBoolean(),
        cardGroupTitles = options["groupTitles"].toBoolean(),
        cardStyle = options["style"] ?: CardStyle.CLASSIC.key,
        lightCard = options["light"].toBoolean(),
    )
    val pages = StatsCard.pages(StatsCard.content(StatsCardPreviewSample.report(advanced = options["advanced"].toBoolean()), settings))
    require(pages.isNotEmpty()) { "The card has no pages. Select rows with a value." }
    out.absoluteFile.parentFile.mkdirs()
    ImageIO.write(StatsCardPreviewSample.sheet(pages.map { ScoreboardSceneImage.render(it, scale) }), "png", out)
    println("Statistics card preview: ${out.absolutePath}")
}

/** A sample match for the card preview, and the sheet that holds the pages. */
object StatsCardPreviewSample {
    private const val GAP = 24
    private val BACKGROUND = Color(0x2E5E3A)

    /**
     * About 100 points of 6 s each with a gap of 14 s. Player 2 wins about two thirds of the points.
     * With [advanced], the first point has a server mark, and each point has an ending, so that the serve rows
     * and the ending rows have values.
     */
    fun report(advanced: Boolean = false): StatsReport {
        val random = Random(7)
        val points = (1..100).map { PointV1(id = "p$it", startMs = it * 20_000, endMs = it * 20_000 + 6_000) }
        val outcomes = points.associate { it.id to if (random.nextDouble() < 0.36) Outcome.P1 else Outcome.P2 }
        val score = ScoreV1(player1Name = "Edvard", player2Name = "Dima", player2ColorHex = "#FF3B3B", outcomes = outcomes)
        if (!advanced) return StatsReport.build(EdlV1(points = points), score)
        val endings = listOf(Ending.ACE, Ending.WINNER, Ending.WINNER, Ending.FORCED_ERROR, Ending.UNFORCED_ERROR, Ending.UNFORCED_ERROR)
        val details = points.associate { point ->
            val ending = endings[random.nextInt(endings.size)]
            val kind = if (ending == Ending.UNFORCED_ERROR) ErrorKind.entries[random.nextInt(ErrorKind.entries.size)] else null
            point.id to PointDetailV1(ending, kind)
        }
        val detailed = score.copy(serverMarks = mapOf("p1" to Outcome.P1), scoringMode = ScoringMode.ADVANCED, details = details)
        return StatsReport.build(EdlV1(points = points), detailed)
    }

    /** The pages one below the other. */
    fun sheet(pages: List<BufferedImage>): BufferedImage {
        val width = pages.maxOf { it.width } + 2 * GAP
        val height = pages.sumOf { it.height + GAP } + GAP
        val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = BACKGROUND
        g.fillRect(0, 0, width, height)
        var y = GAP
        for (page in pages) {
            g.drawImage(page, GAP, y, null)
            y += page.height + GAP
        }
        g.dispose()
        return sheet
    }
}
