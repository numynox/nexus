package io.github.numynox.nexus.abeona.ui.common

import androidx.compose.runtime.Composable
import io.github.numynox.nexus.abeona.data.Trip
import java.time.Duration
import java.time.Instant

/** True from a day before the first departure until the trip has arrived: when the travel day shows. */
fun isLiveDay(trip: Trip, now: Instant): Boolean =
    !trip.departureAt.isAfter(now.plus(Duration.ofHours(24))) &&
        trip.expectedArrival.plus(Duration.ofMinutes(15)).isAfter(now) &&
        trip.legs.any { !it.isWalk }

/**
 * The current time, moving every half minute, so countdowns on screen move
 * with it. The wait runs on wall-clock time, off the composition's frame
 * clock, so a test that advances that clock is never kept busy by it.
 */
@Composable
fun rememberTicker(every: Duration = Duration.ofSeconds(30)): androidx.compose.runtime.State<Instant> =
    androidx.compose.runtime.produceState(Instant.now(), every) {
        while (true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(every.toMillis()) }
            value = Instant.now()
        }
    }
