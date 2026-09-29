package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.GroupCard
import org.litvin.ui.commons.SliderValueFormat
import org.litvin.ui.commons.SliderValueRow
import org.litvin.ui.commons.UiKit

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
 * One row of a group card: the label and the signed value field, the slider, and the two end labels.
 * The slider fills from the center (the default 0).
 */
internal class ColorRow(val control: ColorControl, first: Boolean) : SliderValueRow(
    control.componentName, control.label, control.tooltip, MIN, MAX, 0, emptyList(),
    control.low, null, control.high, VALUE_FORMAT, first,
) {
    /** The value text, for example "+24" or "−24". */
    val valueText: String get() = UiKit.signed(slider.value)

    companion object {
        private const val MIN = -100
        private const val MAX = 100

        /** A signed whole number, for example "+24" or "−24". Up and Down change it by 1. */
        private val VALUE_FORMAT = SliderValueFormat("", 1, UiKit::signed) { text ->
            SliderValueFormat.parse(text, "")?.toInt()
        }
    }
}

/** A card with a caption ("LIGHT" or "COLOR") and the rows of its controls. */
internal class ColorGroupCard(val group: ColorGroup, val rows: List<ColorRow>) :
    GroupCard("colors-group-${group.name.lowercase()}", group.title, group.ikon, rows, SliderValueRow.ROW_HEIGHT)
