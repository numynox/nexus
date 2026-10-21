package io.github.numynox.nexus.annona.screenshots

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.numynox.nexus.annona.MainActivity
import io.github.numynox.nexus.core.testing.ScreenshotRobot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the real app against [FakeBackend], walked screen by screen:
 *
 *     ./gradlew :annona:recordRoborazziDebug
 *
 * writes PNGs to android/annona/build/outputs/roborazzi/app; CI uploads them
 * as the `annona-screenshots` artifact. Phone-sized (Pixel 8: 412 × 915 dp).
 * The camera is not granted, so the scanner shows its permission prompt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApplication::class, qualifiers = "w412dp-h915dp-xxhdpi")
class AppScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val robot = ScreenshotRobot(compose)

    @Before
    fun allowNotifications() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun type(text: String) {
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        robot.settle()
    }

    @Test
    fun walkThroughTheApp() = with(robot) {
        waitForText("USE SOON")
        shot("01-pantry")
        scrollTo("This month")
        shot("02-pantry-use-first")
        tapIcon("Add or use up")
        shot("03-stock-menu")

        tap("Scan to add")
        waitForText("Allow camera")
        type("yog")
        waitForText("Fage Greek yoghurt")
        shot("04-scan-search")
        tap("Fage Greek yoghurt")
        waitForText("Add to stock")
        shot("05-add-known-product")
        back()

        tapIcon("Add or use up")
        tap("Scan to add")
        waitForText("Allow camera")
        type(FakeBackend.NEW_BARCODE)
        compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        waitForText("Filled in from Open Food Facts, nutrition included. Check it before saving.")
        shot("06-add-new-from-open-food-facts")
        scrollIntoView("How many")
        shot("07-add-item-details")
        back()

        tap("Products")
        waitForText("In stock")
        shot("08-products")
        tap("Fage Greek yoghurt")
        waitForText("Nutrition per 100 g")
        shot("09-product")
        tap("Activity · 2")
        waitForText("Opened on Sunday, finish it first")
        shot("10-item-activity")
        dismiss()
        compose.onAllNodes(hasText("Change") and hasClickAction()).onFirst().performClick()
        waitForText("Kept in")
        shot("11-item-change")
        tap("Cancel")
        back()

        tap("Places")
        waitForText("Freezer")
        shot("12-places")

        tap("Settings")
        waitForText("Remind me before things expire")
        tap("Remind me before things expire")
        waitFor(isOn() and hasAnyDescendant(hasText("Remind me before things expire")))
        shot("13-settings")
        scrollTo("Categories")
        scrollIntoView("Dairy & Cheese")
        tapIcon("Edit Dairy & Cheese")
        waitForText("Edit category")
        shot("14-category-sheet")
        tap("Cancel")

        scrollTo("Wallpaper colours")
        compose.onAllNodes(hasText("Dark") and hasClickAction()).onFirst().performClick()
        awaitPreferences()
        tap("Pantry")
        // The pantry keeps its scroll position from earlier in the walk.
        waitForText("This month")
        shot("15-pantry-dark")
        tap("Products")
        waitForText("In stock")
        tap("Fage Greek yoghurt")
        waitForText("Nutrition per 100 g")
        shot("16-product-dark")
        back()

        tap("Settings")
        scrollTo("Sign out")
        shot("16b-settings-account-dark")
        tap("Sign out")
        waitForText("Sign in")
        shot("17-sign-in-dark")
    }
}
