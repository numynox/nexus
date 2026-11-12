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
import io.github.numynox.nexus.abeona.domain.intake.parseTicketTrips
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

    /**
     * Read and looked up: the save sheet shows [draft]. A ticket for both
     * directions has the return in [queued], saved after it with the same PDF.
     */
    data class Ready(
        val draft: IntakeDraft,
        val queued: List<IntakeDraft> = emptyList(),
        /** The first trip saved from this ticket: where the app goes once all are saved. */
        val firstSaved: String? = null,
    ) : IntakeState
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
    private fun run(source: IntakeSource, read: suspend () -> Pair<List<Parsed>, TicketFile?>) {
        if (_state.value is IntakeState.Working) return
        _state.value = IntakeState.Working(matching = false)
        viewModelScope.launch {
            _state.value = runCatching {
                val (parsed, ticket) = read()
                val trips = parsed.filter { it.legs.isNotEmpty() }
                if (trips.isEmpty()) {
                    IntakeState.Failed(
                        if (source == IntakeSource.Pdf) "Abeona could not find a journey in this PDF. It reads DB Online-Tickets and Trenitalia tickets."
                        else "Abeona could not find a journey in this text. Share it from DB Navigator, or paste what its \"Share\" gives you.",
                    )
                } else {
                    _state.value = IntakeState.Working(matching = true)
                    val drafts = trips.map { intake(it, source, repository.intakePort, ticket) }
                    IntakeState.Ready(drafts.first(), drafts.drop(1))
                }
            }.getOrElse { IntakeState.Failed(it.describe()) }
        }
    }

    fun readText(text: String) {
        val value = text.trim()
        if (value.isNotEmpty()) run(IntakeSource.Share) { listOfNotNull(parseText(value)) to null }
    }

    fun readPdf(uri: Uri) = run(IntakeSource.Pdf) {
        val file = PickedFile.read(application, uri, "ticket.pdf")
        val text = withContext(Dispatchers.Default) { PdfText.extract(application, file.bytes) }
        parseTicketTrips(text) to TicketFile(file.name, "application/pdf", file.bytes)
    }

    fun receive(share: IncomingShare) {
        share.uri?.let(::readPdf) ?: share.text?.let(::readText)
    }

    fun dismiss() {
        _state.value = IntakeState.Idle
    }

    /** Saves the shown trip; [onSaved] gets the first trip's id once the last of the ticket's trips is saved. */
    fun save(ready: IntakeState.Ready, input: SaveTripInput, onError: (String) -> Unit, onSaved: (String) -> Unit) {
        val draft = ready.draft
        viewModelScope.launch {
            runCatching { repository.saveTrip(draft.match.journey, input.copy(source = draft.source.wire)) }
                .onSuccess { id ->
                    // The trip stands either way; a failed upload leaves the ticket to be added by hand.
                    draft.ticket?.let { runCatching { repository.attachTicket(id, it.name, it.mimeType, it.bytes) } }
                    val first = ready.firstSaved ?: id
                    val next = ready.queued.firstOrNull()
                    if (next != null) {
                        // The same ticket, the other direction: its own sheet, the same PDF.
                        _state.value = IntakeState.Ready(next, ready.queued.drop(1), first)
                    } else {
                        _state.value = IntakeState.Idle
                        onSaved(first)
                    }
                }
                .onFailure { onError(it.describe()) }
        }
    }
}
