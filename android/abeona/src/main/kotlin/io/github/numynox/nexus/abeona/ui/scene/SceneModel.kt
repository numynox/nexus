package io.github.numynox.nexus.abeona.ui.scene

import io.github.numynox.nexus.abeona.domain.Confidence
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

// The scene (abeona-design.md §7), worked out without drawing anything, so it
// can be tested: the sky's light from the real time of day, its colours, and
// the land's shape. The same code as the web's app/abeona/src/lib/scene.ts;
// change both together. The cases both test suites run are in
// app/abeona/src/lib/scene.fixtures.json (SceneModelFixturesTest).
//
// Units: one unit is one dp. x runs right; heights are measured up from the
// bottom edge of the box, so a taller box only adds sky. Moments are epoch
// milliseconds and colours "#rrggbb" strings, as on the web.

enum class SceneState { Idle, Parked, Waiting, Riding, Stopped, Arrived }

// The strip's bar is the whole journey (abeona-design.md §7, §8), in a frame
// that never changes during the trip: from BAR_PAD to BAR_PAD off either
// edge, in every state. Its parts are the journey's parts, always all of
// them, left to right: ride 1, change 1, ride 2, … ride n. One part is in
// focus and takes the room (the ride on board, else the ride to board; while
// changing, the change itself); the others are short stubs. A focused ride
// shows its stops and how far the train is; a focused change is a dashed line
// in its confidence colour with the walker on it, as far along as the change
// time allows. Before leaving the walker waits on the origin. Worked out from
// the clock with the expected times on every frame of the scene, so nothing
// moves minute by minute.

/** What the traveller is doing, as the bar reads it (the travel day's kind). */
enum class BarState { BeforeLeaving, AtStation, Changing, OnBoard, Arrived }

/** A focused change's colour: its confidence; neutral when it is not known. */
enum class BarTone { Ok, Watch, Bad, Neutral }

/** A part of the journey on the bar. */
enum class BarPart { Ride, Change }

/** A ride as the bar reads it: its expected departure and arrival, and the expected arrivals at the stops between (ms). */
data class BarRide(val departure: Long, val arrival: Long, val stops: List<Long>)

data class BarJourney(
    /** The rides, walks left out. */
    val rides: List<BarRide>,
    /** The ride on board, or the one to board next (while changing: the one after the change). */
    val current: Int,
    val state: BarState,
    /** Changing: the change into the current ride. */
    val confidence: Confidence?,
)

data class SceneBar(
    /** Ride, change, ride, … ride: 2n - 1 parts for n rides. */
    val parts: List<BarPart>,
    /** The part in focus (an index into [parts]); null once arrived. */
    val focus: Int?,
    /** How far along the focused part: the train on a ride (0 until it leaves), the walker on a change. */
    val fraction: Double,
    /** A focused change's colour, from its confidence; null otherwise. */
    val tone: BarTone?,
    /** How many parts, from the left, are done. */
    val done: Int,
    /** Before leaving: the walker waits on the origin. */
    val walkerAtStart: Boolean,
    /** A focused ride's stops as fractions of it: 0, those between by time, 1. Empty otherwise. */
    val stops: List<Double>,
)

/** A part's stretch of the bar, in units. */
data class BarSpan(val from: Double, val to: Double)

/** [height] of the box; how much larger than the strip's the train ([trainScale]) and the hills and trees ([landScale]) are drawn. */
enum class SceneSize(val height: Int, val trainScale: Double, val landScale: Double) {
    Strip(64, 1.0, 1.0),
    Header(96, 1.5, 1.35),
    Full(200, 1.9, 2.1),
}

/**
 * The trains, in their real liveries: the one place in Abeona where red is
 * not a status. [Ice] is long-distance (white, a red stripe, a long nose),
 * [Regional] a red double-deck set, [Suburban] a shorter red set with many
 * doors, [Bus] a bus. Every train has an end car at both ends, as the real
 * sets do.
 */
enum class TrainKind(val spec: SceneModel.TrainSpec) {
    Ice(SceneModel.TrainSpec(4, 44.0, 38.0, 13.0, 1.2)),
    Regional(SceneModel.TrainSpec(3, 38.0, 38.0, 17.0, 1.5)),
    Suburban(SceneModel.TrainSpec(3, 31.0, 30.0, 13.0, 1.2)),
    Bus(SceneModel.TrainSpec(1, 30.0, 0.0, 11.0, 0.0)),
}

object SceneModel {
    /** The top of the rail above the bottom edge. */
    const val RAIL = 12
    /**
     * The ride's progress bar in the strip: a thick bar in the ground under the
     * track, so the train rides above it and never hides it. The ground is this
     * deep then; the bar's centre is BAR_UP above the bottom edge.
     */
    const val RAIL_WITH_BAR = 20
    const val BAR_UP = 8
    const val BAR = 6
    const val BAR_DOT = 4.6
    const val BAR_DOT_END = 6.0

    /**
     * The strip's rail keeps this much clear at its ends. The train's nose marks
     * where the ride is and its cars trail behind, so the start needs room for
     * the leading car to show at the first stop.
     */
    const val RAIL_START = 64
    const val RAIL_END = 22

    /** A train's shape at scale 1: lengths per car ([head] is an end car); the nose is at x = 0 and the cars trail to the left. */
    class TrainSpec(val cars: Int, val head: Double, val car: Double, val height: Double, val gap: Double)

    /** The whole set: an end car at each end (the same, mirrored) and the cars between; a bus is its one body. */
    fun trainLength(kind: TrainKind): Double {
        val t = kind.spec
        if (t.cars < 2) return t.head
        return 2 * t.head + (t.cars - 2) * t.car + (t.cars - 1) * t.gap
    }

    /** Where the train's nose stands off the rail: far enough right for the whole set to show when the box has the room. */
    fun trainNoseX(kind: TrainKind, scale: Double, width: Double): Double =
        min(width - 60, max(width * 0.56, trainLength(kind) * scale + 12))

    private val LONG_DISTANCE = setOf("ICE", "IC", "EC", "ECE", "RJ", "RJX", "TGV", "NJ", "EN", "FLX", "EST", "FR", "IR")
    private val SUBURBAN = Regex("^S(-BAHN)?(\\s|\\d|$)")

    /**
     * Which train a ride is drawn as, from its product ("ICE", "RE", "S") or,
     * failing that, its line's name ("S 8"); a bus by its mode. Long-distance as
     * in the line chip's colours.
     */
    fun trainKind(product: String?, line: String?, bus: Boolean): TrainKind {
        if (bus) return TrainKind.Bus
        val p = product.orEmpty().trim().uppercase()
        if (p in LONG_DISTANCE) return TrainKind.Ice
        val name = p.ifEmpty { line.orEmpty().trim().uppercase() }
        if (SUBURBAN.containsMatchIn(name)) return TrainKind.Suburban
        if (p.isEmpty() && name.split(Regex("\\s|\\d"))[0] in LONG_DISTANCE) return TrainKind.Ice
        return TrainKind.Regional
    }

    /** The land repeats after this many units. */
    const val TILE = 720
    const val MAST_EVERY = 90
    const val SLEEPER_EVERY = 12

    /** How fast each layer moves against the track. */
    const val PARALLAX_FAR = 0.08
    const val PARALLAX_NEAR = 0.25
    const val PARALLAX_TRACK = 1.0

    /** Where the sky is reckoned when the trip names no place: Frankfurt am Main. */
    const val FRANKFURT_LAT = 50.1109
    const val FRANKFURT_LON = 8.6821

    // -------------------------------------------------------------- the sky

    private val KNOWN_NEW_MOON = Instant.parse("2000-01-06T18:14:00Z").toEpochMilli()
    private const val SYNODIC_DAYS = 29.530588853
    private const val DAY = 86_400_000L

    /** JavaScript's Math.round: half rounds up. */
    private fun round(v: Double): Long = floor(v + 0.5).toLong()

    /** Where the moon is in its cycle: 0 new, 0.5 full. */
    fun moonPhase(at: Long): Double {
        val days = (at - KNOWN_NEW_MOON) / DAY.toDouble()
        return (((days % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS) / SYNODIC_DAYS
    }

    private val PHASES = listOf("New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter", "Waning crescent")

    fun moonPhaseName(phase: Double): String = PHASES[(round(phase * 8) % 8).toInt()]

    /** A number as JavaScript prints it: whole numbers without a fraction. */
    private fun js(v: Double): String = if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

    private fun fixed(v: Double, digits: Int): String = String.format(Locale.ROOT, "%.${digits}f", v)

    /** The lit part of a moon of radius r at (cx, cy) as SVG path data; waxing is lit on the right. */
    fun moonLitPath(phase: Double, cx: Double, cy: Double, r: Double): String {
        val rx = abs(cos(2 * PI * phase)) * r
        val waxing = phase < 0.5
        val crescent = phase < 0.25 || phase > 0.75
        val outer = if (waxing) 1 else 0
        val inner = if (waxing) (if (crescent) 0 else 1) else if (crescent) 1 else 0
        return "M ${js(cx)} ${js(cy - r)} A ${js(r)} ${js(r)} 0 0 $outer ${js(cx)} ${js(cy + r)} A ${fixed(rx, 2)} ${js(r)} 0 0 $inner ${js(cx)} ${js(cy - r)} Z"
    }

    data class SunTimes(val sunrise: Long, val sunset: Long)

    /**
     * Sunrise and sunset (ms) at a place on the UTC day of [at]: NOAA's
     * approximation, within about a minute. Null in polar day or night.
     */
    fun sunTimes(at: Long, lat: Double, lon: Double): SunTimes? {
        val date = Instant.ofEpochMilli(at).atOffset(ZoneOffset.UTC).toLocalDate()
        val midnight = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val g = ((2 * PI) / 365) * (date.dayOfYear - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val rad = PI / 180
        val cosHa = cos(90.833 * rad) / (cos(lat * rad) * cos(decl)) - tan(lat * rad) * tan(decl)
        if (cosHa < -1 || cosHa > 1) return null
        val ha = acos(cosHa) / rad
        fun minutes(m: Double) = midnight + round(m * 60_000)
        return SunTimes(minutes(720 - 4 * (lon + ha) - eqTime), minutes(720 - 4 * (lon - ha) - eqTime))
    }

    data class SkyLight(
        /** 1 by day, 0 by night, in between for 40 minutes around sunrise and sunset. */
        val light: Double,
        /** The orange at the horizon: 1 at sunrise and sunset, 0 an hour away from both. */
        val glow: Double,
        /** How far the sun (by day) or the moon (by night) is across the sky, 0 left to 1 right. */
        val across: Double,
        val night: Boolean,
    )

    private const val TWILIGHT = 40 * 60_000.0
    private const val GLOW = 60 * 60_000.0
    private fun clamp01(v: Double) = max(0.0, min(1.0, v))

    /** The light at a moment and place. Without sun times (polar): plain day. */
    fun skyLight(at: Long, lat: Double = FRANKFURT_LAT, lon: Double = FRANKFURT_LON): SkyLight {
        val times = sunTimes(at, lat, lon) ?: return SkyLight(1.0, 0.0, 0.5, false)
        val sunrise = times.sunrise
        val sunset = times.sunset
        val day = sunset - sunrise
        val night = at < sunrise || at >= sunset
        // Twilight is centred on the event: half lit at sunrise and at sunset.
        val light = if (at < (sunrise + sunset) / 2.0) clamp01((at - sunrise) / TWILIGHT + 0.5) else clamp01((sunset - at) / TWILIGHT + 0.5)
        val glow = max(clamp01(1 - abs(at - sunrise) / GLOW), clamp01(1 - abs(at - sunset) / GLOW))
        val across = if (!night) {
            (at - sunrise) / day.toDouble()
        } else {
            val length = (DAY - day).toDouble()
            if (at >= sunset) (at - sunset) / length else 1 - (sunrise - at) / length
        }
        return SkyLight(light, glow, clamp01(across), night)
    }

    // -------------------------------------------------------------- colours

    data class ScenePalette(
        /** The sky at the horizon; it fades to nothing at the top of the box. */
        val sky: String,
        val glow: String,
        val far: String,
        val near: String,
        val tree: String,
        val ground: String,
        val rail: String,
        val mast: String,
        val cloud: String,
        val star: String,
        val sun: String,
        val moon: String,
        val trainBody: String,
        /** The livery's red: the ICE's stripe, the regional and suburban bodies. */
        val trainRed: String,
        /** The brand burgundy: the rail behind the train, passed stops, the flag. */
        val accent: String,
        /** The change's confidence on the bar's walk (safe, tight, at risk): dark enough on the ground in a light theme, light in a dark one. */
        val ok: String,
        val watch: String,
        val bad: String,
        /** "#rrggbbaa". */
        val trainLine: String,
        val trainWindow: String,
        val platform: String,
        val dot: String,
    )

    private val KEYS = listOf("sky", "glow", "far", "near", "tree", "ground", "rail", "mast", "cloud", "star", "sun", "moon", "trainBody", "trainWindow", "platform")

    private fun tones(vararg values: String) = KEYS.zip(values).toMap()

    private val LIGHT_DAY = tones("#d9e7ea", "#ffc48f", "#c7d8c6", "#a6c2a0", "#86a983", "#e7ded3", "#6b5f57", "#8d8178", "#ffffff", "#8e88b5", "#f5a83c", "#ffffff", "#ffffff", "#2a211c", "#cfc3b6")
    private val LIGHT_NIGHT = tones("#cdc9e0", "#ffc48f", "#b7b3cf", "#9995bb", "#7f7aa6", "#ddd5d0", "#6b5f57", "#8d8178", "#ebe8f4", "#7c76a8", "#f5a83c", "#ffffff", "#ffffff", "#ffcf8a", "#c4b9b4")
    private val DARK_DAY = tones("#2c3d44", "#8a4a22", "#30463f", "#3b5a4d", "#4a6f5e", "#322a24", "#b5a79c", "#8a7d73", "#465a61", "#f3eae2", "#f5a83c", "#f3eae2", "#ede4dc", "#1a1512", "#4a4038")
    private val DARK_NIGHT = tones("#262238", "#8a4a22", "#2b2842", "#373354", "#443f66", "#2a2420", "#b5a79c", "#8a7d73", "#34304c", "#f3eae2", "#f5a83c", "#f3eae2", "#ede4dc", "#ffcf8a", "#40372f")

    /** Two "#rrggbb" colours mixed: t = 0 gives a, 1 gives b. */
    fun mix(a: String, b: String, t: Double): String {
        fun channel(i: Int): String {
            val x = a.substring(i, i + 2).toInt(16)
            val y = b.substring(i, i + 2).toInt(16)
            return round(x + (y - x) * t).toString(16).padStart(2, '0')
        }
        return "#${channel(1)}${channel(3)}${channel(5)}"
    }

    /**
     * The scene's colours: the theme decides how light it is (a dark theme never
     * gets a bright band), the time of day what hue. Windows are lit from dusk.
     */
    fun scenePalette(dark: Boolean, light: Double): ScenePalette {
        val day = if (dark) DARK_DAY else LIGHT_DAY
        val night = if (dark) DARK_NIGHT else LIGHT_NIGHT
        fun tone(key: String) = mix(night.getValue(key), day.getValue(key), light)
        return ScenePalette(
            sky = tone("sky"), glow = tone("glow"), far = tone("far"), near = tone("near"), tree = tone("tree"),
            ground = tone("ground"), rail = tone("rail"), mast = tone("mast"), cloud = tone("cloud"), star = tone("star"),
            sun = tone("sun"), moon = tone("moon"), trainBody = tone("trainBody"),
            trainRed = if (dark) "#d9584e" else "#d9342b",
            accent = if (dark) "#f08ca8" else "#8c1d40",
            ok = if (dark) "#7fd3a8" else "#1f7a50",
            watch = if (dark) "#f2c94c" else "#9a6a00",
            bad = if (dark) "#ff8a7a" else "#b3261e",
            trainLine = if (dark) "#00000000" else "#2a211c40",
            trainWindow = (if (light < 0.5) night else day).getValue("trainWindow"),
            platform = tone("platform"),
            dot = if (dark) "#26201c" else "#ffffff",
        )
    }

    // -------------------------------------------------------------- the land

    enum class HillKind { Far, Near }

    private class Wave(val amplitude: Double, val cycles: Double, val shift: Double)

    private fun base(kind: HillKind) = if (kind == HillKind.Far) 20.0 else 13.0

    private val FAR_WAVES = listOf(Wave(12.0, 2.0, 1.3), Wave(7.0, 5.0, 0.4), Wave(3.0, 9.0, 2.2))
    private val NEAR_WAVES = listOf(Wave(8.0, 3.0, 4.1), Wave(5.0, 7.0, 0.9))

    /** How high the hill of a layer stands above the bottom edge at x; repeats every TILE. */
    fun hillHeight(kind: HillKind, x: Double): Double {
        var h = base(kind)
        for (w in if (kind == HillKind.Far) FAR_WAVES else NEAR_WAVES) h += w.amplitude * (0.5 + 0.5 * sin((2 * PI * w.cycles * x) / TILE + w.shift))
        return h
    }

    /** A hill layer from x = 0 to [width] as a closed SVG path in a box [height] tall, sampled every 12 units. */
    fun hillPath(kind: HillKind, width: Int, height: Int): String {
        val d = StringBuilder("M 0 $height")
        var x = 0
        while (x <= width + 12) {
            d.append(" L $x ${fixed(height - hillHeight(kind, x.toDouble()), 1)}")
            x += 12
        }
        return "$d L ${width + 12} $height Z"
    }

    data class Tree(val x: Double, val r: Double)

    /** Where the trees stand on the near hills, within one tile. */
    val TREES = listOf(
        Tree(38.0, 5.0), Tree(52.0, 4.0), Tree(148.0, 5.5), Tree(236.0, 4.0), Tree(250.0, 5.0), Tree(330.0, 4.5),
        Tree(432.0, 5.0), Tree(446.0, 4.0), Tree(540.0, 5.5), Tree(618.0, 4.0), Tree(632.0, 5.0), Tree(700.0, 4.5),
    )

    data class Star(val x: Double, val y: Double, val r: Double)

    /** The night's stars as fractions of the box (x across, y down from the top of the sky). */
    val STARS = listOf(
        Star(0.06, 0.3, 0.9), Star(0.14, 0.12, 0.7), Star(0.23, 0.42, 0.8), Star(0.31, 0.18, 1.0),
        Star(0.42, 0.34, 0.7), Star(0.5, 0.1, 0.9), Star(0.58, 0.4, 0.8), Star(0.67, 0.2, 0.7),
        Star(0.76, 0.36, 1.0), Star(0.84, 0.14, 0.8), Star(0.93, 0.3, 0.7),
    )

    data class LightPosition(val x: Double, val up: Double)

    /** Where the sun or moon stands: across the box, on a shallow arc. x, and the height above the bottom edge. */
    fun lightPosition(across: Double, width: Double, height: Double): LightPosition {
        val margin = 26
        val x = margin + (width - 2 * margin) * across
        val top = height - min(40.0, height * 0.2) - 6
        val low = min(top, 34.0)
        // Highest in the middle of its way; in the strip there is no room to rise.
        return LightPosition(x, low + (top - low) * sin(PI * across))
    }

    /** How fast the land passes while riding, in units a second, from the train's speed when it is known. */
    fun travelSpeed(kmh: Double?): Double {
        if (kmh == null) return 36.0
        return max(10.0, min(80.0, 10 + kmh * 0.28))
    }

    /** Where on the strip's rail a fraction of the ride lies: the train's nose. */
    fun railX(fraction: Double, width: Double): Double = RAIL_START + (width - RAIL_START - RAIL_END) * clamp01(fraction)

    // -------------------------------------------------------------- the journey bar

    private fun barTone(confidence: Confidence): BarTone = when (confidence) {
        Confidence.SAFE -> BarTone.Ok
        Confidence.TIGHT -> BarTone.Watch
        Confidence.AT_RISK, Confidence.IMPOSSIBLE -> BarTone.Bad
    }

    /** The share of [from, to] that lies behind [now]; a span of nothing counts as a millisecond. */
    private fun along(now: Long, from: Long, to: Long): Double = clamp01((now - from).toDouble() / max(to - from, 1L))

    /** The bar at [now] (ms). */
    fun journeyBar(j: BarJourney, now: Long): SceneBar {
        val n = j.rides.size
        val parts = (0 until max(0, 2 * n - 1)).map { if (it % 2 == 0) BarPart.Ride else BarPart.Change }
        if (n == 0 || j.state == BarState.Arrived) return SceneBar(parts, null, 1.0, null, parts.size, false, emptyList())
        val c = j.current.coerceIn(0, n - 1)
        if (j.state == BarState.Changing && c > 0) {
            return SceneBar(
                parts, 2 * c - 1,
                fraction = along(now, j.rides[c - 1].arrival, j.rides[c].departure),
                tone = j.confidence?.let(::barTone) ?: BarTone.Neutral,
                done = 2 * c - 1,
                walkerAtStart = false,
                stops = emptyList(),
            )
        }
        val ride = j.rides[c]
        return SceneBar(
            parts, 2 * c,
            fraction = if (j.state == BarState.OnBoard) along(now, ride.departure, ride.arrival) else 0.0,
            tone = null,
            done = 2 * c,
            walkerAtStart = j.state == BarState.BeforeLeaving,
            stops = listOf(0.0) + ride.stops.map { along(it, ride.departure, ride.arrival) } + 1.0,
        )
    }

    /** A ride and a change out of focus: their stubs, and the least they shrink to. */
    const val RIDE_STUB = 24.0
    const val CHANGE_STUB = 12.0
    const val RIDE_STUB_MIN = 12.0
    const val CHANGE_STUB_MIN = 8.0
    /** The stubs take at most this share of the bar; beyond it they all shrink in proportion. */
    const val STUB_SHARE = 0.4
    /** The bar keeps this much clear at both edges of the strip, in every state. */
    const val BAR_PAD = 16.0
    /** A ride's ends and the focused ride's stops on the bar (radius); the destination keeps BAR_DOT_END's ring. */
    const val BAR_NODE = 3.5
    /** A figure on the bar (the walker) sits in a badge this wide: a disc, a ring, the glyph. */
    const val BADGE = 18.0

    /** How long the bar glides to a value live data moved, and morphs from one focus to the next (ms). */
    const val GLIDE_MS = 1000
    const val MORPH_MS = 600

    /**
     * Each part's stretch of the bar, left to right, on a strip [width]
     * wide. The stubs at their widths (shrunk together when they would take
     * more than STUB_SHARE), the part in [focus] the rest. Without a focus
     * (arrived) the rides share the room and the changes keep their stubs.
     */
    fun barLayout(parts: List<BarPart>, focus: Int?, width: Double): List<BarSpan> {
        if (parts.isEmpty()) return emptyList()
        val room = width - 2 * BAR_PAD
        val widths = if (focus == null) {
            val rides = parts.count { it == BarPart.Ride }
            val changes = (parts.size - rides) * CHANGE_STUB
            parts.map { if (it == BarPart.Ride) (room - changes) / rides else CHANGE_STUB }
        } else {
            fun stub(p: BarPart) = if (p == BarPart.Ride) RIDE_STUB else CHANGE_STUB
            val stubs = parts.withIndex().sumOf { (i, p) -> if (i == focus) 0.0 else stub(p) }
            val k = if (stubs > STUB_SHARE * room) STUB_SHARE * room / stubs else 1.0
            val sized = parts.map { if (it == BarPart.Ride) max(RIDE_STUB_MIN, RIDE_STUB * k) else max(CHANGE_STUB_MIN, CHANGE_STUB * k) }.toMutableList()
            sized[focus] = room - sized.withIndex().sumOf { (i, w) -> if (i == focus) 0.0 else w }
            sized
        }
        var x = BAR_PAD
        return widths.map { w -> BarSpan(x, x + w).also { x += w } }
    }

    /** Where the train's nose stands over a point [x] of the bar: there, but never so far left that its leading car is cut off by the strip's edge. */
    fun trainOnBar(x: Double, kind: TrainKind, scale: Double): Double = max(x, kind.spec.head * scale + 6)
}
