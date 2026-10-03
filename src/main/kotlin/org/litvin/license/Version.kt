package org.litvin.license

/**
 * A version as `MAJOR.MINOR.PATCH` numbers. The text after the numbers is ignored, and a missing
 * number is 0. Thus, `1.3-SNAPSHOT` is `1.3.0`, and a version that does not start with a number is
 * `0.0.0`.
 */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        val ZERO = Version(0, 0, 0)

        fun parse(text: String): Version {
            val numbers = mutableListOf<Int>()
            var index = 0
            while (numbers.size < 3) {
                val start = index
                while (index < text.length && text[index].isAsciiDigit()) index++
                if (index == start) break
                // A number that is too large for an Int is the largest Int. It still compares correctly with normal versions.
                numbers += text.substring(start, index).toIntOrNull() ?: Int.MAX_VALUE
                if (index >= text.length || text[index] != '.') break
                index++
            }
            return Version(numbers.getOrElse(0) { 0 }, numbers.getOrElse(1) { 0 }, numbers.getOrElse(2) { 0 })
        }

        fun startsWithNumber(text: String): Boolean = text.firstOrNull()?.isAsciiDigit() == true

        private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
    }
}
