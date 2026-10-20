package io.github.numynox.nexus.vibilia.data

/**
 * The assumed speed curve: 30 km/h up to 10 km, rising linearly to 50 km/h at
 * 20 km and to 100 km/h at 100 km, 100 km/h beyond. A copy of
 * vibilia_assumed_drive_km, which is the original — see
 * decisions/2026-10-20-assumed-speed-until-learned.md.
 */
object DriveCurve {
    /** Kilometres of one drive of [hours]: solves d = speed(d) × hours. */
    fun km(hours: Double): Double = when {
        hours <= 0 -> 0.0
        hours * 30 <= 10 -> 30 * hours
        hours * 50 <= 20 -> 10 * hours / (1 - 2 * hours)
        hours <= 1 -> 37.5 * hours / (1 - 0.625 * hours)
        else -> 100 * hours
    }
}
