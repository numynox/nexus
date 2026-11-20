package io.github.numynox.nexus.abeona.ui.live

import io.github.numynox.nexus.abeona.ui.common.TripFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/** The live card's one countdown, on the step to do next (abeona-design.md §8). */
class CountdownTest {
    @Test
    fun hoursNeverReadAsAClockTime() {
        assertEquals("in 12 min", TripFormat.relative(12))
        assertEquals("in 1 h 25 min", TripFormat.relative(85))
        assertEquals("in 16 h 54 min", TripFormat.relative(16 * 60 + 54))
        assertEquals("in 2 h", TripFormat.relative(120))
        assertEquals("now", TripFormat.relative(0))
        assertEquals("now", TripFormat.relative(-3))
    }
}
