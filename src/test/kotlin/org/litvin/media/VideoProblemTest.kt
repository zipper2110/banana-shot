package org.litvin.media

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoProblemTest {
    @Test
    fun aDllThatWindowsBlocksNamesWindowsSecurity() {
        val cause = UnsatisfiedLinkError("Unable to load library 'libmpv-2'")

        val problem = VideoProblem.previewFailure(cause, 4551)

        assertEquals(VideoProblem.PREVIEW_BLOCKED.title, problem.title)
        assertTrue("Smart App Control" in problem.explanation)
        assertTrue(problem.reportable)
        assertEquals("Windows error 4551.\nUnsatisfiedLinkError: Unable to load library 'libmpv-2'", problem.details)
    }

    @Test
    fun otherLoadErrorsAreAFailureOfTheApplication() {
        assertEquals(VideoProblem.PREVIEW_FAILED.title, VideoProblem.previewFailure(null, 126).title)
        assertEquals(VideoProblem.PREVIEW_FAILED.title, VideoProblem.previewFailure(IOException("gpu"), null).title)
        assertNull(VideoProblem.previewFailure(null, null).details)
    }

    @Test
    fun theReportHasTheExplanationAndTheDetails() {
        val problem = VideoProblem.previewFailure(IllegalStateException("mpv_create returned null"), null)

        assertEquals(
            VideoProblem.PREVIEW_FAILED.explanation + "\n\nIllegalStateException: mpv_create returned null",
            problem.reportText(),
        )
        assertEquals(VideoProblem.UNREADABLE.explanation, VideoProblem.UNREADABLE.reportText())
    }

    @Test
    fun onlyProblemsWithAnUnknownCauseAreReportable() {
        assertTrue(VideoProblem.UNREADABLE.reportable)
        listOf(
            VideoProblem.NOT_FOUND,
            VideoProblem.STILL_WRITING,
            VideoProblem.TRUNCATED,
            VideoProblem.UNWRITTEN_END,
            VideoProblem.NOT_FINALIZED,
            VideoProblem.NO_VIDEO,
        ).forEach { assertFalse(it.reportable, it.title) }
    }
}
