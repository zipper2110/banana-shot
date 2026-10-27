package org.litvin.ui.tabs.points.ui

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.i18n.DisplayFormats
import org.litvin.i18n.tr
import org.litvin.ui.commons.UiKit
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import javax.swing.Icon

/** One detail of a replay on a replay row: an icon that tells what the value is, and the value. */
internal data class ReplayDetail(val ikon: Ikon?, val text: String)

/**
 * The details of a replay on a replay row, in the Points list and in the replay editor (B-50): the speed, the zoom,
 * the still frame, the replay comments, and a muted sound. The icons of the editor groups tell what each value is.
 */
internal object ReplayDetails {
    private const val ICON_SIZE = 13
    private const val ICON_GAP = 3
    private const val ITEM_GAP = 10

    private val icons = HashMap<Pair<Ikon, Color>, Icon>()

    fun of(speed: Double, zoomFactor: Double?, muted: Boolean, freezeMs: Long? = null, commentCount: Int = 0): List<ReplayDetail> = listOfNotNull(
        ReplayDetail(Material2MZ.SPEED, "${DisplayFormats.decimal(speed, 0, 2)}×"),
        zoomFactor?.let { ReplayDetail(Material2MZ.ZOOM_IN, "${DisplayFormats.decimal(it, 0, 1)}×") },
        freezeMs?.let { ReplayDetail(Material2MZ.PAUSE_CIRCLE_OUTLINE, tr("replay.editor.seconds", "value" to DisplayFormats.decimal(it / 1000.0, 0, 1))) },
        ReplayDetail(Material2AL.CHAT_BUBBLE_OUTLINE, commentCount.toString()).takeIf { commentCount > 0 },
        ReplayDetail(null, tr("replay.editor.sound.muted")).takeIf { muted },
    )

    /** The details as plain text, for example "0.5× · 2× · 0.8 s". */
    fun text(details: List<ReplayDetail>): String = details.joinToString(" · ") { it.text }

    /**
     * Paints [details] from [x] to at most [right], centered in the band from [top] with [height]. A detail that does
     * not fit gets "…", and the details after it do not show.
     */
    fun paint(g2: Graphics2D, details: List<ReplayDetail>, font: Font, color: Color, x: Int, top: Float, height: Float, right: Int) {
        var left = x
        for (detail in details) {
            val icon = detail.ikon?.let { icons.getOrPut(it to color) { UiKit.icon(it, ICON_SIZE, color) } }
            val iconWidth = icon?.let { it.iconWidth + ICON_GAP } ?: 0
            val room = right - left - iconWidth
            if (room <= 0) return
            val text = UiKit.ellipsize(detail.text, font, room.toFloat())
            icon?.paintIcon(null, g2, left, (top + (height - icon.iconHeight) / 2).toInt())
            UiKit.drawText(g2, text, font, color, (left + iconWidth).toFloat(), top, height)
            if (text != detail.text) return
            left += iconWidth + UiKit.textWidth(text, font).toInt() + ITEM_GAP
        }
    }
}
