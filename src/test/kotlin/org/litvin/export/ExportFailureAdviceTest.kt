package org.litvin.export

import org.junit.jupiter.api.Test
import org.litvin.RenderJob
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportFailureAdviceTest {
    @Test
    fun aHardwareEncoderGetsTheSoftwareAdvice() {
        ExportEncoder.entries.filter { it.hardware }.forEach { encoder ->
            assertEquals(ExportFailureAdvice.HARDWARE_ENCODER, ExportFailureAdvice.of(job(encoder.jobLabel)), encoder.name)
        }
    }

    @Test
    fun theSoftwareEncoderGetsNoAdvice() {
        assertNull(ExportFailureAdvice.of(job(ExportEncoder.SOFTWARE.jobLabel)))
    }

    private fun job(encoderLabel: String) = RenderJob(
        sourcePath = "match.mp4",
        presetId = "1080p",
        outWidth = 1920,
        outHeight = 1080,
        encoderLabel = encoderLabel,
        idleTrim = false,
        outputPath = "out.mp4",
    )
}
