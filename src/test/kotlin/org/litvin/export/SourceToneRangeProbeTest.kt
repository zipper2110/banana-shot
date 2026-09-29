package org.litvin.export

import org.junit.jupiter.api.Test
import org.litvin.ToneRange
import kotlin.test.assertEquals

class SourceToneRangeProbeTest {
    @Test
    fun `a pc range or a yuvj format is full range and anything else is limited`() {
        assertEquals(ToneRange.LIMITED, SourceToneRangeProbe.parse("pix_fmt=yuv420p\ncolor_range=tv\n"))
        assertEquals(ToneRange.LIMITED, SourceToneRangeProbe.parse("pix_fmt=yuv420p10le\r\ncolor_range=unknown\r\n"))
        assertEquals(ToneRange.FULL, SourceToneRangeProbe.parse("pix_fmt=yuv420p\ncolor_range=pc\n"))
        assertEquals(ToneRange.FULL, SourceToneRangeProbe.parse("pix_fmt=yuvj420p\ncolor_range=unknown\n"))
        assertEquals(ToneRange.LIMITED, SourceToneRangeProbe.parse(""))
    }
}
