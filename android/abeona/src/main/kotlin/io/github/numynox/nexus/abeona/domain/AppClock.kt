package io.github.numynox.nexus.abeona.domain

import java.time.Clock
import java.time.Instant

/**
 * "Now" for the screens that decide by it whether a journey is past. The
 * screenshot tests pin it, so a sample dated in the past still shows as an
 * upcoming trip; the app never changes it.
 */
object AppClock {
    @Volatile var clock: Clock = Clock.systemUTC()

    fun now(): Instant = clock.instant()
}
