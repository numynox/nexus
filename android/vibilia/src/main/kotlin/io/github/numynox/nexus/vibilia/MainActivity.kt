package io.github.numynox.nexus.vibilia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.numynox.nexus.core.ui.app.startNexusActivity
import io.github.numynox.nexus.vibilia.ui.VibiliaApp
import io.github.numynox.nexus.vibilia.widget.FuelWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as VibiliaApplication).container
        startNexusActivity(container.core)
        super.onCreate(savedInstanceState)
        setContent { VibiliaApp(container) }
    }

    /** Leaving the app is when the home screen shows again: bring the widget up to date. */
    override fun onStop() {
        super.onStop()
        val container = (application as VibiliaApplication).container
        container.scope.launch { FuelWidget.refresh(applicationContext) }
    }
}
