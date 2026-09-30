package org.litvin.ui.commons

import org.litvin.ui.UiStyles
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * A balloon hint that points at an anchor component. The balloon has a message and a close button.
 * With [Placement.RIGHT], the balloon is to the right of the anchor and points left.
 * With [Placement.ABOVE], the balloon is above the anchor and points down.
 * It lives in the popup layer of the anchor's window, so it follows the anchor when the window moves or resizes.
 * The balloon stays until the user clicks the close button or the owner calls [hideBalloon].
 */
class HintBalloon(
    message: String,
    private val placement: Placement = Placement.RIGHT,
    private val onClose: () -> Unit,
) : JPanel(BorderLayout(8, 0)) {
    enum class Placement { RIGHT, ABOVE }

    private var anchor: JComponent? = null
    private var layeredPane: JLayeredPane? = null

    private val anchorListener = object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) = reposition()
        override fun componentResized(e: ComponentEvent) = reposition()
        override fun componentHidden(e: ComponentEvent) = hideBalloon()
    }
    private val ancestorListener = object : HierarchyBoundsAdapter() {
        override fun ancestorMoved(e: HierarchyEvent) = reposition()
        override fun ancestorResized(e: HierarchyEvent) = reposition()
    }

    init {
        name = "hint-balloon"
        isOpaque = false
        border = when (placement) {
            Placement.RIGHT -> EmptyBorder(PADDING, ARROW_WIDTH + PADDING + 2, PADDING, PADDING)
            Placement.ABOVE -> EmptyBorder(PADDING, PADDING + 2, ARROW_WIDTH + PADDING, PADDING)
        }
        val label = JLabel("<html><div style='width:${TEXT_WIDTH}px'>${Html.escapeHtml(message)}</div></html>")
        label.foreground = Palette.FG
        add(label, BorderLayout.CENTER)
        val close = JButton(UiStyles.closeIcon()).apply {
            name = "hint-balloon-close"
            toolTipText = "Close. Do not show this hint again"
            isFocusPainted = false
            isBorderPainted = false
            isContentAreaFilled = false
            border = EmptyBorder(2, 2, 2, 2)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                hideBalloon()
                onClose()
            }
        }
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(close, BorderLayout.NORTH)
        }, BorderLayout.EAST)
    }

    val isShown: Boolean
        get() = layeredPane != null

    /** Shows the balloon next to [target], at the side that [placement] sets. Does nothing if [target] is not on the screen. */
    fun showAt(target: JComponent) {
        hideBalloon()
        val pane = SwingUtilities.getRootPane(target)?.layeredPane ?: return
        if (!target.isShowing) return
        anchor = target
        layeredPane = pane
        pane.add(this, JLayeredPane.POPUP_LAYER)
        target.addComponentListener(anchorListener)
        target.addHierarchyBoundsListener(ancestorListener)
        reposition()
    }

    /** Removes the balloon from the screen. The close callback does not run. */
    fun hideBalloon() {
        anchor?.removeComponentListener(anchorListener)
        anchor?.removeHierarchyBoundsListener(ancestorListener)
        layeredPane?.let { pane ->
            val bounds = bounds
            pane.remove(this)
            pane.repaint(bounds)
        }
        anchor = null
        layeredPane = null
    }

    private fun reposition() {
        val target = anchor ?: return
        val pane = layeredPane ?: return
        if (!target.isShowing) {
            hideBalloon()
            return
        }
        val size = preferredSize
        val location = SwingUtilities.convertPoint(target.parent, target.location, pane)
        if (placement == Placement.ABOVE) {
            // The arrow is a little to the left of the balloon edge, as in the design, and points at the anchor center.
            val x = (location.x + target.width / 2 - ARROW_OFFSET)
                .coerceAtMost(pane.width - size.width - GAP)
                .coerceAtLeast(GAP)
            val y = (location.y - GAP - size.height).coerceAtLeast(0)
            arrowCenter = location.x + target.width / 2 - x
            setBounds(x, y, size.width, size.height)
        } else {
            val x = location.x + target.width + GAP
            val y = (location.y + target.height / 2 - size.height / 2)
                .coerceAtMost(pane.height - size.height)
                .coerceAtLeast(0)
            arrowCenter = location.y + target.height / 2 - y
            setBounds(x, y, size.width, size.height)
        }
        revalidate()
        pane.repaint()
    }

    // Position of the arrow tip along the balloon edge (vertical for RIGHT, horizontal for ABOVE), so that the arrow points at the anchor center
    private var arrowCenter = 0

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val body: RoundRectangle2D.Float
            val arrow = Path2D.Double()
            if (placement == Placement.ABOVE) {
                body = RoundRectangle2D.Float(0.5f, 0.5f, (width - 1).toFloat(), (height - ARROW_WIDTH - 1).toFloat(), ARC, ARC)
                val tipX = arrowCenter.coerceIn(ARC.toInt(), width - ARC.toInt()).toDouble()
                val base = height - ARROW_WIDTH - 1.0
                arrow.moveTo(tipX - ARROW_HALF_HEIGHT, base)
                arrow.lineTo(tipX, height - 0.5)
                arrow.lineTo(tipX + ARROW_HALF_HEIGHT, base)
                arrow.closePath()
            } else {
                body = RoundRectangle2D.Float(
                    ARROW_WIDTH.toFloat(), 0.5f,
                    (width - ARROW_WIDTH - 1).toFloat(), (height - 1).toFloat(),
                    ARC, ARC,
                )
                val tipY = arrowCenter.coerceIn(ARC.toInt(), height - ARC.toInt()).toDouble()
                arrow.moveTo(ARROW_WIDTH + 1.0, tipY - ARROW_HALF_HEIGHT)
                arrow.lineTo(0.5, tipY)
                arrow.lineTo(ARROW_WIDTH + 1.0, tipY + ARROW_HALF_HEIGHT)
                arrow.closePath()
            }
            val shape = Area(body).apply { add(Area(arrow)) }
            g2.color = BACKGROUND
            g2.fill(shape)
            g2.color = Palette.LIME
            g2.stroke = BasicStroke(1f)
            g2.draw(shape)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val GAP = 4
        const val PADDING = 10
        const val TEXT_WIDTH = 210
        const val ARROW_WIDTH = 10
        const val ARROW_HALF_HEIGHT = 8.0
        const val ARROW_OFFSET = 36
        const val ARC = 12f
        val BACKGROUND = Palette.LINE
    }
}
