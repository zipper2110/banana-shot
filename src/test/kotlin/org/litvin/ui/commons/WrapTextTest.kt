package org.litvin.ui.commons

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.litvin.ui.tabs.stats.WrapText as StatsWrapText

/**
 * B-11 E7-L-de and plan section 7: German has long compound nouns. A word that is wider than the line breaks at a
 * character, so that no line is wider than the text.
 */
class WrapTextTest {
    private val font = UiKit.font(14f)
    private val word = "Spielstandsanzeigeneinstellungen"
    private val width = (UiKit.textWidth(word, font) / 3).toInt()

    @Test
    fun `a German compound noun that is wider than the line breaks at a character`() {
        val lines = WrapText("Öffne die $word jetzt", font, Palette.FG).lineTexts(width)
        assertTrue(lines.size >= 4, "lines: $lines")
        lines.forEach { line -> assertTrue(UiKit.textWidth(line, font) <= width, "\"$line\" is wider than $width px") }
        assertEquals("Öffnedie${word}jetzt", lines.joinToString("").replace(" ", ""))
    }

    @Test
    fun `the statistics text breaks a German compound noun that is wider than the line`() {
        val lines = StatsWrapText("Die $word", font, Palette.FG).lines(width)
        assertTrue(lines.size >= 4, "lines: $lines")
        lines.forEach { line -> assertTrue(UiKit.textWidth(line, font) <= width, "\"$line\" is wider than $width px") }
        assertEquals("Die$word", lines.joinToString("").replace(" ", ""))
    }

    @Test
    fun `a word that fits stays whole`() {
        assertEquals(listOf("Satz "), splitToWidth("Satz ", font, 200f))
        assertEquals(listOf(word), splitToWidth(word, font, 0f))
    }
}
