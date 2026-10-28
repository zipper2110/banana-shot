package org.litvin.ui.tabs.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-11 E3-T22: a narrow exports table hides columns, so that the text columns stay readable. */
class ExportTableGridTest {
    @Test
    fun `a wide table shows all columns at their minimum or more`() {
        val columns = ExportTableGrid.columns(ExportTableGrid.MIN_WIDTH)
        (0 until 7).forEach { assertTrue(columns.isShown(it), "column $it") }
        assertTrue(columns.width[2] >= 160)
    }

    @Test
    fun `a narrow table hides the Video column first, then the Time and the Size columns`() {
        val widths = (300..ExportTableGrid.MIN_WIDTH).map { ExportTableGrid.columns(it) }
        // Each width that shows the text columns gives them the readable width.
        widths.forEach { columns ->
            assertTrue(columns.width[1] >= ExportTableGrid.READABLE || !columns.isShown(4), "file ${columns.width[1]}")
        }
        val narrow = ExportTableGrid.columns(540)
        assertFalse(narrow.isShown(3))
        assertFalse(narrow.isShown(5))
        assertTrue(narrow.isShown(4))
        assertTrue(narrow.width[2] >= ExportTableGrid.READABLE, "content ${narrow.width[2]}")
        // The status, file, content, and actions columns always show.
        listOf(0, 1, 2, 6).forEach { assertTrue(ExportTableGrid.columns(300).isShown(it)) }
    }

    @Test
    fun `the shown columns follow each other with one gap and end inside the table`() {
        listOf(540, 700, 900).forEach { total ->
            val columns = ExportTableGrid.columns(total)
            val shown = (0 until 7).filter { columns.isShown(it) }
            shown.zipWithNext().forEach { (a, b) ->
                assertEquals(columns.x[a] + columns.width[a] + ExportTableGrid.GAP, columns.x[b], "at $total")
            }
            val last = shown.last()
            assertTrue(columns.x[last] + columns.width[last] <= total - ExportTableGrid.PAD + 1, "at $total")
        }
    }
}
