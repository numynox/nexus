package io.github.numynox.nexus.noctua.ui.feed.owl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything about the owl that moves, and the little scenes it plays. The
 * values are in scene units (see NightSky.kt) and eased by critically damped
 * springs, so a new target taken mid-way never jerks. Drawing reads them;
 * nothing here draws.
 */
@Stable
class OwlMotion internal constructor(private val scope: CoroutineScope, val reduced: Boolean) {
    private fun value(initial: Float) = Animatable(initial)

    val owlX = value(0f)
    val owlY = value(0f)
    val scaleX = value(1f)
    val scaleY = value(1f)
    val headTurn = value(0f)
    val headLift = value(0f)
    /** 1 the face, through 0 edge-on, back to 1 — with [showBack] flipped at the edge. */
    val headSpin = value(1f)
    val widen = value(0f)
    val lidLeft = value(0f)
    val lidRight = value(0f)
    val lookX = value(0f)
    val lookY = value(0f)
    val tufts = value(0f)
    val wings = value(0f)
    val bend = value(0f)
    val brows = value(0f)
    /** The small owl crossing the moon while a refresh runs. */
    val searching = value(0f)
    /** How far the stars of today's reading have come in, in milliseconds. */
    val reveal = value(0f)

    var flapping by mutableStateOf(false)
        private set
    var showBack by mutableStateOf(false)
        private set
    var sleeping by mutableStateOf(false)
        private set
    /** Up and away looking for news, or on the way back. */
    var away by mutableStateOf(false)
        private set
    var bubble by mutableStateOf<String?>(null)
        private set
    /** Seconds since the scene appeared, ticking only while something can move. */
    var time by mutableFloatStateOf(0f)
    /** When the small owl set off across the moon, in [time]. */
    var searchStart by mutableFloatStateOf(0f)
        private set
    /** Scene units of sky above the scene's top edge, set by the drawing. */
    var skyAbove = 0f
    /** How far the moon and stars are lifted into it ([SceneFrame.skyLift]), set by the drawing. */
    var skyLift = 0f

    /** A scene in progress — a tap's, the flight's — that idle life waits for. */
    private var scene: Job? = null
    private var flight: Job? = null
    val busy: Boolean get() = scene?.isActive == true || away

    /** Eases [this] to [target] at [rate] (roughly 1/s; 4 is a glide, 40 a snap). */
    private fun Animatable<Float, AnimationVector1D>.to(target: Float, rate: Float) {
        scope.launch { animateTo(target, spring(dampingRatio = 1f, stiffness = rate * rate)) }
    }

    private fun play(block: suspend () -> Unit) {
        if (busy) return
        scene = scope.launch { block() }
    }

    // ── Idle life ────────────────────────────────────────────────────────────

    fun blink() {
        if (busy || sleeping) return
        scene = scope.launch {
            lidLeft.to(1f, 45f); lidRight.to(1f, 45f)
            delay(120)
            lidLeft.to(0f, 22f); lidRight.to(0f, 22f)
        }
    }

    /** Lets the eyes wander a little, as they do when nothing is going on. */
    fun drift() {
        lookX.to(Random.nextFloat() * 6f - 3f, 4f)
        lookY.to(Random.nextFloat() * 3f - 1.5f, 4f)
    }

    fun lookAt(x: Float, y: Float) {
        val dx = x - (180f + owlX.value)
        val dy = y - (413.5f + owlY.value - 138f)
        val d = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
        val k = (d / 120f).coerceAtMost(1f)
        lookX.to(dx / d * 4.2f * k, 12f)
        lookY.to(dy / d * 3.6f * k, 12f)
    }

    /** Asleep by day: eyes shut. [instant] for how the scene starts, without a yawn. */
    fun sleep(on: Boolean, instant: Boolean = false) {
        sleeping = on
        val lid = if (on) 1f else 0f
        if (instant) scope.launch { lidLeft.snapTo(lid); lidRight.snapTo(lid) } else { lidLeft.to(lid, 5f); lidRight.to(lid, 4f) }
    }

    /** Hops onto the branch from the right, settles, and blinks once — or wakes asleep, by day. */
    fun enter(night: Boolean) {
        scene = scope.launch {
            // To the longest any sky takes, so stars that arrive after the entrance come in whole.
            launch { reveal.snapTo(0f); reveal.animateTo(StarsRevealMaxMs, tween(StarsRevealMaxMs.toInt(), easing = LinearEasing)) }
            lidLeft.snapTo(if (night) 0f else 1f); lidRight.snapTo(if (night) 0f else 1f)
            sleeping = !night
            if (reduced) return@launch
            owlX.snapTo(230f); owlY.snapTo(-150f)
            flapping = true
            owlX.to(0f, 5f); owlY.to(0f, 5.5f)
            delay(800)
            flapping = false
            wings.to(0f, 10f)
            land()
            if (night) {
                delay(350)
                lidLeft.to(1f, 45f); lidRight.to(1f, 45f)
                delay(130)
                lidLeft.to(0f, 20f); lidRight.to(0f, 20f)
            }
        }
    }

    /** The branch gives under the landing, and the owl sinks into it a moment. */
    private suspend fun land() {
        bend.to(12f, 26f); scaleY.to(0.91f, 26f)
        delay(130)
        bend.to(0f, 6f); scaleY.to(1f, 9f)
    }

    // ── The pull and the refresh ────────────────────────────────────────────

    /** The pull went past its threshold: the head pops up a little. */
    fun ready() {
        scope.launch {
            headLift.to(-4f, 30f)
            delay(90)
            headLift.to(0f, 12f)
        }
    }

    /** Off the branch and up out of the sky, then the small owl starts across the moon. */
    fun takeOff() {
        if (away) return
        scene?.cancel()
        away = true
        bubble = null
        flight = scope.launch {
            showBack = false
            headSpin.to(1f, 20f); headTurn.to(0f, 14f); brows.to(0f, 14f)
            lidLeft.to(0f, 20f); lidRight.to(0f, 20f)
            widen.to(0.7f, 14f); tufts.to(1f, 14f)
            if (reduced) {
                startSearching()
                return@launch
            }
            scaleY.to(0.86f, 22f); scaleX.to(1.05f, 22f); bend.to(9f, 20f)
            delay(170)
            flapping = true
            scaleY.to(1.04f, 12f); scaleX.to(0.98f, 12f); bend.to(-5f, 18f)
            owlY.to(-(skyAbove + 640f), 4.2f); owlX.to(-30f, 3f)
            delay(180)
            bend.to(0f, 7f)
            delay(420)
            startSearching()
        }
    }

    private fun startSearching() {
        searchStart = time
        searching.to(1f, 6f)
    }

    /**
     * Back from the search — once the take-off has played out, however quick
     * the refresh was. [checked] when the refresh came back without anything
     * new, which earns a slow shake of the head; a failed one just lands.
     * Afterwards a day owl goes back to sleep.
     */
    fun comeBack(checked: Boolean, night: Boolean) {
        if (!away) return
        scope.launch {
            flight?.join()
            searching.to(0f, 8f)
            if (!reduced) {
                delay(150)
                owlX.snapTo(150f); owlY.snapTo(-(skyAbove + 560f))
                owlX.to(0f, 4.4f); owlY.to(0f, 4.6f)
                scaleY.to(1f, 10f); scaleX.to(1f, 10f); widen.to(0.3f, 8f)
                delay(950)
                flapping = false
                wings.to(0f, 10f)
                land()
            }
            tufts.to(0f, 8f)
            sleeping = false
            if (checked) {
                for (angle in listOf(-11f, 11f, -7f, 0f)) {
                    headTurn.to(angle, 14f)
                    delay(if (reduced) 60 else 190)
                }
            }
            widen.to(0f, 8f)
            away = false
            if (!night) {
                delay(2_600)
                if (!busy) sleep(true)
            }
        }
    }

    // ── Easter eggs ─────────────────────────────────────────────────────────

    /** A tap: the little owl's head-bob. */
    fun bob() = play {
        headLift.to(-6f, 22f); headTurn.to(13f, 14f)
        delay(240)
        headLift.to(0f, 14f); headTurn.to(-7f, 12f)
        delay(220)
        headTurn.to(0f, 10f)
        delay(120)
    }

    /** Too many taps: it puffs itself up, frowns, and lets it go again. */
    fun grumpy() {
        scene?.cancel()
        scene = scope.launch {
            scaleX.to(1.13f, 10f); scaleY.to(1.06f, 10f); brows.to(1f, 14f)
            lidLeft.to(0.42f, 14f); lidRight.to(0.42f, 14f); tufts.to(1f, 10f); headTurn.to(0f, 10f)
            say("…", 2_200)
            delay(2_400)
            scaleX.to(1f, 4f); scaleY.to(1f, 4f); brows.to(0f, 4f); tufts.to(0f, 4f)
            lidLeft.to(0f, 6f); lidRight.to(0f, 6f)
            delay(500)
        }
    }

    /** A long press: two soft hoots, the tufts twitching with each. */
    fun hoot() = play {
        say("hoo… hoo", 1_500)
        repeat(2) {
            tufts.to(1f, 20f); scaleY.to(1.04f, 18f)
            delay(200)
            tufts.to(0f, 12f); scaleY.to(1f, 12f)
            delay(220)
        }
    }

    /** Poked while asleep: one eye opens a crack, then shuts again. */
    fun peek() = play {
        lidRight.to(0.25f, 20f)
        delay(900)
        lidRight.to(1f, 6f)
    }

    /** Looks up at the moon for a while. */
    fun admireMoon() = play {
        if (!sleeping) {
            lookAt(MoonX, MoonY - skyLift); headTurn.to(9f, 8f)
        }
        delay(1_600)
        headTurn.to(0f, 6f)
    }

    /** A shooting star caught: eyes wide a moment. */
    fun surprised() {
        say("!", 900)
        widen.to(0.8f, 20f)
        scope.launch { delay(700); widen.to(0f, 6f) }
    }

    /** Left alone long enough, it turns its head all the way round and back. */
    fun turnAround() = play {
        if (reduced || sleeping) return@play
        headSpin.to(0f, 14f); delay(230)
        showBack = true; headSpin.to(1f, 16f); delay(1_600)
        headSpin.to(0f, 16f); delay(230)
        showBack = false; headSpin.to(1f, 16f); delay(250)
    }

    private var said: Job? = null
    private fun say(text: String, millis: Long) {
        said?.cancel()
        bubble = text
        said = scope.launch { delay(millis); bubble = null }
    }
}

/** The longest the stars of today's reading take to come in, one after the other: the last starts by 1.8 s. */
internal const val StarsRevealMaxMs = 2_400f

/** The gap between two stars coming in: a long day's sky must not take ages. */
internal fun starDelayMs(count: Int): Float = if (count <= 0) 0f else minOf(120f, 1_500f / count)

@Composable
fun rememberOwlMotion(reduced: Boolean): OwlMotion {
    val scope = rememberCoroutineScope()
    return remember(scope, reduced) { OwlMotion(scope, reduced) }
}
