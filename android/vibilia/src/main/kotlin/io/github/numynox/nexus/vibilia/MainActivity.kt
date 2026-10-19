package io.github.numynox.nexus.vibilia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.numynox.nexus.core.data.AuthState
import io.github.numynox.nexus.vibilia.ui.VibiliaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as VibiliaApplication).container
        // Hold the splash until the stored session has loaded, so a signed-in
        // user never sees the sign-in screen flash past.
        installSplashScreen().setKeepOnScreenCondition { container.auth.state.value is AuthState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { VibiliaApp(container) }
    }
}
