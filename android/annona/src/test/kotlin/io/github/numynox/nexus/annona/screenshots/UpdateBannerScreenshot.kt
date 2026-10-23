package io.github.numynox.nexus.annona.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.numynox.nexus.annona.ui.AnnonaPalette
import io.github.numynox.nexus.core.data.AvailableUpdate
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The update banner in both themes. The fake backend never offers an update,
 * so no screen shows it — which is how its Download button went unreadable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class UpdateBannerScreenshot {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun updateBanner() {
        val update = AvailableUpdate(versionName = "1.4.57", pageUrl = "https://github.com", apkUrl = null)
        compose.setContent {
            Column {
                ThemeMode.entries.filter { it != ThemeMode.System }.forEach { mode ->
                    NexusTheme(AnnonaPalette, themeMode = mode) {
                        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                            UpdateBanner(update)
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app/40-update-banner.png")
    }
}
