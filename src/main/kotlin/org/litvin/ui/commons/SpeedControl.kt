package org.litvin.ui.commons

import com.formdev.flatlaf.ui.FlatArrowButton
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComboBox
import javax.swing.JPanel
import kotlin.math.ceil

/**
 * "Speed", the speed list, and the Up and Down key chips. A [compact] control shows the list only.
 * [listName] is the component name of the speed list. Each tab gives its own name, because the names must be unique.
 */
internal class SpeedControl(listName: String, onSpeedIndex: (Int) -> Unit) : JPanel(null) {
    val combo = object : JComboBox<String>(LABELS) {
        override fun updateUI() {
            super.updateUI()
            // A new UI makes a new arrow button, so the offset is set again after each UI change.
            components.filterIsInstance<FlatArrowButton>().forEach { it.xOffset = ARROW_X_OFFSET }
        }
    }.apply {
        name = listName
        toolTipText = "Use Up/Down to change speed"
        font = UiKit.font(12.5f)
        // Up and Down must stay the speed hotkeys of the tab, so the list never keeps the focus.
        isFocusable = false
        Theme.themedStyle(this) {
            "arc: 8; background: ${Palette.hex(Palette.INSET)}; foreground: ${Palette.hex(Palette.FG)}; " +
                "borderColor: ${Palette.hex(Palette.LINE_2)}; buttonBackground: ${Palette.hex(Palette.INSET)}; " +
                "buttonArrowColor: ${Palette.hex(Palette.FG_2)}; buttonStyle: none; padding: 0,6,0,4"
        }
    }
    private val captionFont get() = UiKit.font(12f)
    private var updating = false

    var compact = false
        set(value) {
            if (field == value) return
            field = value
            revalidate()
            repaint()
        }

    init {
        isOpaque = false
        toolTipText = "Use Up/Down to change speed"
        add(combo)
        combo.addActionListener { if (!updating) onSpeedIndex(combo.selectedIndex) }
    }

    /** Shows the speed preset [index] without a callback. */
    fun setSpeedIndex(index: Int) {
        if (combo.selectedIndex == index) return
        updating = true
        try {
            combo.selectedIndex = index
        } finally {
            updating = false
        }
    }

    private fun captionWidth(compact: Boolean = this.compact) =
        if (compact) 0 else ceil(UiKit.textWidth(CAPTION, captionFont)).toInt() + GAP
    private fun keysWidth(compact: Boolean = this.compact) =
        if (compact) 0 else GAP + KEYS.sumOf { KeyChips.width(it) } + KEY_GAP

    /** The preferred width of the full or the [compact] control, for a layout that selects the form that fits. */
    fun preferredWidth(compact: Boolean) = captionWidth(compact) + COMBO_WIDTH + keysWidth(compact)

    override fun getPreferredSize() = Dimension(preferredWidth(compact), COMBO_HEIGHT)

    override fun doLayout() {
        combo.setBounds(captionWidth(), 0, COMBO_WIDTH, COMBO_HEIGHT)
    }

    override fun paintComponent(g: Graphics) {
        if (compact) return
        val g2 = UiKit.smooth(g)
        try {
            UiKit.drawText(g2, CAPTION, captionFont, Palette.FG_2, 0f, 0f, height.toFloat())
            var x = captionWidth() + COMBO_WIDTH + GAP
            for (key in KEYS) {
                KeyChips.paint(g2, key, x, (height - KeyChips.HEIGHT) / 2)
                x += KeyChips.width(key) + KEY_GAP
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        /** The labels of SessionSettings.speedPresets, from high to low. */
        val LABELS = arrayOf("2×", "1.5×", "1.25×", "1×", "0.5×")
        const val CAPTION = "Speed"
        val KEYS = listOf("↑", "↓")
        const val GAP = 6
        const val KEY_GAP = 3
        const val COMBO_WIDTH = 64
        const val COMBO_HEIGHT = 32

        /** Moves the list arrow 2 px left, away from the right border. */
        const val ARROW_X_OFFSET = -2f
    }
}
