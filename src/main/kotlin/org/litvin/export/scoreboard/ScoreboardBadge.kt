package org.litvin.export.scoreboard

import org.litvin.scoring.ScoreboardPosition
import kotlin.math.max

/**
 * The badge of a deciding point, for example "STAR POINT": a small tab in the accent color next to the board.
 * The "2ND SERVE" mark of a 2nd serve (B-68) is a tab of the same size in a dark color. All scoreboard styles use the
 * same tabs. When the board has the two tabs, they are in one row, the badge first.
 *
 * The tabs are on the side of the board that is away from the edge of the video: under a board at the top,
 * and above a board at the bottom. The scene gets taller, but the board stays at the same place in the video,
 * because the placement keeps the corner of the scene at the corner of the video.
 */
internal object ScoreboardBadge {
    const val HEIGHT = 30.0
    const val GAP = 6.0
    private const val FONT_SIZE = 19.0
    private const val SPACING = 1.5
    private const val PAD_X = 14.0
    private const val RADIUS = 6.0
    private const val DARK_TEXT = 0x111111

    /** The color of the "2ND SERVE" mark. It is dark, so that it does not look like the badge of a deciding point. */
    const val MARK_RGB = 0x1A1A1A
    const val MARK_OPACITY = 0.88

    /** One tab: its text, and the color and opacity of its box. The text is dark or white, for contrast. */
    data class Tab(val text: String, val rgb: Int, val opacity: Double = 1.0)

    /** [scene] with [tabs] in one row next to the board. With no tabs, the result is [scene]. */
    fun add(scene: ScoreboardScene, tabs: List<Tab>, position: ScoreboardPosition): ScoreboardScene {
        if (tabs.isEmpty()) return scene
        val widths = tabs.map { ScoreboardFonts.textWidth(it.text, INTER, true, FONT_SIZE, SPACING) + 2 * PAD_X }
        val rowWidth = widths.sum() + GAP * (tabs.size - 1)
        val width = max(scene.width, rowWidth)
        val right = position == ScoreboardPosition.TOP_RIGHT || position == ScoreboardPosition.BOTTOM_RIGHT
        val bottom = position == ScoreboardPosition.BOTTOM_LEFT || position == ScoreboardPosition.BOTTOM_RIGHT
        // A row that is wider than the board moves the board, so that the board keeps its edge at the video edge.
        val boardDx = if (right) width - scene.width else 0.0
        val boardDy = if (bottom) HEIGHT + GAP else 0.0
        val badgeY = if (bottom) 0.0 else scene.height + GAP
        // The row starts at the edge of the board that is at the video edge.
        var x = if (right) width - rowWidth else 0.0
        val items = tabs.zip(widths).flatMap { (tab, tabWidth) ->
            val tabX = x
            x += tabWidth + GAP
            listOf(
                SceneItem.Box(tabX, badgeY, tabWidth, HEIGHT, tab.rgb, tab.opacity, Corners.all(RADIUS)),
                centered(
                    tabX + tabWidth / 2, badgeY + HEIGHT / 2, tab.text, INTER, FONT_SIZE,
                    textRgbOn(tab.rgb), TextAnchor.CENTER, spacing = SPACING,
                ),
            )
        }
        return ScoreboardScene(
            width = width,
            height = scene.height + HEIGHT + GAP,
            items = scene.items.map { it.moved(boardDx, boardDy) } + items,
        )
    }

    /** Dark text on a light accent, white text on a dark accent. */
    internal fun textRgbOn(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val luma = 0.299 * r + 0.587 * g + 0.114 * b
        return if (luma >= 140) DARK_TEXT else WHITE
    }

    private fun SceneItem.moved(dx: Double, dy: Double): SceneItem {
        if (dx == 0.0 && dy == 0.0) return this
        return when (this) {
            is SceneItem.Box -> copy(x = x + dx, y = y + dy)
            is SceneItem.Label -> copy(x = x + dx, y = y + dy)
            is SceneItem.Polygon -> copy(points = points.map { ScenePoint(it.x + dx, it.y + dy) })
            is SceneItem.Polyline -> copy(points = points.map { ScenePoint(it.x + dx, it.y + dy) })
        }
    }
}
