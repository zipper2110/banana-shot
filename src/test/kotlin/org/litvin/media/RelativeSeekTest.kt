package org.litvin.media

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeSeekTest {
    @Test
    fun `back seek while playing goes back 500 ms more`() {
        assertEquals(-1_500L, relativeSeekDeltaMs(-1_000L, playing = true))
        assertEquals(-5_500L, relativeSeekDeltaMs(-5_000L, playing = true))
    }

    @Test
    fun `back seek while paused and forward seeks stay the same`() {
        assertEquals(-1_000L, relativeSeekDeltaMs(-1_000L, playing = false))
        assertEquals(1_000L, relativeSeekDeltaMs(1_000L, playing = true))
        assertEquals(5_000L, relativeSeekDeltaMs(5_000L, playing = false))
    }
}
