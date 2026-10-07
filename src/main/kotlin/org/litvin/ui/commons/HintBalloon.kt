package org.litvin.ui.commons

import org.litvin.ui.UiStyles
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsDevice.WindowTranslucency
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * A balloon hint that points at an anchor component. The balloon has a message and a close button.
 * With [Placement.RIGHT], the balloon is to the right of the anchor and points left.
 * With [Placement.LEFT], the balloon is to the left of the anchor and points right.
 * With [Placement.ABOVE], the balloon is above the anchor and points down.
 *
 * The balloon is in its own transparent window, owned by the window of the anchor. The video preview is a
 * native window, and a Swing component above it shows a black rectangle around its shape. A window does not.
 * The balloon follows the anchor when the window moves or resizes. It stays until the user clicks the close button,
 * the owner calls [hideBalloon], or the anchor goes off the screen.
 */
class HintBalloon(
    message: String,
    private val placement: Placement = Placement.RIGHT,
    private val onClose: () -> Unit,
) : JPanel(BorderLayout(8, 0)) {
    enum class Placement { RIGHT, LEFT, ABOVE }

    private var anchor: JComponent? = null
    private var host: JWindow? = null

    /** Called each time the balloon goes off the screen, also after the close button. */
    var onHidden: (() -> Unit)? = null

    private val anchorListener = object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) = reposition()
        override fun componentResized(e: ComponentEvent) = reposition()
        override fun componentHidden(e: ComponentEvent) = hideBalloon()
    }
    private val ancestorListener = object : HierarchyBoundsAdapter() {
        override fun ancestorMoved(e: HierarchyEvent) = reposition()
        override fun ancestorResized(e: HierarchyEvent) = reposition()
    }

    // The anchor goes off the screen when its tab closes or when its parent removes it.
    private val showingListener = HierarchyListener { e ->
        if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && anchor?.isShowing == false) hideBalloon()
    }

    init {
        name = "hint-balloon"
        isOpaque = false
        border = when (placement) {
            Placement.RIGHT -> EmptyBorder(PADDING, ARROW_WIDTH + PADDING + 2, PADDING, PADDING)
            Placement.LEFT -> EmptyBorder(PADDING, PADDING + 2, PADDING, ARROW_WIDTH + PADDING)
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
        get() = host != null

    /** Shows the balloon next to [target], at the side that [placement] sets. Does nothing if [target] is not on the screen. */
    fun showAt(target: JComponent) {
        hideBalloon()
        val owner = SwingUtilities.getWindowAncestor(target) ?: return
        if (!target.isShowing) return
        val window = JWindow(owner).apply {
            name = HOST_NAME
            // A click on the balloon must not take the keyboard focus from the main window.
            focusableWindowState = false
            contentPane = this@HintBalloon
            if (supports(owner, WindowTranslucency.PERPIXEL_TRANSLUCENT)) background = Palette.CLEAR
        }
        anchor = target
        host = window
        target.addComponentListener(anchorListener)
        target.addHierarchyBoundsListener(ancestorListener)
        target.addHierarchyListener(showingListener)
        reposition()
        window.isVisible = true
    }

    /** Removes the balloon from the screen. The close callback does not run. */
    fun hideBalloon() {
        val window = host
        anchor?.removeComponentListener(anchorListener)
        anchor?.removeHierarchyBoundsListener(ancestorListener)
        anchor?.removeHierarchyListener(showingListener)
        anchor = null
        host = null
        if (window != null) {
            window.dispose()
            onHidden?.invoke()
        }
    }

    private fun reposition() {
        val target = anchor ?: return
        val window = host ?: return
        if (!target.isShowing) {
            hideBalloon()
            return
        }
        // Screen coordinates. The balloon stays inside the window of the anchor.
        val area = window.owner?.bounds ?: return
        val size = preferredSize
        val location = target.locationOnScreen
        val x: Int
        val y: Int
        if (placement == Placement.ABOVE) {
            // The arrow is a little to the left of the balloon edge, as in the design, and points at the anchor center.
            x = (location.x + target.width / 2 - ARROW_OFFSET)
                .coerceAtMost(area.x + area.width - size.width - GAP)
                .coerceAtLeast(area.x + GAP)
            y = (location.y - GAP - size.height).coerceAtLeast(area.y)
            arrowCenter = location.x + target.width / 2 - x
        } else {
            x = if (placement == Placement.LEFT) (location.x - GAP - size.width).coerceAtLeast(area.x) else location.x + target.width + GAP
            y = (location.y + target.height / 2 - size.height / 2)
                .coerceAtMost(area.y + area.height - size.height)
                .coerceAtLeast(area.y)
            arrowCenter = location.y + target.height / 2 - y
        }
        window.setBounds(x, y, size.width, size.height)
        window.validate()
        // Without translucent windows, a shaped window still hides the corners. Its edge is not smooth.
        if (window.background.alpha != 0 && supports(window, WindowTranslucency.PERPIXEL_TRANSPARENT)) window.shape = outline()
        repaint()
    }

    // Position of the arrow tip along the balloon edge (vertical for RIGHT and LEFT, horizontal for ABOVE), so that the arrow points at the anchor center
    private var arrowCenter = 0

    /** The body and the arrow of the balloon, in the coordinates of this panel. */
    private fun outline(): Shape {
        val body: RoundRectangle2D.Float
        val arrow = Path2D.Double()
        when (placement) {
            Placement.ABOVE -> {
                body = RoundRectangle2D.Float(0.5f, 0.5f, (width - 1).toFloat(), (height - ARROW_WIDTH - 1).toFloat(), ARC, ARC)
                val tipX = arrowCenter.coerceIn(ARC.toInt(), width - ARC.toInt()).toDouble()
                val base = height - ARROW_WIDTH - 1.0
                arrow.moveTo(tipX - ARROW_HALF_HEIGHT, base)
                arrow.lineTo(tipX, height - 0.5)
                arrow.lineTo(tipX + ARROW_HALF_HEIGHT, base)
            }
            Placement.LEFT -> {
                body = RoundRectangle2D.Float(0.5f, 0.5f, (width - ARROW_WIDTH - 1).toFloat(), (height - 1).toFloat(), ARC, ARC)
                val tipY = arrowCenter.coerceIn(ARC.toInt(), height - ARC.toInt()).toDouble()
                val base = width - ARROW_WIDTH - 1.0
                arrow.moveTo(base, tipY - ARROW_HALF_HEIGHT)
                arrow.lineTo(width - 0.5, tipY)
                arrow.lineTo(base, tipY + ARROW_HALF_HEIGHT)
            }
            Placement.RIGHT -> {
                body = RoundRectangle2D.Float(
                    ARROW_WIDTH.toFloat(), 0.5f,
                    (width - ARROW_WIDTH - 1).toFloat(), (height - 1).toFloat(),
                    ARC, ARC,
                )
                val tipY = arrowCenter.coerceIn(ARC.toInt(), height - ARC.toInt()).toDouble()
                arrow.moveTo(ARROW_WIDTH + 1.0, tipY - ARROW_HALF_HEIGHT)
                arrow.lineTo(0.5, tipY)
                arrow.lineTo(ARROW_WIDTH + 1.0, tipY + ARROW_HALF_HEIGHT)
            }
        }
        arrow.closePath()
        return Area(body).apply { add(Area(arrow)) }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val shape = outline()
            g2.color = BACKGROUND
            g2.fill(shape)
            g2.color = Palette.LIME
            g2.stroke = BasicStroke(1f)
            g2.draw(shape)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        /** The name of the window that shows a balloon. */
        const val HOST_NAME = "hint-balloon-window"

        private const val GAP = 4
        private const val PADDING = 10
        private const val TEXT_WIDTH = 210
        private const val ARROW_WIDTH = 10
        private const val ARROW_HALF_HEIGHT = 8.0
        private const val ARROW_OFFSET = 36
        private const val ARC = 12f
        private val BACKGROUND get() = Palette.LINE

        private fun supports(window: Window, kind: WindowTranslucency): Boolean =
            runCatching { window.graphicsConfiguration.device.isWindowTranslucencySupported(kind) }.getOrDefault(false)
    }
}
