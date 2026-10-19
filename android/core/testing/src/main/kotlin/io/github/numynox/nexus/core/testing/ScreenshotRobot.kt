package io.github.numynox.nexus.core.testing

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureScreenRoboImage

/**
 * Walks an app the way a person would and photographs it. Every app's
 * screenshot test is a script of these steps; images land in
 * `build/outputs/roborazzi/app/<name>.png` of the app module, which CI
 * uploads as the app's screenshots artifact.
 */
class ScreenshotRobot(private val compose: AndroidComposeTestRule<*, out ComponentActivity>) {

    /**
     * Lets entrance animations — counters settling, sheets sliding up —
     * finish. The test clock auto-advances; Compose's test policy pauses the
     * infinite ones (loading indicators), so waiting for idle terminates.
     */
    fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
    }

    /**
     * For changes that travel through DataStore (a theme switch, a setting):
     * its writes run on a real IO thread, which the test clock does not wait
     * for, so give them a moment of wall-clock time before settling.
     */
    fun awaitPreferences() {
        Thread.sleep(400)
        settle()
    }

    /**
     * Runs [block] with the test clock stepped by hand. A focused text field
     * blinks its cursor forever, which keeps an auto-advancing clock from
     * ever going idle; dialogs focus their first field.
     */
    fun withFrozenClock(block: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try {
            block()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    fun waitFor(matcher: SemanticsMatcher, timeoutMillis: Long = 30_000) {
        try {
            compose.waitUntil(timeoutMillis) {
                compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: Throwable) {
            // What the screen showed instead is usually the explanation.
            shot("failed-waiting")
            throw timeout
        }
        settle()
    }

    fun waitForText(text: String) = waitFor(hasText(text))

    fun shot(name: String) {
        captureScreenRoboImage("build/outputs/roborazzi/app/$name.png")
    }

    /** Taps the first clickable node showing [label] — a tab, a button, a chip. */
    fun tap(label: String) {
        compose.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
        settle()
    }

    fun tapIcon(contentDescription: String) {
        compose.onAllNodes(hasContentDescription(contentDescription) and hasClickAction()).onFirst().performClick()
        settle()
    }

    fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        settle()
    }

    fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }
}
