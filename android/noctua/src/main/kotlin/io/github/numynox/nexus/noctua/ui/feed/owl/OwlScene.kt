package io.github.numynox.nexus.noctua.ui.feed.owl

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The scene's 360 × 470 units fitted into a size in pixels: whole, centred across, the branch at the foot. */
class SceneFrame(width: Float, height: Float) {
    val scale = minOf(width / SceneWidth, height / SceneHeight)
    /** Extra units beside the scene, half on each side, when the box is wider than it. */
    val left = (width / scale - SceneWidth) / 2f
    /** Extra units above the scene when the box is taller than it. */
    val above = height / scale - SceneHeight
    /** How far the moon or sun rises into that extra room, so a tall screen does not keep it low over the owl. */
    val skyLift = minOf(above * 0.5f, 160f)

    fun toPx(x: Float, y: Float) = Offset((x + left) * scale, (y + above) * scale)
    fun toUnits(at: Offset) = Offset(at.x / scale - left, at.y / scale - above)

    /** A point in pixels on the layer of the moon or sun, which is lifted by [skyLift]. */
    fun toSky(at: Offset) = toUnits(at) + Offset(0f, skyLift)

    companion object {
        const val SceneWidth = 360f
        const val SceneHeight = 470f
    }
}

/** What the scene shows besides the owl. */
class SkyContent(
    val night: Boolean,
    val moonPhase: Double,
    val shootingStar: ShootingStar?,
    val colors: SkyColors,
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

/** The owl's own colours, from its logo: the same in every theme. */
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
    val Bark = Color(0xFF6B4A2F)
    val BarkLight = Color(0xFF8A6446)
    val Leaf = Color(0xFF6F9C4E)
    val LeafDark = Color(0xFF557F3B)
    val LeafVein = Color(0x66E6F2D8)
    val Stalk = Color(0xFF5E7A3A)
    val Bubble = Color(0xFFFAF6EA)
    val Sun = Color(0xFFF6C343)
}

/**
 * The colours that sit on the app's own background, which is the only sky:
 * pale stars and a soft moon on a dark theme, gold ones on a light theme,
 * where white would vanish.
 */
class SkyColors(
    val dark: Boolean,
    val star: Color,
    val moon: Color,
    val moonShadow: Color,
    val glow: Color,
    val rim: Color?,
    val text: Color,
    val mini: Color,
)

@Composable
fun skyColors(): SkyColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return remember(scheme) {
        if (dark) {
            SkyColors(
                dark = true,
                star = Color(0xFFFFE7A6),
                moon = Color(0xFFF7F0D6),
                moonShadow = Color.White.copy(alpha = 0.08f),
                glow = Color(0xFFFFF6D6),
                rim = Color(0x33D2D7FF),
                text = scheme.onSurfaceVariant,
                mini = Color(0xFF0A0C22),
            )
        } else {
            SkyColors(
                dark = false,
                star = Color(0xFFC29A2E),
                moon = Color(0xFFEBD48A),
                moonShadow = scheme.onSurface.copy(alpha = 0.06f),
                glow = Color(0xFFF2D27A),
                rim = null,
                text = scheme.onSurfaceVariant,
                mini = OwlColors.Body,
            )
        }
    }
}

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
    /** A leaf pointing along +x from its stalk at the origin, 18 long. */
    val leaf = svg("M 0 0 C 4 -6 12 -7 18 0 C 12 7 4 6 0 0 Z")
    val leafVein = svg("M 2 0 L 14 0")
    // A shade wider than the white of the eye, so a shut lid leaves no rim of it.
    fun eye(side: Float) = Path().apply { addOval(Rect(Offset(19f * side, -138f), 16f)) }
    val eyeLeft = eye(-1f)
    val eyeRight = eye(1f)
}

/** Where the owl's feet rest on the branch, bent by [bend]. */
const val FeetY = 413.5f

/**
 * A leaf on the branch: at [x] along it, on its upper side ([below] false)
 * or its lower one, pointing at [angle] degrees (0 is right, 90 down),
 * [size] times the leaf's length.
 */
private class Leaf(val x: Float, val below: Boolean, val angle: Float, val size: Float, val dark: Boolean)

private val BranchLeaves = listOf(
    Leaf(92f, below = false, angle = -118f, size = 0.9f, dark = false),
    Leaf(100f, below = false, angle = -72f, size = 0.8f, dark = true),
    Leaf(262f, below = true, angle = 112f, size = 0.8f, dark = true),
    Leaf(292f, below = false, angle = -58f, size = 0.9f, dark = false),
    Leaf(312f, below = true, angle = 64f, size = 0.85f, dark = true),
    Leaf(BranchTipX - 2f, below = false, angle = -22f, size = 0.9f, dark = false),
)

/** The twig that hangs off the branch to the left of the owl, and its leaves at the end and halfway. */
private const val TwigX = 58f

/**
 * The scene on the app's own background: stars and the moon by night, the
 * sun by day, the branch and the owl, drawn from [motion] and [pull] (how far
 * the end of the feed is pulled, 1 at the refresh threshold). Reading
 * [OwlMotion.time] here, in the draw phase, keeps the breathing and the
 * twinkling from recomposing anything.
 */
@Composable
fun OwlScene(motion: OwlMotion, sky: SkyContent, pull: () -> Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val moon = remember(sky.moonPhase) { svg(moonLitPath(sky.moonPhase, SkyLightX, SkyLightY, MoonR)) }
    val colors = sky.colors
    Canvas(modifier) {
        val frame = SceneFrame(size.width, size.height)
        motion.skyAbove = frame.above
        motion.skyLift = frame.skyLift
        val time = motion.time
        withTransform({
            scale(frame.scale, frame.scale, pivot = Offset.Zero)
            translate(frame.left, frame.above)
        }) {
            val reveal = motion.reveal.value
            if (sky.night) {
                SkyStars.forEachIndexed { i, star ->
                    val shown = ((reveal * 1.6f) - i * 0.018f).coerceIn(0f, 1f)
                    val twinkle = if (motion.reduced) 1f else 0.55f + 0.45f * sin(time * 1.3f + star.phase)
                    drawCircle(colors.star.copy(alpha = 0.85f * shown * twinkle), star.r, Offset(star.x, star.y))
                }
            }
            withTransform({ translate(0f, -frame.skyLift) }) {
                if (sky.night) drawMoon(moon, colors, reveal) else drawSun(colors, time, motion.reduced, reveal)
                drawSearchingOwl(motion, colors)
                sky.shootingStar?.let { drawShootingStar(it, time, colors) }
            }
            drawBranch(motion.bend.value + 7f * pull().coerceIn(0f, 1f))
            drawOwl(motion, pull(), colors, measurer)
        }
    }
}

private fun DrawScope.drawMoon(lit: Path, colors: SkyColors, reveal: Float) {
    val centre = Offset(SkyLightX, SkyLightY)
    val shown = reveal.coerceIn(0f, 1f)
    val glowAlpha = if (colors.dark) 0.35f else 0.28f
    drawCircle(Brush.radialGradient(listOf(colors.glow.copy(alpha = glowAlpha * shown), Color.Transparent), centre, 64f), 64f, centre)
    drawCircle(colors.moonShadow, MoonR, centre, alpha = shown)
    drawPath(lit, colors.moon, alpha = shown)
}

/** The sun, by day: a warm disc with a soft glow and short rays turning slowly. */
private fun DrawScope.drawSun(colors: SkyColors, time: Float, reduced: Boolean, reveal: Float) {
    val centre = Offset(SkyLightX, SkyLightY)
    val shown = reveal.coerceIn(0f, 1f)
    drawCircle(Brush.radialGradient(listOf(OwlColors.Sun.copy(alpha = 0.32f * shown), Color.Transparent), centre, 66f), 66f, centre)
    val turn = if (reduced) 0f else time * 0.12f
    for (i in 0 until 10) {
        val a = turn + i * (2 * PI.toFloat() / 10)
        val from = centre + Offset(cos(a), sin(a)) * 28f
        val to = centre + Offset(cos(a), sin(a)) * 36f
        drawLine(OwlColors.Sun.copy(alpha = 0.7f * shown), from, to, strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
    drawCircle(OwlColors.Sun.copy(alpha = shown), 21f, centre)
    if (colors.dark) drawCircle(Color.White.copy(alpha = 0.18f * shown), 21f, centre, style = Stroke(1.2f))
}

private fun DrawScope.drawSearchingOwl(motion: OwlMotion, colors: SkyColors) {
    val shown = motion.searching.value
    if (shown < 0.01f) return
    val time = motion.time
    val t = ((time - motion.searchStart) / 1.7f).mod(1f)
    val x = SkyLightX - 95f + t * 190f
    val y = SkyLightY + 6f - sin(t * PI.toFloat()) * 18f + sin(time * 6.25f) * 2f
    withTransform({ translate(x, y) }) {
        drawOval(colors.mini.copy(alpha = shown), Offset(-6.5f, -5.5f), Size(13f, 11f))
        withTransform({ scale(1f, if (motion.reduced) 1f else sin(time * 18f), pivot = Offset.Zero) }) {
            drawPath(OwlShapes.miniWings, colors.mini.copy(alpha = shown))
        }
    }
}

private fun DrawScope.drawShootingStar(star: ShootingStar, time: Float, colors: SkyColors) {
    val head = star.headAt(time) ?: return
    val p = (time - star.start) / ShootingStar.Duration
    val alpha = if (p < 0.12f) p / 0.12f else 1f - (p - 0.12f) / 0.88f
    val tail = head + Offset(46f, -25f)
    drawLine(Brush.linearGradient(listOf(colors.star.copy(alpha = alpha), Color.Transparent), head, tail), head, tail, strokeWidth = 2f, cap = StrokeCap.Round)
    drawCircle(colors.star.copy(alpha = alpha), 1.8f, head)
}

/** The branch as a filled shape: thick where it comes in, tapering to a rounded tip, with a lighter upper edge. */
private fun DrawScope.drawBranch(bend: Float) {
    val outline = Path()
    val steps = 48
    val xs = List(steps + 1) { BranchStartX + (BranchTipX - BranchStartX) * it / steps }
    xs.forEachIndexed { i, x ->
        val y = branchY(x, bend) - branchWidth(x) / 2f
        if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
    }
    val tipY = branchY(BranchTipX, bend)
    outline.quadraticTo(BranchTipX + 7f, tipY, BranchTipX, tipY + branchWidth(BranchTipX) / 2f)
    xs.reversed().forEach { x -> outline.lineTo(x, branchY(x, bend) + branchWidth(x) / 2f) }
    outline.close()

    // The twig, drawn first so the branch covers where it joins.
    val joinY = branchY(TwigX, bend)
    val twigEnd = Offset(TwigX - 32f, joinY + 22f)
    val twig = Path().apply {
        moveTo(TwigX + 4f, joinY)
        quadraticTo(TwigX - 8f, joinY + 14f, twigEnd.x, twigEnd.y)
    }
    drawPath(twig, OwlColors.Bark, style = Stroke(4.5f, cap = StrokeCap.Round))
    drawPath(outline, OwlColors.Bark)
    val highlight = Path()
    xs.filter { it > -200f && it < BranchTipX - 12f }.forEachIndexed { i, x ->
        val y = branchY(x, bend) - branchWidth(x) / 2f + 1.6f
        if (i == 0) highlight.moveTo(x, y) else highlight.lineTo(x, y)
    }
    drawPath(highlight, OwlColors.BarkLight.copy(alpha = 0.7f), style = Stroke(1.4f, cap = StrokeCap.Round))

    drawLeaf(twigEnd, 150f, 1f, dark = false)
    drawLeaf(twigEnd, 196f, 0.85f, dark = true)
    drawLeaf(Offset(TwigX - 11f, joinY + 12.5f), 58f, 0.75f, dark = false)
    BranchLeaves.forEach { leaf ->
        val half = branchWidth(leaf.x) / 2f - 1.5f
        val at = Offset(leaf.x, branchY(leaf.x, bend) + if (leaf.below) half else -half)
        drawLeaf(at, leaf.angle, leaf.size, leaf.dark)
    }
}

/** A leaf on a short stalk that starts inside the bark, so it grows out of the wood rather than floating beside it. */
private fun DrawScope.drawLeaf(at: Offset, angle: Float, size: Float, dark: Boolean) {
    withTransform({
        translate(at.x, at.y)
        rotate(angle, pivot = Offset.Zero)
        scale(size * 1.35f, size * 1.35f, pivot = Offset.Zero)
    }) {
        drawLine(OwlColors.Stalk, Offset(-3f, 0f), Offset(2.5f, 0f), strokeWidth = 1.6f, cap = StrokeCap.Round)
        withTransform({ translate(2f, 0f) }) {
            drawPath(OwlShapes.leaf, if (dark) OwlColors.LeafDark else OwlColors.Leaf)
            drawPath(OwlShapes.leafVein, OwlColors.LeafVein, style = Stroke(0.9f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawOwl(motion: OwlMotion, pull: Float, colors: SkyColors, measurer: TextMeasurer) {
    val time = motion.time
    val still = motion.reduced || motion.busy || pull > 0f
    val breath = if (still) 0f else sin(time * 1.05f) * 0.012f
    // The pull drives the owl directly, finger and owl as one; the springs carry the rest.
    val g = pull.coerceIn(0f, 1f)
    val ready = pull >= 1f
    val sx = motion.scaleX.value * (1f + 0.035f * g) * (1f + breath * 0.4f)
    val sy = motion.scaleY.value * (1f - 0.07f * g) * (1f + breath)
    val feetY = FeetY + motion.bend.value + 7f * g
    val rim = colors.rim

    withTransform({
        translate(180f + motion.owlX.value, feetY + motion.owlY.value)
        scale(sx, sy, pivot = Offset.Zero)
    }) {
        val wing = if (motion.flapping && !motion.reduced) 38f + sin(time * 20.8f) * 42f else motion.wings.value + if (ready) 10f else 0f
        withTransform({ rotate(wing, Offset(-40f, -96f)) }) { drawPath(OwlShapes.wingLeft, OwlColors.Wing) }
        withTransform({ rotate(-wing, Offset(40f, -96f)) }) { drawPath(OwlShapes.wingRight, OwlColors.Wing) }
        drawPath(OwlShapes.body, OwlColors.Body)
        rim?.let { drawPath(OwlShapes.body, it, style = Stroke(2f)) }
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
                withTransform({ rotate(-tufts * 10f, Offset(-34f, -166f)) }) {
                    drawPath(OwlShapes.tuftLeft, OwlColors.Body)
                    rim?.let { drawPath(OwlShapes.tuftLeft, it, style = Stroke(1.5f)) }
                }
                withTransform({ rotate(tufts * 10f, Offset(34f, -166f)) }) {
                    drawPath(OwlShapes.tuftRight, OwlColors.Body)
                    rim?.let { drawPath(OwlShapes.tuftRight, it, style = Stroke(1.5f)) }
                }
            }
            drawOval(OwlColors.Body, Offset(-50f, -178f), Size(100f, 80f))
            rim?.let { drawOval(it, Offset(-50f, -178f), Size(100f, 80f), style = Stroke(2f)) }
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
    drawRoundRect(OwlColors.Bubble, Offset(44f, -226f), Size(width, 26f), CornerRadius(13f))
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
