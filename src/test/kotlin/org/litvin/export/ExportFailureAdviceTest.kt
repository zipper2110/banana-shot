package org.litvin.export

import org.junit.jupiter.api.Test
import org.litvin.RenderJob
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun aBlockedFfmpegNamesWindowsSecurity() {
        // The text after the code is in the language of Windows.
        val cause = IOException(
            "Cannot run program \"C:\\BananaShot\\ffmpeg.exe\": CreateProcess error=4551, " +
                "An Application Control policy has blocked this file",
        )

        val reason = ExportFailureAdvice.startFailure(cause)

        assertTrue(reason.startsWith(ExportFailureAdvice.FFMPEG_BLOCKED), reason)
        assertTrue("Smart App Control" in reason, reason)
        assertTrue(reason.endsWith("Windows error 4551."), reason)
    }

    @Test
    fun aBlockedFfmpegGetsNoEncoderAdvice() {
        val hardware = ExportEncoder.entries.first { it.hardware }
        val blocked = job(hardware.jobLabel).apply {
            failureReason = ExportFailureAdvice.startFailure(IOException("CreateProcess error=4551, blocked"))
        }

        assertNull(ExportFailureAdvice.of(blocked))
    }

    @Test
    fun otherStartFailuresKeepTheDiagnosticsText() {
        val reason = ExportFailureAdvice.startFailure(IOException("Cannot run program \"ffmpeg\": CreateProcess error=2, not found"))

        assertTrue(reason.startsWith("Failed to start FFmpeg: IOException: "), reason)
        assertTrue(reason.endsWith("distribution diagnostics for details."), reason)
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
