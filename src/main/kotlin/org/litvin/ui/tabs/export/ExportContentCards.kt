package org.litvin.ui.tabs.export

import org.litvin.export.ExportPointSummary
import org.litvin.export.RenderFormatting
import javax.swing.ButtonGroup
import org.litvin.ui.commons.Palette
import org.litvin.i18n.tr

/**
 * The content choice as three radio cards. Each card shows the length of the exported video
 * and the number of points in it. The panel owns the cards and their ButtonGroup.
 */
internal class ExportContentCards : Stack(6) {
    val fullVideo = ChoiceCard("export-content-full")
    val points = ChoiceCard("export-content-points")
    val favorites = ChoiceCard("export-content-favorites")

    private class Entry(val card: ChoiceCard, val title: String, val summary: String, val hasCount: Boolean)

    private val entries = listOf(
        Entry(fullVideo, tr("export.content.full"), tr("export.content.full.summary"), hasCount = false),
        Entry(points, tr("export.content.points"), tr("export.content.points.summary"), hasCount = true),
        Entry(favorites, tr("export.content.favorites"), tr("export.content.favorites.summary"), hasCount = true),
    )

    init {
        name = "export-content-cards"
        val group = ButtonGroup()
        entries.forEach { entry ->
            group.add(entry.card)
            entry.card.titleText.setText(entry.title, entry.card.titleText.runs.first().font, Palette.FG)
            entry.card.subText.setText(entry.summary, entry.card.subText.runs.first().font, Palette.FG_2)
            add(entry.card)
        }
        points.isSelected = true
        show(null, null)
    }

    /**
     * Shows the values. The lengths are for the valid points only, because the export skips
     * empty and overlapping points. A null value shows a dash. The full video has no point count.
     */
    fun show(summary: ExportPointSummary?, sourceDurationMs: Long?) {
        val (full, pointsEntry, favoritesEntry) = entries
        showValues(full, sourceDurationMs?.let(RenderFormatting::formatDurationWords), null)
        showValues(pointsEntry, summary?.let { RenderFormatting.formatDurationWords(it.validTotalMs) }, summary?.validPointCount)
        showValues(favoritesEntry, summary?.let { RenderFormatting.formatDurationWords(it.favoriteTotalMs) }, summary?.favoriteCount)
    }

    /** Shows the length and "128 points" or "1 point". A null value shows a dash. A count of 0 is a warning. */
    private fun showValues(entry: Entry, length: String?, count: Int?) {
        val card = entry.card
        card.metaValue.setText(length ?: DASH, card.metaValue.runs.first().font, Palette.FG)
        val unit = when {
            !entry.hasCount -> ""
            count == null -> DASH
            else -> tr("export.points", "count" to count)
        }
        card.metaUnit.setText(unit, card.metaUnit.runs.first().font, if (count == 0) Palette.YELLOW else Palette.FG_2)
        card.getAccessibleContext().accessibleName = listOf(entry.title, entry.summary, length ?: DASH, unit)
            .filter { it.isNotEmpty() }
            .joinToString(", ")
        card.revalidate()
        card.repaint()
    }

    private companion object {
        const val DASH = "—"
    }
}
