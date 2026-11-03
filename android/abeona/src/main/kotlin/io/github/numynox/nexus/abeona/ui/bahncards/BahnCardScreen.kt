package io.github.numynox.nexus.abeona.ui.bahncards

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.BahnCard
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.common.DbNavigator
import io.github.numynox.nexus.abeona.ui.common.OfflinePill
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.abeona.ui.wallet.FullBrightness
import io.github.numynox.nexus.abeona.ui.wallet.TicketRenderer
import io.github.numynox.nexus.abeona.ui.wallet.ZoomablePages
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.LoadingState
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A BahnCard ready to show: its file as images, and whose it is. */
data class BahnCardPages(val card: BahnCard, val owner: String?, val pages: List<Bitmap>, val offlineSince: Instant?)

class BahnCardViewModel(private val container: AppContainer, private val cardId: String) : ViewModel() {
    private val _card = MutableStateFlow<Loadable<BahnCardPages>>(Loadable.Loading)
    val card: StateFlow<Loadable<BahnCardPages>> = _card.asStateFlow()

    fun load() = viewModelScope.launch {
        _card.value = Loadable.Loading
        _card.value = try {
            val fetched = container.trips.bahnCards()
            val card = fetched.value.firstOrNull { it.id == cardId } ?: error("This BahnCard no longer exists.")
            val owner = if (fetched.offlineSince == null) {
                runCatching { container.repository.travellers().firstOrNull { it.id == card.travellerId }?.name }.getOrNull()
            } else {
                null
            }
            val file = container.trips.bahnCardFile(card)
            val pages = withContext(Dispatchers.Default) { TicketRenderer.render(file) }
            if (pages.isEmpty()) error("This file could not be opened.")
            Loadable.Ready(BahnCardPages(card, owner, pages, fetched.offlineSince))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Loadable.Failed(e.describe())
        }
    }
}

/**
 * The stored BahnCard, white and bright like a ticket, with "Open in DB
 * Navigator" always beside it: the digital BahnCard only counts as proof
 * inside DB's app, so this copy is the fallback, not the proof.
 */
@Composable
fun BahnCardScreen(app: AppViewModel, cardId: String, navigator: Navigator) {
    val context = LocalContext.current
    val viewModel = containerViewModel(key = "bahncard-$cardId") { BahnCardViewModel(it, cardId) }
    val state by viewModel.card.collectAsStateWithLifecycle()

    LaunchedEffect(cardId) { viewModel.load() }
    FullBrightness()

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp, start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = navigator.back) { Icon(Icons.Rounded.Close, contentDescription = "Close BahnCard", tint = AbeonaColors.Ink) }
                val ready = (state as? Loadable.Ready)?.value
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(ready?.owner, ready?.card?.label).joinToString(" · ").ifEmpty { "BahnCard" },
                        style = MaterialTheme.typography.titleMedium,
                        color = AbeonaColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ready?.card?.let { card ->
                        Text(
                            "Valid ${TripFormat.date(card.from)} – ${TripFormat.date(card.to)}" + (card.cardNumber?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = AbeonaColors.Ink.copy(alpha = 0.7f),
                        )
                    }
                    ready?.offlineSince?.let { OfflinePill(it, Modifier.padding(top = 4.dp)) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val current = state) {
                    Loadable.Loading -> LoadingState(Modifier.fillMaxSize())
                    is Loadable.Failed -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(current.message, color = AbeonaColors.Ink)
                        TextButton(onClick = { viewModel.load() }) { Text("Try again") }
                    }
                    is Loadable.Ready -> ZoomablePages(current.value.pages, description = "BahnCard")
                }
            }
            Surface(color = AbeonaColors.Mint, contentColor = AbeonaColors.Ink, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Badge, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            "The digital BahnCard only counts as proof inside the DB Navigator app. Keep this copy for your records.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { DbNavigator.open(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = AbeonaColors.Green, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Open in DB Navigator", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
