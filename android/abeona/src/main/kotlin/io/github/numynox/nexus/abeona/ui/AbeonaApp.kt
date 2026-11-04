package io.github.numynox.nexus.abeona.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.abeona.ui.intake.IncomingShare
import io.github.numynox.nexus.core.ui.app.NexusApp
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun AbeonaApp(container: AppContainer, openTrip: MutableStateFlow<String?>, incoming: MutableStateFlow<IncomingShare?>) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val app = containerViewModel { AppViewModel(it, application) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()

    NexusApp(
        core = container.core,
        auth = auth,
        palette = AbeonaPalette,
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        tagline = "Your trains, your tickets, and every change on time.",
        logo = { painterResource(R.drawable.abeona_logo) },
    ) {
        MainScaffold(app, openTrip, incoming)
    }
}
