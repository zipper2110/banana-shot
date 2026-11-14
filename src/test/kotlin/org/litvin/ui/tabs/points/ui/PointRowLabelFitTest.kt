package org.litvin.ui.tabs.points.ui

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.scoring.ErrorKind
import org.litvin.ui.commons.UiKit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * B-68, check 1 of "Open questions": the row labels of advanced mode fit in the fixed width of the side column
 * ([TableColumns.SIDE_COLUMN_WIDTH]) in all app languages. The label of a row starts at the number column and ends
 * before the marks column ends. A row with a GAME or SET badge has less space: there the label ends with "…".
 */
class PointRowLabelFitTest {
    @Test
    fun `the longest row label of each language fits in the side column`() {
        val font = UiKit.font(LABEL_FONT_SIZE)
        val columns = TableColumns(ROW_WIDTH)
        val available = columns.marksRight - TableColumns.GAP - columns.number
        val problems = AppLanguage.entries.mapNotNull { language ->
            val (text, width) = I18n.inVideo(language) {
                allLabels().map { it.text() }.map { it to UiKit.textWidth(it, font) }.maxBy { it.second }
            }
            println("Longest row label, ${language.code}: \"$text\", ${width.toInt()} of $available px")
            if (width > available) "${language.code}: \"$text\" is ${width.toInt()} px" else null
        }
        assertTrue(problems.isEmpty(), "The labels do not fit in $available px: $problems")
    }

    private fun allLabels(): List<RowLabel> {
        val serves = listOf(RowServe.FIRST, RowServe.SECOND)
        val plain = RowServe.entries.map { RowLabel(it) }
        val endings = serves.flatMap { serve -> RowEnding.entries.map { RowLabel(serve, it) } }
        val errors = serves.flatMap { serve ->
            listOf(RowEnding.FORCED_ERROR, RowEnding.UNFORCED_ERROR).flatMap { ending -> ErrorKind.entries.map { RowLabel(serve, ending, it) } }
        }
        return plain + endings + errors
    }

    private companion object {
        /** The list has a scroll bar of about 12 px. */
        const val ROW_WIDTH = TableColumns.SIDE_COLUMN_WIDTH - 12
        const val LABEL_FONT_SIZE = 11.5f
    }
}
