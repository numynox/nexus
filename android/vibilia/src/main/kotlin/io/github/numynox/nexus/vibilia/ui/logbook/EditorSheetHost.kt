package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.vibilia.data.Car
import io.github.numynox.nexus.vibilia.ui.AppViewModel
import io.github.numynox.nexus.vibilia.ui.EditorRequest
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.valueOrNull
import kotlinx.coroutines.launch

/**
 * The refuel and expense forms, as one bottom sheet above every screen — so
 * "Refuel" on the prices page and in the logbook open the same thing.
 */
@Composable
fun EditorSheetHost(app: AppViewModel) {
    val request by app.editor.collectAsStateWithLifecycle()
    val cars by app.cars.collectAsStateWithLifecycle()
    val current = request ?: return
    val carList = cars.valueOrNull.orEmpty()
    if (carList.isEmpty()) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(saved: Boolean) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { app.closeEditor(saved) }
    }

    // The forms' view models live exactly as long as the sheet.
    val owner = remember(current) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    ModalBottomSheet(onDismissRequest = { app.closeEditor(false) }, sheetState = sheetState) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { EditorContent(current, carList, ::close) }
    }
}

@Composable
private fun EditorContent(current: EditorRequest, carList: List<Car>, close: (Boolean) -> Unit) {
    when (current) {
        is EditorRequest.NewRefuel, is EditorRequest.EditRefuel -> {
            val viewModel = containerViewModel { RefuelEditorViewModel(it, current, carList) }
            RefuelEditor(viewModel, onCancel = { close(false) }, onSaved = { close(true) })
        }
        is EditorRequest.NewExpense, is EditorRequest.EditExpense -> {
            val viewModel = containerViewModel { ExpenseEditorViewModel(it, current) }
            ExpenseEditor(viewModel, onCancel = { close(false) }, onSaved = { close(true) })
        }
    }
}
