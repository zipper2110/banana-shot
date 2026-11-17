package org.litvin.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-49: the times and the format of each file of a match of several files. */
class SourceFileProbeTest {
    private val output = """
        streams.stream.0.codec_name="hevc"
        streams.stream.0.codec_type="video"
        streams.stream.0.width=3840
        streams.stream.0.height=2160
        streams.stream.0.pix_fmt="yuv420p10le"
        streams.stream.0.r_frame_rate="60/1"
        streams.stream.1.codec_name="aac"
        streams.stream.1.codec_type="audio"
        streams.stream.1.sample_rate="48000"
        streams.stream.1.channels=2
        streams.stream.1.r_frame_rate="0/0"
        streams.stream.2.codec_type="data"
        streams.stream.2.r_frame_rate="0/0"
        format.start_time="0.045000"
        format.duration="21.021016"
    """.trimIndent()

    @Test
    fun `the probe reads the start time, the length and the video and audio format`() {
        val info = FfprobeSourceFileProbe.parse("C:\\v\\DJI_0001.MP4", output)!!

        assertEquals(SourceFile("C:\\v\\DJI_0001.MP4", 45, 21_021), info.file)
        assertEquals(listOf("video hevc 3840x2160 60/1 yuv420p10le", "audio aac 48000 Hz 2 ch"), info.format)
    }

    @Test
    fun `a file without a duration cannot join`() {
        assertNull(FfprobeSourceFileProbe.parse("x.mp4", "format.start_time=\"0.000000\""))
    }

    @Test
    fun `only the files with a format different from the first file are different`() {
        val first = SourceFileInfo(SourceFile("a.mp4"), listOf("video h264 1920x1080 30/1 yuv420p"))
        val same = SourceFileInfo(SourceFile("b.mp4"), listOf("video h264 1920x1080 30/1 yuv420p"))
        val other = SourceFileInfo(SourceFile("c.mp4"), listOf("video hevc 3840x2160 60/1 yuv420p"))

        assertEquals(emptyList(), SourceFormats.different(listOf(first, same)))
        assertEquals(listOf(other), SourceFormats.different(listOf(first, same, other)))
        assertEquals(
            "a.mp4: video h264 1920x1080 30/1 yuv420p\nc.mp4: video hevc 3840x2160 60/1 yuv420p",
            SourceFormats.report(listOf(first, other)),
        )
    }
}
