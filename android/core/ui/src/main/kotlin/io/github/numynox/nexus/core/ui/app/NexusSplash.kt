package io.github.numynox.nexus.core.ui.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.core.data.NexusCore

/**
 * Call first in `onCreate`, before `super.onCreate`: holds the splash until
 * the stored session has loaded, so a signed-in user never sees the sign-in
 * screen flash past, and draws edge to edge.
 */
fun ComponentActivity.startNexusActivity(core: NexusCore) {
    installSplashScreen().setKeepOnScreenCondition { core.auth.state.value is AuthState.Loading }
    enableEdgeToEdge()
}
