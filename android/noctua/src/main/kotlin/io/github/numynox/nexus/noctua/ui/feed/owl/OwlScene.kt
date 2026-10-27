package io.github.numynox.nexus.noctua.ui.feed.owl

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/** The scene's 360 × 470 units fitted into a size in pixels: whole, centred across, the branch at the foot. */
class SceneFrame(width: Float, height: Float) {
    val scale = minOf(width / SceneWidth, height / SceneHeight)
    /** Extra units beside the scene, half on each side, when the box is wider than it. */
    val left = (width / scale - SceneWidth) / 2f
    /** Extra units of sky above the scene when the box is taller than it. */
    val above = height / scale - SceneHeight
    /**
     * How far the moon and the stars rise into that extra sky: a tall screen
     * would otherwise keep them low, crowding the owl under an empty sky.
     */
    val skyLift = minOf(above * 0.6f, 260f)

    fun toPx(x: Float, y: Float) = Offset((x + left) * scale, (y + above) * scale)
    fun toUnits(at: Offset) = Offset(at.x / scale - left, at.y / scale - above)

    /** A point of the moon and stars' layer — lifted by [skyLift] — in pixels. */
    fun skyToPx(x: Float, y: Float) = toPx(x, y - skyLift)
    /** A point in pixels on the moon and stars' layer. */
    fun toSky(at: Offset) = toUnits(at) + Offset(0f, skyLift)

    companion object {
        const val SceneWidth = 360f
        const val SceneHeight = 470f
    }
}

/** What the sky shows besides the owl. */
class SkyContent(
    val night: Boolean,
    val moonPhase: Double,
    /** Today's reading, in order: a star each. */
    val stars: List<SkyPoint>,
    val starSizes: List<Float>,
    val selectedStar: Int?,
    val shootingStar: ShootingStar?,
)

/** A shooting star crossing from ([x], [y]), started at [start] in the owl's time. */
data class ShootingStar(val x: Float, val y: Float, val start: Float) {
    /** Where its head is at [time], or null once it has gone. */
    fun headAt(time: Float): Offset? {
        val p = (time - start) / Duration
        if (p !in 0f..1f) return null
        return Offset(x - 190f * p, y + 105f * p)
    }

    companion object {
        const val Duration = 1.25f
    }
}

/** The owl's own colours, from its logo: the same in light and dark themes. */
private object OwlColors {
    val Body = Color(0xFF2F3389)
    val Wing = Color(0xFF262A74)
    val Belly = Color(0xFFF3E7CD)
    val Chevron = Color(0xFFD6C29A)
    val Iris = Color(0xFFF4BD2A)
    val Pupil = Color(0xFF1A1C45)
    val Beak = Color(0xFFE59C34)
    val Brow = Color(0xFF15174A)
    val BackFeather = Color(0xFF4A50B0)
    val Rim = Color(0x29D2D7FF)
    val Bark = Color(0xFF6B4A2F)
    val Leaf = Color(0xFF7AA35A)
    val Bubble = Color(0xFFFAF6EA)
}

/** Sky colours for night and day: a deep navy, and a pale morning blue fading into warm light. */
class SkyColors(val top: Color, val bottom: Color, val ground: Color, val star: Color, val text: Color, val mini: Color)

val NightSky = SkyColors(Color(0xFF080A22), Color(0xFF1B2056), Color(0xFF0F1236), Color(0xFFFFE7A6), Color(0xFFA9ACD0), Color(0xFF0A0C22))
val DaySky = SkyColors(Color(0xFFBCD3EA), Color(0xFFEFE7D4), Color(0xFFE2DBC6), Color(0xFF8B7845), Color(0xFF5D6085), Color(0xFF2F3389))

/** Parses SVG path data — the owl's shapes are the web owl's paths, unchanged. */
private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

private object OwlShapes {
    val body = svg("M -50 -24 C -58 -84 -38 -118 0 -118 C 38 -118 58 -84 50 -24 C 46 -2 26 4 0 4 C -26 4 -46 -2 -50 -24 Z")
    val wingLeft = svg("M -40 -98 C -66 -84 -68 -38 -48 -4 C -40 -30 -34 -66 -40 -98 Z")
    val wingRight = svg("M 40 -98 C 66 -84 68 -38 48 -4 C 40 -30 34 -66 40 -98 Z")
    val chevrons = svg("M -17 -74 q 5 5 10 0 M 7 -74 q 5 5 10 0 M -5 -62 q 5 5 10 0 M -21 -50 q 5 5 10 0 M 11 -50 q 5 5 10 0 M -5 -38 q 5 5 10 0 M -15 -26 q 5 5 10 0 M 5 -26 q 5 5 10 0")
    val feet = svg("M -20 -2 l -3 7 M -15 -2 l 0 8 M -10 -2 l 3 7 M 10 -2 l -3 7 M 15 -2 l 0 8 M 20 -2 l 3 7")
    val tuftLeft = svg("M -46 -156 L -38 -190 L -22 -168 Z")
    val tuftRight = svg("M 46 -156 L 38 -190 L 22 -168 Z")
    val closedLeft = svg("M -29 -137 q 10 7 20 0")
    val closedRight = svg("M 9 -137 q 10 7 20 0")
    val browsPath = svg("M -35 -161 L -9 -151 M 35 -161 L 9 -151")
    val beak = svg("M -6 -125 L 6 -125 L 0 -112 Z")
    val back = svg("M -24 -150 q 6 6 12 0 M 12 -150 q 6 6 12 0 M -6 -134 q 6 6 12 0 M -24 -120 q 6 6 12 0 M 12 -120 q 6 6 12 0")
    val miniWings = svg("M -2 -1 L -14 -7 L -6 2 Z M 2 -1 L 14 -7 L 6 2 Z")
    val bubbleTail = svg("M 52 -201 l -6 9 l 14 -9 Z")
    val hills = svg("M -220 470 L -220 442 Q -120 432 -20 442 Q 60 450 110 440 Q 180 430 250 440 Q 320 450 380 438 Q 480 430 580 440 L 580 470 Z")
    // A shade wider than the white of the eye, so a shut lid leaves no rim of it.
    fun eye(side: Float) = Path().apply { addOval(Rect(Offset(19f * side, -138f), 16f)) }
    val eyeLeft = eye(-1f)
    val eyeRight = eye(1f)
}

/** Where the owl's feet rest on the branch, bent by [bend]. */
const val FeetY = 413.5f

/** The branch: a curve from far left to far right, sagging by [bend] where the owl sits. */
fun branchY(x: Float, bend: Float): Float {
    val t = (x + 220f) / 800f
    return (1 - t) * (1 - t) * 436f + 2 * t * (1 - t) * (418f + 2 * bend) + t * t * 402f
}

private val LeafSpots = listOf(Triple(38f, -30f, -1f), Triple(58f, 24f, 1f), Triple(74f, -20f, 1f), Triple(300f, 210f, -1f), Triple(318f, 160f, 1f))

private class BackgroundStar(val x: Float, val y: Float, val r: Float, val alpha: Float, val phase: Float, val speed: Float)

/**
 * The night sky, the branch and the owl, drawn from [motion] and [pull] (how
 * far the end of the feed is pulled, 1 at the refresh threshold). Reading
 * [OwlMotion.time] here, in the draw phase, keeps the breathing and the
 * twinkling from recomposing anything.
 */
@Composable
fun OwlScene(motion: OwlMotion, sky: SkyContent, pull: () -> Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val background = remember {
        val random = Random(7)
        List(70) {
            BackgroundStar(
                x = -200f + random.nextFloat() * 760f,
                y = -700f + random.nextFloat() * 1_080f,
                r = 0.5f + random.nextFloat() * 0.9f,
                alpha = 0.35f + random.nextFloat() * 0.5f,
                phase = random.nextFloat() * 6.28f,
                speed = 1f + random.nextFloat() * 1.2f,
            )
        }
    }
    val moon = remember(sky.moonPhase) { svg(moonLitPath(sky.moonPhase, MoonX, MoonY, MoonR)) }
    val colors = if (sky.night) NightSky else DaySky
    Canvas(modifier) {
        val frame = SceneFrame(size.width, size.height)
        motion.skyAbove = frame.above
        motion.skyLift = frame.skyLift
        val time = motion.time
        drawRect(Brush.verticalGradient(listOf(colors.top, colors.bottom)))
        withTransform({
            scale(frame.scale, frame.scale, pivot = Offset.Zero)
            translate(frame.left, frame.above)
        }) {
            if (sky.night) {
                background.forEach { star ->
                    val twinkle = if (motion.reduced) 1f else 0.6f + 0.4f * sin(time * star.speed + star.phase)
                    drawCircle(Color.White.copy(alpha = star.alpha * twinkle), star.r, Offset(star.x, star.y))
                }
            }
            withTransform({ translate(0f, -frame.skyLift) }) {
                drawMoon(moon, sky.night)
                drawSearchingOwl(motion, colors)
                drawConstellation(motion, sky, colors)
                sky.shootingStar?.let { drawShootingStar(it, time) }
            }
            drawPath(OwlShapes.hills, colors.ground)
            drawBranch(motion.bend.value)
            drawOwl(motion, sky.night, pull(), colors, measurer)
        }
    }
}

private fun DrawScope.drawMoon(lit: Path, night: Boolean) {
    val centre = Offset(MoonX, MoonY)
    if (night) {
        drawCircle(Brush.radialGradient(listOf(Color(0x8CFFF6D6), Color(0x00FFF6D6)), centre, 70f), 70f, centre)
    }
    drawCircle(Color.White.copy(alpha = 0.07f), MoonR, centre)
    drawPath(lit, if (night) Color(0xFFF7F0D6) else Color.White)
}

private fun DrawScope.drawSearchingOwl(motion: OwlMotion, colors: SkyColors) {
    val shown = motion.searching.value
    if (shown < 0.01f) return
    val time = motion.time
    val t = ((time - motion.searchStart) / 1.7f).mod(1f)
    val x = MoonX - 95f + t * 190f
    val y = MoonY + 6f - sin(t * PI.toFloat()) * 18f + sin(time * 6.25f) * 2f
    withTransform({ translate(x, y) }) {
        drawOval(colors.mini.copy(alpha = shown), Offset(-6.5f, -5.5f), Size(13f, 11f))
        withTransform({ scale(1f, if (motion.reduced) 1f else sin(time * 18f), pivot = Offset.Zero) }) {
            drawPath(OwlShapes.miniWings, colors.mini.copy(alpha = shown))
        }
    }
}

private fun DrawScope.drawConstellation(motion: OwlMotion, sky: SkyContent, colors: SkyColors) {
    val stars = sky.stars
    if (stars.isEmpty()) return
    val elapsed = motion.reveal.value
    val gap = starDelayMs(stars.size)
    stars.forEachIndexed { i, star ->
        if (i == 0) return@forEachIndexed
        val drawn = ((elapsed - 500f - i * gap) / 500f).coerceIn(0f, 1f)
        if (drawn <= 0f) return@forEachIndexed
        val from = Offset(stars[i - 1].x, stars[i - 1].y)
        val to = Offset(star.x, star.y)
        drawLine(colors.star.copy(alpha = if (sky.night) 0.32f else 0.28f), from, from + (to - from) * drawn, strokeWidth = 1f)
    }
    stars.forEachIndexed { i, star ->
        val shown = ((elapsed - 300f - i * gap) / 600f).coerceIn(0f, 1f)
        if (shown <= 0f) return@forEachIndexed
        val r = sky.starSizes[i]
        val newest = i == stars.lastIndex && !motion.reduced
        val twinkle = if (newest) 0.65f + 0.35f * sin(motion.time * 2.4f) else 1f
        val centre = Offset(star.x, star.y)
        drawCircle(colors.star.copy(alpha = shown * if (i == sky.selectedStar) 0.45f else 0.14f), r * 3.2f, centre)
        drawCircle(colors.star.copy(alpha = shown * twinkle), r, centre)
    }
}

private fun DrawScope.drawShootingStar(star: ShootingStar, time: Float) {
    val head = star.headAt(time) ?: return
    val p = (time - star.start) / ShootingStar.Duration
    val alpha = if (p < 0.12f) p / 0.12f else 1f - (p - 0.12f) / 0.88f
    val tail = head + Offset(46f, -25f)
    drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent), head, tail), head, tail, strokeWidth = 2f, cap = StrokeCap.Round)
    drawCircle(Color.White.copy(alpha = alpha), 1.8f, head)
}

private fun DrawScope.drawBranch(bend: Float) {
    val branch = Path().apply {
        moveTo(-220f, 436f)
        quadraticTo(180f, 418f + 2 * bend, 580f, 402f)
    }
    val twigY = branchY(52f, bend)
    val twig = Path().apply {
        moveTo(52f, twigY + 2f)
        quadraticTo(40f, twigY + 16f, 22f, twigY + 22f)
    }
    drawPath(twig, OwlColors.Bark, style = Stroke(4f, cap = StrokeCap.Round))
    drawPath(branch, OwlColors.Bark, style = Stroke(10f, cap = StrokeCap.Round))
    LeafSpots.forEach { (x, angle, side) ->
        withTransform({
            translate(x, branchY(x, bend) - 2f)
            rotate(angle, pivot = Offset.Zero)
        }) {
            drawOval(OwlColors.Leaf, Offset(side * 9f - 10f, -8.5f), Size(20f, 9f))
        }
    }
}

private fun DrawScope.drawOwl(motion: OwlMotion, night: Boolean, pull: Float, colors: SkyColors, measurer: TextMeasurer) {
    val time = motion.time
    val still = motion.reduced || motion.busy || pull > 0f
    val breath = if (still) 0f else sin(time * 1.05f) * 0.012f
    // The pull drives the owl directly, finger and owl as one; the springs carry the rest.
    val g = pull.coerceIn(0f, 1f)
    val ready = pull >= 1f
    val sx = motion.scaleX.value * (1f + 0.035f * g) * (1f + breath * 0.4f)
    val sy = motion.scaleY.value * (1f - 0.07f * g) * (1f + breath)
    val bend = motion.bend.value + 7f * g
    val feetY = FeetY + bend
    val rim = if (night) Stroke(2f) else null

    withTransform({
        translate(180f + motion.owlX.value, feetY + motion.owlY.value)
        scale(sx, sy, pivot = Offset.Zero)
    }) {
        val wing = if (motion.flapping && !motion.reduced) 38f + sin(time * 20.8f) * 42f else motion.wings.value + if (ready) 10f else 0f
        withTransform({ rotate(wing, Offset(-40f, -96f)) }) { drawPath(OwlShapes.wingLeft, OwlColors.Wing) }
        withTransform({ rotate(-wing, Offset(40f, -96f)) }) { drawPath(OwlShapes.wingRight, OwlColors.Wing) }
        drawPath(OwlShapes.body, OwlColors.Body)
        rim?.let { drawPath(OwlShapes.body, OwlColors.Rim, style = it) }
        drawOval(OwlColors.Belly, Offset(-33f, -90f), Size(66f, 84f))
        drawPath(OwlShapes.chevrons, OwlColors.Chevron, style = Stroke(2f, cap = StrokeCap.Round))
        drawPath(OwlShapes.feet, OwlColors.Beak, style = Stroke(3f, cap = StrokeCap.Round))

        withTransform({
            translate(0f, motion.headLift.value)
            rotate(motion.headTurn.value, Offset(0f, -112f))
            scale(motion.headSpin.value, 1f, Offset(0f, -138f))
        }) {
            val tufts = maxOf(motion.tufts.value, if (ready) 1f else 0f)
            withTransform({ translate(0f, -tufts * 6f) }) {
                withTransform({ rotate(-tufts * 10f, Offset(-34f, -166f)) }) { drawPath(OwlShapes.tuftLeft, OwlColors.Body) }
                withTransform({ rotate(tufts * 10f, Offset(34f, -166f)) }) { drawPath(OwlShapes.tuftRight, OwlColors.Body) }
            }
            drawOval(OwlColors.Body, Offset(-50f, -178f), Size(100f, 80f))
            rim?.let { drawOval(OwlColors.Rim, Offset(-50f, -178f), Size(100f, 80f), style = it) }
            if (motion.showBack) {
                drawPath(OwlShapes.back, OwlColors.BackFeather, style = Stroke(2f, cap = StrokeCap.Round))
            } else {
                // A sleeping owl wakes as it is pulled: one eye, then the other.
                val lidLeft = minOf(motion.lidLeft.value, 1f - ((pull - 0.45f) / 0.45f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                val lidRight = minOf(motion.lidRight.value, 1f - ((pull - 0.15f) / 0.45f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                drawEye(-1f, motion, g, lidLeft, OwlShapes.eyeLeft, OwlShapes.closedLeft)
                drawEye(1f, motion, g, lidRight, OwlShapes.eyeRight, OwlShapes.closedRight)
                if (motion.brows.value > 0.01f) {
                    drawPath(OwlShapes.browsPath, OwlColors.Brow.copy(alpha = motion.brows.value), style = Stroke(5f, cap = StrokeCap.Round))
                }
                drawPath(OwlShapes.beak, OwlColors.Beak)
            }
        }

        if (motion.sleeping && !motion.away && pull < 0.2f) drawSnore(time, motion.reduced, colors, measurer)
        motion.bubble?.let { drawBubble(it, measurer) }
    }
}

private fun DrawScope.drawEye(side: Float, motion: OwlMotion, pull: Float, lid: Float, clip: Path, closed: Path) {
    val x = 19f * side
    drawCircle(Color.White, 15.5f, Offset(x, -138f))
    val scale = 1f + 0.2f * maxOf(motion.widen.value, pull * 0.9f)
    // Pulled up, it looks up after the finger.
    val look = Offset(motion.lookX.value, motion.lookY.value - 3.5f * pull)
    withTransform({
        translate(x, -138f)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawCircle(OwlColors.Iris, 11f, Offset.Zero)
        drawCircle(OwlColors.Pupil, 6.5f, look)
        drawCircle(Color.White, 2.2f, look + Offset(-2f, -2.5f))
    }
    if (lid > 0f) {
        clipPath(clip) { drawRect(OwlColors.Body, Offset(x - 17f, -155f), Size(34f, 32f * lid)) }
    }
    if (lid > 0.88f) drawPath(closed, OwlColors.Belly, style = Stroke(2.2f, cap = StrokeCap.Round))
}

/** Three z's rising from a sleeping owl. */
private fun DrawScope.drawSnore(time: Float, reduced: Boolean, colors: SkyColors, measurer: TextMeasurer) {
    for (k in 0..2) {
        val q = if (reduced) 0.3f else ((time + k * 1.05f) / 3.2f).mod(1f)
        val alpha = if (reduced) 0.8f else if (q < 0.2f) q / 0.2f * 0.9f else 0.9f * (1f - (q - 0.2f) / 0.8f)
        val layout = measurer.measure("z", unitsStyle(12f + k * 3f, colors.text.copy(alpha = alpha), this))
        drawText(layout, topLeft = Offset(44f + k * 6f + 14f * q, -194f - k * 6f - 26f * q))
    }
}

private fun DrawScope.drawBubble(text: String, measurer: TextMeasurer) {
    val layout = measurer.measure(text, unitsStyle(13f, OwlColors.Body, this, italic = true))
    val width = maxOf(30f, layout.size.width + 20f)
    drawRoundRect(OwlColors.Bubble, Offset(44f, -226f), Size(width, 26f), androidx.compose.ui.geometry.CornerRadius(13f))
    drawPath(OwlShapes.bubbleTail, OwlColors.Bubble)
    drawText(layout, topLeft = Offset(44f + (width - layout.size.width) / 2f, -213f - layout.size.height / 2f))
}

/** A text style whose glyphs measure [units] tall in the scene's units, however the canvas is scaled. */
private fun unitsStyle(units: Float, color: Color, density: Density, italic: Boolean = false) = TextStyle(
    color = color,
    fontSize = (units / (density.density * density.fontScale)).sp,
    fontWeight = FontWeight.ExtraBold,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
)
