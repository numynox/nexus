package io.github.numynox.nexus.vibilia.data

/**
 * The assumed speed curve, an average speed for the whole drive by its
 * duration: 30 km/h up to 10 min, rising linearly to 50 km/h at 20 min and to
 * 100 km/h at 100 min, 100 km/h beyond. A copy of vibilia_assumed_drive_km,
 * which is the original — see decisions/2026-10-20-assumed-speed-until-learned.md.
 */
object DriveCurve {
    /** Kilometres of one drive of [hours]. */
    fun km(hours: Double): Double {
        if (hours <= 0) return 0.0
        val minutes = hours * 60
        val speed = when {
            minutes <= 10 -> 30.0
            minutes <= 20 -> 30 + 2 * (minutes - 10)
            minutes <= 100 -> 50 + 0.625 * (minutes - 20)
            else -> 100.0
        }
        return speed * hours
    }
}
