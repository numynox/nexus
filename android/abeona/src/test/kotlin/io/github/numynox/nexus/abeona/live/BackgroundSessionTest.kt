package io.github.numynox.nexus.abeona.live

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.numynox.nexus.abeona.AbeonaApplication
import io.github.numynox.nexus.abeona.screenshots.FakeBackend
import io.github.numynox.nexus.abeona.screenshots.ScreenshotApplication
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The live companion runs with the app in the background, where supabase-kt
 * stops refreshing the session and sets its status back to Initializing until
 * the app comes forward. Requests made then must still go out: on a phone,
 * every refresh failed from the moment the app was left until it was opened
 * again, and the notification stayed at its last values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = ScreenshotApplication::class)
class BackgroundSessionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Runs [block] off the main thread, keeping Robolectric's main looper turning meanwhile, as a phone's would. */
    private fun <T> offMain(timeoutMs: Long, block: suspend () -> T): T {
        val job = CoroutineScope(Dispatchers.IO).async { block() }
        val until = System.currentTimeMillis() + timeoutMs
        while (!job.isCompleted) {
            check(System.currentTimeMillis() < until) { "Timed out after $timeoutMs ms" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return runBlocking { job.await() }
    }

    @OptIn(SupabaseInternal::class)
    @Test
    fun requestsGoOutWhileTheAppIsInTheBackground() {
        val container = (context.applicationContext as AbeonaApplication).container
        val supabase = container.core.supabase
        offMain(10_000) { container.core.auth.awaitUserId() }
        // What supabase-kt's android lifecycle observer does in onStop.
        offMain(5_000) {
            supabase.auth.stopAutoRefreshForCurrentSession()
            supabase.auth.setSessionStatus(SessionStatus.Initializing)
        }

        // Well inside the gate's 15 s: the session is loaded again, not waited for.
        val trip = offMain(5_000) { container.trips.trip(FakeBackend.NEXT) }
        assertNull("from the network, not the offline pack", trip.offlineSince)
        assertNotNull(trip.value)
        assertTrue(supabase.auth.sessionStatus.value is SessionStatus.Authenticated)
    }
}
