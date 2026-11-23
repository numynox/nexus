package io.github.numynox.nexus.abeona.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.numynox.nexus.abeona.ui.AbeonaPalette
import io.github.numynox.nexus.abeona.ui.scene.Scene
import io.github.numynox.nexus.abeona.ui.scene.SceneSize
import io.github.numynox.nexus.abeona.ui.scene.SceneState
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.ui.scene.BarJourney
import io.github.numynox.nexus.abeona.ui.scene.BarRide
import io.github.numynox.nexus.abeona.ui.scene.BarState
import io.github.numynox.nexus.abeona.ui.scene.TrainKind
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scene alone (abeona-design.md §7): every state in its sizes, each kind
 * of train, and the sky by day, at dusk and at night, on both themes. The
 * moments are fixed, in Frankfurt, so the pictures do not change with the
 * hour the test runs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h1500dp-xhdpi")
class SceneScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private val day = Instant.parse("2026-11-15T11:30:00Z")
    private val dusk = Instant.parse("2026-11-15T15:25:00Z")
    private val night = Instant.parse("2026-11-15T21:00:00Z")

    /**
     * The strip's journey at [at]: [rides] of them, the one in focus ([current])
     * a hundred minutes with four stops between, [fraction] of it gone by;
     * the others a ten-minute change before and after it.
     */
    private fun journey(at: Instant, state: BarState, rides: Int = 1, current: Int = 0, fraction: Double = 0.0, confidence: Confidence? = null): BarJourney {
        val minute = 60_000L
        val departure = at.toEpochMilli() - (fraction * 100 * minute).toLong() + if (state == BarState.OnBoard) 0 else 6 * minute
        val focus = BarRide(departure, departure + 100 * minute, listOf(22L, 41L, 63L, 80L).map { departure + it * minute })
        val list = (0 until rides).map { i ->
            val shift = (i - current) * 110 * minute
            BarRide(focus.departure + shift, focus.arrival + shift, focus.stops.map { it + shift })
        }
        return BarJourney(list, current, state, if (state == BarState.BeforeLeaving) at.toEpochMilli() - 2 * minute else null, confidence)
    }

    private fun shot(name: String, at: Instant, mode: ThemeMode = ThemeMode.Light) {
        compose.setContent {
            NexusTheme(AbeonaPalette, themeMode = mode) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    @androidx.compose.runtime.Composable
                    fun caption(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    caption("full · idle")
                    Scene(SceneState.Idle, size = SceneSize.Full, at = at)
                    caption("full · riding · regional")
                    Scene(SceneState.Riding, size = SceneSize.Full, train = TrainKind.Regional, at = at)
                    caption("header · parked")
                    Scene(SceneState.Parked, size = SceneSize.Header, at = at)
                    caption("header · stopped")
                    Scene(SceneState.Stopped, size = SceneSize.Header, at = at)
                    caption("header · arrived · suburban")
                    Scene(SceneState.Arrived, size = SceneSize.Header, train = TrainKind.Suburban, at = at)
                    caption("strip · before leaving: on foot to the train")
                    Scene(SceneState.Waiting, size = SceneSize.Strip, journey = journey(at, BarState.BeforeLeaving, rides = 2), now = at, at = at)
                    caption("strip · waiting at the station, the ride's rail")
                    Scene(SceneState.Waiting, size = SceneSize.Strip, journey = journey(at, BarState.AtStation), now = at, at = at)
                    caption("strip · riding ride 2 of 3, half way")
                    Scene(SceneState.Riding, size = SceneSize.Strip, journey = journey(at, BarState.OnBoard, rides = 3, current = 1, fraction = 0.5), now = at, at = at)
                    caption("strip · changing, tight")
                    Scene(
                        SceneState.Waiting, size = SceneSize.Strip, train = TrainKind.Regional,
                        journey = journey(at, BarState.Changing, rides = 2, current = 1, confidence = Confidence.TIGHT), now = at, at = at,
                    )
                    caption("strip · riding · bus, nearly there")
                    Scene(SceneState.Riding, size = SceneSize.Strip, train = TrainKind.Bus, journey = journey(at, BarState.OnBoard, fraction = 0.9), now = at, at = at)
                    caption("strip · stopped")
                    Scene(SceneState.Stopped, size = SceneSize.Strip, at = at)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    /**
     * Smooth, not by the minute: one four-minute hop drawn at its moment and
     * 20 and 40 seconds on. The train and the bar move within the minute;
     * in the app the frames carry the moment on between the card's ticks.
     */
    @Test
    fun withinTheMinute() {
        val start = day.toEpochMilli()
        val hop = BarJourney(listOf(BarRide(start, start + 4 * 60_000, emptyList())), 0, BarState.OnBoard, null, null)
        compose.setContent {
            NexusTheme(AbeonaPalette, themeMode = ThemeMode.Light) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60L, 80L, 100L).forEach { s ->
                        Text("strip · a four-minute hop, ${s - 60} s after the minute", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Scene(SceneState.Riding, size = SceneSize.Strip, train = TrainKind.Suburban, journey = hop, now = day.plusSeconds(s), at = day)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/95-scene-within-the-minute.png")
    }

    @Test fun byDay() = shot("90-scene-day", day)

    @Test fun atDusk() = shot("91-scene-dusk", dusk)

    @Test fun atNight() = shot("92-scene-night", night)

    @Test fun byDayDark() = shot("93-scene-day-dark", day, ThemeMode.Dark)

    @Test fun atNightDark() = shot("94-scene-night-dark", night, ThemeMode.Dark)
}
