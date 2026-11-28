package org.litvin.autodetect

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectDeclinedTest {
    @Test
    fun `the record keeps each declined clip with its detection data, and Undo removes it`() {
        val dir = Files.createTempDirectory("declined").toFile()
        try {
            assertTrue(DetectDeclined.read(dir).isEmpty())
            DetectDeclined.add(dir, DeclinedPoint("a", 1000, 9000, "RALLY", 72, 1, 1.5, 8.5, 0.61, "result-1.json", "import-1.json"))
            DetectDeclined.add(dir, DeclinedPoint("b", 20_000, 25_000, "SHORT"))
            DetectDeclined.add(dir, DeclinedPoint("a", 1000, 9000, "RALLY"))

            val points = DetectDeclined.read(dir)
            assertEquals(listOf("b", "a"), points.map { it.pointId })
            assertTrue(points.all { it.declined.isNotEmpty() })

            DetectDeclined.remove(dir, "b")
            assertEquals(listOf("a"), DetectDeclined.read(dir).map { it.pointId })
        } finally {
            dir.deleteRecursively()
        }
    }
}
