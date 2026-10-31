package org.litvin.ui.commons

import org.kordamp.ikonli.Ikon
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.max

/**
 * Large tabs in a strip across the top of a tab page, for the sub-tabs of a page. The tabs are in one rounded box.
 * Each tab has an icon tile, a title and a short subtitle. The open tab has a raised background and a lime icon
 * tile. The strip has a line at the bottom.
 * Each tab is a button with the name "<componentName>-<key>", so the tests can click it.
 */
internal class HeaderTabs<T>(
    componentName: String,
    options: List<Option<T>>,
) : JPanel(null) {
    data class Option<T>(val value: T, val label: String, val key: String, val sub: String, val ikon: Ikon)

    private val listeners = mutableListOf<(T) -> Unit>()
    private val tabs = options.map { Tab(it, componentName) }

    /** The open tab. A change from the code does not call the listeners. */
    var selected: T? = null
        set(value) {
            field = value
            repaint()
        }

    init {
        name = componentName
        isOpaque = true
        background = Palette.BG
        tabs.forEach { add(it) }
    }

    /** Calls [listener] when the user opens a tab. */
    fun onChange(listener: (T) -> Unit) {
        listeners += listener
    }

    private fun boxWidth(): Int = BOX_PAD * 2 + tabs.sumOf { it.preferredSize.width } + TAB_GAP * (tabs.size - 1).coerceAtLeast(0)

    override fun getPreferredSize() = Dimension(STRIP_PAD_X * 2 + boxWidth(), STRIP_PAD_Y * 2 + TAB_HEIGHT + BOX_PAD * 2 + 1)
    override fun getMinimumSize() = Dimension(0, preferredSize.height)

    override fun doLayout() {
        var x = STRIP_PAD_X + BOX_PAD
        tabs.forEach { tab ->
            val w = tab.preferredSize.width
            tab.setBounds(x, STRIP_PAD_Y + BOX_PAD, w, TAB_HEIGHT)
            x += w + TAB_GAP
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            g2.color = Palette.BG
            g2.fillRect(0, 0, width, height)
            UiKit.paintBox(g2, STRIP_PAD_X, STRIP_PAD_Y, boxWidth(), TAB_HEIGHT + BOX_PAD * 2, BOX_RADIUS, Palette.PANEL, Palette.LINE)
            g2.color = Palette.LINE
            g2.fillRect(0, height - 1, width, 1)
        } finally {
            g2.dispose()
        }
    }

    private inner class Tab(val option: Option<T>, componentName: String) : JButton(option.label) {
        private val titleFont get() = UiKit.font(15f, UiKit.Weight.SEMIBOLD)
        private val subFont get() = UiKit.font(12f)

        init {
            name = "$componentName-${option.key}"
            toolTipText = option.sub
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            // Space must stay the play hotkey, so a tab never keeps the focus.
            isFocusable = false
            isOpaque = false
            isRolloverEnabled = true
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                if (selected == option.value) return@addActionListener
                selected = option.value
                listeners.toList().forEach { it(option.value) }
            }
        }

        override fun getPreferredSize(): Dimension {
            val text = max(UiKit.textWidth(option.label, titleFont), UiKit.textWidth(option.sub, subFont))
            return Dimension(TILE_X + TILE + TEXT_GAP + ceil(text).toInt() + TEXT_PAD_RIGHT, TAB_HEIGHT)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val open = selected == option.value
                val hover = model.isRollover
                when {
                    open -> UiKit.paintBox(g2, 0, 0, width, height, TAB_RADIUS, Palette.RAISED, Palette.LINE_2)
                    hover -> UiKit.paintBox(g2, 0, 0, width, height, TAB_RADIUS, Palette.ROW_HOVER, null)
                }
                val tileY = (height - TILE) / 2
                UiKit.paintBox(g2, TILE_X, tileY, TILE, TILE, TILE_RADIUS, if (open) Palette.LIME_FILL else Palette.RAISED_2, null)
                val icon = UiKit.icon(option.ikon, ICON, if (open) Palette.ON_LIME else Palette.FG_2)
                icon.paintIcon(this, g2, TILE_X + (TILE - icon.iconWidth) / 2, tileY + (TILE - icon.iconHeight) / 2)
                val textX = (TILE_X + TILE + TEXT_GAP).toFloat()
                val titleColor = if (open || hover) Palette.FG_STRONG else Palette.FG
                UiKit.drawText(g2, option.label, titleFont, titleColor, textX, height / 2f - TITLE_LINE, TITLE_LINE)
                UiKit.drawText(g2, option.sub, subFont, Palette.FG_3, textX, height / 2f, SUB_LINE)
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val STRIP_PAD_X = 8
        const val STRIP_PAD_Y = 6
        const val BOX_PAD = 4
        const val BOX_RADIUS = 12
        const val TAB_GAP = 4
        const val TAB_HEIGHT = 52
        const val TAB_RADIUS = 9
        const val TILE_X = 12
        const val TILE = 38
        const val TILE_RADIUS = 8
        const val ICON = 20
        const val TEXT_GAP = 12
        const val TEXT_PAD_RIGHT = 20
        const val TITLE_LINE = 20f
        const val SUB_LINE = 17f
    }
}
