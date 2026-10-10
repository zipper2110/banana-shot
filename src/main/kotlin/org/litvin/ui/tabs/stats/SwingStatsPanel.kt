package org.litvin.ui.tabs.stats

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.projects.ManifestIO
import org.litvin.scoring.PerPlayer
import org.litvin.stats.MatchStat
import org.litvin.stats.Momentum
import org.litvin.stats.StatRows
import org.litvin.stats.StatsCard
import org.litvin.stats.StatsIO
import org.litvin.stats.StatsReport
import org.litvin.stats.StatsSettingsV1
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.VideoFrameLoader
import org.litvin.ui.commons.VideoFrameRequest
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

/**
 * The Stats tab: the match statistics of the project, for the full match or for one set.
 * The user also selects here the rows that the exported video shows.
 *
 * The left side shows the statistics: the player names, the Match / Set control, the momentum chart
 * and the rows in groups, each with an "In video" checkbox in the last column.
 * The right side shows the statistics card of the exported video. The two sides have the same width.
 * The layout comes from design/stats-redesign/final.html.
 *
 * The tab reads edl.json and score.json each time it opens, so it always shows the latest scoring.
 */
class SwingStatsPanel(
    private val dialogs: UserDialogService,
    private val onOpenScoring: () -> Unit = {},
    /** Opens the point with this id in the Scoring tab. */
    private val onOpenPoint: (String) -> Unit = {},
    /** The Match / Set control moves between many card frames, so the cache keeps more frames than the default. */
    private val frameLoader: VideoFrameLoader = VideoFrameLoader(cacheSize = 8),
) : JPanel(BorderLayout()) {
    private val logger = KotlinLogging.logger {}

    private var projectDir: String? = null
    private var manifestPath: String? = null
    /** The video frame that the preview shows or waits for. */
    private var frameRequest: VideoFrameRequest? = null
    private var report: StatsReport? = null
    private var settings = StatsSettingsV1()

    /** 0 is the full match. 1 and more are the sets. */
    private var scope = 0

    private val cards = CardLayout()
    private val body = JPanel(cards)
    private val header = StatsTableHeader()
    private val coverage = CoverageNote()
    private val table = StatsTable(
        onScope = ::selectScope,
        onMomentumInVideo = ::setMomentumInVideo,
        onInVideo = ::setInVideo,
        onShortLimit = { value -> setPointLimits(settings.copy(shortPointMaxSeconds = value)) },
        onLongLimit = { value -> setPointLimits(settings.copy(longPointMinSeconds = value)) },
        onOpenPoint = { pointId -> onOpenPoint(pointId) },
    )
    private val preview = StatsCardPreview()
    private val cardColumn = StatsCardColumn(preview, ::showPreviewPage, ::setCardTransparency)
    private val emptyView = StatsEmptyView { onOpenScoring() }

    init {
        background = Palette.BG
        body.background = Palette.BG
        body.add(statsView(), CARD_STATS)
        body.add(emptyView, CARD_EMPTY)
        add(body, BorderLayout.CENTER)
        showEmpty(StatsEmptyView.Kind.NO_PROJECT, "No project", "Open a project in the Projects tab.")
    }

    fun setProjectManifest(path: String?) {
        projectDir = path?.let { File(it).parentFile.absolutePath }
        manifestPath = path
        scope = 0
        // The frame of another project is not correct for this project.
        frameRequest = null
        preview.background = null
    }

    /** Reads the project again and shows the latest statistics. */
    fun onActivated() {
        val dir = projectDir ?: return showEmpty(StatsEmptyView.Kind.NO_PROJECT, "No project", "Open a project in the Projects tab.")
        val loaded = try {
            settings = StatsIO.readForProjectDir(dir)
            StatsReport.load(dir)
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot read the statistics of $dir" }
            return showEmpty(StatsEmptyView.Kind.ERROR, "Cannot read the statistics", failure.message ?: failure.toString())
        }
        report = loaded
        if (!loaded.hasScoredPoints) {
            return showEmpty(
                StatsEmptyView.Kind.NO_SCORED_POINTS,
                "No scored points",
                "Score the points in the Scoring tab. The statistics then show here.",
            )
        }
        if (scope > loaded.sets.size) scope = 0
        rebuild()
        cards.show(body, CARD_STATS)
    }

    private fun showEmpty(kind: StatsEmptyView.Kind, title: String, text: String) {
        report = null
        emptyView.show(kind, title, text)
        cards.show(body, CARD_EMPTY)
    }

    private fun statsView(): JComponent {
        val scroll = JScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
            background = Palette.BG
            viewport.background = Palette.BG
            // The header with the player names and the Match / Set control stay at the top while the table scrolls.
            setColumnHeaderView(StatsTableTop(header, table.scopeRow))
            columnHeader.background = Palette.BG
            setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, HeaderCorner())
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 16
            try { applyDarkScrollbar(this, Palette.BG) } catch (_: Throwable) { }
        }
        // The coverage note is above the table, so it stays visible.
        val notes = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                if (!coverage.isVisible) return Dimension(0, 0)
                val insets = insets
                val width = (parent?.width ?: 0) - insets.left - insets.right
                return Dimension(width, coverage.heightForWidth(width.coerceAtLeast(1)) + insets.top + insets.bottom)
            }
        }.apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(12, StatsTable.PAD_LEFT, 8, 12)
            add(coverage, BorderLayout.CENTER)
        }
        val left = JPanel(BorderLayout()).apply {
            background = Palette.BG
            add(notes, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }
        val right = JScrollPane(cardColumn).apply {
            border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)
            background = Palette.CARD
            viewport.background = Palette.CARD
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
            try { applyDarkScrollbar(this, Palette.CARD) } catch (_: Throwable) { }
        }
        // The two sides have the same width.
        return JPanel(GridLayout(1, 2)).apply {
            background = Palette.BG
            add(left)
            add(right)
        }
    }

    /** The corner above the vertical scroll bar, next to the table header. */
    private class HeaderCorner : JComponent() {
        override fun paintComponent(g: java.awt.Graphics) {
            g.color = Palette.BG
            g.fillRect(0, 0, width, height)
            g.color = Palette.LINE
            g.fillRect(0, height - 1, width, 1)
        }
    }

    private fun rebuild() {
        val report = report ?: return
        val score = report.score
        val names = PerPlayer(
            displayName(score.player1Name, score.sport.defaultSideName(1)),
            displayName(score.player2Name, score.sport.defaultSideName(2)),
        )
        val colors = PerPlayer(parseColor(score.player1ColorHex), parseColor(score.player2ColorHex))
        header.show(names, colors)
        // One set is the same as the match, so the Match / Set control shows only when there are two sets or more.
        if (report.sets.size < 2) scope = 0
        table.showScopes(report.setScores(), scope)
        val stats = if (scope == 0) report.match else report.sets[scope - 1]
        coverage.lines = listOfNotNull(
            "Based on ${stats.scoredPoints} of ${stats.points} points. The other points have no winner."
                .takeIf { stats.scoredPoints < stats.points },
            (if (report.pointsAfterMatchEnd == 1) "The statistics do not use the point after the end of the match."
            else "The statistics do not use the ${report.pointsAfterMatchEnd} points after the end of the match.")
                .takeIf { report.pointsAfterMatchEnd > 0 },
        )
        table.chart.names = names
        table.chart.colors = colors
        table.chart.momentum = Momentum.of(report, scope)
        table.momentumCheck.isSelected = settings.videoMomentum
        cardColumn.showTransparency(settings.normalized().cardTransparencyPercent)
        showRows(colors)
        updatePreview()
        revalidate()
        repaint()
    }

    /** Shows the values of the rows and their "In video" selection. The row components stay the same. */
    private fun showRows(colors: PerPlayer<Color> = playerColors()) {
        val report = report ?: return
        val stats = if (scope == 0) report.match else report.sets[scope - 1]
        val normalized = settings.normalized()
        table.shortLimit.show(normalized.shortPointMaxSeconds)
        table.longLimit.show(normalized.longPointMinSeconds)
        for (row in StatRows.build(stats, report.score.rules.normalized(), settings)) {
            table.rows[row.stat]?.show(row, description(row.stat), settings.inVideo(row.stat), colors)
        }
        table.showGroupCounts(settings::inVideo)
        table.revalidate()
        table.repaint()
    }

    private fun playerColors(): PerPlayer<Color> {
        val score = report?.score ?: return PerPlayer(Palette.FG, Palette.FG)
        return PerPlayer(parseColor(score.player1ColorHex), parseColor(score.player2ColorHex))
    }

    private fun selectScope(index: Int) {
        if (scope == index) return
        scope = index
        rebuild()
    }

    /** A longer explanation of a row, or null when the label is sufficient. */
    private fun description(stat: MatchStat): String? = when (stat) {
        MatchStat.SHORT_POINTS_WON -> "The points that last ${settings.normalized().shortPointMaxSeconds} s or less, " +
            "from the start mark to the end mark."
        MatchStat.LONG_POINTS_WON -> "The points that last ${settings.normalized().longPointMinSeconds} s or more, " +
            "from the start mark to the end mark."
        MatchStat.RETURN_GAMES_WON -> "The games that the opponent served and lost (breaks)."
        MatchStat.DURATION -> "The time from the start of the first point with a winner to the end of the last point " +
            "with a winner. It is not the length of the video."
        else -> null
    }

    private fun updatePreview() {
        val report = report ?: return
        updatePreviewPages(report)
        updatePreviewFrame(report)
    }

    private fun updatePreviewPages(report: StatsReport) {
        preview.sport = report.score.sport
        val content = StatsCard.content(report, settings, scope)
        preview.pages = StatsCard.pages(content)
        val rowCount = content.rows.size
        cardColumn.showSummary(
            "${content.title} · $rowCount ${if (rowCount == 1) "row" else "rows"}" +
                if (content.momentum != null) " and the momentum chart" else "",
        )
        showPreviewPage(preview.page)
    }

    /**
     * Loads the video frame under the card: the end of the last point for the match card,
     * or the end of the last point of the set for a set card. The export freezes the same frame
     * when the video has these points.
     */
    private fun updatePreviewFrame(report: StatsReport) {
        val request = try {
            val source = manifestPath?.let { ManifestIO.read(it).sourceVideo }?.takeIf { File(it).isFile }
            val lastPoint = if (scope == 0) report.points.lastOrNull() else report.setRanges.getOrNull(scope - 1)?.let { report.points[it.last] }
            if (source == null || lastPoint == null) {
                null
            } else {
                val adjustments = projectDir?.let(AdjustmentsIO::readForProjectDir) ?: AdjustmentsV1()
                VideoFrameRequest(source, lastPoint.endMs.toLong(), adjustments)
            }
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot find the video frame for the statistics preview" }
            null
        }
        if (request == frameRequest) return
        frameRequest = request
        if (request == null) {
            preview.background = null
            return
        }
        // Keep the old frame until the new frame is ready, so the preview does not flash the drawn court.
        frameLoader.cached(request)?.let { preview.background = it }
        frameLoader.load(request) { image ->
            if (frameRequest == request && image != null) preview.background = image
        }
    }

    private fun showPreviewPage(page: Int) {
        val count = preview.pages.size
        preview.page = page.coerceIn(0, (count - 1).coerceAtLeast(0))
        cardColumn.showPage(preview.page, count)
    }

    private fun setPointLimits(requested: StatsSettingsV1) {
        val dir = projectDir ?: return
        val updated = requested.normalized()
        try {
            StatsIO.writeForProjectDir(dir, updated)
            settings = updated
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save stats.json in $dir" }
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the statistics setup")
        }
        // The rows update in place, so the steppers keep the focus.
        showRows()
        updatePreview()
    }

    /**
     * Shows the new transparency in the preview while the user moves the slider.
     * Saves stats.json when the user releases the slider.
     */
    private fun setCardTransparency(percent: Int, adjusting: Boolean) {
        val report = report ?: return
        val dir = projectDir ?: return
        settings = settings.copy(cardTransparencyPercent = percent)
        updatePreviewPages(report)
        if (adjusting) return
        try {
            StatsIO.writeForProjectDir(dir, settings)
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save stats.json in $dir" }
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the statistics setup")
        }
    }

    private fun setMomentumInVideo(selected: Boolean) {
        val dir = projectDir ?: return
        val updated = settings.copy(videoMomentum = selected)
        try {
            StatsIO.writeForProjectDir(dir, updated)
            settings = updated
            updatePreview()
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save stats.json in $dir" }
            table.momentumCheck.isSelected = !selected
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the statistics setup")
        }
    }

    private fun setInVideo(stat: MatchStat, selected: Boolean, check: InVideoCheck) {
        val dir = projectDir ?: return
        val updated = settings.withInVideo(stat, selected)
        try {
            StatsIO.writeForProjectDir(dir, updated)
            settings = updated
            table.rows[stat]?.showSelected(selected)
            table.showGroupCounts(settings::inVideo)
            updatePreview()
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save stats.json in $dir" }
            check.isSelected = !selected
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the statistics setup")
        }
    }

    private fun displayName(name: String, fallback: String) = name.trim().ifEmpty { fallback }

    /**
     * The player color, lighter when it is too dark for a dark theme and darker when it is too light for the Light theme.
     * The card in the video is always dark, so it makes its own color with [StatsCard.onPanel].
     */
    private fun parseColor(hex: String): Color =
        hex.removePrefix("#").toIntOrNull(16)?.let { Palette.readableOnSurface(Color(it)) } ?: Palette.FG

    private companion object {
        const val CARD_STATS = "stats"
        const val CARD_EMPTY = "empty"
    }
}
