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
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.settings.SettingSwitch

/** Fare types whose tickets can carry a train binding at all. */
private val Bindable = setOf(FareType.Flex, FareType.Saver, FareType.Other)

/** Saving a connection as a trip: what the ticket is, so the app knows what applies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveTripSheet(
    journey: Journey,
    onSave: (title: String, fare: FareType, binding: Boolean, travelClass: Int, priceCents: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf(FareType.Saver) }
    var binding by remember { mutableStateOf(true) }
    var travelClass by remember { mutableStateOf(2) }
    var price by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val origin = journey.legs.firstOrNull()?.from?.name.orEmpty()
    val destination = journey.legs.lastOrNull()?.to?.name.orEmpty()
    val priceCents = price.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100).toInt() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Save trip", style = MaterialTheme.typography.headlineSmall)
            JourneyCard(journey, onClick = null)
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
            OutlinedTextField(
                value = price,
                onValueChange = { price = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8) },
                label = { Text("Price (optional)") },
                suffix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        saving = true
                        onSave(title, fare, binding && fare in Bindable, travelClass, priceCents)
                    },
                    enabled = !saving,
                ) { Text("Save trip") }
            }
        }
    }
}
