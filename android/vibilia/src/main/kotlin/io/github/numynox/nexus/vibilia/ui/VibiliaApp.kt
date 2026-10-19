package io.github.numynox.nexus.vibilia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.app.NexusApp
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.R
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel

@Composable
fun VibiliaApp(container: AppContainer) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val app = containerViewModel { AppViewModel(it, application) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()

    NexusApp(
        core = container.core,
        auth = auth,
        palette = VibiliaPalette,
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        tagline = "Fuel prices, refuels and what your car really costs.",
        logo = { painterResource(R.drawable.vibilia_logo) },
    ) {
        MainScaffold(app)
    }
}
