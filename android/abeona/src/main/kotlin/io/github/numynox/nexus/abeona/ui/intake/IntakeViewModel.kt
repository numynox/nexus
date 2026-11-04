package io.github.numynox.nexus.abeona.ui.intake

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.SaveTripInput
import io.github.numynox.nexus.abeona.domain.intake.IntakeDraft
import io.github.numynox.nexus.abeona.domain.intake.IntakeSource
import io.github.numynox.nexus.abeona.domain.intake.Parsed
import io.github.numynox.nexus.abeona.domain.intake.PdfText
import io.github.numynox.nexus.abeona.domain.intake.TicketFile
import io.github.numynox.nexus.abeona.domain.intake.intake
import io.github.numynox.nexus.abeona.domain.intake.parseDbTicket
import io.github.numynox.nexus.abeona.domain.intake.parseText
import io.github.numynox.nexus.abeona.ui.common.PickedFile
import io.github.numynox.nexus.core.data.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface IntakeState {
    data object Idle : IntakeState

    /** Reading the text or PDF, then looking the rides up. */
    data class Working(val matching: Boolean) : IntakeState

    data class Failed(val message: String) : IntakeState

    /** Read and looked up: the save sheet shows it. */
    data class Ready(val draft: IntakeDraft) : IntakeState
}

/**
 * A share text or ticket PDF → a trip: read on the phone (domain/intake),
 * looked up through abeona-transit, saved with its ticket attached. The web's
 * IntakeCard.svelte does the same.
 */
class IntakeViewModel(private val container: AppContainer, private val application: Application) : ViewModel() {
    private val repository = container.repository

    private val _state = MutableStateFlow<IntakeState>(IntakeState.Idle)
    val state: StateFlow<IntakeState> = _state.asStateFlow()

    /** [read] gives what was read and, for a PDF, the file to attach. */
    private fun run(source: IntakeSource, read: suspend () -> Pair<Parsed?, TicketFile?>) {
        if (_state.value is IntakeState.Working) return
        _state.value = IntakeState.Working(matching = false)
        viewModelScope.launch {
            _state.value = runCatching {
                val (parsed, ticket) = read()
                if (parsed == null || parsed.legs.isEmpty()) {
                    IntakeState.Failed(
                        if (source == IntakeSource.Pdf) "Abeona could not find a journey in this PDF. It reads DB Online-Tickets."
                        else "Abeona could not find a journey in this text. Share it from DB Navigator, or paste what its \"Share\" gives you.",
                    )
                } else {
                    _state.value = IntakeState.Working(matching = true)
                    IntakeState.Ready(intake(parsed, source, repository.intakePort, ticket))
                }
            }.getOrElse { IntakeState.Failed(it.describe()) }
        }
    }

    fun readText(text: String) {
        val value = text.trim()
        if (value.isNotEmpty()) run(IntakeSource.Share) { parseText(value) to null }
    }

    fun readPdf(uri: Uri) = run(IntakeSource.Pdf) {
        val file = PickedFile.read(application, uri, "ticket.pdf")
        val text = withContext(Dispatchers.Default) { PdfText.extract(application, file.bytes) }
        (parseDbTicket(text) ?: parseText(text)) to TicketFile(file.name, "application/pdf", file.bytes)
    }

    fun receive(share: IncomingShare) {
        share.uri?.let(::readPdf) ?: share.text?.let(::readText)
    }

    fun dismiss() {
        _state.value = IntakeState.Idle
    }

    fun save(draft: IntakeDraft, input: SaveTripInput, onError: (String) -> Unit, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.saveTrip(draft.match.journey, input.copy(source = draft.source.wire)) }
                .onSuccess { id ->
                    // The trip stands either way; a failed upload leaves the ticket to be added by hand.
                    draft.ticket?.let { runCatching { repository.attachTicket(id, it.name, it.mimeType, it.bytes) } }
                    _state.value = IntakeState.Idle
                    onSaved(id)
                }
                .onFailure { onError(it.describe()) }
        }
    }
}
