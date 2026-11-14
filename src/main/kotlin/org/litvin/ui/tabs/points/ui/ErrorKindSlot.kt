package org.litvin.ui.tabs.points.ui

import org.litvin.ui.commons.Palette
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The space for the error kind strip under the point bar (B-72).
 * - While [reserved] is true, the slot keeps the height of [strip], also when the strip is hidden. Thus the video area
 *   above the point bar keeps its size when the strip shows or hides.
 * - While [reserved] is false, the slot has the height of the strip only when the strip shows.
 * - The empty slot shows the background and the top line of the strip.
 */
internal class ErrorKindSlot(private val strip: JComponent) : JPanel(BorderLayout()) {
    var reserved: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }

    init {
        name = "points-error-kind-slot"
        isOpaque = true
        add(strip, BorderLayout.CENTER)
    }

    override fun getPreferredSize(): Dimension {
        val stripSize = strip.preferredSize
        val height = if (reserved || strip.isVisible) stripSize.height else 0
        return Dimension(stripSize.width, height)
    }

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        g.color = Palette.OVERLAY
        g.fillRect(0, 0, width, height)
        g.color = Palette.LINE
        g.fillRect(0, 0, width, 1)
    }
}
