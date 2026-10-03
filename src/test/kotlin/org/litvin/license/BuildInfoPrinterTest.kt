package org.litvin.license

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildInfoPrinterTest {
    @Test
    fun `prints the lines that Validate-AppImage reads`() {
        val output = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(output, true, Charsets.UTF_8))
        try {
            BuildInfoPrinter.main(emptyArray())
        } finally {
            System.setOut(original)
        }

        val values = output.toString(Charsets.UTF_8).lines()
            .filter { it.isNotBlank() }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

        assertEquals(setOf("buildDate", "expiryDate", "version"), values.keys)
        assertEquals(BuildInfo.BUILD_DATE, values["buildDate"])
        assertEquals(LocalDate.parse(BuildInfo.BUILD_DATE).plusMonths(6).toString(), values["expiryDate"])
        assertEquals(BuildInfo.VERSION, values["version"])
    }
}
