package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.DefaultFillSliderUI
import org.litvin.ui.commons.GroupCard
import org.litvin.ui.commons.SliderRows
import org.litvin.ui.commons.UiKit
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JPanel
import javax.swing.JSlider

/** The two groups of the Color grade panel. */
internal enum class ColorGroup(val title: String, val ikon: Ikon) {
    LIGHT("Light", Material2OutlinedMZ.WB_SUNNY),
    COLOR("Color", Material2OutlinedMZ.PALETTE),
}

/**
 * The six color controls in the order of the panel. Each runs from -100 to +100 with 0 as the default.
 * [low] and [high] are the small labels under the ends of the slider.
 */
internal enum class ColorControl(
    val label: String,
    val componentName: String,
    val low: String,
    val high: String,
    val tooltip: String,
    val group: ColorGroup,
) {
    BRIGHTNESS("Brightness", "colors-brightness", "Darker", "Brighter", "Brightness [-100..+100], default 0. Changes all tones by the same amount.", ColorGroup.LIGHT),
    CONTRAST("Contrast", "colors-contrast", "Flatter", "Stronger", "Contrast [-100..+100], default 0", ColorGroup.LIGHT),
    SHADOWS(
        "Shadows", "colors-shadows", "Deeper", "Lifted",
        "Shadows [-100..+100], default 0. Positive lifts dark areas, negative deepens them. Mid tones do not change.", ColorGroup.LIGHT,
    ),
    HIGHLIGHTS(
        "Highlights", "colors-highlights", "Darker", "Brighter",
        "Highlights [-100..+100], default 0. Negative pulls bright areas down, positive brightens them. Mid tones do not change.", ColorGroup.LIGHT,
    ),
    SATURATION("Saturation", "colors-saturation", "Gray", "Vivid", "Saturation [-100..+100], default 0", ColorGroup.COLOR),
    TEMPERATURE("Temperature", "colors-temperature", "Cooler", "Warmer", "Temperature [-100..+100], default 0. Positive adds a warm (orange) cast, negative adds a cool (blue) cast.", ColorGroup.COLOR),
}

/**
 * One row of a group card: the label and the signed value, the slider, and the two end labels.
 * A lime bar at the left edge marks a changed value. Each row after the first has a line at the top.
 * The row paints its texts, so that the measurement and the painting use the same metrics.
 * The slider fills from the center (the default 0).
 */
internal class ColorRow(val control: ColorControl, private val first: Boolean) : JPanel(null) {
    val slider: JSlider = DefaultFillSliderUI.slider(-100, 100, 0).apply {
        name = control.componentName
        toolTipText = control.tooltip
        getAccessibleContext().accessibleName = control.label
    }

    private val nameFont = UiKit.font(13f)
    private val valueFont = UiKit.font(13f, UiKit.Weight.SEMIBOLD)

    val isChanged: Boolean get() = slider.value != 0

    /** The value text, for example "+24" or "−24". */
    val valueText: String get() = UiKit.signed(slider.value)

    init {
        name = "${control.componentName}-row"
        isOpaque = false
        add(slider)
        slider.addChangeListener { repaint() }
    }

    override fun getPreferredSize() = Dimension(MIN_WIDTH, ROW_HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, ROW_HEIGHT)

    override fun doLayout() {
        slider.setBounds(SliderRows.PAD_X, SLIDER_Y, width - SliderRows.PAD_X * 2, SLIDER_HEIGHT)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            SliderRows.paintFrame(g2, width, height, first, isChanged)
            val right = (width - SliderRows.PAD_X).toFloat()
            val value = valueText
            val valueX = right - UiKit.textWidth(value, valueFont)
            UiKit.drawText(g2, value, valueFont, if (isChanged) UiKit.LIME else UiKit.FG_3, valueX, PAD_TOP.toFloat(), LINE.toFloat())
            val name = UiKit.ellipsize(control.label, nameFont, valueX - VALUE_GAP - SliderRows.PAD_X)
            UiKit.drawText(g2, name, nameFont, UiKit.FG, SliderRows.PAD_X.toFloat(), PAD_TOP.toFloat(), LINE.toFloat())
            SliderRows.paintEnds(g2, width, (SLIDER_Y + SLIDER_HEIGHT - 1).toFloat(), control.low, control.high)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val ROW_HEIGHT = 70
        private const val MIN_WIDTH = 200
        private const val PAD_TOP = 8
        private const val LINE = 18
        private const val VALUE_GAP = 8
        private const val SLIDER_Y = PAD_TOP + LINE + 2
        private const val SLIDER_HEIGHT = 22
    }
}

/** A card with a caption ("LIGHT" or "COLOR") and the rows of its controls. */
internal class ColorGroupCard(val group: ColorGroup, val rows: List<ColorRow>) :
    GroupCard("colors-group-${group.name.lowercase()}", group.title, group.ikon, rows, ColorRow.ROW_HEIGHT)
