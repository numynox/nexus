package io.github.numynox.nexus.abeona.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.core.ui.theme.tabular

/** The most travellers the editor counts to; the database allows more, a ticket rarely has them. */
private const val MAX_TRAVELLERS = 9

/** The most BahnCards the editor adds. */
private const val MAX_CARDS = 9

/** "BC25 · BC50", the short form of a ticket's BahnCards. */
/** "BC25 · BC50"; the same card twice once, counted: "2× BC25". The web's bahnCardsShort. */
internal fun bahnCardsShort(discounts: List<Int>): String =
    discounts.sorted().groupBy { it }.entries.joinToString(" · ") { (d, all) -> if (all.size > 1) "${all.size}× BC$d" else "BC$d" }

/**
 * What a screen reader says for a ticket's needs: "2 travellers, BahnCard 25
 * and BahnCard 50". Null when there is nothing to say.
 */
internal fun ticketNeedsDescription(travellerCount: Int?, discounts: List<Int>): String? {
    val people = travellerCount?.let { if (it == 1) "1 traveller" else "$it travellers" }
    val names = discounts.sorted().map { "BahnCard $it" }
    val cards = when (names.size) {
        0 -> null
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
    return listOfNotNull(people, cards).joinToString(", ").ifEmpty { null }
}

/**
 * How many travellers the ticket is for and which BahnCards it was booked
 * with, small and with icons: a person and "2", a card and "BC25 · BC50".
 * Nothing when the count is unknown and there are no cards.
 */
@Composable
fun TicketNeedsSummary(
    travellerCount: Int?,
    discounts: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val description = ticketNeedsDescription(travellerCount, discounts) ?: return
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (travellerCount != null) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text("$travellerCount", style = MaterialTheme.typography.labelMedium.tabular(), color = color)
        }
        if (discounts.isNotEmpty()) {
            if (travellerCount != null) Text("·", style = MaterialTheme.typography.labelMedium, color = color)
            Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(bahnCardsShort(discounts), style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

/**
 * What a ticket needs, editable: how many travellers (1–9) and the BahnCards
 * it was booked with, one chip per card. Used by the save sheet and the trip's
 * "Edit trip" sheet; the web's editor is the same. Only 25 and 50 can be
 * added; a 100 that came from a ticket stays until removed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketNeedsEditor(
    /** Null when unknown: shown as "?" until a count is chosen. */
    travellerCount: Int?,
    discounts: List<Int>,
    onTravellerCount: (Int) -> Unit,
    onDiscounts: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    /** False for a fare without discounts (Deutschlandticket, BahnCard 100). */
    showDiscounts: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Travellers", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val count = travellerCount
            FilledTonalIconButton(
                onClick = { if (count != null) onTravellerCount((count - 1).coerceAtLeast(1)) },
                enabled = count != null && count > 1,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "One traveller less")
            }
            Text(
                count?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium.tabular(),
                modifier = Modifier.semantics {
                    contentDescription = count?.let { if (it == 1) "1 traveller" else "$it travellers" } ?: "Travellers unknown"
                },
            )
            FilledTonalIconButton(
                onClick = { onTravellerCount(((count ?: 0) + 1).coerceIn(1, MAX_TRAVELLERS)) },
                enabled = count == null || count < MAX_TRAVELLERS,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "One traveller more")
            }
        }
        if (showDiscounts) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (discounts.isEmpty()) "No BahnCard" else "BahnCards",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val sorted = discounts.sorted()
                sorted.forEachIndexed { index, discount ->
                    InputChip(
                        selected = false,
                        onClick = { onDiscounts(sorted.filterIndexed { i, _ -> i != index }) },
                        label = { Text("BC$discount") },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove BahnCard $discount", modifier = Modifier.size(18.dp))
                        },
                    )
                }
                if (sorted.size < MAX_CARDS) {
                    listOf(25, 50).forEach { discount ->
                        AssistChip(
                            onClick = { onDiscounts((sorted + discount).sorted()) },
                            label = { Text("BC$discount") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Add, contentDescription = "Add BahnCard $discount", modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }
        }
    }
}
