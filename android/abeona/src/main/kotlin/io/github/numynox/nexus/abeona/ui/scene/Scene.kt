package io.github.numynox.nexus.abeona.ui.scene

import android.provider.Settings
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/** A stop on the strip's rail: where on the ride it lies, 0 to 1. */
data class SceneStop(val fraction: Double, val name: String? = null)

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
 * travel day a strip whose track is the ride's rail ([stops] and [fraction]).
 * The web's Scene.svelte draws the same layers in the same units, both from
 * the model in SceneModel.kt / scene.ts.
 *
 * The sky is reckoned at [lat]/[lon] (the trip's current stop), Frankfurt
 * when there is none, at [at] or the clock. Clouds drift and, while riding,
 * the land passes, faster with [speedKmh]. That clock is an infinite
 * animation to Compose: with animations off it never starts, and the test
 * policy holds it still, so screenshots are a still picture.
 */
@Composable
fun Scene(
    state: SceneState,
    modifier: Modifier = Modifier,
    size: SceneSize = SceneSize.Header,
    /** Which train the ride is (SceneModel.trainKind). */
    train: TrainKind = TrainKind.Ice,
    stops: List<SceneStop>? = null,
    fraction: Double = 0.0,
    speedKmh: Double? = null,
    at: Instant? = null,
    lat: Double? = null,
    lon: Double? = null,
) {
    val context = LocalContext.current
    val reduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
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

    val rail = stops != null && stops.size > 1 && size == SceneSize.Strip
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
    // Where the train's nose is: on the rail where the ride is, else a little past the middle, its cars running off to the left.
    fun trainX(width: Float): Float =
        if (rail) SceneModel.railX(fraction, width.toDouble()).toFloat() else SceneModel.trainNoseX(train, size.trainScale, width.toDouble()).toFloat()

    val label = when {
        rail -> "${(fraction.coerceIn(0.0, 1.0) * 100).roundToInt()} % of the ride done"
        sky.night -> SceneModel.moonPhaseName(phase)
        sky.glow > 0.5 -> if (sky.across < 0.5) "Sunrise" else "Sunset"
        else -> "Daylight"
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            // The land's tiles and a train at the first stop reach past the box.
            .clipToBounds()
            .semantics { contentDescription = label }
            .pointerInput(size, rail, hasTrain, fraction, sky.across, train) {
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

            val tx = trainX(width)
            if (atPlatform) {
                // The platform under the train, its shelter and lamp.
                val from = -4f
                val to = tx + 52
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
            if (rail && stops != null) {
                // The ride's progress: a bar under the track with a dot per stop, the done part orange up to the train's nose.
                val bar = SceneModel.BAR.toFloat()
                fun at(f: Double) = Offset(SceneModel.railX(f, width.toDouble()).toFloat(), h - SceneModel.BAR_UP)
                drawLine(railColour.copy(alpha = 0.28f), at(0.0), at(1.0), strokeWidth = bar, cap = StrokeCap.Round)
                if (fraction > 0 && state != SceneState.Waiting) drawLine(accent, at(0.0), at(fraction), strokeWidth = bar, cap = StrokeCap.Round)
                stops.forEachIndexed { i, stop ->
                    val end = i == 0 || i == stops.lastIndex
                    val passed = state != SceneState.Waiting && stop.fraction <= fraction
                    val r = (if (end) SceneModel.BAR_DOT_END else SceneModel.BAR_DOT).toFloat()
                    drawCircle(if (passed) accent else colour(c.dot), r, at(stop.fraction))
                    drawCircle(if (passed || end) accent else railColour, r, at(stop.fraction), style = Stroke(2f))
                }
            }

            if (hasTrain) {
                withTransform({
                    translate(tx, railY)
                    scale(trainScale, trainScale, pivot = Offset.Zero)
                }) {
                    drawTrain(
                        train,
                        TrainPaint(
                            body = colour(c.trainBody),
                            line = colour(c.trainLine),
                            red = colour(c.trainRed),
                            window = colour(c.trainWindow).copy(alpha = if (lit) 1f else if (train == TrainKind.Ice) 0.55f else 0.6f),
                            bogie = railColour,
                        ),
                        lit,
                    )
                    if (lit) drawCircle(Color(0xFFFFE3A6), 0.9f + 1.5f * flash.value, Offset(-1.8f, -4.2f))
                }
            }
        }
    }
}
