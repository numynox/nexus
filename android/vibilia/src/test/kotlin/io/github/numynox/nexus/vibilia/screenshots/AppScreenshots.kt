package io.github.numynox.nexus.vibilia.screenshots

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import io.github.numynox.nexus.vibilia.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the real app: MainActivity, its view models and
 * repositories, against [FakeBackend] instead of Supabase. Walks through every
 * screen the way a person would and photographs each.
 *
 *     ./gradlew :vibilia:recordRoborazziDebug
 *
 * writes PNGs to android/vibilia/build/outputs/roborazzi/app; CI uploads the
 * same folder as the `vibilia-screenshots` artifact of every build.
 * Phone-sized (Pixel 8: 412 × 915 dp). The map is drawn schematically, since
 * MapLibre's native library does not load on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AppScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun grantAndLocate() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val manager = app.getSystemService(LocationManager::class.java)
        val shadow = shadowOf(manager)
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).forEach { provider ->
            shadow.setProviderEnabled(provider, true)
            shadow.setLastKnownLocation(
                provider,
                Location(provider).apply {
                    latitude = 49.5897
                    longitude = 11.0040
                    time = System.currentTimeMillis()
                },
            )
        }
    }

    private val robot = ScreenshotRobot(compose)

    private fun waitFor(matcher: SemanticsMatcher) = robot.waitFor(matcher)
    private fun settle() = robot.settle()
    private fun shot(name: String) = robot.shot(name)
    private fun scrollTo(text: String) = robot.scrollTo(text)
    private fun back() = robot.back()
    private fun tab(label: String) {
        compose.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
    }

    @Test
    fun walkThroughTheApp() {
        waitFor(hasText("Fill up"))
        shot("01-prices")
        scrollTo("Esso")
        shot("02-prices-stations")

        tab("Nearby")
        waitFor(hasText("TotalEnergies"))
        shot("03-nearby")

        tab("History")
        settle()
        compose.onNode(hasContentDescription("Log a refuel")).performClick()
        waitFor(hasText("Log refuel"))
        shot("04-refuel-form")
        compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
        settle()

        tab("Logbook")
        waitFor(hasText("Annual inspection"))
        shot("05-logbook")
        compose.onNode(hasContentDescription("Add entry")).performClick()
        settle()
        shot("06-logbook-add")
        compose.onNode(hasContentDescription("Close")).performClick()
        settle()

        tab("Statistics")
        waitFor(hasText("FUEL COST"))
        shot("07-statistics")
        scrollTo("Recent drives")
        shot("08-statistics-driving")
        scrollTo("Kilometres per month")
        shot("09-statistics-charts")

        tab("Settings")
        waitFor(hasText("Drive tracking"))
        shot("10-settings")
        compose.onAllNodes(hasText("Golf") and hasClickAction()).onFirst().performClick()
        waitFor(hasText("Shared with"))
        shot("11-vehicle")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Delete vehicle"))
        settle()
        shot("12-vehicle-sharing")
        back()

        scrollTo("Wallpaper colours")
        compose.onAllNodes(hasText("Dark") and hasClickAction()).onFirst().performClick()
        robot.awaitPreferences()
        tab("Prices")
        waitFor(hasText("My stations"))
        shot("13-prices-dark")
        tab("Statistics")
        waitFor(hasText("FUEL COST"))
        shot("14-statistics-dark")
        tab("Logbook")
        waitFor(hasText("Annual inspection"))
        shot("15-logbook-dark")

        tab("Settings")
        waitFor(hasText("Drive tracking"))
        scrollTo("Sign out")
        compose.onAllNodes(hasText("Sign out") and hasClickAction()).onFirst().performClick()
        waitFor(hasText("Sign in"))
        shot("16-sign-in-dark")
    }
}
