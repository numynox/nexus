package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.vibilia.data.DrivenDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The weeks of the "Distance driven" graph: this week first (left), older
 * weeks to the right, each Monday to Sunday. Only weeks from the one of the
 * first known day; a day after today or before the first known day is null —
 * no square.
 */
object DrivenWeeks {
    const val MAX_WEEKS = 50

    /** The start of the oldest day the graph can show. */
    fun since(today: LocalDate, zone: ZoneId): Instant =
        monday(today).minusWeeks((MAX_WEEKS - 1).toLong()).atStartOfDay(zone).toInstant()

    fun build(days: List<DrivenDay>, today: LocalDate): List<List<Double?>> {
        val first = days.minOfOrNull { it.day } ?: return emptyList()
        val byDay = days.associate { it.day to it.km }
        val thisWeek = monday(today)
        val weeks = (ChronoUnit.WEEKS.between(monday(first), thisWeek) + 1).toInt().coerceIn(1, MAX_WEEKS)
        return (0 until weeks).map { ago ->
            val monday = thisWeek.minusWeeks(ago.toLong())
            (0 until 7).map { offset ->
                val day = monday.plusDays(offset.toLong())
                if (day < first || day > today) null else byDay[day] ?: 0.0
            }
        }
    }

    /** Below this a day counts as not driven. */
    const val DRIVEN = 0.5

    /**
     * The 25th, 50th and 75th percentile of the days driven: a day's shade is
     * how many it exceeds, so a long trip does not wash out the commutes.
     */
    fun quartiles(weeks: List<List<Double?>>): List<Double> {
        val driven = weeks.flatten().filterNotNull().filter { it >= DRIVEN }.sorted()
        if (driven.isEmpty()) return emptyList()
        return listOf(0.25, 0.5, 0.75).map { driven[((driven.size - 1) * it).toInt()] }
    }

    private fun monday(day: LocalDate) = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

/** GitHub-style squares, one per day, Monday on top; darker for more kilometres. */
@Composable
fun DistanceDrivenGraph(days: List<DrivenDay>, modifier: Modifier = Modifier) {
    val weeks = remember(days) { DrivenWeeks.build(days, LocalDate.now()) }
    if (weeks.isEmpty()) return
    val thresholds = remember(weeks) { DrivenWeeks.quartiles(weeks) }
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val levels = listOf(0.35f, 0.55f, 0.8f, 1f).map { primary.copy(alpha = it).compositeOver(surface) }
    val scroll = rememberScrollState()
    val total = weeks.flatten().filterNotNull().sum()

    Column(modifier.fillMaxWidth()) {
        SectionHeader("Distance driven")
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "Distance driven: ${NexusFormat.km(total)} in ${weeks.size} weeks" }
                .fadingEdges(scroll.canScrollBackward, scroll.canScrollForward)
                .horizontalScroll(scroll),
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { km ->
                        if (km == null) {
                            Spacer(Modifier.size(SQUARE))
                        } else {
                            val colour = if (km < DrivenWeeks.DRIVEN) empty else levels[thresholds.count { km > it }]
                            Box(Modifier.size(SQUARE).background(colour, SQUARE_SHAPE))
                        }
                    }
                }
            }
        }
    }
}

private val SQUARE = 14.dp
private val SQUARE_SHAPE = RoundedCornerShape(3.dp)
private val FADE = 40.dp

/** Fades the content out towards an edge that has more to scroll to. */
private fun Modifier.fadingEdges(start: Boolean, end: Boolean): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FADE.toPx()
        if (end) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                blendMode = BlendMode.DstIn,
            )
        }
        if (start) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }
