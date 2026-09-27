package org.litvin.ui.tabs.export

import org.litvin.export.ExportPointSummary
import org.litvin.export.RenderFormatting
import org.litvin.ui.UiStyles
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * The content choice as three cards. Each card shows the length of the exported video
 * and the number of points in it. The panel owns the cards and their ButtonGroup.
 */
class ExportContentCards : JPanel() {
    /** One card and its value labels. A null [count] means that the card has no point count. */
    private class Entry(val card: OptionCard, val title: String, val summary: String, val length: Value, val count: Value?)

    /** A bold value and an optional grey unit after it, for example "128 points". */
    private class Value(val value: JLabel, val unit: JLabel?) {
        var warning: Color? = null
    }

    val fullVideo = OptionCard("export-content-full")
    val points = OptionCard("export-content-points")
    val favorites = OptionCard("export-content-favorites")

    private val entries = listOf(
        entry(fullVideo, "Full video", "The complete video, with the time between points", hasCount = false),
        entry(points, "Only points", "The marked points, without the time between them", hasCount = true),
        entry(favorites, "Only favorites", "Only the points that are marked with a star", hasCount = true),
    )

    init {
        name = "export-content-cards"
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = 0f
        val group = ButtonGroup()
        entries.forEachIndexed { index, entry ->
            group.add(entry.card)
            if (index > 0) add(Box.createRigidArea(Dimension(0, 6)))
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
        full.length.value.text = sourceDurationMs?.let(RenderFormatting::formatDurationWords) ?: DASH
        if (summary == null) {
            listOf(pointsEntry, favoritesEntry).forEach {
                it.length.value.text = DASH
                showCount(it, null)
            }
        } else {
            pointsEntry.length.value.text = RenderFormatting.formatDurationWords(summary.validTotalMs)
            showCount(pointsEntry, summary.validPointCount)
            favoritesEntry.length.value.text = RenderFormatting.formatDurationWords(summary.favoriteTotalMs)
            showCount(favoritesEntry, summary.favoriteCount)
        }
        entries.forEach(::updateEntry)
    }

    /** Shows "128 points" or "1 point". A null count shows a dash. A count of 0 is a warning. */
    private fun showCount(entry: Entry, count: Int?) {
        val value = entry.count ?: return
        value.value.text = count?.toString() ?: DASH
        value.unit?.text = if (count == 1) "point" else "points"
        value.warning = UiStyles.YELLOW.takeIf { count == 0 }
    }

    private fun entry(card: OptionCard, title: String, summary: String, hasCount: Boolean): Entry {
        val componentName = card.name
        val length = value("$componentName-length", unit = null)
        val count = if (hasCount) value("$componentName-count", unit = "points") else null
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            listOfNotNull(length, count).forEachIndexed { index, value ->
                if (index > 0) value.value.border = BorderFactory.createEmptyBorder(0, 16, 0, TEXT_SLACK_PX)
                add(value.value)
                value.unit?.let(::add)
            }
        }
        card.setContent(title)
        card.setSubtitle(summary, secondary = true)
        card.setFixedContent(row)
        val entry = Entry(card, title, summary, length, count)
        card.addPropertyChangeListener("enabled") { updateEntry(entry) }
        return entry
    }

    private fun value(componentName: String, unit: String?): Value {
        val value = JLabel(DASH).apply {
            name = componentName
            // The same style as the values in the details of the quality cards.
            UiStyles.styleHelper(this)
            border = BorderFactory.createEmptyBorder(0, 0, 0, TEXT_SLACK_PX)
        }
        val unitLabel = unit?.let {
            JLabel(it).apply {
                UiStyles.styleHelper(this)
                border = BorderFactory.createEmptyBorder(0, 1, 0, TEXT_SLACK_PX)
            }
        }
        return Value(value, unitLabel)
    }

    /** Sets the colors from the enabled state, and the accessible name from the values. */
    private fun updateEntry(entry: Entry) {
        val enabled = entry.card.isEnabled
        listOfNotNull(entry.length, entry.count).forEach {
            it.unit?.foreground = if (enabled) UiStyles.FG_SECONDARY else UiStyles.FG_DISABLED
            it.value.foreground = if (enabled) it.warning ?: UiStyles.FG_PRIMARY else UiStyles.FG_DISABLED
        }
        entry.card.accessibleContext.accessibleName = listOfNotNull(
            entry.title,
            entry.summary,
            entry.length.value.text,
            entry.count?.let { "${it.value.text} ${it.unit?.text}" },
        ).joinToString(", ")
    }

    private companion object {
        const val DASH = "—"
        // The painted text can be a few pixels wider than the measured text. The slack keeps the last character visible.
        const val TEXT_SLACK_PX = 3
    }
}
