package org.litvin.ui.tabs.crop

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.DefaultFillSliderUI
import org.litvin.ui.commons.GroupCard
import org.litvin.ui.commons.SliderRows
import org.litvin.ui.commons.UiKit
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.Locale
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** The two groups of the Transform panel. */
internal enum class TransformGroup(val title: String, val ikon: Ikon) {
    CROP("Crop", Material2OutlinedAL.CROP),
    ROTATION("Rotation", Material2OutlinedMZ.ROTATE_90_DEGREES_CCW),
}

/**
 * The five transform controls in the order of the panel. The slider ranges and the tooltips are the same as
 * in the earlier tab. [low], [mid] and [high] are the small labels under the slider. [minorTicks] are
 * slider values with a small tick (the ±90° snap angles of Rotation).
 */
internal enum class TransformControl(
    val label: String,
    val componentName: String,
    val tooltip: String,
    val min: Int,
    val max: Int,
    val default: Int,
    val unit: String,
    val low: String,
    val mid: String?,
    val high: String,
    val minorTicks: List<Int>,
    val group: TransformGroup,
) {
    ZOOM("Zoom", "crop-zoom", "Zoom", 100, 250, 100, "%", "100%", null, "250%", emptyList(), TransformGroup.CROP),
    PAN_X("Pan X", "crop-pan-x", "Pan X", -100, 100, 0, "", "Left", "Center", "Right", emptyList(), TransformGroup.CROP),
    PAN_Y("Pan Y", "crop-pan-y", "Pan Y", -100, 100, 0, "", "Down", "Center", "Up", emptyList(), TransformGroup.CROP),
    ROTATION(
        "Rotation", "crop-rotation", "Rotation", -360, 360, 0, "°", "−180°", "0°", "+180°",
        listOf(-180, 180), TransformGroup.ROTATION,
    ),
    FINE_ROTATION(
        "Fine rotation", "crop-rotation-fine", "Fine Rotation", -50, 50, 0, "°", "−5°", "0°", "+5°",
        emptyList(), TransformGroup.ROTATION,
    ),
    ;

    /** The text of the value field for a slider value, for example "140", "+24", "-24", "+2.5" or "0.0". */
    fun toText(value: Int): String = when (this) {
        ZOOM -> value.toString()
        PAN_X, PAN_Y -> if (value > 0) "+$value" else value.toString()
        ROTATION -> signedDecimal(value / 2.0f)
        FINE_ROTATION -> signedDecimal(value / 10.0f)
    }

    /**
     * The slider value for a typed text, or null when the text is not a number. The text can have a sign
     * ("+", "-" or "−") and the unit at the end. The result is not clamped to the slider range.
     */
    fun fromText(text: String): Int? {
        val number = text.trim().replace('−', '-').removeSuffix(unit).trim().toFloatOrNull() ?: return null
        if (!number.isFinite()) return null
        return when (this) {
            ZOOM, PAN_X, PAN_Y -> number.toInt()
            ROTATION -> (number * 2.0f).roundToInt()
            FINE_ROTATION -> (number * 10.0f).roundToInt()
        }
    }

    private fun signedDecimal(value: Float): String {
        val text = String.format(Locale.US, "%.1f", value)
        return if (value > 0 && text != "0.0") "+$text" else if (text == "-0.0") "0.0" else text
    }
}

/**
 * The editable value field of a row, with the unit inside the box ("140 %", "+2.5 °").
 * The component is 2 px larger than the box on each side, so the focus glow has space.
 */
internal class TransformValueField(val control: TransformControl) : JPanel(null) {
    val textField = JTextField().apply {
        name = "${control.componentName}-value"
        toolTipText = control.tooltip
        horizontalAlignment = SwingConstants.RIGHT
        font = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
        foreground = UiKit.FG_2
        caretColor = UiKit.FG
        isOpaque = false
        border = BorderFactory.createEmptyBorder()
        putClientProperty("JComponent.outline", null)
        putClientProperty("JTextField.showClearButton", false)
        getAccessibleContext().accessibleName = "${control.label} value"
        // Space types into the field. It must not also play or pause the video.
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "cropValueFieldSpace")
        actionMap.put("cropValueFieldSpace", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = Unit
        })
    }

    private val unitFont = UiKit.font(12f)

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
        toolTipText = control.tooltip
        add(textField)
        textField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = repaint()
            override fun focusLost(e: FocusEvent) = repaint()
        })
    }

    override fun getPreferredSize() = Dimension(BOX_WIDTH + GLOW * 2, BOX_HEIGHT + GLOW * 2)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = preferredSize

    override fun doLayout() {
        val unitWidth = unitWidth()
        val x = GLOW + PAD_X
        textField.setBounds(x, GLOW + 1, BOX_WIDTH - PAD_X * 2 - UNIT_GAP - unitWidth, BOX_HEIGHT - 2)
    }

    private fun unitWidth(): Int = max(UNIT_MIN_WIDTH, kotlin.math.ceil(UiKit.textWidth(control.unit, unitFont)).toInt())

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            val focused = textField.hasFocus()
            if (focused) UiKit.paintBox(g2, 0, 0, width, height, 6, LIME_GLOW, null)
            UiKit.paintBox(
                g2, GLOW, GLOW, BOX_WIDTH, BOX_HEIGHT, 4, UiKit.RAISED,
                if (focused) UiKit.LIME_LINE else UiKit.LINE_2,
            )
            if (control.unit.isNotEmpty()) {
                val unitX = (GLOW + BOX_WIDTH - PAD_X - unitWidth()).toFloat()
                UiKit.drawText(g2, control.unit, unitFont, UiKit.FG_3, unitX, GLOW.toFloat(), BOX_HEIGHT.toFloat())
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val GLOW = 2
        const val BOX_WIDTH = 78
        const val BOX_HEIGHT = 26
        private const val PAD_X = 7
        private const val UNIT_GAP = 2
        private const val UNIT_MIN_WIDTH = 10
        private val LIME_GLOW = Color(161, 254, 0, 31)
    }
}

/** Creates the slider of a transform control. It fills from the default value. */
internal fun transformSlider(control: TransformControl): JSlider =
    DefaultFillSliderUI.slider(control.min, control.max, control.default, control.minorTicks).apply {
        name = control.componentName
        toolTipText = control.tooltip
        getAccessibleContext().accessibleName = control.label
    }

/**
 * One row of a group card: the label and the value field, the slider, and the end labels.
 * A lime bar at the left edge marks a changed value. Each row after the first has a line at the top.
 * The row paints its texts, so that the measurement and the painting use the same metrics.
 */
internal class TransformRow(val control: TransformControl, private val first: Boolean) : JPanel(null) {
    val slider: JSlider = transformSlider(control)
    val valueField = TransformValueField(control)

    private val nameFont = UiKit.font(13f)

    val isChanged: Boolean get() = slider.value != control.default

    init {
        name = "${control.componentName}-row"
        isOpaque = false
        add(valueField)
        add(slider)
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
        val g = TransformValueField.GLOW
        valueField.setBounds(width - SliderRows.PAD_X - field.width + g, PAD_TOP - g, field.width, field.height)
        slider.setBounds(SliderRows.PAD_X, SLIDER_Y, width - SliderRows.PAD_X * 2, SLIDER_HEIGHT)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            SliderRows.paintFrame(g2, width, height, first, isChanged)
            val nameRight = (valueField.x + TransformValueField.GLOW - NAME_GAP).toFloat()
            val name = UiKit.ellipsize(control.label, nameFont, nameRight - SliderRows.PAD_X)
            UiKit.drawText(
                g2, name, nameFont, UiKit.FG, SliderRows.PAD_X.toFloat(), PAD_TOP.toFloat(), TransformValueField.BOX_HEIGHT.toFloat(),
            )
            val midX = slider.x + DefaultFillSliderUI.xOf(slider, control.default)
            SliderRows.paintEnds(g2, width, (SLIDER_Y + SLIDER_HEIGHT - 1).toFloat(), control.low, control.high, control.mid, midX)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val ROW_HEIGHT = 80
        private const val MIN_WIDTH = 200
        private const val PAD_TOP = 8
        private const val NAME_GAP = 8
        private const val SLIDER_Y = PAD_TOP + TransformValueField.BOX_HEIGHT + 3
        private const val SLIDER_HEIGHT = 22
    }
}

/**
 * A card with a caption ("CROP" or "ROTATION") and the rows of its controls.
 * The Rotation card shows the total angle on the right of the caption, for example "Angle +2.3°".
 */
internal class TransformGroupCard(val group: TransformGroup, val rows: List<TransformRow>) :
    GroupCard("crop-group-${group.name.lowercase()}", group.title, group.ikon, rows, TransformRow.ROW_HEIGHT) {
    private val angleLabelFont = UiKit.font(12f)
    private val angleValueFont = UiKit.font(13f, UiKit.Weight.SEMIBOLD)

    /** The total angle in degrees. Only the Rotation card shows it. */
    var angle: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    /** The angle text of the caption, for example "+2.3°". */
    val angleText: String get() = signedDegrees(angle)

    override fun paintCaptionEnd(g2: Graphics2D, right: Float, top: Float, height: Float) {
        if (group != TransformGroup.ROTATION) return
        val value = angleText
        val valueX = right - UiKit.textWidth(value, angleValueFont)
        val valueColor = if (value != ZERO_ANGLE) UiKit.LIME else UiKit.FG_3
        UiKit.drawText(g2, value, angleValueFont, valueColor, valueX, top, height)
        val labelX = valueX - ANGLE_GAP - UiKit.textWidth(ANGLE_LABEL, angleLabelFont)
        UiKit.drawText(g2, ANGLE_LABEL, angleLabelFont, UiKit.FG_3, labelX, top, height)
    }

    private companion object {
        const val ANGLE_LABEL = "Angle"
        const val ANGLE_GAP = 4
        const val ZERO_ANGLE = "0.0°"
    }
}

/** An angle with its sign and one decimal: "+2.3°", "−2.3°" (with a minus sign, not a hyphen) or "0.0°". */
internal fun signedDegrees(value: Float): String {
    val text = String.format(Locale.US, "%.1f", abs(value))
    return when {
        text == "0.0" -> "0.0°"
        value > 0 -> "+$text°"
        else -> "−$text°"
    }
}
