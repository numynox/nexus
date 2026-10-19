package io.github.numynox.nexus.vibilia.ui.prices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.chart.ChartPoint
import io.github.numynox.nexus.core.ui.chart.ChartSeries
import io.github.numynox.nexus.core.ui.chart.NexusChart
import io.github.numynox.nexus.core.ui.chart.SeriesStyle
import io.github.numynox.nexus.core.ui.chart.VerticalMarker
import io.github.numynox.nexus.vibilia.ui.common.rememberNow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The cheapest price of the day by time of day: today as a bold stepped line,
 * the previous days fading behind it, a dashed line at the current time and a
 * dot for what you paid last — the web app's history chart, redrawn.
 */
@Composable
fun PriceHistoryChart(history: PriceHistory, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val now = rememberNow(60_000)
    val today = LocalDate.now(zone)
    val primary = MaterialTheme.colorScheme.primary
    val older = MaterialTheme.colorScheme.onSurfaceVariant
    val marker = MaterialTheme.colorScheme.error

    val series = remember(history, today, primary, older) {
        val byDay = history.buckets.groupBy { it.day }
        val days = (0..history.days).map { today.minusDays(it.toLong()) }
        val lines = days.mapIndexedNotNull { index, day ->
            val buckets = byDay[day].orEmpty().sortedBy { it.bucketMinute }
            if (buckets.isEmpty()) return@mapIndexedNotNull null
            val label = if (index == 0) "Today" else DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()).format(day)
            ChartSeries(
                label = label,
                points = buckets.map { ChartPoint(it.bucketMinute / 60.0, it.minPrice) },
                color = if (index == 0) primary else older.copy(alpha = (0.75f - (index - 1) * 0.18f).coerceAtLeast(0.2f)),
                style = SeriesStyle.SteppedLine,
                strokeWidth = if (index == 0) 3.5.dp else 1.5.dp,
                fill = index == 0,
            )
        }.reversed()
        val refuel = history.lastRefuel?.let {
            val local = it.fueledAt.atZone(zone)
            ChartSeries(
                label = "Last refuel",
                points = listOf(ChartPoint(local.hour + local.minute / 60.0, it.pricePerLiter)),
                color = primary,
                style = SeriesStyle.Dots,
            )
        }
        lines + listOfNotNull(refuel)
    }

    if (series.none { it.style != SeriesStyle.Dots }) {
        Box(modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            Text("No prices recorded for these days yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val nowLocal = now.atZone(zone)
    NexusChart(
        series = series,
        xFormatter = { hourLabel(it) },
        yFormatter = { NexusFormat.number(it, 2) },
        tooltipYFormatter = { NexusFormat.number(it, 3) + " €" },
        xRange = 0.0..24.0,
        xTicks = listOf(0.0, 6.0, 12.0, 18.0, 24.0),
        verticalMarkers = listOf(VerticalMarker(nowLocal.hour + nowLocal.minute / 60.0, marker)),
        modifier = modifier.fillMaxWidth().height(260.dp),
    )
}

/** The legend under the chart. */
@Composable
fun PriceHistoryLegend(fuelType: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(MaterialTheme.colorScheme.primary, "Today's lowest $fuelType")
        LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), "Previous days")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun hourLabel(hours: Double): String {
    val total = (hours * 60).toInt().coerceIn(0, 24 * 60)
    return "%02d:%02d".format(total / 60, total % 60)
}
