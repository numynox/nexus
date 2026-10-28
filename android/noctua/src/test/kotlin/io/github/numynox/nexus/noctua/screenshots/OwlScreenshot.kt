package io.github.numynox.nexus.noctua.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import io.github.numynox.nexus.noctua.ui.NoctuaPalette
import io.github.numynox.nexus.noctua.ui.feed.EndOfFeed
import io.github.numynox.nexus.noctua.ui.feed.EndPull
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The owl at the end of the feed at a fixed time, by night and by day, in
 * both themes: it is drawn on the app's own background, so each of the four
 * has to work. The app's own run only ever sees the evening
 * (ScreenshotApplication's clock).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class OwlScreenshot {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 10, 27)

    private fun shot(now: ZonedDateTime, mode: ThemeMode, name: String) {
        compose.setContent {
            NexusTheme(NoctuaPalette, themeMode = mode) {
                Surface {
                    EndOfFeed(
                        empty = true,
                        pull = remember { EndPull(300f) {} },
                        refreshing = false,
                        refreshes = 0,
                        now = now,
                        onRefresh = {},
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    @Test
    fun nightLight() = shot(day.atTime(21, 40).atZone(zone), ThemeMode.Light, "41-owl-night-light")

    @Test
    fun nightDark() = shot(day.atTime(21, 40).atZone(zone), ThemeMode.Dark, "42-owl-night-dark")

    @Test
    fun dayLight() = shot(day.atTime(13, 5).atZone(zone), ThemeMode.Light, "43-owl-day-light")

    @Test
    fun dayDark() = shot(day.atTime(13, 5).atZone(zone), ThemeMode.Dark, "44-owl-day-dark")
}
