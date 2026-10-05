package org.litvin.export

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceAudioProbeTest {
    @Test
    fun `an audio line means that the source has audio`() {
        assertTrue(SourceAudioProbe.parse("audio\n"))
        assertTrue(SourceAudioProbe.parse("audio\r\naudio\r\n"))
        assertFalse(SourceAudioProbe.parse(""))
        assertFalse(SourceAudioProbe.parse("\n"))
    }
}
