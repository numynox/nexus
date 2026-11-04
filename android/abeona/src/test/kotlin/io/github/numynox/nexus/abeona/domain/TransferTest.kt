package io.github.numynox.nexus.abeona.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android-only helpers around the shared rules. The shared rules themselves
 * (delays, platforms, transfers, the binding) are tested from the fixture
 * table both clients run: TransferFixturesTest.
 */
class TransferTest {
    @Test
    fun platformChangeNeedsBothPlatformsKnown() {
        assertTrue(platformChanged("Gleis 7", "8"))
        assertFalse(platformChanged("Gleis 7", "7"))
        assertFalse(platformChanged("7", null))
        assertFalse(platformChanged(null, "7"))
    }
}
