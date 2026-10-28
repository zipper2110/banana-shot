package org.litvin.ui.commons

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.litvin.i18n.AppLanguage
import org.litvin.ui.tabs.export.WrapText as ExportWrapText
import org.litvin.ui.tabs.stats.WrapText as StatsWrapText
import kotlin.math.ceil

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

    /** B-11 E8-T4: a Japanese text has no spaces. It breaks between characters by the line break rules. */
    @Test
    fun `a Japanese text wraps between characters`() {
        val text = "「BananaShot」でポイントを選択して、1080pの動画をエクスポートします。"
        val japaneseWidth = (UiKit.textWidth(text, font) / 4).toInt()
        val lines = WrapText(text, font, Palette.FG).lineTexts(japaneseWidth)
        assertTrue(lines.size >= 4, "lines: $lines")
        assertEquals(text, lines.joinToString(""))
        assertTrue(lines.any { "BananaShot" in it }, "lines: $lines")
        lines.forEach { line ->
            assertTrue(UiKit.textWidth(line, font) <= japaneseWidth, "\"$line\" is wider than $japaneseWidth px")
            assertTrue(line.first() !in "、。」）ー", "\"$line\" starts with a closing mark")
            assertTrue(line.last() != '「', "\"$line\" ends with an opening mark")
        }
        val statsLines = StatsWrapText(text, font, Palette.FG).lines(japaneseWidth)
        assertEquals(text, statsLines.joinToString(""))
        statsLines.forEach { line -> assertTrue(UiKit.textWidth(line, font) <= japaneseWidth, "\"$line\" is wider than $japaneseWidth px") }
    }

    /** B-11 E8-T4: a Chinese text in a component wraps between characters, as a Japanese text. */
    @Test
    fun `a Chinese text wraps between characters`() {
        val text = "在 BananaShot 中选择一个回合，然后导出 1080p 视频。统计卡片显示在最后一个回合之后。"
        val chineseFont = UiFonts.forText(font, text, AppLanguage.CHINESE_SIMPLIFIED)
        val width = (UiKit.textWidth(text, chineseFont) / 4).toInt()
        val lines = WrapText(text, chineseFont, Palette.FG).lineTexts(width)
        assertTrue(lines.size >= 4, "lines: $lines")
        assertEquals(text.replace(" ", ""), lines.joinToString("").replace(" ", ""))
        assertTrue(lines.any { "BananaShot" in it }, "lines: $lines")
        lines.forEach { line ->
            assertTrue(UiKit.textWidth(line, chineseFont) <= width, "\"$line\" is wider than $width px")
            assertTrue(line.trim().first() !in "，。）", "\"$line\" starts with a closing mark")
        }
    }

    /** B-11 E8-T4: the last line of a Japanese paragraph has more than one character ("み" alone looked wrong). */
    @Test
    fun `a Japanese paragraph does not end with one character alone`() {
        val text = "ファイルは最大、エクスポートは最も遅い"
        val japaneseFont = UiFonts.forText(font, text, AppLanguage.JAPANESE)
        // At this width, only the last character does not fit on the first line.
        val width = ceil(UiKit.textWidth(text.dropLast(1), japaneseFont)).toInt() + 2
        listOf(
            WrapText(text, japaneseFont, Palette.FG).lineTexts(width),
            ExportWrapText(text, japaneseFont, Palette.FG).lineTexts(width),
        ).forEach { lines ->
            assertEquals(listOf(text.dropLast(2), "遅い"), lines)
        }
    }

    @Test
    fun `a paragraph with one character does not take a character from the paragraph before it`() {
        val text = "テストです\nあ"
        val japaneseFont = UiFonts.forText(font, text, AppLanguage.JAPANESE)
        assertEquals(listOf("テストです", "あ"), WrapText(text, japaneseFont, Palette.FG).lineTexts(400))
        assertEquals(listOf("テストです", "あ"), ExportWrapText(text, japaneseFont, Palette.FG).lineTexts(400))
    }

    @Test
    fun `a word that fits stays whole`() {
        assertEquals(listOf("Satz "), splitToWidth("Satz ", font, 200f))
        assertEquals(listOf(word), splitToWidth(word, font, 0f))
    }
}
