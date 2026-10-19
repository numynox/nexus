package io.github.numynox.nexus.annona

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.numynox.nexus.annona.ui.AnnonaApp
import io.github.numynox.nexus.annona.ui.ScanMode
import io.github.numynox.nexus.core.ui.app.startNexusActivity
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A launcher shortcut's request to open the scanner, consumed by the navigation. */
    private val shortcut = MutableStateFlow<ScanMode?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as AnnonaApplication).container
        startNexusActivity(container.core)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) shortcut.value = intent.scanMode()
        setContent { AnnonaApp(container, shortcut) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.scanMode()?.let { shortcut.value = it }
    }

    private fun Intent.scanMode(): ScanMode? = when (action) {
        // The actions res/xml/shortcuts.xml sends.
        "io.github.numynox.nexus.annona.SCAN_ADD" -> ScanMode.Add
        "io.github.numynox.nexus.annona.SCAN_CONSUME" -> ScanMode.Consume
        else -> null
    }
}
