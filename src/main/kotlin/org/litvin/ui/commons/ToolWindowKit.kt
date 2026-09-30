package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/*
 * The parts of the tool windows (Help and More): a list of pages on the left and the page on the right.
 * The look comes from design/dialogs-redesign (shared.css, "Tool windows"): the list has the icons of the sidebar,
 * and the page is a column of at most 640 px on the panel background.
 */

/** One entry of a [ToolNav]. [caption] starts a group with a caption above this entry. */
internal data class ToolNavItem<T>(val id: T, val title: String, val icon: Ikon, val caption: String? = null)

/**
 * The page list of a tool window. A click selects a page and calls [onSelect].
 * [markerText] shows at the right of the entry that [marked] names, for example "This tab".
 */
internal class ToolNav<T>(
    private val componentName: String,
    private val items: List<ToolNavItem<T>>,
    private val markerText: String = "",
    private val onSelect: (T) -> Unit,
) : JPanel() {
    private val buttons = items.associate { it.id to NavButton(it) }

    var selected: T? = null
        set(value) {
            field = value
            repaint()
        }

    var marked: T? = null
        set(value) {
            field = value
            buttons.values.forEach { it.repaint() }
        }

    init {
        name = componentName
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = Palette.BG
        isOpaque = true
        border = BorderFactory.createEmptyBorder(12, 8, 12, 8)
        items.forEachIndexed { index, item ->
            item.caption?.let { caption ->
                add(JLabel(caption.uppercase()).apply {
                    font = UiKit.trackedFont(9.5f, 0.14)
                    foreground = Palette.FG_3
                    border = BorderFactory.createEmptyBorder(if (index == 0) 2 else 10, 10, 4, 10)
                    alignmentX = Component.LEFT_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                })
            }
            add(buttons.getValue(item.id))
            add(Box.createVerticalStrut(2))
        }
    }

    /** The button of the entry [id], for tests. */
    fun button(id: T): JButton? = buttons[id]

    override fun getPreferredSize() = Dimension(WIDTH, super.getPreferredSize().height)

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.color = Palette.LINE
        g.fillRect(width - 1, 0, 1, height)
        // The lime bar at the left edge of the list, like the sidebar of the main window.
        val button = selected?.let { buttons[it] } ?: return
        val g2 = UiKit.smooth(g)
        try {
            g2.color = Palette.LIME
            g2.fill(RoundRectangle2D.Double(0.0, button.y + 8.0, 3.0, button.height - 16.0, 3.0, 3.0))
        } finally {
            g2.dispose()
        }
    }

    private inner class NavButton(private val item: ToolNavItem<T>) : JButton(item.title) {
        private val iconIdle = UiKit.icon(item.icon, 18, Palette.FG_3)
        private val iconSelected = UiKit.icon(item.icon, 18, Palette.LIME)

        init {
            name = "$componentName-${item.id.toString().lowercase()}"
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isOpaque = false
            isRolloverEnabled = true
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener {
                selected = item.id
                onSelect(item.id)
            }
        }

        override fun getPreferredSize() = Dimension(WIDTH - 16, 36)
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, 36)
        override fun getMinimumSize() = Dimension(0, 36)
        override fun isSelected() = selected == item.id

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val hover = model.isRollover
                when {
                    isSelected -> UiKit.paintBox(g2, 0, 0, width, height, 6, Palette.OVERLAY, null)
                    hover -> UiKit.paintBox(g2, 0, 0, width, height, 6, Palette.PANEL, null)
                }
                if (isFocusOwner) UiKit.paintBox(g2, 0, 0, width, height, 6, null, Palette.LIME_LINE)
                val icon = if (isSelected) iconSelected else iconIdle
                icon.paintIcon(this, g2, 10, (height - icon.iconHeight) / 2)
                val font = if (isSelected) UiKit.font(13f, UiKit.Weight.SEMIBOLD) else UiKit.font(13f)
                val marker = if (marked == item.id) markerText else ""
                val markerFont = UiKit.font(10f, UiKit.Weight.SEMIBOLD)
                val markerWidth = if (marker.isEmpty()) 0f else UiKit.textWidth(marker, markerFont) + 8f
                val textX = 10f + icon.iconWidth + 10f
                val title = UiKit.ellipsize(item.title, font, width - textX - markerWidth - 8f)
                UiKit.drawText(g2, title, font, if (isSelected || hover) Palette.FG else Palette.FG_2, textX, 0f, height.toFloat())
                if (marker.isNotEmpty()) {
                    UiKit.drawText(g2, marker, markerFont, Palette.SAGE, width - 10f - UiKit.textWidth(marker, markerFont), 0f, height.toFloat())
                }
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        const val WIDTH = 200
    }
}

/**
 * The page of a tool window: a column of at most [MAX_WIDTH] px with space around it, on the panel background.
 * It follows the width of its scroll pane, so the text wraps.
 */
internal open class ToolPage : JPanel(null), Scrollable, HeightForWidth {
    val column = Stack()

    init {
        isOpaque = true
        background = Palette.PANEL
        add(column)
    }

    private fun columnWidth(width: Int) = max(200, min(MAX_WIDTH, width - PAD.left - PAD.right))

    override fun heightForWidth(width: Int) = PAD.top + column.heightForWidth(columnWidth(width)) + PAD.bottom

    override fun getPreferredSize(): Dimension {
        val w = (parent as? JViewport)?.width?.takeIf { it > 0 } ?: (MAX_WIDTH + PAD.left + PAD.right)
        return Dimension(w, heightForWidth(w))
    }

    override fun doLayout() {
        val w = columnWidth(width)
        column.setBounds(PAD.left, PAD.top, w, column.heightForWidth(w))
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = max(16, visibleRect.height - 16)
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false

    /** A borderless scroll pane with a dark scroll bar around this page. */
    fun inScrollPane(): JScrollPane = JScrollPane(this).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        viewport.background = Palette.PANEL
        verticalScrollBar.unitIncrement = 16
        runCatching { applyDarkScrollbar(this, Palette.PANEL) }
    }

    companion object {
        const val MAX_WIDTH = 640
        val PAD = Insets(26, 32, 40, 32)

        /** The page title. */
        fun title(text: String): JLabel = JLabel(text).apply {
            font = UiKit.font(22f, UiKit.Weight.SEMIBOLD)
            foreground = Palette.FG
        }

        /** A section caption in capital letters with an icon: the `h2` element of the Help page. */
        fun caption(text: String, ikon: Ikon): JComponent = object : JLabel(text.uppercase(), UiKit.icon(ikon, 15, Palette.FG_3), LEFT) {
            override fun getPreferredSize(): Dimension =
                Dimension(icon.iconWidth + iconTextGap + ceil(UiKit.textWidth(text, font)).toInt() + 4, 18)
        }.apply {
            font = UiKit.trackedFont(10.5f, 0.1, UiKit.Weight.BOLD)
            foreground = Palette.FG_3
            iconTextGap = 6
        }
    }
}

/**
 * A row with a small part on the left, for example a step number or a bullet icon, and a wrapped text on the right.
 * [leadWidth] is the width of the left column.
 */
internal class LeadRow(
    private val lead: JComponent,
    private val text: WrapText,
    private val leadWidth: Int,
    private val gap: Int,
    private val pad: Insets = Insets(0, 0, 0, 0),
) : JPanel(null), HeightForWidth {
    init {
        isOpaque = false
        add(lead)
        add(text)
    }

    private fun textWidth(width: Int) = width - pad.left - pad.right - leadWidth - gap

    override fun heightForWidth(width: Int) =
        pad.top + max(lead.preferredSize.height, text.heightForWidth(textWidth(width))) + pad.bottom

    override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 300))

    override fun doLayout() {
        val size = lead.preferredSize
        lead.setBounds(pad.left, pad.top, min(size.width, leadWidth), size.height)
        val w = textWidth(width)
        text.setBounds(pad.left + leadWidth + gap, pad.top, w, text.heightForWidth(w))
    }
}

/** A card of a tool page: the `.sec` element of the More window and the shortcut table of the Help window. */
internal open class ToolCard(pad: Insets = Insets(14, 16, 14, 16), gap: Int = 10) : Stack(pad = pad, gap = gap) {
    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 8, Palette.CARD, Palette.LINE)
        } finally {
            g2.dispose()
        }
    }
}
