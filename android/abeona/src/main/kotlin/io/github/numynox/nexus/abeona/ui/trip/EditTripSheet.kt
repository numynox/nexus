package io.github.numynox.nexus.abeona.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.SeatEdit
import io.github.numynox.nexus.abeona.ui.common.WellField
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.common.PrimaryButton
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.data.TripEdit
import io.github.numynox.nexus.abeona.ui.search.Bindable
import io.github.numynox.nexus.abeona.ui.search.TicketFields
import io.github.numynox.nexus.abeona.ui.search.centsOf
import io.github.numynox.nexus.abeona.ui.search.euros
import io.github.numynox.nexus.abeona.ui.search.takesDiscounts

/**
 * "Edit trip" from the trip's ⋮ menu: the title and what the save sheet asked
 * about the ticket — fare type, train binding, class, travellers and
 * BahnCards, price — saved in one update; then each ride's coach and seat,
 * written to that ride.
 */
@Composable
fun EditTripSheet(trip: Trip, onSave: (TripEdit) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(trip.title.orEmpty()) }
    var fare by remember { mutableStateOf(trip.fareType) }
    var binding by remember { mutableStateOf(trip.trainBinding) }
    var travelClass by remember { mutableStateOf(trip.travelClass) }
    var price by remember { mutableStateOf(trip.priceCents?.let(::euros).orEmpty()) }
    var travellerCount by remember { mutableStateOf(trip.travellerCount) }
    var discounts by remember { mutableStateOf(trip.bahncardDiscounts.sorted()) }
    // Per ride (not the walks): its coach and seats as typed, in the trip's own legs.
    val rides = remember(trip) { trip.legs.indices.filter { !trip.legs[it].isWalk } }
    val coaches = remember(trip) { mutableStateListOf(*rides.map { trip.legs[it].coach.orEmpty() }.toTypedArray()) }
    val seats = remember(trip) { mutableStateListOf(*rides.map { trip.legs[it].seats.orEmpty() }.toTypedArray()) }
    var saving by remember { mutableStateOf(false) }
    // A price that is not a number is not saved as none by accident.
    val priceValid = price.isBlank() || centsOf(price) != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit trip", style = AbeonaType.headline)
            // The same well field as the save sheet, above the same ticket fields.
            io.github.numynox.nexus.abeona.ui.common.WellField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Title (optional), e.g. ${trip.originName} → ${trip.destinationName}",
                description = "Title",
                modifier = Modifier.fillMaxWidth(),
            )
            TicketFields(
                fare = fare,
                onFare = { option ->
                    fare = option
                    // As in the save sheet: a saver fare is bound to its train, the others are not unless said so.
                    binding = option == FareType.Saver
                },
                binding = binding,
                onBinding = { binding = it },
                travelClass = travelClass,
                onTravelClass = { travelClass = it },
                travellerCount = travellerCount,
                onTravellerCount = { travellerCount = it },
                discounts = discounts,
                onDiscounts = { discounts = it },
                price = price,
                onPrice = { price = it },
            )
            rides.forEachIndexed { n, idx ->
                val leg = trip.legs[idx]
                // "ICE 25 · Frankfurt(Main)Hbf → Würzburg Hbf", then the two small fields.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.EventSeat, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                        Text(
                            "${leg.label} · ${leg.from.name} → ${leg.to.name}",
                            style = AbeonaType.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Labelled above as well: a filled field no longer shows its placeholder.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Coach", style = AbeonaType.meta, color = muted)
                            WellField(
                                value = coaches[n],
                                onValueChange = { coaches[n] = it.take(8) },
                                placeholder = "Coach",
                                description = "Coach on the ${leg.label}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Seat", style = AbeonaType.meta, color = muted)
                            WellField(
                                value = seats[n],
                                onValueChange = { seats[n] = it.take(40) },
                                placeholder = "Seat",
                                description = "Seat on the ${leg.label}",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                PrimaryButton(
                    "Save",
                    onClick = {
                        saving = true
                        onSave(
                            TripEdit(
                                title = title.trim().ifEmpty { null },
                                fareType = fare,
                                trainBinding = binding && fare in Bindable,
                                travelClass = travelClass,
                                priceCents = if (price.isBlank()) null else centsOf(price),
                                travellerCount = travellerCount,
                                bahncardDiscounts = if (fare.takesDiscounts) discounts else emptyList(),
                                // Only the rides whose coach or seat changed: trimmed, empty is none.
                                seats = rides.mapIndexedNotNull { n, idx ->
                                    val leg = trip.legs[idx]
                                    val coach = coaches[n].trim().ifEmpty { null }
                                    val seat = seats[n].trim().ifEmpty { null }
                                    if (coach == leg.coach && seat == leg.seats) null else SeatEdit(idx, coach, seat)
                                },
                            ),
                        )
                    },
                    enabled = !saving && priceValid,
                )
            }
        }
    }
}
