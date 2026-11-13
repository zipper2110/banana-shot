package org.litvin.ui.tabs.points

import org.litvin.scoring.Ending
import org.litvin.scoring.ErrorKind
import org.litvin.ui.commons.AppShortcuts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-68 E5: the error kind question of advanced mode. */
class ErrorKindQuestionTest {
    @Test
    fun `an error ending opens the question only when the option is on`() {
        val question = ErrorKindQuestion()

        question.onEnding(asks = false, pointId = "A", ending = Ending.UNFORCED_ERROR)
        assertFalse(question.isOpen)

        question.onEnding(asks = true, pointId = "A", ending = Ending.FORCED_ERROR)
        assertTrue(question.isOpen)
        assertEquals("A", question.target)
    }

    @Test
    fun `an ending that is not an error does not open the question and closes an open question`() {
        val question = ErrorKindQuestion()

        question.onEnding(asks = true, pointId = "A", ending = Ending.WINNER)
        assertFalse(question.isOpen)
        question.onEnding(asks = true, pointId = "A", ending = Ending.ACE)
        assertFalse(question.isOpen)

        question.onEnding(asks = true, pointId = "A", ending = Ending.UNFORCED_ERROR)
        question.onEnding(asks = true, pointId = "B", ending = Ending.WINNER)
        assertFalse(question.isOpen)
        assertNull(question.target)
    }

    @Test
    fun `while the question is open, the digits answer it`() {
        val question = ErrorKindQuestion()
        question.onEnding(asks = true, pointId = "A", ending = Ending.UNFORCED_ERROR)

        assertEquals(ErrorKindKey.Answer("A", ErrorKind.LONG), question.onKey(AppShortcuts.ENDING_P2_FORCED_ERROR))
        assertFalse(question.isOpen)
    }

    @Test
    fun `each answer has its digit`() {
        assertEquals("1", ErrorKindQuestion.keyOf(ErrorKind.NET).display)
        assertEquals("2", ErrorKindQuestion.keyOf(ErrorKind.LONG).display)
        assertEquals("3", ErrorKindQuestion.keyOf(ErrorKind.WIDE).display)
        ErrorKind.entries.forEach { assertEquals(it, ErrorKindQuestion.kindOf(ErrorKindQuestion.keyOf(it))) }
        assertNull(ErrorKindQuestion.kindOf(AppShortcuts.ENDING_P2_WINNER))
    }

    @Test
    fun `while the question is open, a digit that is not an answer does nothing and selects no ending`() {
        val question = ErrorKindQuestion()
        question.onEnding(asks = true, pointId = "A", ending = Ending.FORCED_ERROR)

        assertEquals(ErrorKindKey.Ignored, question.onKey(AppShortcuts.ENDING_P2_WINNER))
        assertTrue(question.isOpen)
    }

    @Test
    fun `when the question is closed, the digits select endings`() {
        val question = ErrorKindQuestion()

        assertEquals(ErrorKindKey.SelectsEnding, question.onKey(AppShortcuts.ENDING_P1_WINNER))
        assertEquals(ErrorKindKey.SelectsEnding, question.onKey(AppShortcuts.ENDING_P2_UNFORCED_ERROR))
    }

    @Test
    fun `Esc closes only an open question`() {
        val question = ErrorKindQuestion()
        assertFalse(question.onEscape())

        question.onEnding(asks = true, pointId = "A", ending = Ending.FORCED_ERROR)
        assertTrue(question.onEscape())
        assertFalse(question.isOpen)
        // The next Esc can cancel the pending point again.
        assertFalse(question.onEscape())
    }

    @Test
    fun `the question closes when it does not apply any more`() {
        val question = ErrorKindQuestion()
        question.onEnding(asks = true, pointId = "A", ending = Ending.FORCED_ERROR)

        // No active clip keeps the question open: the video plays on after the end of the point.
        question.sync(asks = true, targetIsError = true, activeId = null)
        assertTrue(question.isOpen)
        question.sync(asks = true, targetIsError = true, activeId = "A")
        assertTrue(question.isOpen)

        question.sync(asks = true, targetIsError = true, activeId = "B")
        assertFalse(question.isOpen)

        question.open("A")
        question.sync(asks = true, targetIsError = false, activeId = "A")
        assertFalse(question.isOpen)

        question.open("A")
        question.sync(asks = false, targetIsError = true, activeId = "A")
        assertFalse(question.isOpen)
    }

    @Test
    fun `the strip shows the open question, and the active error clip after the question closes`() {
        val question = ErrorKindQuestion()
        question.onEnding(asks = true, pointId = "A", ending = Ending.UNFORCED_ERROR)
        assertEquals("A", question.stripClip(asks = true, activeId = null, activeIsError = false))

        question.close()
        assertEquals("A", question.stripClip(asks = true, activeId = "A", activeIsError = true))
        assertNull(question.stripClip(asks = true, activeId = "A", activeIsError = false))
        assertNull(question.stripClip(asks = false, activeId = "A", activeIsError = true))
        assertNull(question.stripClip(asks = true, activeId = null, activeIsError = false))
    }
}
