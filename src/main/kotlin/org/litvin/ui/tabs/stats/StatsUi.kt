package org.litvin.ui.tabs.stats

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.scoring.PerPlayer
import org.litvin.ui.commons.HeightForWidth
import org.litvin.ui.commons.UiKit
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractAction
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.Timer
import kotlin.math.ceil
import kotlin.math.max

/*
 * Colors and small parts of the Stats tab. The values come from design/stats-redesign/final.html and its shared.css.
 */

internal object StatsColors {
    /** The "In video" column: a light lime background, and a stronger background for a selected row. */
    val LANE = Color(161, 254, 0, 9)
    val LANE_SELECTED = Color(161, 254, 0, 20)
    val LANE_LINE = Color(161, 254, 0, 36)

    /** The line under each statistics row, and the background of the row under the mouse. */
    val ROW_LINE = Color(0x1B1B1B)
    val ROW_HOVER = Color(0x151515)

    /** The main value of the player that leads the row. The other value is [UiKit.FG_2]. */
    val LEAD = Color.WHITE

    /** The "—" of a row without a value. */
    val OFF_VALUE = Color(0x444444)

    /** The count of selected rows in a group title, when the group has selected rows. */
    val GREEN = Color(0xAFF625)

    /** The checkbox border. */
    val CHECK_LINE = Color(0x5A5A5A)
    val CHECK_LINE_HOVER = Color(0x8A8A8A)

    /** A value that opens a point: the background under the mouse. */
    val LINK_HOVER = Color(161, 254, 0, 26)

    /** The coverage note. */
    val NOTE_BG = Color(255, 213, 74, 20)
    val NOTE_LINE = Color(255, 213, 74, 77)
    val NOTE_TEXT = Color(0xF3E2A4)
    val NOTE_ICON = Color(0xFFD54A)

    /** The text of a read error. */
    val ERROR_TEXT = Color(0xFFC2B3)

    /** The line of the momentum chart and its middle line. */
    val CHART_LINE = Color(0xD8D8D8)
    val CHART_AXIS = Color(0x3A3A3A)
}

/** Paints [paint] with the opacity of a disabled control when [enabled] is false. */
internal inline fun paintFaded(g: Graphics, enabled: Boolean, paint: (Graphics) -> Unit) {
    if (enabled) {
        paint(g)
        return
    }
    val g2 = g.create() as Graphics2D
    try {
        g2.composite = AlphaComposite.SrcOver.derive(0.35f)
        paint(g2)
    } finally {
        g2.dispose()
    }
}

/** Makes a button paint only what its own paintComponent paints, with a hand cursor and a hover state. */
internal fun AbstractButton.plain() {
    layout = null
    isContentAreaFilled = false
    isBorderPainted = false
    isFocusPainted = false
    isOpaque = false
    isRolloverEnabled = true
    // The tab has no keyboard actions, so the buttons do not take the focus from the other tabs.
    isFocusable = false
    border = BorderFactory.createEmptyBorder()
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addPropertyChangeListener("enabled") {
        cursor = Cursor.getPredefinedCursor(if (isEnabled) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
        repaint()
    }
}

/** The height of this component at [width]. */
internal fun Component.heightAt(width: Int): Int =
    if (this is HeightForWidth) heightForWidth(width) else preferredSize.height

/**
 * Text that wraps at the spaces to the width of the component.
 * The preferred width is the width of the text on one line.
 */
internal class WrapText(
    text: String,
    font: Font,
    color: Color,
    private val align: Int = SwingConstants.LEFT,
    private val lineSpacing: Float = 1.45f,
) : JPanel(null), HeightForWidth {
    var text: String = text
        set(value) {
            if (field == value) return
            field = value
            getAccessibleContext().accessibleName = value
            revalidate()
            repaint()
        }

    var color: Color = color
        set(value) {
            field = value
            repaint()
        }

    init {
        this.font = font
        isOpaque = false
        getAccessibleContext().accessibleName = text
    }

    private val lineHeight get() = font.size2D * lineSpacing

    fun lines(width: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && UiKit.textWidth(candidate, font) > width) {
                    lines += line
                    line = word
                } else {
                    line = candidate
                }
            }
            lines += line
        }
        return lines
    }

    fun naturalWidth(): Int = text.split('\n').maxOf { ceil(UiKit.textWidth(it, font)).toInt() } + 1

    override fun heightForWidth(width: Int): Int = ceil(lines(width).size * lineHeight).toInt()

    override fun getPreferredSize() = Dimension(naturalWidth(), heightForWidth(if (width > 0) width else naturalWidth()))
    override fun getMinimumSize() = Dimension(0, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            var top = 0f
            for (line in lines(width)) {
                val lineWidth = UiKit.textWidth(line, font)
                val x = when (align) {
                    SwingConstants.CENTER -> (width - lineWidth) / 2f
                    SwingConstants.RIGHT -> width - lineWidth
                    else -> 0f
                }
                UiKit.drawText(g2, line, font, color, x, top, lineHeight)
                top += lineHeight
            }
        } finally {
            g2.dispose()
        }
    }
}

/** The lime checkbox of the design, without a text. */
internal class InVideoCheck(componentName: String) : JCheckBox() {
    init {
        name = componentName
        isOpaque = false
        isFocusable = false
        isContentAreaFilled = false
        isBorderPainted = false
        isRolloverEnabled = true
        border = BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        icon = CheckIcon(checked = false, hover = false)
        rolloverIcon = CheckIcon(checked = false, hover = true)
        selectedIcon = CheckIcon(checked = true, hover = false)
        rolloverSelectedIcon = selectedIcon
        getAccessibleContext().accessibleName = "In video"
    }

    override fun getPreferredSize() = Dimension(SIZE + PAD * 2, SIZE + PAD * 2)

    private class CheckIcon(private val checked: Boolean, private val hover: Boolean) : Icon {
        override fun getIconWidth() = SIZE
        override fun getIconHeight() = SIZE

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = UiKit.smooth(g)
            try {
                if (checked) {
                    g2.color = UiKit.LIME
                    g2.fill(RoundRectangle2D.Double(x.toDouble(), y.toDouble(), SIZE.toDouble(), SIZE.toDouble(), 6.0, 6.0))
                    g2.color = UiKit.ON_LIME
                    g2.stroke = BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
                    g2.draw(Path2D.Double().apply {
                        moveTo(x + 4.4, y + 8.0)
                        lineTo(x + 7.0, y + 10.6)
                        lineTo(x + 11.8, y + 5.4)
                    })
                } else {
                    g2.color = if (hover) StatsColors.CHECK_LINE_HOVER else StatsColors.CHECK_LINE
                    g2.stroke = BasicStroke(1.5f)
                    g2.draw(RoundRectangle2D.Double(x + 0.75, y + 0.75, SIZE - 1.5, SIZE - 1.5, 5.0, 5.0))
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val SIZE = 16
        const val PAD = 4
    }
}

/**
 * A button of the tab. The normal style is the small gray button of the design (btn-sm).
 * The lime style is the main action of the empty view (btn-lime).
 */
internal class StatsButton(
    text: String,
    private val ikon: Ikon?,
    private val iconAfter: Boolean = false,
    private val lime: Boolean = false,
) : JButton(text) {
    private val buttonHeight get() = if (lime) 30 else 26
    private val padX get() = if (lime) 12 else 10

    init {
        plain()
        font = if (lime) UiKit.font(12.5f, UiKit.Weight.BOLD) else UiKit.font(12f)
    }

    private fun icon(color: Color): Icon? = ikon?.let { UiKit.icon(it, ICON, color) }

    override fun getPreferredSize(): Dimension {
        val iconWidth = if (ikon != null) ICON + GAP else 0
        return Dimension(padX * 2 + iconWidth + ceil(UiKit.textWidth(text.orEmpty(), font)).toInt() + 1, buttonHeight)
    }

    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun paint(g: Graphics) = paintFaded(g, isEnabled) { super.paint(it) }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = isEnabled && model.isRollover
            val textColor: Color
            if (lime) {
                UiKit.paintBox(g2, 0, 0, width, height, 4, if (hover) UiKit.LIME_HOVER else UiKit.LIME, null)
                textColor = UiKit.ON_LIME
            } else {
                UiKit.paintBox(g2, 0, 0, width, height, 4, if (hover) UiKit.RAISED_2 else UiKit.RAISED, if (hover) UiKit.HOVER_LINE else UiKit.LINE_2)
                textColor = UiKit.FG
            }
            val textWidth = UiKit.textWidth(text.orEmpty(), font)
            val icon = icon(textColor)
            val contentWidth = textWidth + if (icon != null) ICON + GAP else 0
            var x = (width - contentWidth) / 2f
            if (icon != null && !iconAfter) {
                icon.paintIcon(this, g2, x.toInt(), (height - icon.iconHeight) / 2)
                x += ICON + GAP
            }
            UiKit.drawText(g2, text.orEmpty(), font, textColor, x, 0f, height.toFloat())
            if (icon != null && iconAfter) {
                icon.paintIcon(this, g2, (x + textWidth + GAP).toInt(), (height - icon.iconHeight) / 2)
            }
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val ICON = 18
        const val GAP = 6
    }
}

/** One button of the [ScopeControl]: "Match" or "Set 2", with the score in a small line under it. */
internal class ScopeButton(componentName: String, private val title: String, private val small: String) : JToggleButton() {
    init {
        name = componentName
        plain()
        getAccessibleContext().accessibleName = "$title, $small"
        model.addChangeListener { repaint() }
    }

    override fun getPreferredSize() = Dimension(
        max(UiKit.textWidth(title, TITLE_FONT), UiKit.textWidth(small, SMALL_FONT)).toInt() + PAD_X * 2 + 2,
        HEIGHT,
    )

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val hover = model.isRollover
            when {
                isSelected -> UiKit.paintBox(g2, 0, 0, width, height, 4, UiKit.RAISED_2, UiKit.LIME_LINE)
                hover -> UiKit.paintBox(g2, 0, 0, width, height, 4, HOVER_BG, null)
            }
            val titleColor = if (isSelected || hover) UiKit.FG else UiKit.FG_2
            val smallColor = if (isSelected) UiKit.FG_2 else UiKit.FG_3
            val top = (height - TITLE_LINE - SMALL_LINE) / 2f
            val titleText = UiKit.ellipsize(title, TITLE_FONT, (width - PAD_X * 2).toFloat())
            val smallText = UiKit.ellipsize(small, SMALL_FONT, (width - PAD_X * 2).toFloat())
            UiKit.drawText(g2, titleText, TITLE_FONT, titleColor, (width - UiKit.textWidth(titleText, TITLE_FONT)) / 2f, top, TITLE_LINE)
            UiKit.drawText(
                g2, smallText, SMALL_FONT, smallColor,
                (width - UiKit.textWidth(smallText, SMALL_FONT)) / 2f, top + TITLE_LINE, SMALL_LINE,
            )
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 14
        const val HEIGHT = 42
        const val TITLE_LINE = 17f
        const val SMALL_LINE = 15f
        val TITLE_FONT = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
        val SMALL_FONT = UiKit.font(11f)
        val HOVER_BG = Color(0x1C1C1C)
    }
}

/** The Match / Set control: buttons of equal width in a dark rounded frame. Only one button can be selected. */
internal class ScopeControl(private val onSelect: (Int) -> Unit) : JPanel(null) {
    private var group = ButtonGroup()
    private var scores: List<String> = emptyList()
    val buttons: List<ScopeButton> get() = components.filterIsInstance<ScopeButton>()

    init {
        isOpaque = false
    }

    /** Shows "Match" and one button for each set, with [scope] selected. The buttons are made again only for new scores. */
    fun show(setScores: List<String>, scope: Int) {
        if (setScores != scores) {
            scores = setScores
            removeAll()
            group = ButtonGroup()
            val entries = listOf("Match" to "All sets") + setScores.mapIndexed { index, score -> "Set ${index + 1}" to score }
            entries.forEachIndexed { index, (title, small) ->
                val button = ScopeButton("stats-scope-$index", title, small)
                button.addActionListener { onSelect(index) }
                group.add(button)
                add(button)
            }
            revalidate()
            repaint()
        }
        buttons.getOrNull(scope)?.isSelected = true
    }

    override fun getPreferredSize() = Dimension(
        buttons.sumOf { it.preferredSize.width } + GAP * (buttons.size - 1).coerceAtLeast(0) + INSET * 2,
        (buttons.maxOfOrNull { it.preferredSize.height } ?: 0) + INSET * 2,
    )

    override fun doLayout() {
        val count = buttons.size.coerceAtLeast(1)
        val w = ((width - INSET * 2 - GAP * (count - 1)) / count).coerceAtLeast(0)
        buttons.forEachIndexed { index, button ->
            val x = INSET + index * (w + GAP)
            val right = if (index == buttons.lastIndex) width - INSET else x + w
            button.setBounds(x, INSET, right - x, height - INSET * 2)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 6, UiKit.BG, UiKit.LINE_2)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        // The 1 px border and the 2 px padding.
        const val INSET = 3
        const val GAP = 2
    }
}

/**
 * A number field with a minus button on the left and a plus button on the right.
 * The user can type a value, and Up and Down change it by 1. A held button repeats its step.
 * A change from the user calls [onChange]. [show] sets the value without a call.
 */
internal class LimitStepper(
    componentName: String,
    private val min: Int,
    private val max: Int,
    initial: Int,
    private val onChange: (Int) -> Unit,
) : JPanel(null) {
    private val input = JTextField(initial.toString()).apply {
        name = "$componentName-field"
        horizontalAlignment = SwingConstants.CENTER
        font = UiKit.font(12.5f, UiKit.Weight.SEMIBOLD)
        foreground = UiKit.FG
        caretColor = UiKit.FG
        isOpaque = false
        border = BorderFactory.createEmptyBorder()
        addActionListener { commit() }
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = commit()
        })
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "stepUp")
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "stepDown")
        actionMap.put("stepUp", action { step(1) })
        actionMap.put("stepDown", action { step(-1) })
    }
    private val minus = StepButton(Material2MZ.REMOVE, "Decrease") { step(-1) }
    private val plus = StepButton(Material2AL.ADD, "Increase") { step(1) }
    private var current = initial

    /** The value. A new value from the code counts as a change from the user. */
    var value: Int
        get() = current
        set(newValue) {
            val clamped = newValue.coerceIn(min, max)
            input.text = clamped.toString()
            if (clamped == current) return
            current = clamped
            onChange(clamped)
        }

    init {
        name = componentName
        isOpaque = false
        add(minus)
        add(input)
        add(plus)
    }

    /** Shows [newValue] without a call to [onChange]. */
    fun show(newValue: Int) {
        current = newValue
        if (input.text != newValue.toString()) input.text = newValue.toString()
    }

    private fun step(delta: Int) {
        commit()
        value = current + delta
    }

    private fun commit() {
        val typed = input.text.trim().toIntOrNull()
        if (typed == null) {
            input.text = current.toString()
            return
        }
        value = typed
    }

    private fun action(block: () -> Unit) = object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) = block()
    }

    override fun getPreferredSize() = Dimension(BUTTON * 2 + FIELD + 2, HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun doLayout() {
        minus.setBounds(1, 1, BUTTON, height - 2)
        input.setBounds(1 + BUTTON, 1, width - 2 - BUTTON * 2, height - 2)
        plus.setBounds(width - 1 - BUTTON, 1, BUTTON, height - 2)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 4, UiKit.BG, null)
        } finally {
            g2.dispose()
        }
    }

    override fun paintChildren(g: Graphics) {
        // The buttons fill the corners, so the frame clips them to its rounded shape.
        val g2 = UiKit.smooth(g)
        try {
            g2.clip(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 8.0, 8.0))
            super.paintChildren(g2)
        } finally {
            g2.dispose()
        }
        val border = UiKit.smooth(g)
        try {
            UiKit.paintBox(border, 0, 0, width, height, 4, null, UiKit.LINE_2)
        } finally {
            border.dispose()
        }
    }

    /** A minus or plus button. A held button repeats its step. */
    private class StepButton(ikon: Ikon, label: String, private val onStep: () -> Unit) : JButton() {
        private val iconIdle = UiKit.icon(ikon, 14, UiKit.FG_2)
        private val iconHover = UiKit.icon(ikon, 14, UiKit.FG)
        private val repeat = Timer(REPEAT_MS) { onStep() }.apply { initialDelay = HOLD_MS }

        init {
            plain()
            getAccessibleContext().accessibleName = label
            toolTipText = null
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1 || !isEnabled) return
                    onStep()
                    repeat.restart()
                }

                override fun mouseReleased(e: MouseEvent) = repeat.stop()
                override fun mouseExited(e: MouseEvent) = repeat.stop()
            })
        }

        override fun paintComponent(g: Graphics) {
            val hover = model.isRollover
            g.color = if (hover) UiKit.RAISED_2 else UiKit.RAISED
            g.fillRect(0, 0, width, height)
            val icon = if (hover) iconHover else iconIdle
            icon.paintIcon(this, g, (width - icon.iconWidth) / 2, (height - icon.iconHeight) / 2)
        }

        private companion object {
            const val HOLD_MS = 500
            const val REPEAT_MS = 100
        }
    }

    private companion object {
        const val HEIGHT = 24
        const val BUTTON = 20
        const val FIELD = 34
    }
}

/**
 * Two bars that compare the players: player 1 from the left, player 2 from the right, with a gap between them.
 * A row with 0 for both players shows two dim half bars.
 */
internal class CompareBar : JComponent() {
    var values: PerPlayer<Double>? = null
        set(value) {
            field = value
            repaint()
        }
    var colors: PerPlayer<Color> = PerPlayer(UiKit.FG, UiKit.FG_2)
        set(value) {
            field = value
            repaint()
        }

    init {
        isOpaque = false
    }

    override fun getPreferredSize() = Dimension(100, HEIGHT)

    override fun paintComponent(g: Graphics) {
        val bar = values ?: return
        val g2 = UiKit.smooth(g)
        try {
            val total = bar.p1 + bar.p2
            val part = if (total > 0) bar.p1 / total else 0.5
            if (total <= 0) g2.composite = AlphaComposite.SrcOver.derive(0.3f)
            val available = (width - GAP).coerceAtLeast(0).toDouble()
            val w1 = available * part
            val w2 = available - w1
            val h = height.toDouble()
            if (w1 > 0.5) {
                g2.color = colors.p1
                g2.fill(RoundRectangle2D.Double(0.0, 0.0, w1, h, h, h))
            }
            if (w2 > 0.5) {
                g2.color = colors.p2
                g2.fill(RoundRectangle2D.Double(width - w2, 0.0, w2, h, h, h))
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val HEIGHT = 6
        private const val GAP = 4
    }
}
