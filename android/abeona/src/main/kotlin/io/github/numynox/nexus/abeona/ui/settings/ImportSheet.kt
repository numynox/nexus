package io.github.numynox.nexus.abeona.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.ImportResult
import io.github.numynox.nexus.abeona.data.ImportedTrip
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.data.describe
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where an import stands: reading, the trips read, the ones chosen, how many were saved. */
data class ImportState(
    val reading: Boolean = false,
    val saving: Boolean = false,
    val result: ImportResult? = null,
    val chosen: Set<String> = emptySet(),
    val saved: Int = 0,
    val error: String? = null,
)

class ImportViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Reads the check-ins; the token is passed on and not kept, here or in the sheet afterwards. */
    fun read(token: String, from: LocalDate, to: LocalDate) {
        if (_state.value.reading) return
        _state.value = ImportState(reading = true)
        viewModelScope.launch {
            runCatching { container.repository.readTraewelling(token, from, to) }
                .onSuccess { result ->
                    _state.value = ImportState(result = result, chosen = result.trips.filter { !it.duplicate }.map { it.externalId }.toSet())
                }
                .onFailure { e -> _state.value = ImportState(error = e.describe()) }
        }
    }

    fun toggle(trip: ImportedTrip) = _state.update {
        it.copy(chosen = if (trip.externalId in it.chosen) it.chosen - trip.externalId else it.chosen + trip.externalId)
    }

    fun importChosen(onDone: (Int) -> Unit) {
        val start = _state.value
        val result = start.result ?: return
        if (start.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            var saved = 0
            try {
                for (trip in result.trips) {
                    if (trip.externalId !in _state.value.chosen) continue
                    container.repository.saveImportedTrip(trip)
                    saved++
                    _state.update { s ->
                        s.copy(
                            chosen = s.chosen - trip.externalId,
                            saved = s.saved + 1,
                            result = s.result?.copy(trips = s.result.trips.map { if (it.externalId == trip.externalId) it.copy(duplicate = true) else it }),
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.describe()) }
            }
            _state.update { it.copy(saving = false) }
            if (saved > 0) onDone(saved)
        }
    }
}

/** Past trips from Träwelling: paste a personal token once, choose the range, pick the trips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(onImported: (Int) -> Unit, onDismiss: () -> Unit) {
    val viewModel = containerViewModel { ImportViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(LocalDate.now().minusDays(90).toString()) }
    var to by remember { mutableStateOf(LocalDate.now().toString()) }
    val fromDate = runCatching { LocalDate.parse(from) }.getOrNull()
    val toDate = runCatching { LocalDate.parse(to) }.getOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Import from Träwelling", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Create a personal access token on traewelling.de under Settings → Applications and paste it here. " +
                    "It is used once to read your check-ins and is not kept. Trips from the last month can also be found in Search and saved as past trips.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it.trim() },
                label = { Text("Träwelling token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = from, onValueChange = { from = it.take(10) }, label = { Text("From") }, singleLine = true,
                    isError = fromDate == null, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = to, onValueChange = { to = it.take(10) }, label = { Text("To") }, singleLine = true,
                    isError = toDate == null, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    if (fromDate != null && toDate != null) {
                        viewModel.read(token, fromDate, toDate)
                        token = ""
                    }
                },
                enabled = !state.reading && token.length >= 20 && fromDate != null && toDate != null && !fromDate.isAfter(toDate),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.reading) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                Text("Read check-ins")
            }

            state.result?.let { result ->
                Text(
                    buildString {
                        append("${result.statuses} check-ins, ${result.trips.size} ${if (result.trips.size == 1) "trip" else "trips"}")
                        if (result.skipped > 0) append(", ${result.skipped} left out (planes, taxis)")
                        append('.')
                        if (result.truncated) append(" Only the most recent were read; import an older range next.")
                        if (state.saved > 0) append(" ${state.saved} imported.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                result.trips.forEach { trip -> ImportRow(trip, trip.externalId in state.chosen, enabled = !trip.duplicate && !state.saving) { viewModel.toggle(trip) } }
                if (result.trips.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.importChosen(onImported) },
                        enabled = state.chosen.isNotEmpty() && !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.saving) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                        Text("Import ${state.chosen.size} ${if (state.chosen.size == 1) "trip" else "trips"}")
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ImportRow(trip: ImportedTrip, chosen: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val departure = trip.plannedDeparture.toInstantOrNull()
    val arrival = trip.plannedArrival.toInstantOrNull()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = chosen, onCheckedChange = { onToggle() }, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text("${trip.origin} → ${trip.destination}", style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    departure?.let { TripFormat.day(it) },
                    "${TripFormat.time(departure)}–${TripFormat.time(arrival)}",
                    trip.lines,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            when {
                trip.duplicate -> "Saved"
                (trip.arrivalDelayMinutes ?: 0) > 0 -> "+${trip.arrivalDelayMinutes}"
                else -> ""
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (trip.duplicate) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
        )
    }
}
