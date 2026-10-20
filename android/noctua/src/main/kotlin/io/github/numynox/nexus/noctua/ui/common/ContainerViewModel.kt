package io.github.numynox.nexus.noctua.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import io.github.numynox.nexus.core.ui.nexusViewModel
import io.github.numynox.nexus.noctua.AppContainer
import io.github.numynox.nexus.noctua.NoctuaApplication

/** A ViewModel built from the app's [AppContainer]; [key] separates instances. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as NoctuaApplication).container
    return nexusViewModel(key) { create(container) }
}
