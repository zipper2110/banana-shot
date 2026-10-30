package org.litvin.ui.commons

import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.math.ceil

/**
 * Large tabs in a strip across the top of a tab page, for the sub-tabs of a page. Each tab is a heading: the open tab
 * has the strong text color and a lime line under it, the other tabs are dim. The strip has a line at the bottom.
 * Each tab is a button with the name "<componentName>-<key>", so the tests can click it.
 */
internal class HeaderTabs<T>(
    componentName: String,
    options: List<Option<T>>,
) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {
    data class Option<T>(val value: T, val label: String, val key: String)

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
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.LINE),
            BorderFactory.createEmptyBorder(0, PAD_X, 0, PAD_X),
        )
        tabs.forEach { add(it) }
    }

    /** Calls [listener] when the user opens a tab. */
    fun onChange(listener: (T) -> Unit) {
        listeners += listener
    }

    private inner class Tab(val option: Option<T>, componentName: String) : JButton(option.label) {
        private val labelFont get() = UiKit.font(FONT_SIZE, UiKit.Weight.SEMIBOLD)

        init {
            name = "$componentName-${option.key}"
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

        override fun getPreferredSize() =
            Dimension(ceil(UiKit.textWidth(option.label, labelFont)).toInt() + TAB_PAD_X * 2, TAB_HEIGHT)

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                val open = selected == option.value
                val color = when {
                    open -> Palette.FG
                    model.isRollover -> Palette.FG_2
                    else -> Palette.FG_3
                }
                UiKit.drawText(g2, option.label, labelFont, color, TAB_PAD_X.toFloat(), 0f, (height - LINE).toFloat())
                if (open) {
                    g2.color = Palette.LIME
                    g2.fillRoundRect(TAB_PAD_X, height - LINE - 1, width - TAB_PAD_X * 2, LINE, LINE, LINE)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val TAB_HEIGHT = 46
        const val PAD_X = 8
        const val TAB_PAD_X = 12
        const val LINE = 3
        const val FONT_SIZE = 16f
    }
}
