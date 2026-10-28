package org.litvin.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** B-11 E7-T4: the video draws each text in the composed form (NFC). */
class DrawnTextTest {
    @Test
    fun `a letter and a combining mark become one letter`() {
        assertEquals("ё", DrawnText.composed("ё"))
        assertEquals("Peña", DrawnText.composed("Peña"))
        assertEquals("Ştefan", DrawnText.composed("Ştefan"))
        assertEquals("が", DrawnText.composed("が"))
    }

    @Test
    fun `a composed text stays the same object`() {
        val text = "Аксёнов"
        assertSame(text, DrawnText.composed(text))
    }

    @Test
    fun `an ASS text is composed and has no override block or escape sequence`() {
        assertEquals("Ён \\{b1\\} \\\\N", DrawnText.ass("Ён {b1} \\N"))
    }
}
