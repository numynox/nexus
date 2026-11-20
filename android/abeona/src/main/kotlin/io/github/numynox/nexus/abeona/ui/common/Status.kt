package io.github.numynox.nexus.abeona.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.numynox.nexus.abeona.data.BahnCardDeadline
import io.github.numynox.nexus.abeona.domain.Confidence
import io.github.numynox.nexus.abeona.reminders.BahnCardReminders
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.Tone
import io.github.numynox.nexus.abeona.ui.bad
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.ok
import io.github.numynox.nexus.abeona.ui.watch
import io.github.numynox.nexus.abeona.ui.well
import java.time.Instant

/** The three statuses (abeona-design.md §3), and Quiet for state that is none of them. */
enum class StatusTone { Ok, Watch, Bad, Quiet }

@Composable
private fun StatusTone.soft(): Tone = when (this) {
    StatusTone.Ok -> ok.soft
    StatusTone.Watch -> watch.soft
    StatusTone.Bad -> bad.soft
    StatusTone.Quiet -> Tone(well.container, muted)
}

/**
 * A status in its soft colour, always with its icon. The visible [label] is
 * short (minutes, an amount) or empty; [description] carries the words the
 * icon replaces, for a screen reader.
 */
@Composable
fun StatusChip(
    tone: StatusTone,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    label: String = "",
    description: String? = null,
) {
    val colors = tone.soft()
    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = CircleShape,
        modifier = if (description != null) modifier.clearAndSetSemantics { contentDescription = description } else modifier,
    ) {
        Row(
            Modifier.height(24.dp).defaultMinSize(minWidth = 24.dp).padding(horizontal = if (label.isEmpty()) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            if (label.isNotEmpty()) Text(label, style = AbeonaType.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/**
 * How a change looks: its icon and the minutes ("✓ 12 min"). The word (Safe,
 * Tight, At risk, Impossible) shows only when there are no minutes to show,
 * and is always what a screen reader hears. With [word], as on the live
 * card's line, a change that is not safe says so after its minutes ("6 min ·
 * tight", "2 min · at risk"), and an impossible one only "impossible".
 */
@Composable
fun ConfidenceChip(confidence: Confidence, modifier: Modifier = Modifier, minutes: Int? = null, word: Boolean = false) {
    val label = when {
        minutes == null -> confidence.label
        !word || confidence == Confidence.SAFE -> "$minutes min"
        confidence == Confidence.IMPOSSIBLE -> confidence.label.lowercase()
        else -> "$minutes min · ${confidence.label.lowercase()}"
    }
    StatusChip(
        tone = when (confidence) {
            Confidence.SAFE -> StatusTone.Ok
            Confidence.TIGHT -> StatusTone.Watch
            Confidence.AT_RISK, Confidence.IMPOSSIBLE -> StatusTone.Bad
        },
        icon = when (confidence) {
            Confidence.SAFE -> Icons.Rounded.CheckCircle
            Confidence.TIGHT -> Icons.Rounded.Timelapse
            Confidence.AT_RISK -> Icons.Rounded.ErrorOutline
            Confidence.IMPOSSIBLE -> Icons.Rounded.Block
        },
        modifier = modifier,
        label = label,
        description = "${confidence.label} change" + (minutes?.let { ", $it min" } ?: ""),
    )
}

/** Offline, and since when the values are from: a quiet chip. */
@Composable
fun OfflinePill(since: Instant?, modifier: Modifier = Modifier) {
    if (since == null) return
    StatusChip(StatusTone.Quiet, Icons.Rounded.CloudOff, modifier, label = TripFormat.time(since), description = "Offline · updated ${TripFormat.time(since)}")
}

/**
 * A full-width status message: its icon, one bold line, at most one sentence
 * under it, and at most one action below. Watch or bad soft (ok where
 * something was settled); quiet, in the well with ink, for an offer that is
 * no status.
 */
@Composable
fun Banner(
    tone: StatusTone,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = if (tone == StatusTone.Quiet) Tone(well.container, well.content) else tone.soft()
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold))
                if (text != null) Text(text, style = AbeonaType.meta.copy(fontWeight = FontWeight.Normal))
                if (action != null) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = action)
                }
            }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Surface(onClick = onClick, color = colors.container, contentColor = colors.content, shape = shape, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Surface(color = colors.container, contentColor = colors.content, shape = shape, modifier = modifier.fillMaxWidth(), content = content)
    }
}

/**
 * The rule DB applies at 20 minutes (glossary: train binding lifted), in one
 * sentence: why, and what the ticket now allows. [because] replaces the delay
 * as the reason.
 */
@Composable
fun BindingBanner(delay: Int, modifier: Modifier = Modifier, because: String? = null) {
    Banner(
        StatusTone.Watch,
        Icons.Rounded.SwapHoriz,
        "You may take another train",
        modifier,
        text = (because ?: "Expected +$delay min at your destination") + ": train binding lifted.",
    )
}

/** Subscriptions to cancel soon (abeona_bahncard_deadlines); a tap leads to the cards. */
@Composable
fun DeadlineBanner(deadlines: List<BahnCardDeadline>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (deadlines.isEmpty()) return
    Banner(
        StatusTone.Watch,
        Icons.Rounded.Event,
        if (deadlines.size == 1) "A BahnCard renews soon" else "${deadlines.size} BahnCards renew soon",
        modifier,
        text = (deadlines.map { "${BahnCardReminders.cardName(it)}: ${BahnCardReminders.deadlineText(it)}" } +
            "Already cancelled? Turn off \"Renews automatically\" on the card.").joinToString("\n"),
        onClick = onClick,
    )
}
