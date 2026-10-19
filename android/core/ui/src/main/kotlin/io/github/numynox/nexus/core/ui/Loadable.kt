package io.github.numynox.nexus.core.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.numynox.nexus.core.data.describe

/** What a screen shows while its data is on the way. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Failed(val message: String) : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
}

val <T> Loadable<T>.valueOrNull: T? get() = (this as? Loadable.Ready)?.value

suspend fun <T> loadable(block: suspend () -> T): Loadable<T> =
    runCatching { Loadable.Ready(block()) }.getOrElse { Loadable.Failed(it.describe()) }

/**
 * A ViewModel built by [create], scoped to the current back-stack entry; [key]
 * separates instances, e.g. per car. Each app wraps this in its own
 * `containerViewModel`, which hands [create] the app's container.
 */
@Composable
inline fun <reified VM : ViewModel> nexusViewModel(
    key: String? = null,
    crossinline create: () -> VM,
): VM = viewModel(key = key, factory = viewModelFactory { initializer { create() } })
