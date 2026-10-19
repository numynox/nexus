package io.github.numynox.nexus.noctua.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.app.NexusApp
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.R
import io.github.numynox.nexus.noctua.ui.common.containerViewModel

@Composable
fun NoctuaApp(container: AppContainer) {
    val app = containerViewModel { AppViewModel(it) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()

    NexusApp(
        core = container.core,
        auth = auth,
        palette = NoctuaPalette,
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        tagline = "Your feeds, the same story once, and what you actually read.",
        logo = { painterResource(R.drawable.noctua_logo) },
    ) {
        MainScaffold(app)
    }
}
