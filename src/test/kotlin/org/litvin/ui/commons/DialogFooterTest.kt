package org.litvin.ui.commons

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The foot of a dialog wraps when its buttons do not fit. A long translation must not push a button out of the
 * dialog: in Spanish, the Close button of the expired dialog was out of view.
 */
class DialogFooterTest {
    private fun item(width: Int) = JPanel().apply { preferredSize = Dimension(width, 30) }

    private fun layOut(footer: JComponent, width: Int) {
        footer.setSize(width, (footer as HeightForWidth).heightForWidth(width))
        footer.doLayout()
    }

    @Test
    fun `items that fit stay in one row, the left items on the left and the right items on the right`() {
        val check = item(100)
        val cancel = item(80)
        val ok = item(60)
        val footer = DialogKit.footer(left = listOf(check), right = listOf(cancel, ok))
        layOut(footer, 500)

        assertEquals(30 + 24, footer.height)
        assertEquals(DialogKit.PAD_X, check.x)
        assertEquals(500 - DialogKit.PAD_X - 60, ok.x)
        assertEquals(ok.x - 8 - 80, cancel.x)
        assertEquals(check.y, ok.y)
    }

    @Test
    fun `items that do not fit wrap onto more rows, and each item stays inside the foot`() {
        val check = item(120)
        val update = item(150)
        val download = item(240)
        val close = item(80)
        val footer = DialogKit.footer(left = listOf(check), right = listOf(update, download, close))
        layOut(footer, 440)

        val items = listOf(check, update, download, close)
        items.forEach { assertTrue(it.x >= DialogKit.PAD_X && it.x + it.width <= 440 - DialogKit.PAD_X, "${it.bounds}") }
        assertEquals(DialogKit.PAD_X, check.x)
        assertTrue(update.y > check.y, "The right items start a new row")
        assertEquals(update.y, download.y)
        assertTrue(close.y > download.y, "Close wraps onto a third row")
        assertEquals(440 - DialogKit.PAD_X - 80, close.x)
        assertEquals(close.y + close.height + 12, footer.height)
    }

    @Test
    fun `a hidden item takes no space`() {
        val hidden = item(400).apply { isVisible = false }
        val close = item(80)
        val footer = DialogKit.footer(left = listOf(item(100)), right = listOf(hidden, close))

        assertEquals(30 + 24, (footer as HeightForWidth).heightForWidth(300))
    }

    @Test
    fun `a gap at the start of a wrapped row takes no space`() {
        val reset = item(200)
        val cancel = item(200)
        val footer = DialogKit.footer(right = listOf(reset, DialogKit.gap(16), cancel))
        layOut(footer, 300)

        assertEquals(300 - DialogKit.PAD_X - 200, cancel.x)
        assertTrue(cancel.y > reset.y)
    }
}
