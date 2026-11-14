package io.github.numynox.nexus.abeona.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTest {
    @Test
    fun hoursNeverReadAsAClockTime() {
        assertEquals("16 h" to "54 min", countdown(16 * 60 + 54))
        assertEquals("2 h" to "", countdown(120))
        assertEquals("23" to "min", countdown(23))
        assertEquals("now" to "", countdown(0))
        assertNull(countdown(null))
    }
}
