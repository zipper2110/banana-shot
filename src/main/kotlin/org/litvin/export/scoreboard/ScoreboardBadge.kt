package org.litvin.export.scoreboard

import org.litvin.scoring.ScoreboardPosition
import kotlin.math.max

/**
 * The badge of a deciding point, for example "STAR POINT": a small tab in the accent color next to the board.
 * All scoreboard styles use the same badge.
 *
 * The badge is on the side of the board that is away from the edge of the video: under a board at the top,
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

    fun add(scene: ScoreboardScene, text: String, accentRgb: Int, position: ScoreboardPosition): ScoreboardScene {
        val textWidth = ScoreboardFonts.textWidth(text, SEGOE, true, FONT_SIZE, SPACING)
        val badgeWidth = textWidth + 2 * PAD_X
        val width = max(scene.width, badgeWidth)
        val right = position == ScoreboardPosition.TOP_RIGHT || position == ScoreboardPosition.BOTTOM_RIGHT
        val bottom = position == ScoreboardPosition.BOTTOM_LEFT || position == ScoreboardPosition.BOTTOM_RIGHT
        // A badge that is wider than the board moves the board, so that the board keeps its edge at the video edge.
        val boardDx = if (right) width - scene.width else 0.0
        val boardDy = if (bottom) HEIGHT + GAP else 0.0
        val badgeX = if (right) width - badgeWidth else 0.0
        val badgeY = if (bottom) 0.0 else scene.height + GAP
        val badge = listOf(
            SceneItem.Box(badgeX, badgeY, badgeWidth, HEIGHT, accentRgb, 1.0, Corners.all(RADIUS)),
            centered(
                badgeX + badgeWidth / 2, badgeY + HEIGHT / 2, text, SEGOE, FONT_SIZE,
                textRgbOn(accentRgb), TextAnchor.CENTER, spacing = SPACING,
            ),
        )
        return ScoreboardScene(
            width = width,
            height = scene.height + HEIGHT + GAP,
            items = scene.items.map { it.moved(boardDx, boardDy) } + badge,
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
