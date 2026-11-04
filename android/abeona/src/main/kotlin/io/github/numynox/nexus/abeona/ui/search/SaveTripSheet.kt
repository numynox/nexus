package io.github.numynox.nexus.abeona.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.SaveTripInput
import io.github.numynox.nexus.abeona.data.Traveller
import io.github.numynox.nexus.abeona.domain.intake.IntakeDraft
import io.github.numynox.nexus.abeona.domain.intake.ParsedTicket
import io.github.numynox.nexus.abeona.domain.intake.MatchStatus
import io.github.numynox.nexus.abeona.domain.intake.PriceScope
import io.github.numynox.nexus.abeona.domain.intake.halfOf
import io.github.numynox.nexus.abeona.domain.intake.matchSummary
import androidx.compose.material3.Surface
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import java.time.Instant
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.settings.SettingSwitch

/** Cents as the price field shows them: "117.40". */
private fun euros(cents: Int): String = String.format(java.util.Locale.ROOT, "%d.%02d", cents / 100, cents % 100)

/** What intake read, and how well the timetable knows it. */
@Composable
private fun IntakeBanner(draft: IntakeDraft) {
    val full = draft.match.status == MatchStatus.Full
    Surface(
        color = if (full) MaterialTheme.colorScheme.secondaryContainer else AbeonaColors.Amber.copy(alpha = 0.22f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (draft.parsed is ParsedTicket) "Read from your ticket" else "Read from DB Navigator", style = MaterialTheme.typography.titleSmall)
            Text(matchSummary(draft.match), style = MaterialTheme.typography.bodyMedium)
            if (draft.ticket != null) {
                Text("The PDF goes into the trip's ticket wallet.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Fare types whose tickets can carry a train binding at all. */
private val Bindable = setOf(FareType.Flex, FareType.Saver, FareType.Other)

/** Saving a connection as a trip: what the ticket is, so the app knows what applies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveTripSheet(
    journey: Journey,
    /** The household's travellers; null until loaded (or when they cannot be), and the database then picks the saver. */
    travellers: List<Traveller>?,
    /** The signed-in user, whose own traveller is on the trip by default. */
    me: String?,
    onSave: (SaveTripInput) -> Unit,
    onDismiss: () -> Unit,
    /** Set when the journey came from a share text or a ticket: the fields start from what was read. */
    intake: IntakeDraft? = null,
) {
    val prefill = intake?.prefill
    var title by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf(prefill?.fareType ?: FareType.Saver) }
    var binding by remember { mutableStateOf(prefill?.trainBinding ?: (fare == FareType.Saver)) }
    var travelClass by remember { mutableStateOf(prefill?.travelClass ?: 2) }
    var price by remember { mutableStateOf(prefill?.priceCents?.let(::euros).orEmpty()) }
    var discount by remember { mutableStateOf(prefill?.bahncardDiscount) }
    // A ticket that prints a return booking's total: the user says what this trip cost. Never halved silently.
    val wholeBooking = prefill?.priceCents?.takeIf { prefill.priceScope == PriceScope.Booking }
    var priceConfirmed by remember { mutableStateOf(wholeBooking == null) }
    var chosen by remember(travellers) { mutableStateOf(travellers?.filter { it.userId == me }?.map { it.id }?.toSet()) }
    var saving by remember { mutableStateOf(false) }
    val origin = journey.legs.firstOrNull()?.from?.name.orEmpty()
    val destination = journey.legs.lastOrNull()?.to?.name.orEmpty()
    val priceCents = price.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100).toInt() }
    // A journey that has already arrived is saved as a past trip: final, never refreshed.
    val past = remember(journey) { journey.legs.lastOrNull()?.to?.expectedAt?.isBefore(Instant.now()) == true }
    var delay by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (past) "Save past trip" else "Save trip", style = MaterialTheme.typography.headlineSmall)
            JourneyCard(journey, onClick = null)
            intake?.let { IntakeBanner(it) }
            if (past) {
                Surface(color = AbeonaColors.Amber.copy(alpha = 0.22f), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A past trip", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "It is saved as it happened and not followed live. Say how late you arrived, if you were.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = delay,
                            onValueChange = { delay = it.filter(Char::isDigit).take(4) },
                            label = { Text("Arrived late by (optional)") },
                            suffix = { Text("min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                placeholder = { Text("$origin → $destination") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Ticket", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FareType.entries.forEach { option ->
                    FilterChip(
                        selected = option == fare,
                        onClick = {
                            fare = option
                            // A saver fare is bound to its train; the others are not, unless said so.
                            binding = option == FareType.Saver
                        },
                        label = { Text(option.label) },
                    )
                }
            }
            SettingSwitch(
                title = "Train binding",
                description = if (fare in Bindable) {
                    "Only valid on the booked trains. Abeona tells you when a delay of 20 minutes lifts it."
                } else {
                    "${fare.label} tickets have no train binding."
                },
                checked = binding && fare in Bindable,
                onCheckedChange = { binding = it },
                enabled = fare in Bindable,
            )
            ConnectedChoiceRow(
                options = listOf(2, 1),
                selected = travelClass,
                onSelect = { travelClass = it },
                label = { if (it == 1) "1st class" else "2nd class" },
                modifier = Modifier.fillMaxWidth(),
            )
            if (fare != FareType.BahnCard100 && fare != FareType.Deutschlandticket) {
                Text("BahnCard discount", style = MaterialTheme.typography.labelLarge)
                ConnectedChoiceRow(
                    options = listOf(null, 25, 50),
                    selected = discount,
                    onSelect = { discount = it },
                    label = { it?.let { kind -> "BC $kind" } ?: "None" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (travellers != null && travellers.size > 1) {
                Text(
                    listOfNotNull(
                        "Who travels",
                        prefill?.travellers?.let { n -> "the ticket is for ${if (n == 1) "1 person" else "$n people"}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    travellers.forEach { traveller ->
                        val on = chosen?.contains(traveller.id) == true
                        FilterChip(
                            selected = on,
                            onClick = { chosen = (chosen ?: emptySet()).let { if (on) it - traveller.id else it + traveller.id } },
                            label = { Text(traveller.name) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = price,
                onValueChange = {
                    price = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8)
                    priceConfirmed = true
                },
                label = { Text("Price (optional)") },
                suffix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            wholeBooking?.let { whole ->
                Surface(
                    color = if (priceConfirmed) MaterialTheme.colorScheme.surfaceContainerHigh else AbeonaColors.Amber.copy(alpha = 0.22f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("€${euros(whole)} is likely the whole booking", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "This ticket is one direction of a return booking, and DB prints the booking's total. What did this trip cost? Compensation is worked out from it.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = priceConfirmed && price == euros(halfOf(whole)),
                                onClick = { price = euros(halfOf(whole)); priceConfirmed = true },
                                label = { Text("Half · €${euros(halfOf(whole))}") },
                            )
                            FilterChip(
                                selected = priceConfirmed && price == euros(whole),
                                onClick = { price = euros(whole); priceConfirmed = true },
                                label = { Text("All of it · €${euros(whole)}") },
                            )
                        }
                        Text("Or type the price above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        saving = true
                        onSave(
                            SaveTripInput(
                                title = title,
                                fareType = fare,
                                trainBinding = binding && fare in Bindable,
                                travelClass = travelClass,
                                priceCents = priceCents,
                                bahncardDiscount = discount.takeIf { fare != FareType.BahnCard100 && fare != FareType.Deutschlandticket },
                                travellerIds = chosen?.toList(),
                                past = past,
                                arrivalDelayMinutes = delay.toIntOrNull()?.coerceIn(0, 1440),
                                source = intake?.source?.wire,
                            ),
                        )
                    },
                    enabled = !saving && priceConfirmed,
                ) { Text(if (past) "Save past trip" else "Save trip") }
            }
        }
    }
}
