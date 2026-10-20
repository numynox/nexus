package io.github.numynox.nexus.vibilia.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** The same points supabase/tests/drive_tracking.test.sql pins for the original. */
class DriveCurveTest {
    @Test
    fun `the curve matches vibilia_assumed_drive_km`() {
        listOf(0.0 to 0.0, 1.0 / 6 to 5.0, 0.25 to 10.0, 1.0 / 3 to 16.667, 1.0 to 75.0, 5.0 / 3 to 166.667, 2.0 to 200.0)
            .forEach { (hours, km) -> assertEquals("$hours h", km, DriveCurve.km(hours), 1e-3) }
    }

    @Test
    fun `a last drive is scaled by the learned factor`() {
        val start = Instant.parse("2026-01-14T10:00:00Z")
        val drive = Pair(start, start.plusSeconds(24 * 60))
        // 24 min averages 52.5 km/h: 21 km, × 0.8
        assertEquals(16.8, LastDrive.of(null, drive, DriveModel(speedFactor = 0.8))!!.estimatedKm!!, 1e-9)
        // A database before the curve: the flat km per hour, as before.
        assertEquals(24.0, LastDrive.of(null, drive, DriveModel(kmPerHour = 60.0))!!.estimatedKm!!, 1e-9)
    }
}
