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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
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
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.domain.Confidence
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

/** "+7" in amber when late; nothing when on time — a delay is information, not an alarm. */
@Composable
fun DelayBadge(minutes: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    if (minutes <= 0) return
    val colors = attention
    Surface(color = colors.container, contentColor = colors.content, shape = CircleShape, modifier = modifier) {
        Text(
            "+$minutes",
            style = (if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium).copy(fontWeight = FontWeight.Bold).tabular(),
            modifier = Modifier.padding(horizontal = if (large) 10.dp else 7.dp, vertical = if (large) 2.dp else 1.dp),
        )
    }
}

/** A planned time, and the expected one beside it when they differ. */
@Composable
fun TimeWithDelay(
    planned: Instant?,
    current: Instant?,
    delay: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    cancelled: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            TripFormat.time(planned),
            style = style.tabular().copy(textDecoration = if (cancelled) TextDecoration.LineThrough else null),
            color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (delay > 0 && current != null && !cancelled) {
            Text(
                TripFormat.time(current),
                style = style.copy(fontSize = style.fontSize * 0.72f, fontWeight = FontWeight.SemiBold).tabular(),
                color = MaterialTheme.colorScheme.tertiary,
            )
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

/** "Safe", "Tight" or "At risk", with the minutes to spare. */
@Composable
fun ConfidenceChip(confidence: Confidence, modifier: Modifier = Modifier, minutes: Int? = null) {
    val colors = when (confidence) {
        Confidence.SAFE -> calm
        Confidence.TIGHT -> attention
        Confidence.AT_RISK -> danger
    }
    val icon = when (confidence) {
        Confidence.SAFE -> Icons.Rounded.CheckCircle
        Confidence.TIGHT -> Icons.Rounded.Timelapse
        Confidence.AT_RISK -> Icons.Rounded.ErrorOutline
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
fun BindingLiftedBanner(delay: Int, modifier: Modifier = Modifier) {
    val colors = attention
    Surface(color = colors.container, contentColor = colors.content, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(28.dp))
            Column {
                Text("Train binding lifted", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Expected +$delay min at your destination. Your saver ticket is valid on other trains for this route.",
                    style = MaterialTheme.typography.bodySmall,
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
