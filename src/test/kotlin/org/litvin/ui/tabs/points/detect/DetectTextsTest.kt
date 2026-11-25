package org.litvin.ui.tabs.points.detect

import org.litvin.autodetect.DetectResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The texts of a result: all warnings, and the partial result (chain-inventory G13). */
class DetectTextsTest {
    @Test
    fun `the dialog shows all warnings, not only the first one`() {
        val warnings = listOf("No refined court model.", "The shot chain failed on 2 rallies.")
        assertEquals("• No refined court model.\n• The shot chain failed on 2 rallies.", DetectTexts.warnings(warnings))
        assertEquals("One.", DetectTexts.warnings(listOf("One.")))
        assertNull(DetectTexts.warnings(emptyList()))
    }

    @Test
    fun `a partial result says so`() {
        assertEquals("Result ready (partial, see the warnings) · rallies: 0", DetectTexts.result(DetectResult(partial = true)))
        assertEquals("Result ready · rallies: 0", DetectTexts.result(DetectResult()))
    }
}
