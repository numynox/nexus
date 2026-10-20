package io.github.numynox.nexus.noctua

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.numynox.nexus.core.ui.app.startNexusActivity
import io.github.numynox.nexus.noctua.ui.NoctuaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as NoctuaApplication).container
        startNexusActivity(container.core)
        super.onCreate(savedInstanceState)
        setContent { NoctuaApp(container) }
    }
}
