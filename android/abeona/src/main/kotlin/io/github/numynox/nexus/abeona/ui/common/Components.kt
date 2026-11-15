package io.github.numynox.nexus.abeona.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.domain.delayMinutes
import io.github.numynox.nexus.abeona.domain.platformChanged
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.watch
import io.github.numynox.nexus.abeona.ui.well
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Instant

// The shared pieces every Abeona screen is built from (abeona-design.md §6),
// named as on the web. This file: times, platforms and lines. Status.kt: chips
// and banners. Rows.kt: the row grid, facts, headers, the primary button.

/**
 * A time with its delay, the same way everywhere (glossary: delay): the new
 * time is the one to read, the minutes late beside it in the watch colour,
 * never red. [block] where there is room: beside the new time a column with
 * the minutes late above the planned time, struck through and grey. Inline,
 * in rows: the new time, then "+6". On time: the time alone. [cancelled]: the
 * planned time struck through, nothing else (the caller says why).
 */
@Composable
fun ShiftedTime(
    planned: Instant?,
    expected: Instant?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    block: Boolean = true,
    cancelled: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val delay = if (cancelled) 0 else delayMinutes(planned, expected)
    val main = style.tabular()
    if (cancelled) {
        Text(TripFormat.time(planned), style = main.copy(textDecoration = TextDecoration.LineThrough), color = muted, maxLines = 1, modifier = modifier)
        return
    }
    if (delay <= 0) {
        Text(TripFormat.time(planned ?: expected), style = main, color = color, maxLines = 1, modifier = modifier)
        return
    }
    val late = watch.soft.content
    val spoken = modifier.clearAndSetSemantics { contentDescription = "${TripFormat.time(expected)}, $delay min late" }
    if (block) {
        val small = (style.fontSize.value * 0.42f).coerceAtLeast(11f).sp
        Row(spoken, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(TripFormat.time(expected), style = main, color = color, maxLines = 1)
            Column {
                Text("+$delay", style = main.copy(fontSize = small, lineHeight = small * 1.1f, fontWeight = FontWeight.Bold, letterSpacing = 0.sp), color = late, maxLines = 1)
                Text(
                    TripFormat.time(planned),
                    style = main.copy(fontSize = small, lineHeight = small * 1.1f, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, textDecoration = TextDecoration.LineThrough),
                    color = muted,
                    maxLines = 1,
                )
            }
        }
    } else {
        Row(spoken, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(TripFormat.time(expected), style = main, color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
            Text("+$delay", style = main.copy(fontSize = style.fontSize * 0.8f, fontWeight = FontWeight.Bold), color = late, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

/**
 * The platform in a box, the way a board shows it. The box is the word: no
 * "Pl."; a screen reader hears "Platform 7". A changed platform is the one
 * filled thing in its row, with the old number struck through beside it.
 * [lead] for the platform that is the answer; otherwise the row size.
 */
@Composable
fun PlatformBox(
    planned: String?,
    current: String?,
    modifier: Modifier = Modifier,
    lead: Boolean = false,
    changed: Boolean = platformChanged(planned, current),
) {
    val shown = current ?: planned ?: return
    val box = if (changed) watch.fill else well
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = if (changed && planned != null) "Platform $shown, changed from $planned" else "Platform $shown"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (changed && planned != null) {
            Text(planned, style = (if (lead) AbeonaType.body else AbeonaType.meta).copy(textDecoration = TextDecoration.LineThrough), color = muted, maxLines = 1)
        }
        Surface(color = box.container, contentColor = box.content, shape = RoundedCornerShape(8.dp)) {
            Box(
                Modifier.defaultMinSize(minWidth = if (lead) 36.dp else 24.dp).height(if (lead) 36.dp else 24.dp).padding(horizontal = if (lead) 8.dp else 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(shown, style = (if (lead) AbeonaType.headline.copy(fontSize = 20.sp) else AbeonaType.meta.copy(fontWeight = FontWeight.SemiBold)), maxLines = 1)
            }
        }
    }
}

private val LongDistance = setOf("ICE", "IC", "EC", "ECE", "RJ", "RJX", "TGV", "NJ", "EN", "FLX", "EST", "FR", "IR")

/**
 * "ICE 597" squared, as a board would show it. Ink only: filled for
 * long-distance trains, outlined for regional ones, the well with its icon
 * for a bus. A ride that does not run is struck through.
 */
@Composable
fun LineChip(leg: Leg, modifier: Modifier = Modifier, large: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val bus = leg.mode == LegMode.Bus
    val long = !bus && leg.product?.uppercase().orEmpty() in LongDistance
    Surface(
        color = when {
            bus -> well.container
            long -> scheme.onSurface
            else -> Color.Transparent
        },
        contentColor = if (long) scheme.surface else scheme.onSurface,
        border = if (!bus && !long) BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.6f)) else null,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.alpha(if (leg.cancelled) 0.6f else 1f),
    ) {
        Row(
            Modifier.height(if (large) 28.dp else 24.dp).padding(horizontal = if (large) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (bus) Icon(Icons.Rounded.DirectionsBus, contentDescription = "Bus", modifier = Modifier.size(14.dp))
            Text(
                leg.label,
                style = (if (large) AbeonaType.body else AbeonaType.meta).copy(
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (leg.cancelled) TextDecoration.LineThrough else null,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * The rides of a journey as chips in travel order, a walk as its icon between
 * them. [wrap] lets a long journey continue on a second line rather than be cut.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegChips(legs: List<Leg>, modifier: Modifier = Modifier, wrap: Boolean = false) {
    val chips: @Composable () -> Unit = {
        legs.forEach { leg ->
            if (leg.isWalk) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = "Walk", tint = muted, modifier = Modifier.size(16.dp))
            } else {
                LineChip(leg)
            }
        }
    }
    if (wrap) {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) { chips() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { chips() }
    }
}
