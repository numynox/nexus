package io.github.numynox.nexus.abeona

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.numynox.nexus.abeona.ui.AbeonaApp
import io.github.numynox.nexus.abeona.widget.NextTripWidget
import io.github.numynox.nexus.core.ui.app.startNexusActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A trip to open, from the widget or a notification; consumed by the navigation. */
    private val openTrip = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as AbeonaApplication).container
        startNexusActivity(container.core)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openTrip.value = intent.getStringExtra(EXTRA_TRIP_ID)
        setContent { AbeonaApp(container, openTrip) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TRIP_ID)?.let { openTrip.value = it }
    }

    /** Leaving the app is when the home screen shows again: bring the widget up to date. */
    override fun onStop() {
        super.onStop()
        val container = (application as AbeonaApplication).container
        container.core.scope.launch { NextTripWidget.refresh(applicationContext) }
    }

    companion object {
        const val EXTRA_TRIP_ID = "io.github.numynox.nexus.abeona.TRIP_ID"

        /** Opens [tripId]'s detail, bringing the app forward if it is running. */
        fun intentFor(context: Context, tripId: String?): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { tripId?.let { putExtra(EXTRA_TRIP_ID, it) } }
    }
}
