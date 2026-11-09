package io.github.numynox.nexus.abeona.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.data.Alternative
import io.github.numynox.nexus.abeona.data.Validity
import io.github.numynox.nexus.abeona.domain.AlternativesReason
import io.github.numynox.nexus.abeona.domain.arrivalDelay
import io.github.numynox.nexus.abeona.domain.changes
import io.github.numynox.nexus.abeona.ui.attention
import io.github.numynox.nexus.abeona.ui.calm
import io.github.numynox.nexus.abeona.ui.common.DelayBadge
import io.github.numynox.nexus.abeona.ui.common.LegChips
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Duration

/** The sheet's headline: why it is worth a look, or just what it is. */
internal fun alternativesTitle(reason: AlternativesReason?): String = when (reason) {
    AlternativesReason.Missed -> "Looks like you missed a connection"
    AlternativesReason.Cancelled -> "A train on your trip is cancelled"
    AlternativesReason.Impossible -> "A change on your trip is impossible"
    AlternativesReason.AtRisk -> "A change on your trip is at risk"
    null -> "Alternatives"
}

/**
 * Connections from where the traveller is to the destination (glossary:
 * Alternatives), in the Edge Function's order — arrival, then changes — each
 * marked by whether the ticket covers it, with the rule's "check the terms".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlternativesSheet(
    state: AlternativesState?,
    reason: AlternativesReason?,
    destination: String,
    onSearchAgain: () -> Unit,
    onDismiss: () -> Unit,
    /** Follow this connection from now on; null where nothing can be taken (the screenshot of the list alone). */
    onTake: ((Alternative) -> Unit)? = null,
) {
    // A connection the ticket may not cover waits here for the traveller's yes.
    var confirming by remember { mutableStateOf<Alternative?>(null) }
    confirming?.let { alternative ->
        TakeAnywayDialog(alternative, onConfirm = { confirming = null; onTake?.invoke(alternative) }, onDismiss = { confirming = null })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(alternativesTitle(reason), style = MaterialTheme.typography.titleLarge)
                    state?.let {
                        Text(
                            "From ${it.origin.name}, ${TripFormat.time(it.origin.time)}, to $destination",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state?.result?.allowance?.takeIf { it.bindingLifted }?.let { BindingLiftedLine(it.bindingLiftedReason) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state?.loading == true) LinearProgressIndicator(Modifier.weight(1f).padding(end = 12.dp)) else Row(Modifier.weight(1f)) {}
                        TextButton(onClick = onSearchAgain, enabled = state?.loading != true) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Search again", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            state?.error?.let { message ->
                item(key = "error") {
                    Text("Could not search: $message", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val list = state?.result?.alternatives
            if (list != null && list.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No connections found from here. DB Navigator may know more.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(list.orEmpty(), key = { i, _ -> "alt-$i" }) { _, alternative ->
                AlternativeRow(
                    alternative,
                    onTake = onTake?.let { take -> { if (alternative.validity == Validity.Valid) take(alternative) else confirming = alternative } },
                )
            }
            state?.result?.let { result ->
                item(key = "hint") {
                    Text(
                        result.allowance?.hint ?: "Check the terms of your ticket before you board another train.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Why any train on the route counts now, from the database's reason. */
@Composable
private fun BindingLiftedLine(reason: String?) {
    Surface(color = calm.container, contentColor = calm.content, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            "Train binding lifted: " + when (reason) {
                "impossible_connection" -> "a change cannot be made, so other trains on your route count."
                "missed_connection" -> "you missed a short change, so other trains on your route count."
                "cancelled" -> "a train is cancelled, so other trains on your route count."
                "delay" -> "20 minutes late, so other trains on your route count."
                else -> "other trains on your route count."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Taking a connection the ticket does not (or may not) cover: what the
 * ticket says, and that the traveller takes it knowing that.
 */
@Composable
internal fun TakeAnywayDialog(alternative: Alternative, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val invalid = alternative.validity == Validity.Invalid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (invalid) "Your ticket does not cover this" else "Your ticket may not cover this") },
        text = {
            Text(
                listOf(
                    alternative.validityNote.ifBlank { "Abeona cannot tell whether your ticket covers this connection." },
                    "You may need to buy a ticket for it. Abeona follows this connection from now on.",
                ).joinToString("\n\n"),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("I understand, take it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AlternativeRow(alternative: Alternative, onTake: (() -> Unit)? = null) {
    val legs = alternative.legs
    val first = legs.firstOrNull { !it.isWalk } ?: legs.firstOrNull() ?: return
    val last = legs.lastOrNull { !it.isWalk } ?: legs.last()
    val minutes = run {
        val a = legs.first().from.plannedAt
        val b = legs.last().to.plannedAt
        if (a != null && b != null) Duration.between(a, b).toMinutes() else null
    }
    val n = changes(legs)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (alternative.validity == Validity.Invalid) 0.62f else 1f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${TripFormat.time(first.from.expectedAt)} – ${TripFormat.time(last.to.expectedAt)}",
                    style = MaterialTheme.typography.titleLarge.tabular(),
                )
                DelayBadge(arrivalDelay(last))
                Text(
                    listOfNotNull(minutes?.let { TripFormat.duration(it) }, if (n == 0) "direct" else if (n == 1) "1 change" else "$n changes").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LegChips(legs)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValidityChip(alternative.validity)
                Text(
                    "from ${first.from.name}" + (first.from.shownPlatform?.let { ", Pl. $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (alternative.validity != Validity.Valid && alternative.validityNote.isNotBlank()) {
                Text(alternative.validityNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onTake != null) {
                if (alternative.validity == Validity.Valid) {
                    FilledTonalButton(onClick = onTake) { Text("Take this connection") }
                } else {
                    OutlinedButton(onClick = onTake) { Text("Take it anyway…") }
                }
            }
        }
    }
}

@Composable
private fun ValidityChip(validity: Validity) {
    val (container, content) = when (validity) {
        Validity.Valid -> calm.container to calm.content
        Validity.Check -> attention.container.copy(alpha = 0.45f) to attention.content
        Validity.Invalid -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (validity) {
        Validity.Valid -> Icons.Rounded.CheckCircle
        Validity.Check -> Icons.AutoMirrored.Rounded.HelpOutline
        Validity.Invalid -> Icons.Rounded.RemoveCircleOutline
    }
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(validity.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}
