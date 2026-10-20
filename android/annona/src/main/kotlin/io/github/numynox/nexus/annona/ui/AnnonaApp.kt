package io.github.numynox.nexus.annona.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.R
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.app.NexusApp
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun AnnonaApp(container: AppContainer, shortcut: MutableStateFlow<ScanMode?>) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val app = containerViewModel { AppViewModel(it, application) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()

    NexusApp(
        core = container.core,
        auth = auth,
        palette = AnnonaPalette,
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        tagline = "What's in the fridge, and what to eat first.",
        logo = { painterResource(R.drawable.annona_logo) },
    ) {
        MainScaffold(app, shortcut)
    }
}
