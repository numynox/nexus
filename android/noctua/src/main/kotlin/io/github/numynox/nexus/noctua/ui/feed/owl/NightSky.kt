package io.github.numynox.nexus.noctua.ui.feed.owl

import java.time.Instant
import java.time.LocalTime
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * The sky above the owl, worked out without drawing anything, so it can be
 * tested. Every position is in the units of the owl's scene: 360 wide, 470
 * tall, the branch near its foot — the web's SVG uses the same units.
 */

/** A new moon to count from: 6 January 2000, 18:14 UTC. */
private val KnownNewMoon: Instant = Instant.parse("2000-01-06T18:14:00Z")
private const val SynodicDays = 29.530588853

/** Where the moon is in its cycle at [at]: 0 is new, 0.5 full, just under 1 new again. */
fun moonPhase(at: Instant): Double {
    val days = (at.toEpochMilli() - KnownNewMoon.toEpochMilli()) / 86_400_000.0
    return ((days % SynodicDays + SynodicDays) % SynodicDays) / SynodicDays
}

/** How much of the disc is lit at [phase], 0 to 1. */
fun moonLit(phase: Double): Double = (1 - cos(2 * PI * phase)) / 2

fun moonPhaseName(phase: Double): String =
    listOf("New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter", "Waning crescent")[
        (phase * 8).roundToInt() % 8
    ]

/**
 * The lit part of a moon of radius [r] at ([cx], [cy]) as SVG path data: the
 * outer edge is a half circle on the lit side, the terminator a half ellipse
 * that bulges towards the dark side for a crescent and away from it past a
 * quarter. Waxing is lit on the right, as seen from the northern hemisphere.
 */
fun moonLitPath(phase: Double, cx: Float, cy: Float, r: Float): String {
    val rx = abs(cos(2 * PI * phase)) * r
    val waxing = phase < 0.5
    val crescent = phase < 0.25 || phase > 0.75
    val outer = if (waxing) 1 else 0
    val inner = if (waxing) (if (crescent) 0 else 1) else (if (crescent) 1 else 0)
    return "M $cx ${cy - r} A $r $r 0 0 $outer $cx ${cy + r} A ${"%.2f".format(java.util.Locale.ROOT, rx)} $r 0 0 $inner $cx ${cy - r} Z"
}

/** Night is when the owl is awake: from seven in the evening to seven in the morning. */
fun isNight(time: LocalTime): Boolean = time.hour < 7 || time.hour >= 19

data class SkyPoint(val x: Float, val y: Float)

/** Where the moon sits in the scene, and how big it is. */
const val MoonX = 282f
const val MoonY = 78f
const val MoonR = 24f

/**
 * A star for each article read today, in reading order. Each star's place is
 * drawn from its article's id, so the stars already up stay put when a new
 * one joins — and it keeps clear of the moon, the owl's head and the stars
 * placed before it, giving up on the spacing only when the sky is full.
 */
fun constellation(articleIds: List<Long>): List<SkyPoint> {
    val placed = ArrayList<SkyPoint>(articleIds.size)
    articleIds.forEach { id ->
        val random = Random(id * 7919 + 17)
        var point: SkyPoint
        var tries = 0
        do {
            point = SkyPoint(26f + random.nextFloat() * 308f, 58f + random.nextFloat() * 178f)
            tries++
        } while (tries < 60 && !clear(point, placed))
        placed += point
    }
    return placed
}

private fun clear(p: SkyPoint, placed: List<SkyPoint>): Boolean =
    hypot(p.x - MoonX, p.y - MoonY) >= 48f &&
        !(p.x in 105f..255f && p.y > 205f) &&
        placed.none { hypot(it.x - p.x, it.y - p.y) < 34f }
