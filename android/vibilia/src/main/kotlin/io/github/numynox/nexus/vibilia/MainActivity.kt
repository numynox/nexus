package io.github.numynox.nexus.vibilia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.numynox.nexus.core.ui.app.startNexusActivity
import io.github.numynox.nexus.vibilia.ui.VibiliaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as VibiliaApplication).container
        startNexusActivity(container.core)
        super.onCreate(savedInstanceState)
        setContent { VibiliaApp(container) }
    }
}
