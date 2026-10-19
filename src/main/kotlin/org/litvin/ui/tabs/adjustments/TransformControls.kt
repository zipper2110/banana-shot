package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.commons.GroupCard
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SliderValueFormat
import org.litvin.ui.commons.SliderValueRow
import org.litvin.ui.commons.UiKit
import java.awt.Graphics2D
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats

/** The two groups of the Transform panel. */
internal enum class TransformGroup(private val titleKey: String, val ikon: Ikon) {
    CROP("adjust.group.crop", Material2OutlinedAL.CROP),
    ROTATION("adjust.rotation", Material2OutlinedMZ.ROTATE_90_DEGREES_CCW),
    ;

    val title: String get() = tr(titleKey)
}

/**
 * The five transform controls in the order of the panel. The slider ranges and the tooltips are the same as
 * in the earlier tab. [low], [mid] and [high] are the small labels under the slider. [minorTicks] are
 * slider values with a small tick (the ±90° snap angles of Rotation).
 */
internal enum class TransformControl(
    val componentName: String,
    val min: Int,
    val max: Int,
    val default: Int,
    val unit: String,
    val minorTicks: List<Int>,
    val group: TransformGroup,
) {
    ZOOM("crop-zoom", 100, 250, 100, "%", emptyList(), TransformGroup.CROP),
    PAN_X("crop-pan-x", -100, 100, 0, "", emptyList(), TransformGroup.CROP),
    PAN_Y("crop-pan-y", -100, 100, 0, "", emptyList(), TransformGroup.CROP),
    ROTATION("crop-rotation", -360, 360, 0, "°", listOf(-180, 180), TransformGroup.ROTATION),
    FINE_ROTATION("crop-rotation-fine", -50, 50, 0, "°", emptyList(), TransformGroup.ROTATION),
    ;

    val label: String
        get() = when (this) {
            ZOOM -> tr("adjust.zoom")
            PAN_X -> tr("adjust.panX")
            PAN_Y -> tr("adjust.panY")
            ROTATION -> tr("adjust.rotation")
            FINE_ROTATION -> tr("adjust.fineRotation")
        }

    val tooltip: String
        get() = when (this) {
            FINE_ROTATION -> tr("adjust.fineRotation.tooltip")
            else -> label
        }

    val low: String
        get() = when (this) {
            ZOOM -> "${DisplayFormats.integer(100)}%"
            PAN_X -> tr("adjust.left")
            PAN_Y -> tr("adjust.down")
            ROTATION -> "−180°"
            FINE_ROTATION -> "−5°"
        }

    val mid: String?
        get() = when (this) {
            ZOOM -> null
            PAN_X, PAN_Y -> tr("adjust.center")
            ROTATION, FINE_ROTATION -> "0°"
        }

    val high: String
        get() = when (this) {
            ZOOM -> "${DisplayFormats.integer(250)}%"
            PAN_X -> tr("adjust.right")
            PAN_Y -> tr("adjust.up")
            ROTATION -> "+180°"
            FINE_ROTATION -> "+5°"
        }

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
        val number = SliderValueFormat.parse(text, unit) ?: return null
        return when (this) {
            ZOOM, PAN_X, PAN_Y -> number.toInt()
            ROTATION -> (number * 2.0f).roundToInt()
            FINE_ROTATION -> (number * 10.0f).roundToInt()
        }
    }

    /** One decimal with the decimal separator of the format locale. */
    private fun signedDecimal(value: Float): String {
        val text = DisplayFormats.decimal(value.toDouble(), 1, 1, grouping = false)
        val zero = DisplayFormats.decimal(0.0, 1, 1, grouping = false)
        return if (value > 0 && text != zero) "+$text" else if (text == "-$zero") zero else text
    }
}

/** The format of the value field of a transform control. Up and Down change Rotation by 1° and the others by 1 unit. */
internal fun TransformControl.valueFormat(): SliderValueFormat =
    SliderValueFormat(unit, if (this == TransformControl.ROTATION) 2 else 1, ::toText, ::fromText)

/**
 * One row of a group card: the label and the value field, the slider, and the end labels.
 * The slider fills from the default value.
 */
internal class TransformRow(val control: TransformControl, first: Boolean) : SliderValueRow(
    control.componentName, control.label, control.tooltip, control.min, control.max, control.default,
    control.minorTicks, control.low, control.mid, control.high, control.valueFormat(), first,
)

/**
 * A card with a caption ("CROP" or "ROTATION") and the rows of its controls.
 * The Rotation card shows the total angle on the right of the caption, for example "Angle +2.3°".
 */
internal class TransformGroupCard(val group: TransformGroup, val rows: List<TransformRow>) :
    GroupCard("crop-group-${group.name.lowercase()}", group.title, group.ikon, rows, SliderValueRow.ROW_HEIGHT) {
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
        val valueColor = if (value != ZERO_ANGLE) Palette.LIME else Palette.FG_3
        UiKit.drawText(g2, value, angleValueFont, valueColor, valueX, top, height)
        val labelX = valueX - ANGLE_GAP - UiKit.textWidth(ANGLE_LABEL, angleLabelFont)
        UiKit.drawText(g2, ANGLE_LABEL, angleLabelFont, Palette.FG_3, labelX, top, height)
    }

    private companion object {
        val ANGLE_LABEL: String get() = tr("adjust.angle")
        const val ANGLE_GAP = 4
        val ZERO_ANGLE: String get() = signedDegrees(0f)
    }
}

/**
 * An angle with its sign and one decimal: "+2.3°", "−2.3°" (with a minus sign, not a hyphen) or "0.0°".
 * The decimal separator is the one of the format locale.
 */
internal fun signedDegrees(value: Float): String {
    val text = DisplayFormats.decimal(abs(value).toDouble(), 1, 1, grouping = false)
    return when {
        text == DisplayFormats.decimal(0.0, 1, 1, grouping = false) -> "$text°"
        value > 0 -> "+$text°"
        else -> "−$text°"
    }
}
