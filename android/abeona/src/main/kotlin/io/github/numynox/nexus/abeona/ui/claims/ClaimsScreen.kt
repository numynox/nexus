package io.github.numynox.nexus.abeona.ui.claims

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.hairline
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.watch
import io.github.numynox.nexus.abeona.ui.common.Banner
import io.github.numynox.nexus.abeona.ui.common.Fact
import io.github.numynox.nexus.abeona.ui.common.SectionHeader
import io.github.numynox.nexus.abeona.ui.common.StatusChip
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.SurfaceCard
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.loadable
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "earned") {
            // What came back this year: the screen's one figure.
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(TripFormat.price(overview.earnedCents), style = AbeonaType.headline)
                Text(
                    "Earned back in ${LocalDate.now().year}" + if (overview.paid > 0) " · ${overview.paid} ${if (overview.paid == 1) "claim" else "claims"} paid" else "",
                    style = AbeonaType.meta,
                    color = muted,
                )
            }
        }
        if (overview.toClaim.isEmpty() && overview.waiting.isEmpty()) {
            item(key = "empty") {
                Banner(
                    StatusTone.Quiet,
                    Icons.Rounded.Euro,
                    "Nothing to claim",
                    text = "When a trip arrives an hour or more late, it shows up here with what it is worth and the last day to claim.",
                )
            }
        }
        if (overview.toClaim.isNotEmpty()) {
            // Soonest deadline first, as the database sorts them.
            item(key = "to-claim") { SectionHeader("To claim") }
            item(key = "to-claim-rows") { ClaimRows(overview.toClaim, waiting = false, onTrip) }
        }
        if (overview.waiting.isNotEmpty()) {
            item(key = "waiting") { SectionHeader("Waiting for DB") }
            item(key = "waiting-rows") { ClaimRows(overview.waiting, waiting = true, onTrip) }
        }
    }
}

@Composable
private fun ClaimRows(entries: List<ClaimEntry>, waiting: Boolean, onTrip: (String) -> Unit) {
    SurfaceCard(padding = PaddingValues(vertical = 4.dp)) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = hairline, modifier = Modifier.padding(horizontal = 16.dp))
            ClaimRow(entry, waiting) { onTrip(entry.tripId) }
        }
    }
}

/**
 * One claim: the trip and its amount on the first line; the day, the delay
 * and the last day to claim under it, with the claim's state as a chip.
 */
@Composable
private fun ClaimRow(entry: ClaimEntry, waiting: Boolean, onClick: () -> Unit) {
    val day = entry.plannedDeparture.toInstantOrNull()?.let { TripFormat.day(it) }.orEmpty()
    val deadline = TripFormat.date(LocalDate.parse(entry.deadline))
    val soon = !waiting && entry.daysLeft <= 7
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.displayTitle, style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(entry.amountCents?.let(TripFormat::price) ?: "fare?", style = AbeonaType.title)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(day, style = AbeonaType.meta, color = muted, maxLines = 1)
            entry.delayMinutes?.let { Text("+$it", style = AbeonaType.meta.copy(fontWeight = FontWeight.Bold), color = watch.soft.content) }
            if (!waiting) {
                Fact(
                    Icons.Rounded.Event,
                    "Claim by",
                    deadline,
                    color = if (soon) watch.soft.content else muted,
                    modifier = Modifier.semantics { contentDescription = CompensationText.deadline(deadline, entry.daysLeft) },
                )
            }
            Spacer(Modifier.weight(1f))
            when {
                waiting -> StatusChip(StatusTone.Watch, Icons.Rounded.HourglassEmpty, label = "Claimed", description = "Claimed, waiting for DB")
                soon -> StatusChip(StatusTone.Watch, Icons.Rounded.Event, label = "${entry.daysLeft} d", description = CompensationText.deadline(deadline, entry.daysLeft))
                else -> StatusChip(StatusTone.Quiet, null, label = "Not claimed")
            }
        }
    }
}
