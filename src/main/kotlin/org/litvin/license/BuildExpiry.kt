package org.litvin.license

import java.time.LocalDate

/** The build expiry: the build date plus 6 calendar months. No code stores the expiry date. */
object BuildExpiry {
    const val MONTHS: Long = 6

    val buildDate: LocalDate
        get() = LocalDate.parse(BuildInfo.BUILD_DATE)

    /** Gives the last day of the target month when the day does not exist in it. */
    fun expiryDate(buildDate: LocalDate = this.buildDate): LocalDate = buildDate.plusMonths(MONTHS)
}
