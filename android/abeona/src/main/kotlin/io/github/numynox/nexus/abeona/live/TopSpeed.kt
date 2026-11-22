package io.github.numynox.nexus.abeona.live

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The highest speed the train's Wi-Fi reports on each ride, and when it is
 * worth writing to the ride's leg (abeona_legs.top_speed_kmh): a whole km/h
 * from 1 to 450 (anything else is a glitch), at least 3 km/h above what the
 * leg already holds, and at most once a minute per ride. One per companion
 * run; the year in review and the postcard read the stored value.
 */
class TopSpeedRecorder {
    private val best = mutableMapOf<Int, Int>()
    private val stored = mutableMapOf<Int, Int>()
    private val writtenAt = mutableMapOf<Int, Instant>()

    /**
     * [kmh] seen now on board ride [idx], whose leg holds [storedKmh]: the
     * value to write, or null when there is nothing worth writing yet.
     */
    fun observe(idx: Int, kmh: Double?, storedKmh: Int?, now: Instant): Int? {
        val value = kmh?.takeIf { it.isFinite() }?.roundToInt()?.takeIf { it in 1..MAX_KMH } ?: return null
        val top = maxOf(best[idx] ?: 0, value).also { best[idx] = it }
        val have = maxOf(stored[idx] ?: 0, storedKmh ?: 0)
        if (top < have + STEP_KMH) return null
        writtenAt[idx]?.let { if (Duration.between(it, now) < EVERY) return null }
        writtenAt[idx] = now
        stored[idx] = top
        return top
    }

    companion object {
        /** The column's check: no train in Europe runs faster. */
        const val MAX_KMH = 450
        /** Less than this above the stored value is not worth a write. */
        const val STEP_KMH = 3
        val EVERY: Duration = Duration.ofMinutes(1)
    }
}
