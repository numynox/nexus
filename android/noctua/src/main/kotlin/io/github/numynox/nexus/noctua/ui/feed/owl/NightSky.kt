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

/** Where the moon by night, or the sun by day, sits in the scene. */
const val SkyLightX = 282f
const val SkyLightY = 78f
const val MoonR = 24f

/** One of the night's small stars. */
data class SkyStar(val x: Float, val y: Float, val r: Float, val phase: Float)

/**
 * The night's stars: the same few dots every night, scattered over the sky
 * above the branch — some higher than the scene, for a tall screen — clear
 * of the moon and the owl's head.
 */
val SkyStars: List<SkyStar> = run {
    val random = Random(7)
    val stars = ArrayList<SkyStar>()
    while (stars.size < 34) {
        val x = 12f + random.nextFloat() * 336f
        val y = -320f + random.nextFloat() * 560f
        val r = 0.7f + random.nextFloat() * 0.9f
        val phase = random.nextFloat() * 6.28f
        val clear = hypot(x - SkyLightX, y - SkyLightY) > 46f && !(x in 110f..250f && y > 190f) &&
            stars.none { hypot(it.x - x, it.y - y) < 26f }
        if (clear) stars += SkyStar(x, y, r, phase)
    }
    stars
}

/** Where the branch's centre line runs at [x], sagging by [bend] under the owl. */
fun branchY(x: Float, bend: Float): Float {
    val t = (x + 220f) / 800f
    return (1 - t) * (1 - t) * 436f + 2 * t * (1 - t) * (418f + 2 * bend) + t * t * 402f
}

/** The branch comes in from beyond the left edge and ends here, in a tip right of the owl. */
const val BranchStartX = -220f
const val BranchTipX = 326f

/** How thick the branch is at [x]: stout where it comes in, slender towards its tip. */
fun branchWidth(x: Float): Float {
    val t = ((x - BranchStartX) / (BranchTipX - BranchStartX)).coerceIn(0f, 1f)
    return 15f - 9f * t
}
