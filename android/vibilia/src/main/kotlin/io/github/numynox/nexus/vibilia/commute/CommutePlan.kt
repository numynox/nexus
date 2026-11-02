package io.github.numynox.nexus.vibilia.commute

import io.github.numynox.nexus.vibilia.data.Commute
import io.github.numynox.nexus.vibilia.data.CommuteExpectation
import io.github.numynox.nexus.vibilia.location.LatLng
import io.github.numynox.nexus.vibilia.location.distanceTo
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CommuteDirection(val key: String) {
    ToWork("to_work"),
    ToHome("to_home"),
}

/**
 * The decisions behind the arrival announcement, free of Android so they can
 * be tested (decisions/2026-11-02-commute-arrival-announcement.md):
 * which window a connection falls in, whether the trip starts where that
 * direction starts, and what to say.
 */
object CommutePlan {
    /** The windows are local time in public.nexus_time_zone(), like the RPC that reads them. */
    val Zone: ZoneId = ZoneId.of("Europe/Berlin")

    /** How long the phone listens for its position after connecting, whatever it hears. */
    val ListenFor: Duration = Duration.ofMinutes(1)

    private val SpokenTime: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()

    /** The direction whose window [at] falls in on a commute weekday; the way to work wins an overlap. */
    fun directionAt(commute: Commute, at: Instant, zone: ZoneId = Zone): CommuteDirection? {
        val local = at.atZone(zone)
        if (local.dayOfWeek.value !in commute.weekdays) return null
        val time = local.toLocalTime()
        fun inside(from: String, until: String): Boolean {
            val start = parseTime(from) ?: return false
            val end = parseTime(until) ?: return false
            return !time.isBefore(start) && !time.isAfter(end)
        }
        return when {
            inside(commute.toWorkFrom, commute.toWorkUntil) -> CommuteDirection.ToWork
            inside(commute.toHomeFrom, commute.toHomeUntil) -> CommuteDirection.ToHome
            else -> null
        }
    }

    /** Where a trip in [direction] starts, and how close counts as there. */
    fun origin(commute: Commute, direction: CommuteDirection): Pair<LatLng, Int> = when (direction) {
        CommuteDirection.ToWork -> LatLng(commute.homeLat, commute.homeLng) to commute.homeRadiusM
        CommuteDirection.ToHome -> LatLng(commute.workLat, commute.workLng) to commute.workRadiusM
    }

    /** True when any of the fixes lies inside the geofence the trip starts from. */
    fun startsAtOrigin(commute: Commute, direction: CommuteDirection, fixes: List<LatLng>): Boolean {
        val (centre, radius) = origin(commute, direction)
        return fixes.any { it.distanceTo(centre) <= radius }
    }

    /** The expected travel time, or null while no drive of yours fell in the window. */
    fun expectedSeconds(expectations: List<CommuteExpectation>, direction: CommuteDirection): Int? =
        expectations.firstOrNull { it.direction == direction.key }?.medianSeconds?.takeIf { it > 0 }

    /** "Die Ankunftszeit auf der Arbeit ist 7:45 Uhr. Gute Fahrt." */
    fun announcement(direction: CommuteDirection, arrival: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = SpokenTime.format(arrival.atZone(zone))
        val where = when (direction) {
            CommuteDirection.ToWork -> "auf der Arbeit"
            CommuteDirection.ToHome -> "daheim"
        }
        return "Die Ankunftszeit $where ist $time Uhr. Gute Fahrt."
    }

    /** The number to text on this way, or null when this way texts nobody. */
    fun smsNumber(commute: Commute, direction: CommuteDirection): String? {
        val wanted = when (direction) {
            CommuteDirection.ToWork -> commute.smsToWork
            CommuteDirection.ToHome -> commute.smsToHome
        }
        return commute.smsNumber?.takeIf { wanted && it.isNotBlank() }
    }

    /** "Bin losgefahren. Ankunft daheim ca. 17:45 Uhr." — sent from your own number, so first person. */
    fun smsText(direction: CommuteDirection, arrival: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val where = when (direction) {
            CommuteDirection.ToWork -> "auf der Arbeit"
            CommuteDirection.ToHome -> "daheim"
        }
        return "Bin losgefahren. Ankunft $where ca. ${SpokenTime.format(arrival.atZone(zone))} Uhr."
    }

    /**
     * "+49 170 123-4567" → "+491701234567": digits and a leading plus, as the
     * table stores it. Null when that leaves no plausible number.
     */
    fun normalizeNumber(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        val number = (if (trimmed.startsWith("+")) "+" else "") + digits
        return number.takeIf { digits.length in 3..20 }
    }

    /** One announcement per car, direction and day, however often the head unit reconnects. */
    fun announcedKey(carId: String, direction: CommuteDirection, at: Instant, zone: ZoneId = Zone): String =
        "$carId|${direction.key}|${at.atZone(zone).toLocalDate()}"
}
