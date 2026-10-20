package io.github.numynox.nexus.core.ui.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.data.NexusCore
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.signin.SignInScreen
import io.github.numynox.nexus.core.ui.theme.NexusPalette
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import io.github.numynox.nexus.core.ui.theme.ThemeMode

/**
 * The root of every app: its theme, and the session gate — the native
 * counterpart of `@nexus/ui`'s session gate. A spinner while the stored
 * session loads (normally hidden behind the splash screen), the shared sign-in
 * screen when signed out, [signedIn] otherwise.
 */
@Composable
fun NexusApp(
    core: NexusCore,
    auth: AuthState,
    palette: NexusPalette,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    tagline: String,
    logo: @Composable () -> Painter,
    signedIn: @Composable () -> Unit,
) {
    NexusTheme(palette = palette, themeMode = themeMode, dynamicColor = dynamicColor) {
        AnimatedContent(
            targetState = auth::class,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "auth",
        ) { state ->
            when (state) {
                AuthState.Loading::class -> LoadingState()
                AuthState.SignedIn::class -> signedIn()
                else -> SignInScreen(
                    appName = core.config.appName,
                    tagline = tagline,
                    logo = logo(),
                    onSignIn = { email, password -> core.auth.signIn(email, password) },
                    notice = if (core.config.isConfigured) null else "This build has no Supabase connection configured.",
                )
            }
        }
    }
}
