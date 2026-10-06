package org.litvin.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SafeFileNameTest {
    @Test
    fun replacesTheCharactersThatWindowsDoesNotPermit() {
        assertEquals("Final 3_2", SafeFileName.of("Final 3:2", "x"))
        assertEquals("Who won_", SafeFileName.of("Who won?", "x"))
        assertEquals("a_b_c_d_e_f_g_h_i_j", SafeFileName.of("a<b>c\"d/e\\f|g*h\ti\nj", "x"))
        assertEquals("Club final (2)", SafeFileName.of("Club final (2)", "x"))
    }

    @Test
    fun removesThePeriodsAndSpacesAtTheEnd() {
        assertEquals("Final", SafeFileName.of("Final.", "x"))
        assertEquals("Final", SafeFileName.of("  Final . . ", "x"))
        assertEquals("match.2026", SafeFileName.of("match.2026", "x"))
    }

    @Test
    fun changesTheReservedNames() {
        assertEquals("con_", SafeFileName.of("con", "x"))
        assertEquals("COM1_", SafeFileName.of("COM1", "x"))
        assertEquals("NUL_.txt", SafeFileName.of("NUL.txt", "x"))
        assertEquals("AUX _.final", SafeFileName.of("AUX .final", "x"))
        assertEquals("CONSOLE", SafeFileName.of("CONSOLE", "x"))
        assertEquals("COM10", SafeFileName.of("COM10", "x"))
    }

    @Test
    fun usesTheFallbackWhenNoCharactersStay() {
        assertEquals("x", SafeFileName.of("", "x"))
        assertEquals("x", SafeFileName.of(" ... ", "x"))
        assertEquals("___", SafeFileName.of("???", "x"))
    }
}
