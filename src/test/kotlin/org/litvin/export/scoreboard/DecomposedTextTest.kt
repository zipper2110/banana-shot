package org.litvin.export.scoreboard

import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.share.ThumbnailField
import org.litvin.share.ThumbnailLooks
import org.litvin.share.ThumbnailScene
import org.litvin.share.ThumbnailStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-11 E7-T4: a user can type a letter with an accent as a letter and a combining mark (NFD). The video texts use
 * the composed form (NFC), so the scoreboard fonts without combining marks show them.
 */
class DecomposedTextTest {
    private val composed = "Аксёнов"
    private val decomposed = "Аксёнов"

    @Test
    fun `the scoreboard shows a decomposed player name in the composed form`() {
        val display = ScoreboardComponent.display(OverlaySpan(0L, 1_000L, "", p1Name = decomposed, p2Name = "Peña"))
        assertEquals("АКСЁНОВ", display.player1Name)
    }

    /** Before the fix, the combining mark counted as one letter, so the name lost one more letter. */
    @Test
    fun `the length limit of a name counts the letters, not the combining marks`() {
        val long = "ё".repeat(ScoreboardComponent.PLAYER_NAME_MAX_CHARS)
        val display = ScoreboardComponent.display(OverlaySpan(0L, 1_000L, "", p1Name = long, p2Name = "B"))
        assertEquals("Ё".repeat(ScoreboardComponent.PLAYER_NAME_MAX_CHARS), display.player1Name)
    }

    @Test
    fun `the font and the width of a decomposed text are those of the composed text`() {
        listOf(ARIAL_BLACK, GEORGIA, TREBUCHET, INK_FREE).forEach { family ->
            assertEquals(ScoreboardFonts.family(family, composed), ScoreboardFonts.family(family, decomposed), family)
            assertEquals(
                ScoreboardFonts.textWidth(composed, family, false, 40.0),
                ScoreboardFonts.textWidth(decomposed, family, false, 40.0),
                family,
            )
        }
    }

    /** B-11 E3-T23: the video adds the letter spacing once for each character. An emoji is two Java chars. */
    @Test
    fun `the letter spacing counts an emoji as one character`() {
        val withoutSpacing = ScoreboardFonts.textWidth("A🎾", "Segoe UI", false, 40.0)
        val withSpacing = ScoreboardFonts.textWidth("A🎾", "Segoe UI", false, 40.0, spacing = 3.0)
        assertEquals(6.0, withSpacing - withoutSpacing, 0.001)
    }

    @Test
    fun `a thumbnail shows a decomposed text in the composed form`() {
        val style = ThumbnailStyle.entries.first()
        val scene = ThumbnailScene(style, style.places.first(), mapOf(ThumbnailField.TEAM1 to " $decomposed "), ThumbnailLooks.colors(style, null))
        assertEquals(composed, scene.text(ThumbnailField.TEAM1))
    }
}
