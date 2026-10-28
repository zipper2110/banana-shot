package org.litvin.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-11 E3-T19: the fit of a video text in its box. */
class TextFitTest {
    /** A simple font: each code point is half of the font size wide. */
    private val width = { text: String, size: Double -> text.codePointCount(0, text.length) * size / 2 }

    private fun fit(text: String, maxWidth: Double, suffix: String = "", hasEllipsis: Boolean = true) =
        TextFit.fit(text, SIZE, maxWidth, width, suffix, hasEllipsis)

    @Test
    fun `a text that fits does not change`() {
        val fitted = fit("Points won", maxWidth = 1000.0)

        assertEquals(TextFit.Fitted("Points won", SIZE, SIZE), fitted)
        assertFalse(fitted.shrunk)
    }

    @Test
    fun `a text that is a little too wide gets a smaller font`() {
        // 10 code points: 200 at the size 40. The box gives 95 % of 190.
        val fitted = fit("Points won", maxWidth = 190.0)

        assertEquals("Points won", fitted.text)
        assertTrue(fitted.shrunk && !fitted.cut, fitted.toString())
        assertTrue(fitted.size >= SIZE * TextFit.MIN_SCALE, fitted.toString())
        assertTrue(width(fitted.text, fitted.size) <= 190.0 * (1 - TextFit.MARGIN))
    }

    @Test
    fun `the fit keeps a margin in the box`() {
        assertTrue(fit("Points won", maxWidth = 200.0).shrunk)
        assertFalse(fit("Points won", maxWidth = 200.0 / (1 - TextFit.MARGIN) + 0.01).shrunk)
    }

    @Test
    fun `a text that is too wide at the smallest font is cut with an ellipsis`() {
        val fitted = fit("Points won on return", maxWidth = 200.0)

        assertTrue(fitted.cut, fitted.toString())
        assertTrue(fitted.text.endsWith(TextFit.ELLIPSIS), fitted.text)
        assertEquals(SIZE * TextFit.MIN_SCALE, fitted.size)
        assertTrue(width(fitted.text, fitted.size) <= 200.0 * (1 - TextFit.MARGIN))
        assertTrue("Points won on return".startsWith(fitted.text.removeSuffix(TextFit.ELLIPSIS)))
    }

    @Test
    fun `a cut does not break a letter with a combining accent or an emoji`() {
        val texts = listOf(
            "José García Martínez",
            "Ana 🎾🎾🎾 Bea",
            "Team 👨‍👩‍👧 wins",
        )
        for (text in texts) {
            // Each box width gives a different cut position.
            for (maxWidth in 20..400) {
                val fitted = fit(text, maxWidth.toDouble())
                val shown = fitted.text.removeSuffix(TextFit.ELLIPSIS)
                assertTrue(text.startsWith(shown), "$maxWidth: ${fitted.text}")
                if (shown.length < text.length) {
                    val next = text[shown.length]
                    assertFalse(
                        Character.isLowSurrogate(next) || next == '‍' || Character.getType(next) == Character.NON_SPACING_MARK.toInt(),
                        "$maxWidth: \"${fitted.text}\" breaks a character",
                    )
                }
            }
        }
    }

    @Test
    fun `a font without the ellipsis letter gets three points`() {
        val fitted = fit("Points won on return", maxWidth = 200.0, hasEllipsis = false)

        assertTrue(fitted.text.endsWith(TextFit.ASCII_ELLIPSIS), fitted.text)
        assertFalse(TextFit.ELLIPSIS in fitted.text)
    }

    @Test
    fun `an empty text does not change`() {
        assertEquals(TextFit.Fitted("", SIZE, SIZE), fit("", maxWidth = 0.0))
    }

    @Test
    fun `a box without space for the ellipsis shows no text`() {
        val fitted = fit("Points won", maxWidth = 10.0)

        assertEquals("", fitted.text)
        assertTrue(fitted.cut)
    }

    @Test
    fun `the suffix is never cut`() {
        val fitted = fit("Maximilian Alexander", maxWidth = 250.0, suffix = " +12")

        assertTrue(fitted.text.endsWith("${TextFit.ELLIPSIS} +12"), fitted.text)
    }

    @Test
    fun `a cut by length keeps whole characters`() {
        val tennis = "🎾"

        assertEquals("ab", TextFit.takeWhole("ab$tennis", 3))
        assertEquals("ab$tennis", TextFit.takeWhole("ab$tennis", 4))
        assertEquals("Jos", TextFit.takeWhole("José", 4))
        assertEquals("abc${TextFit.ELLIPSIS}", TextFit.cutToChars("abc${tennis}def", 5))
        assertEquals("short", TextFit.cutToChars("short", 5))
    }

    @Test
    fun `the fit test gets only the changed fits`() {
        val (_, fits) = TextFit.changedFits {
            fit("Points won", maxWidth = 1000.0)
            fit("Points won", maxWidth = 190.0)
        }

        assertEquals(listOf("Points won"), fits.map { it.text })
    }

    private companion object {
        const val SIZE = 40.0
    }
}
