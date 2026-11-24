package io.github.numynox.nexus.abeona.ui.scene

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Clock
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The moment the scene's sky shows. The screenshot tests pin it to a
 * daytime, so the pictures are the same at whatever hour they run; the app
 * never changes it.
 */
object SceneClock {
    @Volatile var clock: Clock = Clock.systemUTC()

    fun now(): Long = clock.millis()
}

/**
 * The bar as drawn: where each part lies ([at]), and the state it shows;
 * during a morph also the bar it comes from ([was]) and how far along the
 * morph is ([t], 1 when settled), for the focus to hand over.
 */
private class DrawnBar(val at: List<BarSpan>, val bar: SceneBar, val was: SceneBar? = null, val t: Double = 1.0)

private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

private fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)

/**
 * Between two bars of the same journey, [t] of the way: each part from its
 * old stretch to its new one, so the part losing the focus shrinks into its
 * stub as the next grows; the ends stay put. Another journey is a cut.
 */
private fun between(a: DrawnBar, b: DrawnBar, t: Double): DrawnBar {
    if (a.at.size != b.at.size) return b
    return DrawnBar(b.at.mapIndexed { i, r -> BarSpan(lerp(a.at[i].from, r.from, t), lerp(a.at[i].to, r.to, t)) }, b.bar, a.bar, t)
}

/** What the last frame drew, for the next change to start from, and where the train was (for a tap). */
private class Painted {
    var bar: DrawnBar? = null
    var train: TrainKind = TrainKind.Ice
    var trainX: Float = 0f
}

/** How the bar changes: from a picture of the old one, and the glide's distance left. */
private class Changes {
    var key: String? = null
    var times: List<BarRide>? = null
    var morphFrom: DrawnBar? = null
    var glideFraction = 0.0
    var swapKind: TrainKind = TrainKind.Ice
    var swapX = 0f
}

/** How long the old train takes to drive out and the next one in. */
private const val SWAP_MS = 1200

/** Material's standard easing, near enough: (0.2, 0, 0, 1). */
private fun standard(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - Math.pow(-2 * t + 2, 3.0) / 2

private fun easeOut(t: Double) = 1 - Math.pow(1 - t, 3.0)

/** The walking figure in the bar's badge (Material's directions_walk, as the web draws it), at scale 1 in a 24 box. */
private val Walker = svg(
    "M13.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM9.8 8.9L7 23h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3C14.8 12 16.8 13 19 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7",
)

private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

private fun colour(hex: String): Color {
    val rgb = hex.substring(1, 7).toLong(16)
    val alpha = if (hex.length >= 9) hex.substring(7, 9).toInt(16) else 0xFF
    return Color((alpha.toLong() shl 24) or rgb)
}

// The leading cars at scale 1 with their wheels on y = 0 and the nose at
// x = 0: the same path data as the web's Scene.svelte, so a change can be
// copied across.
private object TrainShapes {
    val iceBody = svg("M-44 -13 H-15 C-8 -13 -3 -8.5 0 -4.5 C0.6 -3.2 -0.4 -2 -2 -2 H-44 Z")
    val iceWindscreen = svg("M-13.5 -11.4 H-11.5 C-8.2 -10.9 -5.6 -9.2 -4.1 -7.6 H-12.5 C-13.1 -7.6 -13.5 -8 -13.5 -8.6 Z")
    val iceStripe = svg("M-44 -6.6 H-2.4 L-1.6 -5.5 H-44 Z")
    val regionalBody = svg("M-38 -17 H-5.5 C-3.4 -17 -1.9 -15.6 -1.5 -13.4 L0 -4.4 C0.2 -3 -0.8 -2 -2.2 -2 H-38 Z")
    val regionalWindscreen = svg("M-7.5 -14.6 H-4.6 C-3.9 -14.6 -3.4 -14.1 -3.3 -13.4 L-2.6 -9 H-7.5 Z")
    val suburbanBody = svg("M-31 -13 H-4.5 C-2.8 -13 -1.6 -11.9 -1.3 -10.2 L0 -4.2 C0.2 -3 -0.8 -2 -2.2 -2 H-31 Z")
    val suburbanWindscreen = svg("M-6.4 -11 H-4 C-3.4 -11 -2.9 -10.6 -2.8 -10 L-2.2 -7 H-6.4 Z")
}

/** The scene's colours as Compose ones, and whether the train's lights are on. */
private class TrainPaint(val body: Color, val line: Color, val red: Color, val window: Color, val bogie: Color)

private fun DrawScope.box(colour: Color, x: Float, y: Float, width: Float, height: Float, r: Float = 0f) =
    drawRoundRect(colour, Offset(x, y), Size(width, height), CornerRadius(r))

private fun DrawScope.bogies(p: TrainPaint, from: Float, length: Float) {
    box(p.bogie, from + 4, -2.6f, 8f, 2.6f, 1.3f)
    box(p.bogie, from + length - 12, -2.6f, 8f, 2.6f, 1.3f)
}

/** A car behind the leading one: its right end at [x]. */
private fun DrawScope.car(kind: TrainKind, p: TrainPaint, x: Float, length: Float) {
    val from = x - length
    bogies(p, from, length)
    when (kind) {
        TrainKind.Ice -> {
            box(p.body, from, -13f, length, 11f, 1.6f)
            drawRoundRect(p.line, Offset(from, -13f), Size(length, 11f), CornerRadius(1.6f), style = Stroke(0.5f))
            box(p.window, from + 2.5f, -11.2f, length - 5, 3.4f, 1.2f)
            box(p.red, from, -6.6f, length, 1.1f)
        }
        TrainKind.Regional -> {
            box(p.red, from, -17f, length, 15f, 1.8f)
            box(p.window, from + 3, -15.2f, length - 6, 3f, 1f)
            box(p.window, from + 7, -8.6f, length - 14, 3f, 1f)
            box(p.body, from + 2.2f, -10.5f, 3f, 8f, 0.8f)
            box(p.body, from + length - 5.2f, -10.5f, 3f, 8f, 0.8f)
        }
        else -> {
            box(p.red, from, -13f, length, 11f, 1.6f)
            box(p.window, from + 2, -11f, length - 4, 4f, 1f)
            for (door in listOf(0.14f, 0.5f, 0.86f)) box(p.body, from + length * door - 1.8f, -11.6f, 3.6f, 9.2f, 0.8f)
        }
    }
}

/** An end car with its nose at x = 0; the last car is the same, mirrored. */
private fun DrawScope.endCar(kind: TrainKind, p: TrainPaint) {
    when (kind) {
        TrainKind.Ice -> {
            bogies(p, -44f, 40f)
            drawPath(TrainShapes.iceBody, p.body)
            drawPath(TrainShapes.iceBody, p.line, style = Stroke(0.5f))
            box(p.window, -41.5f, -11.2f, 26f, 3.4f, 1.2f)
            drawPath(TrainShapes.iceWindscreen, p.window)
            drawPath(TrainShapes.iceStripe, p.red)
        }
        TrainKind.Regional -> {
            bogies(p, -38f, 36f)
            drawPath(TrainShapes.regionalBody, p.red)
            box(p.window, -35f, -15.2f, 25f, 3f, 1f)
            box(p.window, -31f, -8.6f, 17f, 3f, 1f)
            box(p.body, -35.8f, -10.5f, 3f, 8f, 0.8f)
            drawPath(TrainShapes.regionalWindscreen, p.window)
            box(p.body, -12f, -10.5f, 3f, 8f, 0.8f)
        }
        TrainKind.Suburban -> {
            bogies(p, -31f, 29f)
            drawPath(TrainShapes.suburbanBody, p.red)
            box(p.window, -29f, -11f, 21f, 4f, 1f)
            for (door in listOf(-25.5f, -15f)) box(p.body, door, -11.6f, 3.6f, 9.2f, 0.8f)
            drawPath(TrainShapes.suburbanWindscreen, p.window)
        }
        TrainKind.Bus -> {
            // A bus: one body, two wheels.
            box(p.body, -30f, -12f, 30f, 10.4f, 2.2f)
            drawRoundRect(p.line, Offset(-30f, -12f), Size(30f, 10.4f), CornerRadius(2.2f), style = Stroke(0.5f))
            box(p.red, -30f, -5.4f, 30f, 3.8f, 1.6f)
            box(p.window, -27.5f, -10.4f, 20f, 3.8f, 1f)
            box(p.window, -6.2f, -10.4f, 4.6f, 5.4f, 1f)
            drawCircle(p.bogie, 2.1f, Offset(-23f, -1.6f))
            drawCircle(p.bogie, 2.1f, Offset(-7f, -1.6f))
        }
    }
}

/**
 * The train in its livery, the cars trailing to the left of the nose at
 * x = 0: an end car at each end and the cars between. [lit]: the red tail
 * light at the far end; the headlight is the caller's, since a tap flashes it.
 */
private fun DrawScope.drawTrain(kind: TrainKind, p: TrainPaint, lit: Boolean) {
    val spec = kind.spec
    repeat(maxOf(0, spec.cars - 2)) { c -> car(kind, p, (-spec.head - spec.gap - c * (spec.car + spec.gap)).toFloat(), spec.car.toFloat()) }
    if (spec.cars > 1) {
        withTransform({
            translate(left = -SceneModel.trainLength(kind).toFloat())
            scale(-1f, 1f, pivot = Offset.Zero)
        }) {
            endCar(kind, p)
            if (lit) drawCircle(Color(0xFFFF6A5A), 0.9f, Offset(-1.8f, -4.2f))
        }
    }
    endCar(kind, p)
}

private val Cloud = Path().apply {
    addRoundRect(RoundRect(0f, 4f, 34f, 12f, CornerRadius(4f)))
    addOval(Rect(Offset(11f, 5f), 6f))
    addOval(Rect(Offset(21f, 3f), 7.5f))
}

private class CloudAt(val x: Double, val up: Double, val scale: Float)

private val Clouds = listOf(CloudAt(60.0, 16.0, 1f), CloudAt(250.0, 9.0, 0.75f), CloudAt(430.0, 20.0, 0.9f))

private fun mod(v: Double, m: Double) = ((v % m) + m) % m

/**
 * The scene (abeona-design.md §7): a landscape with a railway through it,
 * under the real sky of the moment. Large when nothing is urgent; on the
 * travel day a strip whose bar under the track is the whole [journey], in a
 * frame that never moves: every ride and change, the one in focus taking the
 * room, the others stubs (SceneModel.journeyBar, barLayout). The web's Scene.svelte draws the same layers
 * in the same units, both from the model in SceneModel.kt / scene.ts.
 *
 * The sky is reckoned at [lat]/[lon] (the trip's current stop), Frankfurt
 * when there is none, at [at] or the clock. Clouds drift and, while riding,
 * the land passes, faster with [speedKmh]. That clock is an infinite
 * animation to Compose: with animations off it never starts, and the test
 * policy holds it still, so screenshots are a still picture.
 *
 * The bar and the train move by the same frames, on from [now] (the card's
 * moment) with the expected times: continuously, not minute by minute. A
 * time moved by live data glides to its new place; when the focus moves,
 * each part grows or shrinks to its new width (the bar's ends stay put)
 * while the old train drives out and the next one in. With animations off
 * all of it is simply set, at [now]. Only the drawing reads the frames. A
 * figure on the bar is a badge, so it never vanishes into the line under it.
 */
@Composable
fun Scene(
    state: SceneState,
    modifier: Modifier = Modifier,
    size: SceneSize = SceneSize.Header,
    /** Which train the ride is (SceneModel.trainKind). */
    train: TrainKind = TrainKind.Ice,
    /** The strip's bar: the journey's rides with their expected times, and where the traveller is in it. */
    journey: BarJourney? = null,
    /** The moment [journey] is drawn at; the frames carry it on. The clock when left out. */
    now: Instant? = null,
    speedKmh: Double? = null,
    at: Instant? = null,
    lat: Double? = null,
    lon: Double? = null,
    /** A still picture whatever the settings: drawn once, into an image to share. */
    still: Boolean = false,
) {
    val context = LocalContext.current
    // Animations off (animator duration scale 0) asks for a still picture: the clock never starts.
    val reduced = still || remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    // Off the frame clock, like rememberTicker: a test advancing that clock is not kept busy.
    val clock by produceState(SceneClock.now()) {
        while (true) {
            withContext(Dispatchers.Default) { delay(60_000) }
            value = SceneClock.now()
        }
    }
    val moment = at?.toEpochMilli() ?: clock
    val sky = remember(moment, lat, lon) { SceneModel.skyLight(moment, lat ?: SceneModel.FRANKFURT_LAT, lon ?: SceneModel.FRANKFURT_LON) }
    val palette = remember(dark, sky.light) { SceneModel.scenePalette(dark, sky.light) }
    val phase = SceneModel.moonPhase(moment)

    val height = size.height
    val farHills = remember(height) { svg(SceneModel.hillPath(SceneModel.HillKind.Far, SceneModel.TILE, height)) }
    val nearHills = remember(height) { svg(SceneModel.hillPath(SceneModel.HillKind.Near, SceneModel.TILE, height)) }

    // How far the land has passed, in units; and the clouds. Seconds since the far train set off, or -1.
    var travel by remember { mutableDoubleStateOf(0.0) }
    var drift by remember { mutableDoubleStateOf(0.0) }
    var farTrain by remember { mutableDoubleStateOf(-1.0) }
    val flash = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val currentState by rememberUpdatedState(state)
    val currentSpeed by rememberUpdatedState(speedKmh)

    // The bar's moment: [now], carried on by the frames since it was given.
    val rail = journey != null && journey.rides.isNotEmpty() && size == SceneSize.Strip
    val base = remember(now) { now?.toEpochMilli() ?: System.currentTimeMillis() }
    var since by remember(base) { mutableLongStateOf(0L) }
    LaunchedEffect(base, rail, reduced) {
        if (!rail || reduced) return@LaunchedEffect
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) since = withInfiniteAnimationFrameMillis { it } - start
    }
    val painted = remember { Painted() }
    val changes = remember { Changes() }
    val morph = remember { Animatable(1f) }
    val glide = remember { Animatable(1f) }
    val swap = remember { Animatable(1f) }
    // A new journey: the same state with moved times glides; another state, or another ride, morphs.
    val key = journey?.takeIf { rail }?.let { "${it.state}|${it.current}|${it.rides.size}" }
    val times = journey?.rides
    LaunchedEffect(key, times) {
        val lastKey = changes.key
        val lastTimes = changes.times
        changes.key = key
        changes.times = times
        val before = painted.bar
        if (key == null || lastKey == null || before == null || reduced || journey == null) return@LaunchedEffect
        if (key != lastKey) {
            changes.morphFrom = before
            changes.glideFraction = 0.0
            glide.snapTo(1f)
            if (lastKey.split('|')[1] != journey.current.toString() || painted.train != train) {
                changes.swapKind = painted.train
                changes.swapX = painted.trainX
                launch {
                    swap.snapTo(0f)
                    swap.animateTo(1f, tween(SWAP_MS, easing = LinearEasing))
                }
            }
            morph.snapTo(0f)
            morph.animateTo(1f, tween(SceneModel.MORPH_MS, easing = LinearEasing))
        } else if (times != lastTimes) {
            changes.glideFraction = before.bar.fraction - SceneModel.journeyBar(journey, base + since).fraction
            glide.snapTo(0f)
            glide.animateTo(1f, tween(SceneModel.GLIDE_MS, easing = LinearEasing))
        }
    }

    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        var last = withInfiniteAnimationFrameMillis { it }
        var velocity = 0.0
        var idleFor = 0.0
        while (true) {
            val t = withInfiniteAnimationFrameMillis { it }
            val dt = min(0.1, (t - last) / 1000.0)
            last = t
            // The land eases into and out of motion with the train.
            val target = if (currentState == SceneState.Riding) SceneModel.travelSpeed(currentSpeed) else 0.0
            velocity += (target - velocity) * min(1.0, dt * 1.5)
            if (velocity > 0.05) travel += velocity * dt
            drift += dt * 2
            if (currentState == SceneState.Idle) {
                if (farTrain >= 0) {
                    farTrain += dt
                    if (farTrain > 10) farTrain = -1.0
                } else {
                    idleFor += dt
                    if (idleFor > 18) {
                        idleFor = 0.0
                        farTrain = 0.0
                    }
                }
            } else {
                farTrain = -1.0
            }
        }
    }

    val ground = if (rail) SceneModel.RAIL_WITH_BAR else SceneModel.RAIL
    val trainScale = size.trainScale.toFloat()
    val lightR = when (size) {
        SceneSize.Strip -> 8f
        SceneSize.Header -> 11f
        SceneSize.Full -> 16f
    }
    val hasTrain = state != SceneState.Idle && state != SceneState.Stopped
    val atPlatform = !rail && (state == SceneState.Parked || state == SceneState.Waiting || state == SceneState.Arrived)
    val lit = state != SceneState.Parked && state != SceneState.Idle
    // Where the train's nose is: on the bar where the ride is (as last drawn), else a little past the middle, its cars running off to the left.
    fun trainX(width: Float): Float =
        if (rail) painted.trainX else SceneModel.trainNoseX(train, size.trainScale, width.toDouble()).toFloat()

    val label = when {
        rail && journey != null -> SceneModel.journeyBar(journey, base).let { bar ->
            if (bar.focus != null && bar.parts[bar.focus] == BarPart.Change) "Changing trains" else "${(bar.fraction * 100).roundToInt()} % of the ride done"
        }
        sky.night -> SceneModel.moonPhaseName(phase)
        sky.glow > 0.5 -> if (sky.across < 0.5) "Sunrise" else "Sunset"
        else -> "Daylight"
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .semantics { contentDescription = label }
            .pointerInput(size, rail, hasTrain, sky.across, train) {
                detectTapGestures { tap ->
                    val x = tap.x / density
                    val y = tap.y / density
                    val width = this.size.width / density
                    val light = SceneModel.lightPosition(sky.across, width.toDouble(), height.toDouble())
                    val tx = trainX(width)
                    val onTrain = hasTrain && x in (tx - SceneModel.trainLength(train).toFloat() * trainScale)..tx &&
                        y in (height - ground - train.spec.height.toFloat() * trainScale)..(height - ground.toFloat())
                    // A tap on the train flashes its headlight; one on the sun or moon makes it swell.
                    val animate = when {
                        onTrain -> flash
                        hypot(x - light.x, y - (height - light.up)) <= lightR * 1.8 -> pulse
                        else -> null
                    }
                    if (animate != null) {
                        scope.launch {
                            animate.animateTo(1f, tween(200))
                            delay(500)
                            animate.animateTo(0f, tween(200))
                        }
                    }
                }
            },
    ) {
        scale(density, density, pivot = Offset.Zero) {
            val width = this.size.width / density
            val h = height.toFloat()
            val railY = h - ground
            val land = size.landScale.toFloat()
            val c = palette
            val accent = colour(c.accent)
            val railColour = colour(c.rail)
            val mast = colour(c.mast)

            // The land's tiles and a train at the strip's edge reach past the box: all of it is cut to the box but the walker's
            // badge, which sits on the bar near the bottom edge and may reach a little below it, into the card.
            var badge: Triple<Float, Color, Float>? = null
            val badgeY = h - SceneModel.BAR_UP
            drawContext.canvas.save()
            drawContext.transform.clipRect(0f, 0f, width, h)

            // The sky fades into the surface behind it at its top edge, so the scene has no box.
            val skyColour = colour(c.sky)
            drawRect(
                Brush.verticalGradient(0f to skyColour.copy(alpha = 0f), 0.55f to skyColour.copy(alpha = 0.85f), 1f to skyColour, startY = 0f, endY = h),
                size = Size(width, h),
            )
            if (sky.glow > 0) {
                val glow = colour(c.glow)
                drawRect(
                    Brush.verticalGradient(0.3f to glow.copy(alpha = 0f), 1f to glow.copy(alpha = (0.85 * sky.glow).toFloat()), startY = 0f, endY = railY),
                    size = Size(width, railY),
                )
            }

            if (sky.light < 0.6) {
                val star = colour(c.star).copy(alpha = (1 - sky.light / 0.6).toFloat())
                SceneModel.STARS.forEach { drawCircle(star, it.r.toFloat(), Offset((it.x * width).toFloat(), (it.y * (h - 30)).toFloat())) }
            }

            // The sun by day, the moon in its phase by night.
            val lightAt = SceneModel.lightPosition(sky.across, width.toDouble(), h.toDouble())
            val centre = Offset(lightAt.x.toFloat(), (h - lightAt.up).toFloat())
            scale(1f + 0.25f * pulse.value, pivot = centre) {
                if (sky.night) {
                    val moon = colour(c.moon)
                    drawCircle(moon.copy(alpha = 0.22f), lightR, centre)
                    drawPath(svg(SceneModel.moonLitPath(phase, centre.x.toDouble(), centre.y.toDouble(), lightR.toDouble())), moon)
                } else {
                    val sun = colour(c.sun)
                    drawCircle(sun.copy(alpha = 0.2f), lightR * 1.5f, centre)
                    drawCircle(sun, lightR, centre)
                }
            }

            // Clouds always drift.
            val cloud = colour(c.cloud).copy(alpha = 0.9f)
            Clouds.forEachIndexed { i, at ->
                val span = width + 120.0
                val x = (mod(at.x + drift * (0.6 + i * 0.2), span) - 60).toFloat()
                val y = max(6.0, h - 30 - at.up * (h / 56.0)).toFloat()
                withTransform({
                    translate(x, y)
                    scale(at.scale * if (size == SceneSize.Full) 1.6f else 1f, pivot = Offset.Zero)
                }) { drawPath(Cloud, cloud) }
            }

            // The land grows from the rail, so the track stays where it is.
            val tiles = ceil(width / land / SceneModel.TILE).toInt() + 1
            fun DrawScope.onLand(shift: Double, block: DrawScope.() -> Unit) = withTransform({
                scale(land, land, pivot = Offset(0f, railY))
                translate(left = -shift.toFloat())
            }, block)

            // Far hills, and now and then a small train on them when nothing is planned.
            val farShift = mod(travel * SceneModel.PARALLAX_FAR, SceneModel.TILE.toDouble())
            onLand(farShift) {
                repeat(tiles) { t -> translate(left = t * SceneModel.TILE.toFloat()) { drawPath(farHills, colour(c.far)) } }
            }
            if (farTrain >= 0) {
                val x = -40 + (width / land + 80) * (farTrain / 10)
                val y = h - SceneModel.hillHeight(SceneModel.HillKind.Far, x + farShift) + 1
                onLand(0.0) {
                    translate(x.toFloat(), y.toFloat()) {
                        drawRoundRect(colour(c.trainBody).copy(alpha = 0.8f), Offset(0f, -4f), Size(24f, 4f), CornerRadius(2f))
                        drawRect(colour(c.trainRed).copy(alpha = 0.8f), Offset(0f, -2.2f), Size(24f, 0.7f))
                    }
                }
            }

            // Near hills with their trees.
            onLand(mod(travel * SceneModel.PARALLAX_NEAR, SceneModel.TILE.toDouble())) {
                val tree = colour(c.tree)
                repeat(tiles) { t ->
                    translate(left = t * SceneModel.TILE.toFloat()) {
                        drawPath(nearHills, colour(c.near))
                        SceneModel.TREES.forEach {
                            val base = (h - SceneModel.hillHeight(SceneModel.HillKind.Near, it.x) + 1).toFloat()
                            val r = it.r.toFloat()
                            val x = it.x.toFloat()
                            drawRect(tree, Offset(x - 0.6f, base - r * 1.6f), Size(1.2f, r * 1.6f))
                            drawCircle(tree, r, Offset(x, base - r * 2))
                        }
                    }
                }
            }

            // The ground, the masts and their wire, the sleepers.
            drawRect(colour(c.ground), Offset(0f, railY), Size(width, ground.toFloat()))
            val mastHeight = min(trainScale, 1.6f)
            translate(left = -mod(travel * SceneModel.PARALLAX_TRACK, SceneModel.MAST_EVERY.toDouble()).toFloat()) {
                repeat(ceil(width / SceneModel.MAST_EVERY).toInt() + 2) { m ->
                    val x = m * SceneModel.MAST_EVERY + 20f
                    val top = railY - 26 * mastHeight
                    drawLine(mast.copy(alpha = 0.7f), Offset(x, railY), Offset(x, top), strokeWidth = 1f)
                    drawLine(mast.copy(alpha = 0.7f), Offset(x, top), Offset(x + 7, top), strokeWidth = 1f)
                }
            }
            drawLine(mast.copy(alpha = 0.6f), Offset(0f, railY - 24 * mastHeight), Offset(width, railY - 24 * mastHeight), strokeWidth = 0.6f)
            // In the strip the progress bar lies where the sleepers would.
            if (!rail) translate(left = -mod(travel * SceneModel.PARALLAX_TRACK, SceneModel.SLEEPER_EVERY.toDouble()).toFloat()) {
                repeat(ceil(width / SceneModel.SLEEPER_EVERY).toInt() + 2) { s ->
                    drawRoundRect(railColour.copy(alpha = 0.3f), Offset(s * SceneModel.SLEEPER_EVERY.toFloat(), railY + 3), Size(6f, 2f), CornerRadius(1f))
                }
            }

            val platformX = SceneModel.trainNoseX(train, size.trainScale, width.toDouble()).toFloat()
            if (atPlatform) {
                // The platform under the train, its shelter and lamp.
                val from = -4f
                val to = platformX + 52
                val top = railY - 3
                drawRoundRect(colour(c.platform), Offset(from, top), Size(to - from, 5f), CornerRadius(1.5f))
                drawLine(mast, Offset(to - 30, top), Offset(to - 30, top - 13 * trainScale), strokeWidth = 1.4f, cap = StrokeCap.Round)
                drawLine(mast, Offset(to - 12, top), Offset(to - 12, top - 13 * trainScale), strokeWidth = 1.4f, cap = StrokeCap.Round)
                drawLine(mast, Offset(to - 36, top - 13 * trainScale), Offset(to - 6, top - 13 * trainScale), strokeWidth = 2.4f, cap = StrokeCap.Round)
                if (state == SceneState.Arrived) {
                    val pole = top - 20 * trainScale
                    drawLine(mast, Offset(to - 44, top), Offset(to - 44, pole), strokeWidth = 1.2f)
                    drawPath(
                        Path().apply {
                            moveTo(to - 44, pole)
                            relativeLineTo(9 * trainScale, 3 * trainScale)
                            relativeLineTo(-9 * trainScale, 3 * trainScale)
                            close()
                        },
                        accent,
                    )
                }
            }

            if (state == SceneState.Idle || state == SceneState.Stopped) {
                // The signal: green when the line is clear, red when nothing runs.
                val x = if (rail) width - 14 else width * 0.72f
                val stopped = state == SceneState.Stopped
                drawLine(mast, Offset(x, railY), Offset(x, railY - 17 * trainScale), strokeWidth = 1.4f)
                drawRoundRect(railColour, Offset(x - 3 * trainScale, railY - 26 * trainScale), Size(6 * trainScale, 11 * trainScale), CornerRadius(3 * trainScale))
                drawCircle(if (stopped) Color(0xFFFF5A4A) else Color(0xFF57D39A), 1.9f * trainScale, Offset(x, railY - (if (stopped) 23 else 18) * trainScale))
            }

            drawLine(railColour.copy(alpha = 0.85f), Offset(0f, railY), Offset(width, railY), strokeWidth = 2f)

            // The bar this frame: the journey at the moment, gliding from where live data moved it, morphing from the last focus.
            var trainKind = train
            var tx = SceneModel.trainNoseX(train, size.trainScale, width.toDouble()).toFloat()
            if (rail && journey != null) {
                var bar = SceneModel.journeyBar(journey, base + since)
                if (!reduced && glide.value < 1f) {
                    bar = bar.copy(fraction = clamp01(bar.fraction + changes.glideFraction * (1 - easeOut(glide.value.toDouble()))))
                }
                val target = DrawnBar(SceneModel.barLayout(bar.parts, bar.focus, width.toDouble()), bar)
                val from = changes.morphFrom
                val shown = if (from != null && !reduced && morph.value < 1f) between(from, target, standard(morph.value.toDouble())) else target
                painted.bar = shown
                val b = shown.bar
                val at = shown.at
                val focus = b.focus
                // How far the train has gone on the focused ride: nothing yet while waiting for it.
                val fill = if (focus == null || b.parts[focus] != BarPart.Ride || state == SceneState.Waiting) 0.0 else b.fraction

                // The journey under the track, its ends where they always are; each element with one meaning. The train in
                // focus is a line with its stops as dots and a dot at either end ("board here … get off here"), filled up to the
                // train; every other train a capsule without dots; a change out of focus the gap between two capsules, the
                // change in focus a dashed line in its confidence colour. Done in burgundy, ahead in the track's colour. The
                // destination at the far right: the end ring on the train in focus, else a ring cap on the last capsule.
                val barY = h - SceneModel.BAR_UP
                fun x(part: BarSpan, f: Double) = lerp(part.from, part.to, f).toFloat()
                fun tone(t: BarTone) = when (t) {
                    BarTone.Ok -> colour(c.ok)
                    BarTone.Watch -> colour(c.watch)
                    BarTone.Bad -> colour(c.bad)
                    BarTone.Neutral -> railColour
                }
                fun node(x: Float, passed: Boolean, alpha: Float) {
                    if (alpha <= 0f) return
                    drawCircle((if (passed) accent else colour(c.dot)).copy(alpha = alpha), SceneModel.BAR_NODE.toFloat(), Offset(x, barY))
                    drawCircle((if (passed) accent else railColour).copy(alpha = alpha), SceneModel.BAR_NODE.toFloat(), Offset(x, barY), style = Stroke(1.5f))
                }
                val was = shown.was
                val moved = shown.t
                // How much part i shows of being in focus (its dots, a change's dashes and walker): gained over the last 40 % of
                // a morph, lost over the first 40 %.
                fun focusShown(i: Int): Float {
                    val now = i == focus
                    val then = if (was != null) was.focus == i else now
                    return when {
                        now && then -> 1.0
                        now -> clamp01((moved - 0.6) / 0.4)
                        then -> clamp01(1 - moved / 0.4)
                        else -> 0.0
                    }.toFloat()
                }
                at.forEachIndexed { i, part ->
                    val shows = focusShown(i)
                    if (b.parts[i] == BarPart.Ride) {
                        // Thick in focus, a capsule otherwise; round caps inside the part's stretch, so the bar's ends stay put.
                        val inFocus = lerp(if (was != null) (if (was.focus == i) 1.0 else 0.0) else (if (i == focus) 1.0 else 0.0), if (i == focus) 1.0 else 0.0, moved)
                        val thick = lerp(SceneModel.CAPSULE, SceneModel.BAR.toDouble(), inFocus).toFloat()
                        val done = i < b.done
                        val last = i == at.lastIndex
                        val capped = last && inFocus < 0.5
                        val alpha = if (done) 1f else lerp(0.45, 0.3, inFocus).toFloat()
                        val ink = if (done) accent else railColour
                        val x1 = part.from.toFloat() + thick / 2
                        val x2 = if (capped) part.to.toFloat() else maxOf(x1, part.to.toFloat() - thick / 2)
                        drawLine(ink.copy(alpha = ink.alpha * alpha), Offset(x1, barY), Offset(x2, barY), strokeWidth = thick, cap = StrokeCap.Round)
                        if (i == focus && !done && fill > 0) {
                            drawLine(accent, Offset(x1, barY), Offset(maxOf(x1, x(part, fill) - thick / 2), barY), strokeWidth = thick, cap = StrokeCap.Round)
                        }
                        if (shows > 0f) {
                            // Its stops and its two ends as dots; at the destination the ring below instead.
                            val stops = if (i == focus) b.stops else was?.stops.orEmpty()
                            val reached = if (i == focus) (if (done) 1.0 else fill) else 1.0
                            stops.forEachIndexed { k, stop ->
                                if (!(last && k == stops.lastIndex)) node(x(part, stop), reached > 0 && stop <= reached, shows)
                            }
                        }
                        if (last) {
                            // The destination: the end ring on the ride in focus, else a small ring capping the last capsule.
                            val end = Offset(part.to.toFloat(), barY)
                            if (capped) {
                                drawCircle(colour(c.dot), SceneModel.DESTINATION_CAP.toFloat(), end)
                                drawCircle(ink.copy(alpha = if (done) 1f else maxOf(alpha, 0.6f)), SceneModel.DESTINATION_CAP.toFloat(), end, style = Stroke(2f))
                            } else {
                                drawCircle(colour(c.dot), SceneModel.BAR_DOT_END.toFloat(), end)
                                drawCircle(accent, SceneModel.BAR_DOT_END.toFloat(), end, style = Stroke(2f))
                            }
                        }
                    } else if (shows > 0f && (b.tone ?: was?.tone) != null) {
                        // The change in focus: its line in the change's confidence colour.
                        drawLine(
                            tone(b.tone ?: was!!.tone!!).copy(alpha = shows), Offset(part.from.toFloat(), barY), Offset(part.to.toFloat(), barY),
                            strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)),
                        )
                    }
                }
                // The walker as a badge, so it never vanishes into the line under it: a disc of the card's surface, a ring and
                // the figure in the colour of the part it is on, lifted by a soft shadow. On the change, from the arrival to the
                // next departure as the change time passes; before leaving, waiting on the origin.
                badge = when {
                    // It comes and goes with the change's focus, as the dashes do.
                    focus != null && b.parts[focus] == BarPart.Change -> Triple(x(at[focus], b.fraction), tone(b.tone ?: BarTone.Neutral), focusShown(focus))
                    b.walkerAtStart -> Triple(at[0].from.toFloat(), tone(BarTone.Neutral), 1f)
                    else -> null
                }

                // The train over the point of the focused ride it has reached; while changing at the start of the next ride,
                // waiting; during a swap the old one on its way out, then the new one on its way in.
                val over = when {
                    focus == null -> null
                    b.parts[focus] == BarPart.Ride -> lerp(at[focus].from, at[focus].to, fill)
                    else -> at.getOrNull(focus + 1)?.from ?: at[focus].to
                }
                val rest = over?.let { SceneModel.trainOnBar(it, train, size.trainScale).toFloat() } ?: tx
                tx = rest
                if (!reduced && swap.value < 1f) {
                    val p = swap.value.toDouble()
                    val out = 0.45
                    if (p < out) {
                        trainKind = changes.swapKind
                        val gone = width + SceneModel.trainLength(trainKind).toFloat() * trainScale + 8
                        tx = lerp(changes.swapX.toDouble(), gone.toDouble(), (p / out) * (p / out)).toFloat()
                    } else {
                        tx = lerp(0.0, rest.toDouble(), easeOut((p - out) / (1 - out))).toFloat()
                    }
                }
                painted.train = train
                painted.trainX = rest
            }

            if (hasTrain) {
                withTransform({
                    translate(tx, railY)
                    scale(trainScale, trainScale, pivot = Offset.Zero)
                }) {
                    drawTrain(
                        trainKind,
                        TrainPaint(
                            body = colour(c.trainBody),
                            line = colour(c.trainLine),
                            red = colour(c.trainRed),
                            window = colour(c.trainWindow).copy(alpha = if (lit) 1f else if (trainKind == TrainKind.Ice) 0.55f else 0.6f),
                            bogie = railColour,
                        ),
                        lit,
                    )
                    if (lit) drawCircle(Color(0xFFFFE3A6), 0.9f + 1.5f * flash.value, Offset(-1.8f, -4.2f))
                }
            }
            drawContext.canvas.restore()

            badge?.takeIf { it.third > 0f }?.let { (wx, ink, a) ->
                val d = SceneModel.BADGE.toFloat()
                drawCircle(Color.Black.copy(alpha = (if (dark) 0.45f else 0.16f) * a), d / 2 + 0.6f, Offset(wx, badgeY + 0.8f))
                drawCircle(colour(c.dot).copy(alpha = a), d / 2 - 1, Offset(wx, badgeY))
                drawCircle(ink.copy(alpha = ink.alpha * a), d / 2 - 1, Offset(wx, badgeY), style = Stroke(2f))
                withTransform({
                    translate(wx - 5.4f, badgeY - 5.5f)
                    scale(0.45f, 0.45f, pivot = Offset.Zero)
                }) { drawPath(Walker, ink.copy(alpha = ink.alpha * a)) }
            }
        }
    }
}
