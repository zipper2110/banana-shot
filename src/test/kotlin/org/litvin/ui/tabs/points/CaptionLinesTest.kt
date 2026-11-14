package org.litvin.ui.tabs.points

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptionLinesTest {
    private val width: (String) -> Float = { it.length.toFloat() }

    @Test
    fun theSplitIsAtTheSpaceThatGivesTheNarrowestLines() {
        assertEquals(listOf("Point", "for Dima"), CaptionLines.split("Point for Dima", width))
    }

    @Test
    fun aTextWithNoSpaceSplitsBetweenCharacters() {
        assertEquals(listOf("Dim", "a得分"), CaptionLines.split("Dima得分", width))
    }

    @Test
    fun aTextOfOneCharacterStaysOnOneLine() {
        assertEquals(listOf("A"), CaptionLines.split("A", width))
    }
}
