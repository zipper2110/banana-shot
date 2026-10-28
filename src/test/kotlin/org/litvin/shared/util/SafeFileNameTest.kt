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
        assertEquals("x", SafeFileName.of("???", "x"))
        assertEquals("x", SafeFileName.of("   ", "x"))
        assertEquals("x", SafeFileName.of("?* <>", "x"))
        assertEquals("x", SafeFileName.of("\uD83C", "x"))
        assertEquals("a__", SafeFileName.of("a??", "x"))
    }

    /** B-11 E3-T23: a name in another script gives a folder name with the same letters. */
    @Test
    fun keepsTheLettersOfOtherScripts() {
        listOf("Финал Кубка", "Τελικός", "决赛 第二盘", "決勝戦", "Ünal & Çelik").forEach { name ->
            assertEquals(name, SafeFileName.of(name, "x"))
        }
        assertEquals("Финал 3_2", SafeFileName.of("Финал 3:2", "x"))
    }

    @Test
    fun keepsAnEmojiAndReplacesHalfOfAnEmoji() {
        assertEquals("Final 🎾", SafeFileName.of("Final 🎾", "x"))
        assertEquals("Final _", SafeFileName.of("Final \uD83C", "x"))
        assertEquals("_ Final", SafeFileName.of("\uDFBE Final", "x"))
    }

    @Test
    fun givesOneNameForTheTwoFormsOfALetterWithAnAccent() {
        assertEquals("Café", SafeFileName.of("Café", "x"))
    }
}
