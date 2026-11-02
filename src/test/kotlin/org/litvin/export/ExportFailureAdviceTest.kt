package org.litvin.export

import org.junit.jupiter.api.Test
import org.litvin.RenderJob
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Test
    fun anFfmpegThatMacosStopsAtOnceNamesMacosSecurity() {
        // macOS stops a program with a code signature that is not valid with SIGKILL, before it writes text.
        val reason = ExportFailureAdvice.exitFailure(137, "", osName = "Mac OS X")

        assertNotNull(reason)
        assertTrue(reason.startsWith(ExportFailureAdvice.FFMPEG_BLOCKED_MAC), reason)
        assertTrue("code signature" in reason, reason)
        val hardware = ExportEncoder.entries.first { it.hardware }
        assertNull(ExportFailureAdvice.of(job(hardware.jobLabel).apply { failureReason = reason }))
    }

    @Test
    fun otherFfmpegExitsHaveNoSpecialText() {
        // The text shows that ffmpeg ran, for example when the system stopped it later for lack of memory.
        assertNull(ExportFailureAdvice.exitFailure(137, "frame=120 fps=30", osName = "Mac OS X"))
        assertNull(ExportFailureAdvice.exitFailure(1, "", osName = "Mac OS X"))
        assertNull(ExportFailureAdvice.exitFailure(137, "", osName = "Windows 11"))
    }

    @Test
    fun anFfmpegThatMacosRefusesToLoadNamesMacosSecurity() {
        val cause = IOException("Cannot run program \"ffmpeg\": missing code signature in '/Applications/BananaShot.app'")

        val reason = ExportFailureAdvice.startFailure(cause, osName = "Mac OS X")

        assertTrue(reason.startsWith(ExportFailureAdvice.FFMPEG_BLOCKED_MAC), reason)
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
