package io.github.numynox.nexus.vibilia.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.VibiliaApplication
import androidx.compose.ui.platform.LocalContext

/** What a screen shows while its data is on the way. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Failed(val message: String) : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
}

val <T> Loadable<T>.valueOrNull: T? get() = (this as? Loadable.Ready)?.value

suspend fun <T> loadable(block: suspend () -> T): Loadable<T> =
    runCatching { Loadable.Ready(block()) }.getOrElse { Loadable.Failed(it.describe()) }

/** A ViewModel built from the app's [AppContainer]; [key] separates instances, e.g. per car. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as VibiliaApplication).container
    val factory: ViewModelProvider.Factory = viewModelFactory { initializer { create(container) } }
    return viewModel(key = key, factory = factory)
}
