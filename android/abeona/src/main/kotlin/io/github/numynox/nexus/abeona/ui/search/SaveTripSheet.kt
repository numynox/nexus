package io.github.numynox.nexus.abeona.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.SaveTripInput
import io.github.numynox.nexus.abeona.domain.AppClock
import io.github.numynox.nexus.abeona.domain.TicketNeeds
import io.github.numynox.nexus.abeona.domain.intake.IntakeDraft
import io.github.numynox.nexus.abeona.domain.intake.MatchStatus
import io.github.numynox.nexus.abeona.domain.intake.ParsedTicket
import io.github.numynox.nexus.abeona.domain.intake.directionLabel
import io.github.numynox.nexus.abeona.domain.intake.matchSummary
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.common.Banner
import io.github.numynox.nexus.abeona.ui.common.ChoicePills
import io.github.numynox.nexus.abeona.ui.common.FieldRow
import io.github.numynox.nexus.abeona.ui.common.PrimaryButton
import io.github.numynox.nexus.abeona.ui.common.Segmented
import io.github.numynox.nexus.abeona.ui.common.StatusTone
import io.github.numynox.nexus.abeona.ui.common.TicketNeedsEditor
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.WellField

/** Cents as the price field shows them: "117.40". */
internal fun euros(cents: Int): String = String.format(java.util.Locale.ROOT, "%d.%02d", cents / 100, cents % 100)

/**
 * What intake read, and how well the timetable knows it: ok when every ride
 * was found, watch (check it) when some are saved as printed.
 */
@Composable
private fun IntakeBanner(draft: IntakeDraft, more: Int) {
    val full = draft.match.status == MatchStatus.Full
    val source = if (draft.parsed is ParsedTicket) "Read from your ticket" else "Read from DB Navigator"
    Banner(
        if (full) StatusTone.Ok else StatusTone.Watch,
        if (full) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.HelpOutline,
        directionLabel(draft.parsed)?.let { "$source · $it" } ?: source,
        text = listOfNotNull(
            matchSummary(draft.match),
            if (more > 0) "The ticket is for both directions: the return comes next, with the same PDF." else null,
            if (draft.ticket != null) "The PDF goes into the trip's ticket wallet." else null,
        ).joinToString(" "),
    )
}

/** "117,40" or "117.40" as cents; null when empty or not a number. */
internal fun centsOf(price: String): Int? = price.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100).toInt() }

/** A Deutschlandticket or a BahnCard 100 is bought without a BahnCard discount. */
internal val FareType.takesDiscounts: Boolean get() = this != FareType.BahnCard100 && this != FareType.Deutschlandticket

/** Fare types whose tickets can carry a train binding at all. */
internal val Bindable = setOf(FareType.Flex, FareType.Saver, FareType.Other)

/** Saving a connection as a trip: what the ticket is, so the app knows what applies. */
@Composable
fun SaveTripSheet(
    journey: Journey,
    onSave: (SaveTripInput) -> Unit,
    onDismiss: () -> Unit,
    /** Set when the journey came from a share text or a ticket: the fields start from what was read. */
    intake: IntakeDraft? = null,
    /** How many trips from the same ticket follow this one (the return of a ticket for both directions). */
    more: Int = 0,
) {
    val prefill = intake?.prefill
    var title by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf(prefill?.fareType ?: FareType.Saver) }
    var binding by remember { mutableStateOf(prefill?.trainBinding ?: (fare == FareType.Saver)) }
    var travelClass by remember { mutableStateOf(prefill?.travelClass ?: 2) }
    var price by remember { mutableStateOf(prefill?.priceCents?.let(::euros).orEmpty()) }
    // From a ticket, what it printed; from a search or a share, one traveller without a BahnCard.
    val needs = remember(intake) {
        if (intake?.parsed is ParsedTicket) TicketNeeds.summary(prefill?.travellers, prefill?.discounts.orEmpty()) else null
    }
    var travellerCount by remember { mutableStateOf(needs?.travellerCount ?: 1) }
    var discounts by remember { mutableStateOf(needs?.bahncardDiscounts.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    val origin = journey.legs.firstOrNull()?.from?.name.orEmpty()
    val destination = journey.legs.lastOrNull()?.to?.name.orEmpty()
    val priceCents = centsOf(price)
    // A journey that has already arrived is saved as a past trip: final, never refreshed.
    val past = remember(journey) { journey.legs.lastOrNull()?.to?.expectedAt?.isBefore(AppClock.now()) == true }
    var delay by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // One header block: the day, the route, then times and facts in the same columns as the results.
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                journey.legs.firstOrNull()?.from?.plannedAt?.let { Text(TripFormat.longDay(it), style = AbeonaType.meta, color = muted) }
                Text("$origin → $destination", style = AbeonaType.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                JourneySummary(journey, Modifier.padding(top = 6.dp))
            }
            intake?.let { IntakeBanner(it, more) }
            if (past) {
                Banner(
                    StatusTone.Quiet,
                    Icons.Rounded.History,
                    "A past trip",
                    text = "It is saved as it happened and not followed live. Say how late you arrived, if you were.",
                )
                FieldRow("Arrived late by (optional)") {
                    WellField(
                        value = delay,
                        onValueChange = { delay = it.filter(Char::isDigit).take(4) },
                        placeholder = "0",
                        description = "Arrived late by, minutes",
                        suffix = "min",
                        keyboard = KeyboardType.Number,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(112.dp),
                    )
                }
            }
            WellField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Title (optional), e.g. $origin → $destination",
                description = "Title",
                modifier = Modifier.fillMaxWidth(),
            )
            TicketFields(
                fare = fare,
                onFare = { option ->
                    fare = option
                    // A saver fare is bound to its train; the others are not, unless said so.
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
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                PrimaryButton(
                    (if (past) "Save past trip" else "Save trip") + if (more > 0) " · then the return" else "",
                    onClick = {
                        saving = true
                        onSave(
                            SaveTripInput(
                                title = title,
                                fareType = fare,
                                trainBinding = binding && fare in Bindable,
                                travelClass = travelClass,
                                priceCents = priceCents,
                                travellerCount = travellerCount,
                                bahncardDiscounts = if (fare.takesDiscounts) discounts else emptyList(),
                                past = past,
                                arrivalDelayMinutes = delay.toIntOrNull()?.coerceIn(0, 1440),
                                source = intake?.source?.wire,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = !saving,
                )
            }
        }
    }
}

/**
 * The ticket's facts, as the save sheet and the trip's "Edit trip" sheet ask
 * them: fare type, train binding, class, travellers and BahnCards, price.
 */
@Composable
internal fun TicketFields(
    fare: FareType,
    onFare: (FareType) -> Unit,
    binding: Boolean,
    onBinding: (Boolean) -> Unit,
    travelClass: Int,
    onTravelClass: (Int) -> Unit,
    travellerCount: Int?,
    onTravellerCount: (Int) -> Unit,
    discounts: List<Int>,
    onDiscounts: (List<Int>) -> Unit,
    price: String,
    onPrice: (String) -> Unit,
) {
    ChoicePills(FareType.entries, fare, onFare, { it.label })
    FieldRow("Train binding", info = if (fare in Bindable) {
        "Only valid on the booked trains. Abeona tells you when a delay of 20 minutes lifts it."
    } else {
        "${fare.label} tickets have no train binding."
    }, toggle = binding && fare in Bindable, onToggle = onBinding, enabled = fare in Bindable) {
        Switch(checked = binding && fare in Bindable, onCheckedChange = null, enabled = fare in Bindable)
    }
    FieldRow("Class") {
        Segmented(listOf(1, 2), travelClass, onTravelClass, { if (it == 1) "1st" else "2nd" }, description = "Class")
    }
    TicketNeedsEditor(
        travellerCount = travellerCount,
        discounts = discounts,
        onTravellerCount = onTravellerCount,
        onDiscounts = onDiscounts,
        showDiscounts = fare.takesDiscounts,
    )
    FieldRow("Price (optional)") {
        WellField(
            value = price,
            onValueChange = { onPrice(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8)) },
            placeholder = "0.00",
            description = "Price in euros",
            suffix = "€",
            keyboard = KeyboardType.Decimal,
            textAlign = TextAlign.End,
            modifier = Modifier.width(132.dp),
        )
    }
}
