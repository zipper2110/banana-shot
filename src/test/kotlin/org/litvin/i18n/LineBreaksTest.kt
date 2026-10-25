package org.litvin.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B-11 E8-T4: Chinese and Japanese have no spaces between words. A line breaks between the characters, but not
 * before a closing mark, not after an opening mark, and not inside a Latin word or a number.
 */
class LineBreaksTest {
    @Test
    fun `a Japanese word breaks between its characters by the line break rules`() {
        val text = "「BananaShot」でポイントを選択して、1080pの動画をエクスポートします。"
        val pieces = LineBreaks.pieces(text)
        assertTrue(pieces.size > 10, "pieces: $pieces")
        assertEquals(text, pieces.joinToString(""))
        assertTrue(pieces.any { "BananaShot" in it }, "pieces: $pieces")
        assertTrue(pieces.any { "1080p" in it }, "pieces: $pieces")
        pieces.forEach { piece ->
            assertTrue(piece.first() !in CLOSING, "a line starts with \"$piece\"")
            assertTrue(piece.last() !in OPENING, "a line ends with \"$piece\"")
        }
    }

    @Test
    fun `a Chinese word keeps a Latin name in one piece`() {
        val text = "在BananaShot中选择一个点，然后导出视频。"
        val pieces = LineBreaks.pieces(text)
        assertEquals(text, pieces.joinToString(""))
        assertTrue(pieces.any { "BananaShot" in it }, "pieces: $pieces")
        pieces.forEach { piece -> assertTrue(piece.first() !in CLOSING, "a line starts with \"$piece\"") }
    }

    /** B-11 E8-T4: the Chinese punctuation, also the quotes “…” and 「…」, follows the line break rules. */
    @Test
    fun `a Chinese text does not break before a closing mark or after an opening mark`() {
        listOf(
            "点击“导入新比赛”，选择视频（MP4）。然后标记回合：每个回合都有得分方！",
            "點選「匯入新比賽」，選取影片（MP4）。然後標記回合：每個回合都有得分方！",
        ).forEach { text ->
            val pieces = LineBreaks.pieces(text)
            assertEquals(text, pieces.joinToString(""))
            assertTrue(pieces.size > 10, "pieces: $pieces")
            pieces.forEach { piece ->
                assertTrue(piece.first() !in CHINESE_CLOSING, "a line starts with \"$piece\"")
                assertTrue(piece.last() !in CHINESE_OPENING, "a line ends with \"$piece\"")
            }
        }
    }

    @Test
    fun `a word of the other languages stays one piece`() {
        assertEquals(listOf("well-known "), LineBreaks.pieces("well-known "))
        assertEquals(listOf("Spielstandsanzeige"), LineBreaks.pieces("Spielstandsanzeige"))
        // Korean has spaces between words, so a Korean word does not break.
        assertEquals(listOf("포인트를 "), LineBreaks.pieces("포인트를 "))
        assertEquals(emptySet<Int>(), LineBreaks.cjkBreaks("C:\\Videos\\match.mp4"))
    }

    @Test
    fun `a break position is between two Japanese characters`() {
        val text = "試合の動画"
        assertEquals(setOf(1, 2, 3, 4), LineBreaks.cjkBreaks(text))
        // No break after "「" (position 1) and before "」" (position 3).
        assertEquals(setOf(2), LineBreaks.cjkBreaks("「試合」"))
    }

    private companion object {
        const val CLOSING = "、。，．」』）】ー！？"
        const val OPENING = "「『（【"
        const val CHINESE_CLOSING = "，。、：；！？）」』”"
        const val CHINESE_OPENING = "（「『“"
    }
}
