package org.litvin.ui.tabs.adjustments

import org.junit.jupiter.api.AfterEach
import org.litvin.i18n.DisplayFormats
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-11 E1-T6: the values of the Transform tab with a decimal comma (`es-ES`). */
class TransformValueTextTest {
    private val saved = DisplayFormats.formatLocale

    @AfterEach
    fun restore() {
        DisplayFormats.formatLocale = saved
    }

    @Test
    fun `zero shows without a sign with a decimal comma`() {
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-ES")
        listOf(0.0f, -0.0f, 0.04f, -0.04f).forEach { value ->
            assertEquals("0,0°", signedDegrees(value), "$value")
        }
        assertEquals("+2,3°", signedDegrees(2.3f))
        assertEquals("−2,3°", signedDegrees(-2.3f))
        assertEquals("0,0", TransformControl.ROTATION.toText(0))
        assertEquals("+12,5", TransformControl.ROTATION.toText(25))
        assertEquals("-0,2", TransformControl.FINE_ROTATION.toText(-2))
        assertEquals("0,0", TransformControl.FINE_ROTATION.toText(0))
    }

    @Test
    fun `a typed value can use a comma or a point`() {
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-ES")
        assertEquals(3, TransformControl.ROTATION.fromText("1,5"))
        assertEquals(3, TransformControl.ROTATION.fromText("1.5"))
        assertEquals(15, TransformControl.FINE_ROTATION.fromText("+1,5°"))
        assertNull(TransformControl.ROTATION.fromText("abc"))
    }

    @Test
    fun `the zoom ends follow the percent form of the regional format`() {
        DisplayFormats.formatLocale = Locale.forLanguageTag("es-ES")
        assertEquals("100 %", TransformControl.ZOOM.low)
        DisplayFormats.formatLocale = Locale.US
        assertEquals("250%", TransformControl.ZOOM.high)
    }
}
