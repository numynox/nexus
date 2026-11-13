package io.github.numynox.nexus.abeona.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.BahnCard
import io.github.numynox.nexus.abeona.data.BahnCardInput
import io.github.numynox.nexus.abeona.data.HouseholdMember
import io.github.numynox.nexus.abeona.ui.common.PickedFile
import io.github.numynox.nexus.core.data.NexusConfig
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Household(
    val members: List<HouseholdMember>,
    /** The household's BahnCards, the one running longest first. */
    val cards: List<BahnCard> = emptyList(),
)

class SettingsViewModel(private val container: AppContainer, private val application: Application) : ViewModel() {
    private val repository = container.repository
    val config: NexusConfig = container.core.config
    val userId: String? get() = container.core.auth.currentUserId

    private val _household = MutableStateFlow<Loadable<Household>>(Loadable.Loading)
    val household: StateFlow<Loadable<Household>> = _household.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun load() = viewModelScope.launch {
        if (_household.value !is Loadable.Ready) _household.value = Loadable.Loading
        _household.value = loadable {
            coroutineScope {
                val members = async { repository.members() }
                val cards = async { container.trips.bahnCards().value }
                Household(members.await(), cards.await())
            }
        }
    }

    private fun write(done: String?, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { done?.let(_messages::tryEmit); load() }
            .onFailure { _messages.tryEmit(it.describe()) }
    }

    fun invite(email: String) = write("Invited ${email.trim()}") { repository.invite(email) }
    fun leave(onDone: () -> Unit) = write("You left the household") { repository.leaveHousehold(); onDone() }

    /** Saves a card, then its file: a new one uploaded, or the old one removed. */
    fun saveCard(input: BahnCardInput, id: String?, file: Uri?, removeFile: Boolean) = write("BahnCard saved") {
        val saved = repository.saveBahnCard(input, id)
        when {
            file != null -> {
                val picked = PickedFile.read(application, file, "bahncard.pdf")
                container.trips.attachBahnCardFile(saved, picked.name, picked.mimeType, picked.bytes)
            }
            removeFile && saved.filePath != null -> repository.removeBahnCardFile(saved)
        }
    }

    fun deleteCard(card: BahnCard) = write("BahnCard deleted") { repository.deleteBahnCard(card) }
}
