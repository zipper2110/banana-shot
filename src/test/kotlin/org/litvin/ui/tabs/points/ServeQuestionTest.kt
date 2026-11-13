package org.litvin.ui.tabs.points

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-68 E2: the "Who serves?" question of advanced mode. */
class ServeQuestionTest {
    @Test
    fun `advanced mode with no serve mark asks the question`() {
        val question = ServeQuestion()

        question.onModeSelected(advanced = true, serverKnown = false)

        assertTrue(question.isOpen)
    }

    @Test
    fun `basic mode or a known server does not ask`() {
        val question = ServeQuestion()

        question.onModeSelected(advanced = false, serverKnown = false)
        assertFalse(question.isOpen)
        question.onModeSelected(advanced = true, serverKnown = true)
        assertFalse(question.isOpen)
        assertNull(question.onPointStart(advanced = true, serverKnown = true))
        assertFalse(question.isOpen)
    }

    @Test
    fun `the answer marks the pending or the selected point`() {
        val question = ServeQuestion()
        question.onModeSelected(advanced = true, serverKnown = false)

        assertEquals("p1" to 2, question.answer(2, target = "p1"))
        assertFalse(question.isOpen)
    }

    @Test
    fun `an answer with no point waits for the next point start`() {
        val question = ServeQuestion()
        question.onModeSelected(advanced = true, serverKnown = false)

        assertNull(question.answer(1, target = null))
        assertFalse(question.isOpen)
        assertEquals(1, question.onPointStart(advanced = true, serverKnown = false))
        assertFalse(question.isOpen)
    }

    @Test
    fun `a point start with no answer asks the question`() {
        val question = ServeQuestion()

        assertNull(question.onPointStart(advanced = true, serverKnown = false))

        assertTrue(question.isOpen)
    }

    @Test
    fun `a serve mark or basic mode closes the question and forgets a kept answer`() {
        val question = ServeQuestion()
        question.onModeSelected(advanced = true, serverKnown = false)
        question.sync(advanced = true, serverKnown = true)
        assertFalse(question.isOpen)

        question.answer(1, target = null)
        question.sync(advanced = false, serverKnown = false)
        assertTrue(question.onPointStart(advanced = true, serverKnown = false) == null && question.isOpen)
    }
}
