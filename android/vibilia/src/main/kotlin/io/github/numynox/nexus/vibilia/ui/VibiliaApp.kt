package io.github.numynox.nexus.vibilia.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.signin.SignInScreen
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.R
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel

@Composable
fun VibiliaApp(container: AppContainer) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val app = containerViewModel { AppViewModel(it, application) }
    val settings by app.settings.collectAsStateWithLifecycle()
    val auth by app.auth.collectAsStateWithLifecycle()

    NexusTheme(palette = VibiliaPalette, themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        AnimatedContent(
            targetState = auth::class,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "auth",
        ) { state ->
            when (state) {
                AuthState.Loading::class -> LoadingState()
                AuthState.SignedIn::class -> MainScaffold(app)
                else -> SignInScreen(
                    appName = "Vibilia",
                    tagline = "Fuel prices, refuels and what your car really costs.",
                    logo = painterResource(R.drawable.vibilia_logo),
                    onSignIn = { email, password -> container.auth.signIn(email, password) },
                    notice = if (container.config.isConfigured) null else "This build has no Supabase connection configured.",
                )
            }
        }
    }
}
