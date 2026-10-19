package org.litvin.ui.tabs.adjustments

import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.backgroundHex
import org.litvin.ui.commons.GroupCard
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SliderRows
import org.litvin.ui.commons.SwatchButton
import org.litvin.ui.commons.UiKit
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JPanel

/**
 * The row of the fill color: the areas outside the rotated video get this color in the preview and in the export.
 * A click on the swatch calls [onPick]. The row shows the lime bar when the color is not black.
 */
internal class BackgroundRow(onPick: () -> Unit) : JPanel(null) {
    val swatch = SwatchButton(Color(AdjustmentsV1.DEFAULT_BACKGROUND_RGB)).apply {
        name = "crop-background"
        toolTipText = "Choose the fill color"
        getAccessibleContext().accessibleName = LABEL
        addActionListener { onPick() }
    }

    private val nameFont = UiKit.font(13f)
    private val hexFont = UiKit.font(12f)

    /** The fill color as 0xRRGGBB. */
    var rgb: Int = AdjustmentsV1.DEFAULT_BACKGROUND_RGB
        set(value) {
            if (field == value) return
            field = value
            swatch.color = Color(value)
            repaint()
        }

    /** The color text next to the swatch, for example "#000000". */
    val hexText: String get() = backgroundHex(rgb)

    init {
        name = "crop-background-row"
        isOpaque = false
        add(swatch)
    }

    override fun getPreferredSize() = Dimension(MIN_WIDTH, ROW_HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, ROW_HEIGHT)

    override fun doLayout() {
        val size = swatch.preferredSize
        swatch.setBounds(width - SliderRows.PAD_X - size.width, (height - size.height) / 2, size.width, size.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            SliderRows.paintFrame(g2, width, height, first = true, changed = rgb != AdjustmentsV1.DEFAULT_BACKGROUND_RGB)
            UiKit.drawText(g2, LABEL, nameFont, Palette.FG, SliderRows.PAD_X.toFloat(), 0f, height.toFloat())
            val hex = hexText
            val hexX = swatch.x - HEX_GAP - UiKit.textWidth(hex, hexFont)
            UiKit.drawText(g2, hex, hexFont, Palette.FG_3, hexX, 0f, height.toFloat())
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val LABEL = "Fill color"
        const val MIN_WIDTH = 200
        const val ROW_HEIGHT = 48
        const val HEX_GAP = 10
    }
}

/** The "BACKGROUND" card with the fill color row. */
internal class BackgroundCard(val row: BackgroundRow) :
    GroupCard("crop-group-background", "Background", Material2OutlinedAL.FORMAT_COLOR_FILL, listOf(row), row.preferredSize.height)
