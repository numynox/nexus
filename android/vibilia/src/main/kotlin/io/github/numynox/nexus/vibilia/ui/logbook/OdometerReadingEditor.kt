package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.vibilia.AppContainer
import io.github.numynox.nexus.vibilia.data.OdometerReading
import io.github.numynox.nexus.vibilia.data.OdometerReadingDraft
import io.github.numynox.nexus.vibilia.ui.EditorRequest
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/**
 * A dated odometer value without a cost — from a service record, say. It
 * extends the odometer chart and changes no statistics. The web form's limits
 * (OdometerReadingForm.svelte).
 */
class OdometerReadingEditorViewModel(private val container: AppContainer, request: EditorRequest) : ViewModel() {
    private val editing: OdometerReading? = (request as? EditorRequest.EditReading)?.reading
    private val carId: String = editing?.carId ?: (request as EditorRequest.NewReading).carId
    val isNew get() = editing == null

    var mileage by mutableStateOf(editing?.mileage?.toString().orEmpty())
    var notes by mutableStateOf(editing?.notes.orEmpty())
    var readAt by mutableStateOf(editing?.readAt?.toLocalDateTimeHere() ?: LocalDateTime.now().withSecond(0).withNano(0))

    var submitted by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val mileageError: String?
        get() {
            if (!submitted && mileage.isEmpty()) return null
            val value = mileage.toIntOrNull()
            return when {
                value == null || value < 0 -> "Enter the odometer value in whole kilometres."
                value >= 1_000_000 -> "Mileage must be below 1,000,000 km."
                else -> null
            }
        }

    val notesError: String?
        get() = if (notes.trim().length > 500) "Notes must be at most 500 characters." else null

    fun save(onSaved: () -> Unit) {
        submitted = true
        if (mileageError != null || notesError != null || saving) return
        val draft = OdometerReadingDraft(
            readAt = readAt.toInstantHere(),
            mileage = mileage.toIntOrNull() ?: return,
            notes = notes.trim().ifEmpty { null },
        )
        run(onSaved) {
            if (editing != null) container.logbook.updateReading(editing.id, draft) else container.logbook.createReading(carId, draft)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val reading = editing ?: return
        if (saving) return
        run(onDeleted) { container.logbook.deleteReading(reading.id) }
    }

    private fun run(onDone: () -> Unit, block: suspend () -> Unit) {
        saving = true
        error = null
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { onDone() }
                .onFailure { error = it.describe() }
            saving = false
        }
    }
}

@Composable
fun OdometerReadingEditor(viewModel: OdometerReadingEditorViewModel, onCancel: () -> Unit, onSaved: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (viewModel.isNew) "Log odometer reading" else "Edit odometer reading", style = MaterialTheme.typography.headlineSmall)
        Text(
            "An odometer value without a cost, for example from a service record from before you had the car. " +
                "It appears on the odometer chart and changes no statistics.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField(
            value = viewModel.mileage,
            onValueChange = { viewModel.mileage = it },
            label = "Odometer",
            icon = Icons.Rounded.Speed,
            suffix = "km",
            decimal = false,
            error = viewModel.mileageError,
        )
        DateTimeChips(viewModel.readAt, onChange = { viewModel.readAt = it })
        OutlinedTextField(
            value = viewModel.notes,
            onValueChange = { viewModel.notes = it },
            label = { Text("Notes") },
            placeholder = { Text("e.g. Service record") },
            leadingIcon = { Icon(Icons.Rounded.Notes, contentDescription = null) },
            isError = viewModel.notesError != null,
            supportingText = viewModel.notesError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        FormError(viewModel.error)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!viewModel.isNew) {
                TextButton(onClick = { confirmDelete = true }, enabled = !viewModel.saving) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Delete", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = { viewModel.save(onSaved) }, enabled = !viewModel.saving, modifier = Modifier.height(48.dp)) {
                    if (viewModel.saving) {
                        LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (viewModel.isNew) "Save reading" else "Update reading")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("Delete this reading?") },
            text = { Text("It disappears from the logbook and the odometer chart.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onSaved)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
