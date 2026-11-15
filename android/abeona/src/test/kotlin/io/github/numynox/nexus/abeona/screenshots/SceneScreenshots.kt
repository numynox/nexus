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
import io.github.numynox.nexus.abeona.ui.scene.SceneStop
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

    private val stops = listOf(0.0, 0.22, 0.41, 0.63, 0.8, 1.0).map { SceneStop(it) }

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
                    caption("strip · waiting, the ride's rail")
                    Scene(SceneState.Waiting, size = SceneSize.Strip, stops = stops, at = at)
                    caption("strip · riding, half way")
                    Scene(SceneState.Riding, size = SceneSize.Strip, stops = stops, fraction = 0.5, at = at)
                    caption("strip · riding · bus, nearly there")
                    Scene(SceneState.Riding, size = SceneSize.Strip, train = TrainKind.Bus, stops = stops, fraction = 0.9, at = at)
                    caption("strip · stopped")
                    Scene(SceneState.Stopped, size = SceneSize.Strip, stops = stops, at = at)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    @Test fun byDay() = shot("90-scene-day", day)

    @Test fun atDusk() = shot("91-scene-dusk", dusk)

    @Test fun atNight() = shot("92-scene-night", night)

    @Test fun byDayDark() = shot("93-scene-day-dark", day, ThemeMode.Dark)

    @Test fun atNightDark() = shot("94-scene-night-dark", night, ThemeMode.Dark)
}
