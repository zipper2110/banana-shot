package org.litvin.ui.commons

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.ceil
import kotlin.math.max

/**
 * The format of the value of a slider row.
 * [toText] gives the field text for a slider value. [fromText] gives the slider value for a typed text,
 * or null when the text is not a number. [step] is the change of Up and Down, in slider units.
 */
internal class SliderValueFormat(
    val unit: String,
    val step: Int,
    val toText: (Int) -> String,
    val fromText: (String) -> Int?,
) {
    companion object {
        /**
         * The number in a typed text, or null when the text is not a finite number. The text can have a sign
         * ("+", "-" or "−") and [unit] at the end.
         */
        fun parse(text: String, unit: String): Float? {
            val number = text.trim().replace('−', '-').removeSuffix(unit).trim().toFloatOrNull() ?: return null
            return number.takeIf { it.isFinite() }
        }
    }
}

/**
 * The editable value field of a slider row, with the unit inside the box ("140 %", "+2.5 °").
 * The component is 2 px larger than the box on each side, so the focus glow has space.
 *
 * [bind] connects the field to a slider. A valid number in the field moves the slider at once.
 * Up and Down change the value by one step. When the user holds the key, the step repeats
 * after [HOLD_DELAY_MS], 10 times each second.
 */
internal class SliderValueField(
    componentName: String,
    label: String,
    tooltip: String,
    private val unit: String,
) : JPanel(null) {
    val textField = JTextField().apply {
        name = "$componentName-value"
        toolTipText = tooltip
        horizontalAlignment = SwingConstants.RIGHT
        font = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
        foreground = UiKit.FG_2
        caretColor = UiKit.FG
        isOpaque = false
        border = BorderFactory.createEmptyBorder()
        putClientProperty("JComponent.outline", null)
        putClientProperty("JTextField.showClearButton", false)
        getAccessibleContext().accessibleName = "$label value"
        // Space types into the field. It must not also play or pause the video.
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "valueFieldSpace")
        actionMap.put("valueFieldSpace", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = Unit
        })
    }

    private val unitFont = UiKit.font(12f)

    /** The direction of the held step key: +1, -1, or 0 when no step key is held. */
    private var stepDirection = 0
    private var stepAction: (Int) -> Unit = {}
    private val stepRepeat = Timer(REPEAT_MS) { stepAction(stepDirection) }.apply { initialDelay = HOLD_DELAY_MS }

    /** True when the value is not the default. The value text is lime then. */
    var changed = false
        set(value) {
            if (field == value) return
            field = value
            textField.foreground = if (value) UiKit.LIME else UiKit.FG_2
            repaint()
        }

    init {
        isOpaque = false
        toolTipText = tooltip
        add(textField)
        textField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = repaint()
            override fun focusLost(e: FocusEvent) {
                stopStepping()
                repaint()
            }
        })
        bindStepKey("UP", 1)
        bindStepKey("DOWN", -1)
    }

    /**
     * Connects the field to [slider]. The field shows the slider value when it does not have the focus.
     * Enter confirms the value: the focus goes to the slider, and the field shows the value in its format.
     */
    fun bind(slider: JSlider, format: SliderValueFormat) {
        fun syncFromSlider() {
            val text = format.toText(slider.value)
            if (textField.text != text) textField.text = text
        }

        slider.addChangeListener { if (!textField.hasFocus()) syncFromSlider() }
        syncFromSlider()

        textField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = textField.selectAll()
            override fun focusLost(e: FocusEvent) = syncFromSlider()
        })
        textField.addActionListener { slider.requestFocusInWindow() }
        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateFromText()
            override fun removeUpdate(e: DocumentEvent) = updateFromText()
            override fun changedUpdate(e: DocumentEvent) = updateFromText()

            private fun updateFromText() {
                if (!textField.hasFocus()) return
                val value = format.fromText(textField.text) ?: return
                slider.value = value.coerceIn(slider.minimum, slider.maximum)
            }
        })

        stepAction = { direction ->
            slider.value = (slider.value + direction * format.step).coerceIn(slider.minimum, slider.maximum)
            syncFromSlider()
        }
    }

    /** The key press steps once and starts the repeat. The key repeat of the system does not add steps. */
    private fun bindStepKey(key: String, direction: Int) {
        val inputMap = textField.getInputMap(JComponent.WHEN_FOCUSED)
        inputMap.put(KeyStroke.getKeyStroke("pressed $key"), "valueFieldStep$key")
        inputMap.put(KeyStroke.getKeyStroke("released $key"), "valueFieldStepEnd$key")
        textField.actionMap.put("valueFieldStep$key", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (stepDirection == direction && stepRepeat.isRunning) return
                stepDirection = direction
                stepAction(direction)
                stepRepeat.restart()
            }
        })
        textField.actionMap.put("valueFieldStepEnd$key", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (stepDirection == direction) stopStepping()
            }
        })
    }

    private fun stopStepping() {
        stepRepeat.stop()
        stepDirection = 0
    }

    override fun getPreferredSize() = Dimension(BOX_WIDTH + GLOW * 2, BOX_HEIGHT + GLOW * 2)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun doLayout() {
        textField.setBounds(GLOW + PAD_X, GLOW + 1, BOX_WIDTH - PAD_X * 2 - UNIT_GAP - unitWidth(), BOX_HEIGHT - 2)
    }

    private fun unitWidth(): Int = max(UNIT_MIN_WIDTH, ceil(UiKit.textWidth(unit, unitFont)).toInt())

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val focused = textField.hasFocus()
            if (focused) UiKit.paintBox(g2, 0, 0, width, height, 6, LIME_GLOW, null)
            UiKit.paintBox(
                g2, GLOW, GLOW, BOX_WIDTH, BOX_HEIGHT, 4, UiKit.RAISED,
                if (focused) UiKit.LIME_LINE else UiKit.LINE_2,
            )
            if (unit.isNotEmpty()) {
                val unitX = (GLOW + BOX_WIDTH - PAD_X - unitWidth()).toFloat()
                UiKit.drawText(g2, unit, unitFont, UiKit.FG_3, unitX, GLOW.toFloat(), BOX_HEIGHT.toFloat())
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val GLOW = 2
        const val BOX_WIDTH = 78
        const val BOX_HEIGHT = 26

        /** The time that the user holds Up or Down before the step repeats. */
        const val HOLD_DELAY_MS = 600

        /** The time between two repeated steps: 10 steps each second. */
        const val REPEAT_MS = 100

        private const val PAD_X = 7
        private const val UNIT_GAP = 2
        private const val UNIT_MIN_WIDTH = 10
        private val LIME_GLOW = Color(161, 254, 0, 31)
    }
}

/**
 * One row of a group card: the label and the value field, the slider, and the end labels.
 * A lime bar at the left edge marks a changed value. Each row after the first has a line at the top.
 * The row paints its texts, so that the measurement and the painting use the same metrics.
 * The slider fills from [default]. [minorTicks] are slider values with a small tick.
 */
internal open class SliderValueRow(
    componentName: String,
    private val label: String,
    tooltip: String,
    min: Int,
    max: Int,
    default: Int,
    private val minorTicks: List<Int>,
    private val low: String,
    private val mid: String?,
    private val high: String,
    format: SliderValueFormat,
    private val first: Boolean,
) : JPanel(null) {
    val slider: JSlider = DefaultFillSliderUI.slider(min, max, default, minorTicks).apply {
        name = componentName
        toolTipText = tooltip
        getAccessibleContext().accessibleName = label
    }
    val valueField = SliderValueField(componentName, label, tooltip, format.unit)

    private val nameFont = UiKit.font(13f)

    /**
     * The default value. The slider fills from it, and a value that is not the default marks the row as changed.
     * A caller can change it, for example when the default depends on another setting.
     */
    var default: Int = default
        set(value) {
            if (field == value) return
            field = value
            slider.setUI(DefaultFillSliderUI(value, minorTicks))
            valueField.changed = isChanged
            repaint()
        }

    val isChanged: Boolean get() = slider.value != default

    init {
        name = "$componentName-row"
        isOpaque = false
        add(valueField)
        add(slider)
        valueField.bind(slider, format)
        valueField.changed = isChanged
        slider.addChangeListener {
            valueField.changed = isChanged
            repaint()
        }
    }

    override fun getPreferredSize() = Dimension(MIN_WIDTH, ROW_HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, ROW_HEIGHT)

    override fun doLayout() {
        val field = valueField.preferredSize
        val g = SliderValueField.GLOW
        valueField.setBounds(width - SliderRows.PAD_X - field.width + g, PAD_TOP - g, field.width, field.height)
        slider.setBounds(SliderRows.PAD_X, SLIDER_Y, width - SliderRows.PAD_X * 2, SLIDER_HEIGHT)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            SliderRows.paintFrame(g2, width, height, first, isChanged)
            val nameRight = (valueField.x + SliderValueField.GLOW - NAME_GAP).toFloat()
            val name = UiKit.ellipsize(label, nameFont, nameRight - SliderRows.PAD_X)
            UiKit.drawText(
                g2, name, nameFont, UiKit.FG, SliderRows.PAD_X.toFloat(), PAD_TOP.toFloat(), SliderValueField.BOX_HEIGHT.toFloat(),
            )
            val midX = slider.x + DefaultFillSliderUI.xOf(slider, default)
            SliderRows.paintEnds(g2, width, (SLIDER_Y + SLIDER_HEIGHT - 1).toFloat(), low, high, mid, midX)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val ROW_HEIGHT = 80
        private const val MIN_WIDTH = 200
        private const val PAD_TOP = 8
        private const val NAME_GAP = 8
        private const val SLIDER_Y = PAD_TOP + SliderValueField.BOX_HEIGHT + 3
        private const val SLIDER_HEIGHT = 22
    }
}
