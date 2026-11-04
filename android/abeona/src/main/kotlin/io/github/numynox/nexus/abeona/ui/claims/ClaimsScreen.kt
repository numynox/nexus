package io.github.numynox.nexus.abeona.ui.claims

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.ClaimEntry
import io.github.numynox.nexus.abeona.data.ClaimsOverview
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import io.github.numynox.nexus.abeona.domain.CompensationText
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.attention
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClaimsViewModel(private val container: AppContainer) : ViewModel() {
    private val _overview = MutableStateFlow<Loadable<ClaimsOverview>>(Loadable.Loading)
    val overview: StateFlow<Loadable<ClaimsOverview>> = _overview.asStateFlow()

    fun load(year: Int = LocalDate.now().year) = viewModelScope.launch {
        _overview.value = loadable { container.repository.claimsOverview(year) }
    }
}

/**
 * The household's compensation claims: what is still to be claimed, soonest
 * deadline first, what waits for DB's answer, and what came back this year.
 * The web shows the same under the year in review.
 */
@Composable
fun ClaimsScreen(app: AppViewModel, navigator: Navigator) {
    val viewModel = containerViewModel { ClaimsViewModel(it) }
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    LaunchedEffect(version) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compensation") },
                navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when (val state = overview) {
            Loadable.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is Loadable.Failed -> ErrorState(state.message, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding))
            is Loadable.Ready -> ClaimsList(state.value, Modifier.padding(top = padding.calculateTopPadding()), onTrip = navigator::trip)
        }
    }
}

@Composable
internal fun ClaimsList(overview: ClaimsOverview, modifier: Modifier = Modifier, onTrip: (String) -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "earned") {
            NexusCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text("Earned back in ${LocalDate.now().year}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(TripFormat.price(overview.earnedCents), style = MaterialTheme.typography.displaySmall.tabular(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (overview.paid > 0) {
                    Text("${overview.paid} ${if (overview.paid == 1) "claim" else "claims"} paid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        if (overview.toClaim.isEmpty() && overview.waiting.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.Euro,
                    title = "Nothing to claim",
                    message = "When a trip arrives an hour or more late, it shows up here with what it is worth and the last day to claim.",
                )
            }
        }
        if (overview.toClaim.isNotEmpty()) {
            item(key = "to-claim") { SectionHeader("To claim", supporting = "Soonest deadline first") }
            items(overview.toClaim, key = { "c-${it.tripId}" }) { ClaimRow(it, waiting = false) { onTrip(it.tripId) } }
        }
        if (overview.waiting.isNotEmpty()) {
            item(key = "waiting") { SectionHeader("Waiting for DB") }
            items(overview.waiting, key = { "w-${it.tripId}" }) { ClaimRow(it, waiting = true) { onTrip(it.tripId) } }
        }
    }
}

@Composable
private fun ClaimRow(entry: ClaimEntry, waiting: Boolean, onClick: () -> Unit) {
    val day = entry.plannedDeparture.toInstantOrNull()?.let { TripFormat.day(it) }.orEmpty()
    NexusCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(entry.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (waiting) {
                        "$day · claimed"
                    } else {
                        "$day · +${entry.delayMinutes ?: 0} min · " +
                            CompensationText.deadline(TripFormat.date(LocalDate.parse(entry.deadline)), entry.daysLeft)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!waiting && entry.daysLeft <= 7) attention.content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(entry.amountCents?.let(TripFormat::price) ?: "fare?", style = MaterialTheme.typography.titleMedium.tabular())
        }
    }
}
