package io.github.numynox.nexus.noctua.ui

import io.github.numynox.nexus.noctua.ui.feed.owl.BranchTipX
import io.github.numynox.nexus.noctua.ui.feed.owl.SkyLightX
import io.github.numynox.nexus.noctua.ui.feed.owl.SkyLightY
import io.github.numynox.nexus.noctua.ui.feed.owl.SkyStars
import io.github.numynox.nexus.noctua.ui.feed.owl.branchWidth
import io.github.numynox.nexus.noctua.ui.feed.owl.isNight
import io.github.numynox.nexus.noctua.ui.feed.owl.moonLit
import io.github.numynox.nexus.noctua.ui.feed.owl.moonLitPath
import io.github.numynox.nexus.noctua.ui.feed.owl.moonPhase
import io.github.numynox.nexus.noctua.ui.feed.owl.moonPhaseName
import io.github.numynox.nexus.noctua.ui.feed.owl.sunTimes
import java.time.Instant
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightSkyTest {
    @Test
    fun moonPhaseMatchesKnownMoons() {
        // An average lunation, not an ephemeris: within a day of the real moon, plenty for a drawing.
        // Full moon 23 April 2024, 23:49 UTC; new moon 8 April 2024, 18:21 UTC.
        val full = moonPhase(Instant.parse("2024-04-23T23:49:00Z"))
        assertEquals(0.5, full, 0.02)
        assertEquals("Full moon", moonPhaseName(full))
        assertTrue(moonLit(full) > 0.99)
        val new = moonPhase(Instant.parse("2024-04-08T18:21:00Z"))
        assertTrue("new moon at $new", new < 0.02 || new > 0.98)
        assertEquals("New moon", moonPhaseName(new))
        // First quarter 15 April 2024, 19:13 UTC: half lit, waxing.
        val quarter = moonPhase(Instant.parse("2024-04-15T19:13:00Z"))
        assertEquals(0.25, quarter, 0.03)
        assertEquals("First quarter", moonPhaseName(quarter))
        assertEquals(0.5, moonLit(quarter), 0.1)
    }

    @Test
    fun moonPhaseIsInRangeBeforeTheReferenceToo() {
        val phase = moonPhase(Instant.parse("1969-07-20T20:17:00Z"))
        assertTrue(phase in 0.0..1.0)
    }

    @Test
    fun litPathFollowsThePhase() {
        // Waxing crescent: lit edge on the right (sweep 1), terminator bulging right (sweep 0).
        assertEquals("M 282.0 54.0 A 24.0 24.0 0 0 1 282.0 102.0 A 16.97 24.0 0 0 0 282.0 54.0 Z", moonLitPath(0.125, 282f, 78f, 24f))
        // Waning gibbous: lit edge on the left, terminator bulging right.
        assertTrue(moonLitPath(0.625, 282f, 78f, 24f).contains("0 0 0 282.0 102.0 A 16.97 24.0 0 0 0"))
        // Full: the terminator is the far edge, a whole disc.
        assertTrue(moonLitPath(0.5, 282f, 78f, 24f).contains("A 24.00 24.0 0 0 0 282.0 54.0"))
    }

    /** Frankfurt's published times, within the formula's few minutes; the web's nightSky.test.ts. */
    private fun near(actual: Instant, expected: String) =
        assertTrue("$actual vs $expected", kotlin.math.abs(actual.toEpochMilli() - Instant.parse(expected).toEpochMilli()) <= 3 * 60_000)

    @Test
    fun frankfurtsSunriseAndSunset() {
        val (juneRise, juneSet) = sunTimes(Instant.parse("2026-06-21T12:00:00Z"))!!
        near(juneRise, "2026-06-21T03:14:00Z")
        near(juneSet, "2026-06-21T19:39:00Z")
        val (decRise, decSet) = sunTimes(Instant.parse("2026-12-21T12:00:00Z"))!!
        near(decRise, "2026-12-21T07:23:00Z")
        near(decSet, "2026-12-21T15:23:00Z")
    }

    @Test
    fun theOwlWakesAtSunsetAndSleepsAtSunrise() {
        assertFalse(isNight(Instant.parse("2026-06-21T19:00:00Z")))
        assertTrue(isNight(Instant.parse("2026-06-21T20:00:00Z")))
        assertTrue(isNight(Instant.parse("2026-12-21T16:00:00Z")))
        assertTrue(isNight(Instant.parse("2026-12-21T07:00:00Z")))
        assertFalse(isNight(Instant.parse("2026-12-21T09:00:00Z")))
    }

    @Test
    fun starsKeepClearOfTheMoonAndTheOwl() {
        SkyStars.forEach { star ->
            assertTrue("$star is off the sky", star.x in 0f..360f && star.y < 250f)
            assertTrue("$star is on the moon", hypot(star.x - SkyLightX, star.y - SkyLightY) > 46f)
            assertFalse("$star is on the owl", star.x in 110f..250f && star.y > 190f)
        }
    }

    @Test
    fun theBranchTapers() {
        assertTrue(branchWidth(-220f) > branchWidth(180f))
        assertTrue(branchWidth(180f) > branchWidth(BranchTipX))
        assertTrue(branchWidth(BranchTipX) > 0f)
    }
}
