package io.github.numynox.nexus.abeona.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.Traveller
import io.github.numynox.nexus.abeona.data.TripTraveller
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow

/** The discounts a fare can be bought with; a BahnCard 100 is a fare type of its own. */
internal val BahnCardDiscounts: List<Int?> = listOf(null, 25, 50)

/**
 * Who travels, and the BahnCard discount of each one's share: one ticket can
 * carry a BahnCard 25, a BahnCard 50 and someone without. Used by the save
 * sheet and the trip page; the web's TravellerPicker.svelte is the same.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TravellerPicker(
    travellers: List<Traveller>,
    selected: List<TripTraveller>,
    /** False for a fare without discounts (Deutschlandticket, BahnCard 100). */
    showDiscount: Boolean,
    onToggle: (Traveller) -> Unit,
    onDiscount: (travellerId: String, discount: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byId = selected.associateBy { it.travellerId }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (travellers.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                travellers.forEach { traveller ->
                    val on = traveller.id in byId
                    FilterChip(
                        selected = on,
                        onClick = { onToggle(traveller) },
                        label = { Text(traveller.name) },
                        leadingIcon = if (on) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (showDiscount) {
            travellers.filter { it.id in byId }.forEach { traveller ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        traveller.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(72.dp),
                    )
                    ConnectedChoiceRow(
                        options = BahnCardDiscounts,
                        selected = byId[traveller.id]?.bahncardDiscount,
                        onSelect = { onDiscount(traveller.id, it) },
                        label = { it?.let { kind -> "BC$kind" } ?: "None" },
                        modifier = Modifier.weight(1f),
                        showCheck = false,
                    )
                }
            }
        }
    }
}
