package io.github.numynox.nexus.noctua.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import io.github.numynox.nexus.noctua.data.Article
import io.github.numynox.nexus.noctua.data.DatedArticle
import io.github.numynox.nexus.noctua.ui.NoctuaPalette
import io.github.numynox.nexus.noctua.ui.feed.EndOfFeed
import io.github.numynox.nexus.noctua.ui.feed.EndPull
import io.github.numynox.nexus.noctua.ui.feed.owl.SceneFrame
import io.github.numynox.nexus.noctua.ui.feed.owl.constellation
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
 * The owl at the end of the feed, at a fixed time: awake at night under a
 * full day's constellation, a star tapped open, and asleep by day. The app's
 * own run only ever sees the evening (ScreenshotApplication's clock).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class OwlScreenshot {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 10, 27)

    private val reads = listOf(
        Triple("Tagesschau", "Deutsche Bahn reports punctuality up for the third month running", 7),
        Triple("Heise", "Linux 7.2 released with a new scheduler for hybrid CPUs", 8),
        Triple("Quanta Magazine", "A new proof settles a 90-year-old question about prime gaps", 10),
        Triple("Ars Technica", "Inside the factory that recycles a million phone batteries a year", 12),
        Triple("NASA", "Webb spots carbon dioxide in the atmosphere of a rocky exoplanet", 15),
        Triple("The Guardian", "Heatwaves are getting longer across southern Europe, study finds", 18),
        Triple("The Verge", "Hands-on with the new e-ink tablets for note-taking", 20),
    ).mapIndexed { i, (feed, title, hour) ->
        val article = Article(
            id = 700L + i * 13, feedId = i.toLong(), feedName = feed, feedIcon = null, title = title, url = "https://example.com/$i",
            published = null, updated = null, author = null, summary = null, imageUrl = null, tags = emptyList(),
        )
        DatedArticle(article, day.atTime(hour, 10 + i * 5).atZone(zone).toInstant())
    }

    private fun show(now: ZonedDateTime, mode: ThemeMode) {
        compose.setContent {
            NexusTheme(NoctuaPalette, themeMode = mode) {
                Surface {
                    EndOfFeed(
                        empty = true,
                        pull = remember { EndPull(300f) {} },
                        refreshing = false,
                        refreshes = 0,
                        today = reads,
                        now = now,
                        onRefresh = {},
                        onOpen = {},
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
    }

    @Test
    fun awakeAtNight() {
        show(day.atTime(21, 40).atZone(zone), ThemeMode.Light)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/41-owl-night.png")

        // The fourth star: what was read, and when.
        val star = constellation(reads.map { it.article.id })[3]
        compose.onNodeWithContentDescription("An owl", substring = true).performTouchInput {
            click(SceneFrame(width.toFloat(), height.toFloat()).skyToPx(star.x, star.y))
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/42-owl-star.png")
    }

    @Test
    fun asleepByDay() {
        show(day.atTime(13, 5).atZone(zone), ThemeMode.Dark)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/43-owl-day-dark.png")
    }
}
