package org.litvin.license

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildExpiryTest {
    @Test
    fun `expiry is the build date plus 6 calendar months`() {
        assertEquals(LocalDate.of(2027, 4, 2), BuildExpiry.expiryDate(LocalDate.of(2026, 10, 2)))
    }

    @Test
    fun `31 August plus 6 months is 28 February`() {
        assertEquals(LocalDate.of(2027, 2, 28), BuildExpiry.expiryDate(LocalDate.of(2026, 8, 31)))
    }

    @Test
    fun `31 August plus 6 months is 29 February in a leap year`() {
        assertEquals(LocalDate.of(2028, 2, 29), BuildExpiry.expiryDate(LocalDate.of(2027, 8, 31)))
    }

    @Test
    fun `default build date is BUILD_DATE`() {
        assertEquals(LocalDate.parse(BuildInfo.BUILD_DATE).plusMonths(6), BuildExpiry.expiryDate())
    }
}
