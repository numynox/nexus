package io.github.numynox.nexus.abeona.ui.scene

import io.github.numynox.nexus.abeona.domain.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The header's bar moves with the frames, not the minute: from one frame to
 * the next (16 ms) the train and the walker move by a sliver, never a jump.
 * The fixtures (SceneModelFixturesTest) pin where they are; this pins how.
 */
class SceneBarTest {
    private val t0 = 1_800_000_000_000L
    private val minute = 60_000L
    private val ride = BarRide(t0, t0 + 32 * minute, listOf(t0 + 12 * minute))
    private val next = BarRide(t0 + 40 * minute, t0 + 72 * minute, emptyList())

    /** The largest step between two frames, and how far all of them went, from [from] for [frames] frames. */
    private fun frames(from: Long, frames: Int, at: (Long) -> Double): Pair<Double, Double> {
        val values = (0..frames).map { at(from + it * 16L) }
        return values.zipWithNext { a, b -> b - a }.max() to values.last() - values.first()
    }

    @Test
    fun theTrainMovesBySliversAcrossTheMinute() {
        val j = BarJourney(listOf(ride, next), 0, BarState.OnBoard, null)
        // Two seconds of frames either side of a full minute: no jump at it.
        val (step, total) = frames(t0 + 10 * minute - 1_000, 125) { SceneModel.journeyBar(j, it).fraction }
        val perFrame = 16.0 / (32 * minute)
        assertTrue("step $step", step <= perFrame * 1.01)
        assertEquals(2_000.0 / (32 * minute), total, 1e-9)
    }

    @Test
    fun theWalkerMovesBySliversThroughTheChange() {
        val j = BarJourney(listOf(ride, next), 1, BarState.Changing, Confidence.TIGHT)
        val (step, total) = frames(t0 + 34 * minute, 125) { SceneModel.journeyBar(j, it).fraction }
        assertTrue("step $step", step <= 16.0 / (8 * minute) * 1.01)
        assertEquals(2_000.0 / (8 * minute), total, 1e-9)
        // On the change, in focus, in its confidence's colour.
        val bar = SceneModel.journeyBar(j, t0 + 34 * minute)
        assertEquals(1, bar.focus)
        assertEquals(BarTone.Watch, bar.tone)
    }
}
