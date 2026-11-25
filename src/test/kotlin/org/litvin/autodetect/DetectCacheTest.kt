package org.litvin.autodetect

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** The cache keys of the detection (chain-inventory G12): the video identity, the work name, the overlay of a result. */
class DetectCacheTest {
    private fun file(size: Int): File {
        val f = Files.createTempFile("video", ".bin").toFile().apply { deleteOnExit() }
        f.writeBytes(ByteArray(size) { i -> ((i * 7 + 3) % 256).toByte() })
        return f
    }

    @Test
    fun `the video identity is the value of detect_py`() {
        // The values of detect.video_id for the same bytes (Python, 2026-11-26).
        assertEquals("c1330d6024ad321e3cb274a568501a83beafd4fd", VideoIdentity.of(file(1000)))
        assertEquals("507c983c7f15660142d0b6e1a6f239d4eafb0a66", VideoIdentity.of(file(5 shl 19)))
    }

    @Test
    fun `the work name has the project name and the short video identity`() {
        val project = Files.createTempDirectory("Dima Ilya").toFile()
        val a = file(5 shl 19)
        val b = file(5 shl 19).apply { writeBytes(readBytes().also { it[it.size - 1] = 0 }) }
        assertEquals("${project.name}-507c983c", DetectRunner.workName(project, a))
        assertNotEquals(DetectRunner.workName(project, a), DetectRunner.workName(project, b))
    }

    @Test
    fun `the overlay belongs to the result of the newest import`() {
        val project = Files.createTempDirectory("project").toFile()
        val folder = File(project, DetectImport.FOLDER).apply { mkdirs() }
        File(folder, "result-20261120-100000.json").writeText("{}")
        File(folder, "overlay-20261120-100000.json").writeText("{}")
        File(folder, "result-20261121-100000.json").writeText("{}")   // this run wrote no overlay
        assertNull(DetectImport.overlayOfResult(project))
        File(folder, "import-20261121-110000.json").writeText(
            DetectJson.mapper.writeValueAsString(ImportRecord("20261121-110000", "result-20261120-100000.json", DetectSettings(), emptyList())),
        )
        assertEquals("overlay-20261120-100000.json", DetectImport.overlayOfResult(project)?.name)
    }
}
