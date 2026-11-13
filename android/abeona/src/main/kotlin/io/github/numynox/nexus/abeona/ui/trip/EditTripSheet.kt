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
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.FareType
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
 * BahnCards, price — saved in one update.
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
    var saving by remember { mutableStateOf(false) }
    // A price that is not a number is not saved as none by accident.
    val priceValid = price.isBlank() || centsOf(price) != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit trip", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                placeholder = { Text("${trip.originName} → ${trip.destinationName}") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
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
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
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
                            ),
                        )
                    },
                    enabled = !saving && priceValid,
                ) { Text("Save") }
            }
        }
    }
}
