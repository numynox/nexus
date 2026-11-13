package io.github.numynox.nexus.abeona.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.abeona.domain.delayMinutes
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import io.github.numynox.nexus.abeona.data.BahnCardDeadline
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.reminders.BahnCardReminders
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import io.github.numynox.nexus.abeona.ui.attention
import io.github.numynox.nexus.abeona.ui.calm
import io.github.numynox.nexus.abeona.ui.danger
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.Instant

/** One of the brand illustrations, cropped into a rounded frame. */
@Composable
fun Illustration(@DrawableRes res: Int, modifier: Modifier = Modifier, height: Dp = 160.dp, corner: Dp = 28.dp) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(corner)),
    )
}

/** Long-distance trains in ink, regional in green, buses in sky: what the board's colour would tell you. */
@Composable
fun productColors(product: String?, mode: LegMode): Pair<Color, Color> {
    val p = product?.uppercase().orEmpty()
    return when {
        mode == LegMode.Bus -> AbeonaColors.Sky to AbeonaColors.Ink
        p in setOf("ICE", "IC", "EC", "ECE", "RJ", "RJX", "TGV", "NJ", "EN", "FLX", "EST", "FR", "IR") ->
            if (MaterialTheme.colorScheme.surface.isDark()) Color(0xFFDCE8E4) to AbeonaColors.Ink else AbeonaColors.Ink to Color.White
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
}

private fun Color.isDark() = (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

/** "ICE 597" as a board would show it, coloured by the kind of train. */
@Composable
fun LineChip(leg: Leg, modifier: Modifier = Modifier, large: Boolean = false) {
    val (container, content) = productColors(leg.product, leg.mode)
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = if (large) 10.dp else 8.dp, vertical = if (large) 5.dp else 3.dp),
        ) {
            if (leg.mode == LegMode.Bus) {
                Icon(Icons.Rounded.DirectionsBus, contentDescription = null, modifier = Modifier.size(14.dp).padding(end = 2.dp))
            }
            Text(
                leg.label,
                style = (if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium).copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

/** The legs of a journey as chips, walks as a small figure between them. */
@Composable
fun LegChips(legs: List<Leg>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        legs.forEachIndexed { index, leg ->
            if (leg.isWalk) {
                Icon(
                    Icons.AutoMirrored.Rounded.DirectionsWalk,
                    contentDescription = "Walk",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                LineChip(leg)
            }
            if (index < legs.lastIndex && !leg.isWalk && !legs[index + 1].isWalk) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * A time with its delay, the same way everywhere (glossary: delay): the new
 * time is the one to read. [block] where there is room: the new time, and
 * beside it a column with the minutes late above the planned time, struck
 * through and grey. Inline, in rows and lists: the planned time struck
 * through, then the new one. On time: the time alone. [cancelled]: the
 * planned time struck through, nothing else (the caller says "Cancelled").
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
    val scheme = MaterialTheme.colorScheme
    val delay = if (cancelled) 0 else delayMinutes(planned, expected)
    val main = style.tabular()
    if (cancelled) {
        Text(TripFormat.time(planned), style = main.copy(textDecoration = TextDecoration.LineThrough), color = scheme.onSurfaceVariant, modifier = modifier)
        return
    }
    if (delay <= 0) {
        Text(TripFormat.time(planned ?: expected), style = main, color = color, modifier = modifier)
        return
    }
    val small = (style.fontSize.value * 0.45f).coerceAtLeast(11f).sp
    val struck = main.copy(textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Normal)
    if (block) {
        Row(modifier.semantics(mergeDescendants = true) { contentDescription = "${TripFormat.time(expected)}, $delay min late" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(TripFormat.time(expected), style = main, color = color)
            Column {
                Text("+$delay", style = main.copy(fontSize = small, lineHeight = small * 1.1f, fontWeight = FontWeight.Bold), color = scheme.tertiary)
                Text(TripFormat.time(planned), style = struck.copy(fontSize = small, lineHeight = small * 1.1f), color = scheme.onSurfaceVariant)
            }
        }
    } else {
        Row(modifier.semantics(mergeDescendants = true) { contentDescription = "${TripFormat.time(expected)}, $delay min late" }, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(TripFormat.time(planned), style = struck.copy(fontSize = style.fontSize * 0.82f), color = scheme.onSurfaceVariant)
            Text(TripFormat.time(expected), style = main, color = color)
        }
    }
}

/** The platform in a box, the way a board shows it; a changed one in amber with the old number struck through. */
@Composable
fun PlatformBox(planned: String?, current: String?, changed: Boolean, modifier: Modifier = Modifier, large: Boolean = false) {
    val shown = current ?: planned ?: return
    val colors = if (changed) attention else null
    Surface(
        color = colors?.container ?: MaterialTheme.colorScheme.secondaryContainer,
        contentColor = colors?.content ?: MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(if (large) 14.dp else 8.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 6.dp else 2.dp),
        ) {
            Text("Pl.", style = MaterialTheme.typography.labelSmall)
            Text(
                shown,
                style = (if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Bold).tabular(),
            )
            if (changed && planned != null) {
                Text(planned, style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.LineThrough))
            }
        }
    }
}

/** "Safe", "Tight", "At risk" or "Impossible" (no time at all), with the minutes to spare. */
@Composable
fun ConfidenceChip(confidence: Confidence, modifier: Modifier = Modifier, minutes: Int? = null) {
    val colors = when (confidence) {
        Confidence.SAFE -> calm
        Confidence.TIGHT -> attention
        Confidence.AT_RISK, Confidence.IMPOSSIBLE -> danger
    }
    val icon = when (confidence) {
        Confidence.SAFE -> Icons.Rounded.CheckCircle
        Confidence.TIGHT -> Icons.Rounded.Timelapse
        Confidence.AT_RISK -> Icons.Rounded.ErrorOutline
        Confidence.IMPOSSIBLE -> Icons.Rounded.Block
    }
    Surface(color = colors.container, contentColor = colors.content, shape = CircleShape, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                confidence.label + (minutes?.let { " · $it min" } ?: ""),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/** The worst change of a journey, summed up: "2 changes · all safe", "1 change · tight", "Direct". */
fun confidenceSummary(changes: Int, worst: Confidence?): String = when {
    changes == 0 -> "Direct"
    worst == null -> if (changes == 1) "1 change" else "$changes changes"
    else -> (if (changes == 1) "1 change · " else "$changes changes · ") + when (worst) {
        Confidence.SAFE -> if (changes == 1) "safe" else "all safe"
        Confidence.TIGHT -> "tight"
        Confidence.AT_RISK -> "at risk"
        Confidence.IMPOSSIBLE -> "impossible"
    }
}

/** A small pill for state that is not the content itself: "Offline · updated 07:42", "Timetable only". */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, offline: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (offline) Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun OfflinePill(since: Instant?, modifier: Modifier = Modifier) {
    if (since == null) return
    StatusPill("Offline · updated ${TripFormat.time(since)}", modifier, offline = true)
}

/** The rule DB applies at 20 minutes, said plainly, in amber. */
@Composable
fun BindingLiftedBanner(delay: Int, modifier: Modifier = Modifier, because: String? = null) {
    val colors = attention
    Surface(color = colors.container, contentColor = colors.content, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(28.dp))
            Column {
                Text("Train binding lifted", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    (because ?: "Expected +$delay min at your destination.") + " Your saver ticket is valid on other trains for this route.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Subscriptions to cancel soon (abeona_bahncard_deadlines), in amber; a tap leads to the cards. */
@Composable
fun DeadlineBanner(deadlines: List<BahnCardDeadline>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (deadlines.isEmpty()) return
    val colors = attention
    Surface(
        onClick = onClick,
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.EventBusy, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (deadlines.size == 1) "A BahnCard renews soon" else "${deadlines.size} BahnCards renew soon",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                deadlines.forEach { d ->
                    Text("${BahnCardReminders.cardName(d)}: ${BahnCardReminders.deadlineText(d)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Already cancelled? Turn off \"Renews automatically\" on the card.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A train icon in a soft circle, for list rows. */
@Composable
fun ModeIcon(mode: LegMode, modifier: Modifier = Modifier) {
    Icon(
        when (mode) {
            LegMode.Bus -> Icons.Rounded.DirectionsBus
            LegMode.Walk -> Icons.AutoMirrored.Rounded.DirectionsWalk
            else -> Icons.Rounded.Train
        },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
