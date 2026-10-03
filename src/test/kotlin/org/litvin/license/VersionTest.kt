package org.litvin.license

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTest {
    @Test
    fun `a full version gives its three numbers`() {
        assertEquals(Version(1, 2, 3), Version.parse("1.2.3"))
    }

    @Test
    fun `1_3-SNAPSHOT is 1_3_0`() {
        assertEquals(Version(1, 3, 0), Version.parse("1.3-SNAPSHOT"))
    }

    @Test
    fun `1_2_1-beta is 1_2_1`() {
        assertEquals(Version(1, 2, 1), Version.parse("1.2.1-beta"))
    }

    @Test
    fun `a missing number is 0`() {
        assertEquals(Version(2, 0, 0), Version.parse("2"))
        assertEquals(Version(2, 0, 0), Version.parse("2."))
        assertEquals(Version(1, 2, 3), Version.parse("1.2.3.4"))
    }

    @Test
    fun `a version that does not start with a number is 0_0_0`() {
        assertEquals(Version.ZERO, Version.parse("development"))
        assertEquals(Version.ZERO, Version.parse("v1.2.3"))
        assertEquals(Version.ZERO, Version.parse(" 1.2.3"))
        assertEquals(Version.ZERO, Version.parse(""))
        assertFalse(Version.startsWithNumber("v1.2.3"))
        assertFalse(Version.startsWithNumber(""))
        assertTrue(Version.startsWithNumber("1.2.3"))
    }

    @Test
    fun `versions compare as numbers, not as text`() {
        assertTrue(Version.parse("1.10.0") > Version.parse("1.9.0"))
        assertTrue(Version.parse("2.0.0") > Version.parse("1.99.99"))
        assertTrue(Version.parse("1.2.10") > Version.parse("1.2.9"))
        assertEquals(0, Version.parse("1.3-SNAPSHOT").compareTo(Version.parse("1.3.0")))
    }

    @Test
    fun `a number that is too large is the largest number`() {
        assertEquals(Version(Int.MAX_VALUE, 1, 0), Version.parse("99999999999.1"))
    }
}
