package io.github.numynox.nexus.abeona.ui.trip

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.domain.CompensationText
import io.github.numynox.nexus.abeona.domain.manualReplacement
import io.github.numynox.nexus.abeona.ui.attention
import io.github.numynox.nexus.abeona.ui.calm
import io.github.numynox.nexus.abeona.ui.common.LegChips
import io.github.numynox.nexus.abeona.ui.common.LineChip
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * What the trip's delay is worth (abeona_compensation), the German text for
 * DB's form, and the household's claim. The same card as the web's
 * CompensationCard.svelte; reasons and hints come from SQL, as they are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompensationCard(
    trip: Trip,
    state: AfterRide,
    onStatus: (String) -> Unit,
    onPrice: (Int) -> Unit,
    onForceMajeure: (Boolean) -> Unit,
) {
    val c = state.compensation
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val amount = c.amountCents?.let(TripFormat::price)
    val colors = when (c.verdict) {
        "eligible", "unknown_price" -> calm
        "unknown_arrival" -> attention
        else -> null
    }
    NexusCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Euro, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Compensation", style = MaterialTheme.typography.titleMedium)
        }
        Surface(
            color = colors?.container ?: MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = colors?.content ?: MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(CompensationText.headline(c.verdict, amount), style = MaterialTheme.typography.titleLarge)
                c.delayMinutes?.let { delay ->
                    Text(
                        listOfNotNull(
                            if (delay == 0) "On time" else "$delay min late",
                            c.actualArrival?.let { "arrived ${TripFormat.time(java.time.Instant.parse(it))}" },
                            CompensationText.source(c.delaySource).ifEmpty { null },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        c.reasons.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
        c.hints.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = attention.content, modifier = Modifier.padding(top = 6.dp)) }

        if (c.verdict == "unknown_price") {
            var price by rememberSaveable { mutableStateOf("") }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("What the ticket cost (€)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { price.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { onPrice(Math.round(it * 100).toInt()) } },
                    enabled = !state.busy,
                ) { Text("Save") }
            }
        }

        if (c.verdict != "unknown_arrival" && (trip.forceMajeure || (c.delayMinutes ?: 0) >= (c.thresholdMinutes ?: 60))) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clickable(enabled = !state.busy) { onForceMajeure(!trip.forceMajeure) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = trip.forceMajeure, onCheckedChange = { onForceMajeure(it) }, enabled = !state.busy)
                Text("Caused by a storm, flooding or a person on the track", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (CompensationText.claimable(c.verdict) && c.summaryDe != null) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(c.deadline)).toInt()
            Text(
                CompensationText.deadline(TripFormat.date(LocalDate.parse(c.deadline)), days),
                style = MaterialTheme.typography.titleSmall,
                color = if (c.deadlinePassed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(c.summaryDe))
                    copied = true
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (copied) "Copied" else "Copy for the form", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.claimUrl))) }
                }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("DB form", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if ((CompensationText.claimable(c.verdict) && c.summaryDe != null) || state.claim != null) {
            Text("Your claim", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompensationText.STATUSES.forEach { status ->
                    FilterChip(
                        selected = (state.claim?.status ?: "open") == status,
                        onClick = { if (!state.busy) onStatus(status) },
                        label = { Text(if (status == "open") "Open" else CompensationText.status(status)) },
                    )
                }
            }
            val claim = state.claim
            when {
                claim?.status == "paid" && claim.paidAt != null ->
                    "${claim.amountCents?.let(TripFormat::price) ?: ""} paid on ${TripFormat.date(LocalDate.parse(claim.paidAt.take(10)))}"
                claim?.status == "claimed" && claim.claimedAt != null ->
                    "Claimed on ${TripFormat.date(LocalDate.parse(claim.claimedAt.take(10)))}"
                else -> null
            }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
        }

        Text(
            "A summary of DB's passenger rights, not legal advice. Check fahrgastrechte.info or bahn.de before you claim.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** What happened: the missed ride and the trains taken instead, or the way to record them. */
@Composable
internal fun WhatHappenedCard(trip: Trip, busy: Boolean, onRecord: () -> Unit, onClear: () -> Unit) {
    val missed = trip.missedLegIdx?.let { trip.legs.getOrNull(it) }
    val replacement = trip.replacementLegs.orEmpty().filter { !it.isWalk }
    NexusCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("What happened", style = MaterialTheme.typography.titleMedium)
        }
        if (missed == null) {
            Text(
                "Missed a change, or a train was cancelled? Record the trains you took instead: the delay and the compensation follow your real arrival.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(onClick = onRecord, modifier = Modifier.padding(top = 10.dp)) { Text("Record a missed connection") }
        } else {
            Text("Missed or cancelled: ${missed.label} from ${missed.from.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            if (replacement.isEmpty()) {
                Text("Add the train you took instead, so the delay can be worked out.", style = MaterialTheme.typography.bodySmall, color = attention.content, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text("Taken instead", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                replacement.forEach { ride ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        LineChip(ride)
                        Text("${ride.from.name} → ${ride.to.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("arr. ${TripFormat.time(ride.to.expectedAt)}", style = MaterialTheme.typography.bodyMedium.tabular())
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRecord, enabled = !busy) { Text("Change") }
                TextButton(onClick = onClear, enabled = !busy) { Text("Clear") }
            }
        }
    }
}

/**
 * Records a missed connection: which ride, then a connection from its stop
 * (searched like the alternatives, at the ride's planned time) or one entered
 * by hand as a train and an arrival time.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecordDisruptionSheet(
    trip: Trip,
    search: ReplacementSearch?,
    onSearch: (Int) -> Unit,
    onSave: (Int, List<Leg>) -> Unit,
    onDismiss: () -> Unit,
) {
    val rides = trip.legs.withIndex().filter { !it.value.isWalk }
    val last = rides.lastOrNull()?.value ?: return
    var missedIdx by rememberSaveable { mutableStateOf(trip.missedLegIdx ?: rides.getOrNull(1)?.index ?: rides.first().index) }
    var line by rememberSaveable { mutableStateOf("") }
    val zone = ZoneId.systemDefault()
    var arrived by rememberSaveable { mutableStateOf(TripFormat.time(last.to.expectedAt)) }
    var timeError by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item(key = "head") { Text("Record a missed connection", style = MaterialTheme.typography.titleLarge) }
            item(key = "which") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Which train did you miss?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rides.forEach { (idx, leg) ->
                            FilterChip(selected = missedIdx == idx, onClick = { missedIdx = idx }, label = { Text("${leg.label} · ${TripFormat.time(leg.from.plannedAt)}") })
                        }
                    }
                }
            }
            item(key = "instead") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What did you take instead?", style = MaterialTheme.typography.labelLarge)
                    Button(onClick = { onSearch(missedIdx) }, enabled = search?.loading != true) { Text("Find the connections from there") }
                    if (search?.loading == true && search.missedIdx == missedIdx) LinearProgressIndicator(Modifier.fillMaxWidth())
                    search?.error?.let { Text("Could not search: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (search?.options?.isEmpty() == true) Text("No connections found. Enter your train below.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (search != null && search.missedIdx == missedIdx) {
                itemsIndexed(search.options.orEmpty(), key = { i, _ -> "option-$i" }) { _, journey -> ReplacementRow(journey) { onSave(missedIdx, journey.legs) } }
            }
            item(key = "manual") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Or by hand", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(line, { line = it }, label = { Text("Train") }, placeholder = { Text("IC 2063") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            arrived,
                            { arrived = it; timeError = false },
                            label = { Text("Arrived") },
                            placeholder = { Text("13:05") },
                            isError = timeError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.7f),
                        )
                    }
                    Button(onClick = {
                        val day = (last.to.plannedAt ?: java.time.Instant.now()).atZone(zone).toLocalDate()
                        val time = try { LocalTime.parse(arrived.trim().padStart(5, '0')) } catch (_: DateTimeParseException) { null }
                        if (time == null) {
                            timeError = true
                        } else {
                            val at = day.atTime(time).atZone(zone).toInstant().toString()
                            onSave(missedIdx, listOf(manualReplacement(trip.legs[missedIdx], last, line, at)))
                        }
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun ReplacementRow(journey: Journey, onPick: () -> Unit) {
    val first = journey.legs.firstOrNull { !it.isWalk } ?: return
    val last = journey.legs.lastOrNull { !it.isWalk } ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${TripFormat.time(first.from.plannedAt)} – ${TripFormat.time(last.to.expectedAt)}", style = MaterialTheme.typography.titleMedium.tabular())
            LegChips(journey.legs)
            Text(
                journey.legs.filter { !it.isWalk }.joinToString(" › ") { it.label } + " · from ${first.from.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
