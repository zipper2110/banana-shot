package org.litvin.ui.commons

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsDevice.WindowTranslucency
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import org.litvin.i18n.tr

/**
 * A toast: a short hint that closes by itself after [durationMs] (B-68, "Hints"). It does not stop the video and does
 * not take the keyboard focus. It has a "Don't show again" button, which calls [onDontShowAgain].
 *
 * The toast is in its own window, owned by the window of the anchor, as [HintBalloon]. The video preview is a native
 * window, and a Swing component above it shows a black rectangle. A window does not.
 * The toast shows centered above the anchor. While the pointer is on the toast, the toast stays.
 */
class HintToast(
    message: String,
    private val durationMs: Int = DEFAULT_DURATION_MS,
    private val onDontShowAgain: () -> Unit,
) : JPanel(BorderLayout(12, 0)) {
    private var anchor: JComponent? = null
    private var host: JWindow? = null
    private val timer = Timer(durationMs) { hideToast() }.apply { isRepeats = false }

    /** Called each time the toast goes off the screen. */
    var onHidden: (() -> Unit)? = null

    // The anchor goes off the screen when its tab closes or when its parent removes it.
    private val showingListener = HierarchyListener { e ->
        if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && anchor?.isShowing == false) hideToast()
    }

    // The toast stays while the pointer is on it, so that the user can read it and click "Don't show again".
    private val hoverListener = object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) = timer.stop()
        override fun mouseExited(e: MouseEvent) {
            val window = host ?: return
            val point = SwingUtilities.convertPoint(e.component, e.point, window)
            if (point.x !in 0 until window.width || point.y !in 0 until window.height) timer.restart()
        }
    }

    init {
        name = "hint-toast"
        isOpaque = false
        border = EmptyBorder(PADDING, PADDING + 2, PADDING, PADDING)
        val label = JLabel("<html><div style='width:${TEXT_WIDTH}px'>${Html.escapeHtml(message)}</div></html>").apply {
            name = "hint-toast-message"
            foreground = Palette.FG
        }
        add(label, BorderLayout.CENTER)
        val dontShow = JButton(tr("hint.toast.dontShowAgain")).apply {
            name = "hint-toast-dont-show"
            foreground = Palette.LIME
            font = UiKit.font(12f, UiKit.Weight.SEMIBOLD)
            isFocusPainted = false
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusable = false
            border = EmptyBorder(2, 2, 2, 2)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                hideToast()
                onDontShowAgain()
            }
        }
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(dontShow, BorderLayout.CENTER)
        }, BorderLayout.EAST)
        listOf(this, label, dontShow).forEach { it.addMouseListener(hoverListener) }
    }

    val isShown: Boolean
        get() = host != null

    /** Shows the toast centered above [target] and starts the timer. Does nothing if [target] is not on the screen. */
    fun showAbove(target: JComponent) {
        hideToast()
        val owner = SwingUtilities.getWindowAncestor(target) ?: return
        if (!target.isShowing) return
        val window = JWindow(owner).apply {
            name = HOST_NAME
            // The toast must not take the keyboard focus from the main window: the marking keys go on.
            focusableWindowState = false
            isAutoRequestFocus = false
            contentPane = this@HintToast
            if (supports(owner, WindowTranslucency.PERPIXEL_TRANSLUCENT)) background = Palette.CLEAR
        }
        anchor = target
        host = window
        target.addHierarchyListener(showingListener)
        val size = preferredSize
        val location = target.locationOnScreen
        val area = owner.bounds
        val x = (location.x + (target.width - size.width) / 2)
            .coerceAtMost(area.x + area.width - size.width - GAP)
            .coerceAtLeast(area.x + GAP)
        val y = (location.y - GAP - size.height).coerceAtLeast(area.y)
        window.setBounds(x, y, size.width, size.height)
        window.validate()
        if (window.background.alpha != 0 && supports(window, WindowTranslucency.PERPIXEL_TRANSPARENT)) {
            window.shape = RoundRectangle2D.Float(0f, 0f, size.width.toFloat(), size.height.toFloat(), ARC, ARC)
        }
        window.isVisible = true
        timer.restart()
    }

    /** Removes the toast from the screen. [onDontShowAgain] does not run. */
    fun hideToast() {
        timer.stop()
        val window = host
        anchor?.removeHierarchyListener(showingListener)
        anchor = null
        host = null
        if (window != null) {
            window.dispose()
            onHidden?.invoke()
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val shape = RoundRectangle2D.Float(0.5f, 0.5f, (width - 1).toFloat(), (height - 1).toFloat(), ARC, ARC)
            g2.color = Palette.LINE
            g2.fill(shape)
            g2.color = Palette.LIME
            g2.stroke = BasicStroke(1f)
            g2.draw(shape)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        /** The name of the window that shows a toast. */
        const val HOST_NAME = "hint-toast-window"

        /** A toast closes by itself after this time. */
        const val DEFAULT_DURATION_MS = 7_000

        private const val GAP = 8
        private const val PADDING = 10
        private const val TEXT_WIDTH = 300
        private const val ARC = 12f

        private fun supports(window: Window, kind: WindowTranslucency): Boolean =
            runCatching { window.graphicsConfiguration.device.isWindowTranslucencySupported(kind) }.getOrDefault(false)
    }
}
