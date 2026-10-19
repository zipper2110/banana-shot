package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.GroupCard
import org.litvin.ui.commons.SliderValueFormat
import org.litvin.ui.commons.SliderValueRow
import org.litvin.ui.commons.UiKit
import org.litvin.i18n.tr

/** The two groups of the Color grade panel. */
internal enum class ColorGroup(private val titleKey: String, val ikon: Ikon) {
    LIGHT("adjust.group.light", Material2OutlinedMZ.WB_SUNNY),
    COLOR("adjust.group.color", Material2OutlinedMZ.PALETTE),
    ;

    val title: String get() = tr(titleKey)
}

/**
 * The six color controls in the order of the panel. Each runs from -100 to +100 with 0 as the default.
 * [low] and [high] are the small labels under the ends of the slider. The constructor gets catalog keys.
 */
internal enum class ColorControl(
    private val labelKey: String,
    val componentName: String,
    private val lowKey: String,
    private val highKey: String,
    private val tooltipKey: String,
    val group: ColorGroup,
) {
    BRIGHTNESS("adjust.brightness", "colors-brightness", "adjust.darker", "adjust.brighter", "adjust.brightness.tooltip", ColorGroup.LIGHT),
    CONTRAST("adjust.contrast", "colors-contrast", "adjust.flatter", "adjust.stronger", "adjust.contrast.tooltip", ColorGroup.LIGHT),
    SHADOWS("adjust.shadows", "colors-shadows", "adjust.deeper", "adjust.lifted", "adjust.shadows.tooltip", ColorGroup.LIGHT),
    HIGHLIGHTS("adjust.highlights", "colors-highlights", "adjust.darker", "adjust.brighter", "adjust.highlights.tooltip", ColorGroup.LIGHT),
    SATURATION("adjust.saturation", "colors-saturation", "adjust.gray", "adjust.vivid", "adjust.saturation.tooltip", ColorGroup.COLOR),
    TEMPERATURE("adjust.temperature", "colors-temperature", "adjust.cooler", "adjust.warmer", "adjust.temperature.tooltip", ColorGroup.COLOR),
    ;

    val label: String get() = tr(labelKey)
    val low: String get() = tr(lowKey)
    val high: String get() = tr(highKey)
    val tooltip: String get() = tr(tooltipKey)
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
