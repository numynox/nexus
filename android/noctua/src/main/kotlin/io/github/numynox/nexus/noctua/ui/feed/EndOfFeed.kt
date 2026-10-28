package io.github.numynox.nexus.noctua.ui.feed

import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.noctua.ui.feed.owl.FeetY
import io.github.numynox.nexus.noctua.ui.feed.owl.OwlMotion
import io.github.numynox.nexus.noctua.ui.feed.owl.OwlScene
import io.github.numynox.nexus.noctua.ui.feed.owl.SceneFrame
import io.github.numynox.nexus.noctua.ui.feed.owl.ShootingStar
import io.github.numynox.nexus.noctua.ui.feed.owl.SkyContent
import io.github.numynox.nexus.noctua.ui.feed.owl.SkyLightX
import io.github.numynox.nexus.noctua.ui.feed.owl.SkyLightY
import io.github.numynox.nexus.noctua.ui.feed.owl.hoot
import io.github.numynox.nexus.noctua.ui.feed.owl.isNight
import io.github.numynox.nexus.noctua.ui.feed.owl.moonLit
import io.github.numynox.nexus.noctua.ui.feed.owl.moonPhase
import io.github.numynox.nexus.noctua.ui.feed.owl.moonPhaseName
import io.github.numynox.nexus.noctua.ui.feed.owl.rememberOwlMotion
import io.github.numynox.nexus.noctua.ui.feed.owl.skyColors
import java.time.ZonedDateTime
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Refresh by pulling up past the last article — the web's gesture
 * (decisions/2026-09-06-refresh-at-the-end-of-the-feed.md). Not the top of the
 * list: a refresh retakes the seen snapshot, so the articles already scrolled
 * past disappear, and scrolling back up to reread one must never do that by
 * accident. And the moment to ask for more is when you have run out.
 */
class EndPull(private val threshold: Float, private val onRefresh: () -> Unit) : NestedScrollConnection {
    /** How far the list has been dragged up past its end, in pixels. */
    var distance by mutableFloatStateOf(0f)
        private set

    val fraction: Float get() = (distance / threshold).coerceIn(0f, 1f)

    /** How far past the end, 1 at the threshold and up to 1.5 beyond it. */
    val stretch: Float get() = distance / threshold

    // Dragged up (negative y) with nothing left to scroll: the list is at its end.
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
        distance = (distance - available.y).coerceAtMost(threshold * 1.5f)
        return Offset(0f, available.y)
    }

    // Dragging back down first takes back the pull, before the list scrolls.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || distance <= 0f || available.y <= 0f) return Offset.Zero
        val taken = min(distance, available.y)
        distance -= taken
        return Offset(0f, taken)
    }

    // The finger lifted: far enough is a refresh, either way the pull lets go.
    override suspend fun onPreFling(available: Velocity): Velocity {
        val pulled = distance
        if (pulled >= threshold) onRefresh()
        distance = 0f
        return if (pulled > 0f) available else Velocity.Zero
    }
}


/**
 * The end of the feed: a full screen tall, like the web's panel, so the last
 * article can scroll all the way past the top and count as seen — a shorter
 * one would leave the progress short of done for good.
 *
 * An owl on a branch, drawn straight onto the app's background: by night
 * under a few stars and the moon in its real phase, by day asleep in the sun.
 * The pull past the end is the owl's: its eyes widen and its ear tufts pop up
 * at the threshold; let go and it flies off to look for news. When the
 * refresh finds some the list starts over at its top and the owl is out of
 * sight; when it finds none the owl comes back and shakes its head. A few
 * small things answer a tap or a long press.
 */
@Composable
fun EndOfFeed(
    empty: Boolean,
    pull: EndPull,
    refreshing: Boolean,
    /** Refreshes landed so far ([FeedLoad.refreshes]): one landing while the owl is away means it checked. */
    refreshes: Int,
    now: ZonedDateTime,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /** The list's side padding, which the scene reaches across so the branch comes in from the screen's edge. */
    bleed: Dp = 16.dp,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Animations switched off in the developer options: no flight, no idle life.
    val reduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val motion = rememberOwlMotion(reduced)
    val night = isNight(now.toLocalTime())
    val phase = remember(now.toLocalDate()) { moonPhase(now.toInstant()) }
    val colors = skyColors()

    var shooting by remember { mutableStateOf<ShootingStar?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    /** The last refresh came back, and the panel is still here: nothing new. */
    var checked by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableFloatStateOf(0f) }
    val taps = remember { ArrayDeque<Float>() }

    LaunchedEffect(motion) { motion.enter(night) }

    // The owl's clock, one tick a frame while the panel is on screen. An
    // infinite animation as far as Compose is concerned, so its test policy
    // holds it still and screenshots come out the same every time.
    LaunchedEffect(motion) {
        if (motion.reduced) return@LaunchedEffect
        val start = withInfiniteAnimationFrameMillis { it }
        var nextBlink = 2.5f
        var nextDrift = 1f
        var nextShoot = 9f
        while (true) {
            val t = (withInfiniteAnimationFrameMillis { it } - start) / 1000f
            motion.time = t
            if (pull.distance > 0f) lastTouch = t
            if (t > nextBlink) {
                motion.blink()
                nextBlink = t + 2.6f + Random.nextFloat() * 3.8f
            }
            if (t > nextDrift) {
                if (!motion.busy && pull.distance == 0f) motion.drift()
                nextDrift = t + 2.5f + Random.nextFloat() * 3.5f
            }
            // Left alone for twenty seconds, the owl turns its head right round;
            // after that, once a minute at most.
            if (t - lastTouch > 20f) {
                lastTouch = t + 40f
                motion.turnAround()
            }
            if (night && t > nextShoot) {
                val star = ShootingStar(150f + Random.nextFloat() * 180f, 20f + Random.nextFloat() * 70f, t)
                shooting = star
                if (!motion.busy && !motion.sleeping) motion.lookAt(star.x - 60f, star.y + 30f - motion.skyLift)
                nextShoot = t + 18f + Random.nextFloat() * 20f
            }
            shooting?.let { if (it.headAt(t) == null) shooting = null }
        }
    }

    var startedAt by remember { mutableIntStateOf(refreshes) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            startedAt = refreshes
            checked = false
            motion.takeOff()
        } else if (motion.away) {
            // Still on screen after a refresh that landed: it found nothing new.
            checked = refreshes != startedAt
            motion.comeBack(checked, night)
        }
    }

    val ready = pull.fraction >= 1f
    LaunchedEffect(ready) {
        if (ready) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            motion.ready()
        }
    }
    LaunchedEffect(note) {
        if (note != null) {
            delay(2_600)
            note = null
        }
    }

    Column(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .weight(1f)
                // Wider than the list's padding allows, to the screen's edges.
                .layout { measurable, constraints ->
                    val extra = (bleed * 2).roundToPx()
                    val width = constraints.maxWidth + extra
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
                }
                .clipToBounds(),
        ) {
            OwlScene(
                motion = motion,
                sky = SkyContent(night, phase, shooting, colors),
                pull = { pull.stretch },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = if (night) "An owl on a branch under the moon and stars" else "An owl asleep on a branch in the sun"
                    }
                    .pointerInput(motion, night) {
                        detectTapGestures(
                            onTap = { at ->
                                lastTouch = motion.time
                                val frame = SceneFrame(size.width.toFloat(), size.height.toFloat())
                                val u = frame.toUnits(at)
                                val sky = frame.toSky(at)
                                val caught = shooting?.headAt(motion.time)
                                when {
                                    caught != null && hypot(caught.x - sky.x, caught.y - sky.y) < 40f -> {
                                        shooting = null
                                        motion.surprised()
                                        note = "You caught a shooting star. Make a wish."
                                    }
                                    hypot(sky.x - SkyLightX, sky.y - SkyLightY) < 40f -> if (night) {
                                        motion.admireMoon()
                                        note = "${moonPhaseName(phase)} · ${(moonLit(phase) * 100).roundToInt()}% lit"
                                    } else {
                                        note = "The owl sleeps until dark"
                                    }
                                    onOwl(u, motion) && !motion.away -> when {
                                        motion.sleeping -> motion.peek()
                                        else -> {
                                            val now = motion.time
                                            while (taps.isNotEmpty() && now - taps.first() > 2.5f) taps.removeFirst()
                                            taps.addLast(now)
                                            if (taps.size >= 6) {
                                                taps.clear()
                                                motion.grumpy()
                                            } else {
                                                motion.bob()
                                            }
                                        }
                                    }
                                }
                            },
                            onLongPress = { at ->
                                lastTouch = motion.time
                                val u = SceneFrame(size.width.toFloat(), size.height.toFloat()).toUnits(at)
                                if (onOwl(u, motion) && !motion.busy && !motion.sleeping) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    motion.hoot()
                                    scope.launch { hoot(context) }
                                }
                            },
                        )
                    },
            )
            note?.let {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 32.dp, end = 32.dp),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(if (empty) "Nothing new" else "That's everything", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                refreshing || motion.away -> "The owl is out looking for new articles…"
                ready -> "Let go to send the owl"
                checked -> "Checked just now. Nothing has come in since."
                motion.sleeping && empty -> "The owl naps by day. Pull up to wake it and look for new articles."
                empty -> "Every article here has been seen. Pull up or refresh for what came in since."
                else -> "Pull up to fetch what came in since and clear what you've seen."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Refresh", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Whether a point of the scene is on the owl, wherever it is perched. */
private fun onOwl(u: Offset, motion: OwlMotion): Boolean {
    val x = 180f + motion.owlX.value
    val feet = FeetY + motion.bend.value + motion.owlY.value
    return u.x in x - 60f..x + 60f && u.y in feet - 200f..feet + 10f
}
