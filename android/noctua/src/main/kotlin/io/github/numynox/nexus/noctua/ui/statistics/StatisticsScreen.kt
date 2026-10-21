package io.github.numynox.nexus.noctua.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.chart.ChartPoint
import io.github.numynox.nexus.core.ui.chart.ChartSeries
import io.github.numynox.nexus.core.ui.chart.NexusChart
import io.github.numynox.nexus.core.ui.chart.SeriesStyle
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.MetricCard
import io.github.numynox.nexus.core.ui.components.MetricRow
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.theme.NexusThemeDefaults
import io.github.numynox.nexus.core.ui.theme.tabular
import io.github.numynox.nexus.noctua.ui.common.containerViewModel

private val Weekdays = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
fun StatisticsScreen() {
    val viewModel = containerViewModel { StatisticsViewModel(it) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(stats) { if (stats !is Loadable.Loading) refreshing = false }

    Scaffold(topBar = { TopAppBar(title = { Text("Statistics") }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; viewModel.load() },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            when (val state = stats) {
                Loadable.Loading -> PlaceholderList(count = 4, modifier = Modifier.padding(horizontal = 16.dp))
                is Loadable.Failed -> ErrorState(state.message, onRetry = viewModel::load)
                is Loadable.Ready -> if (state.value.totalRead == 0) {
                    EmptyState(Icons.Rounded.AutoStories, "No reading yet", "Open a few articles and your reading habits show up here.")
                } else {
                    Statistics(state.value)
                }
            }
        }
    }
}

@Composable
private fun Statistics(stats: ReadingStats) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val extended = NexusThemeDefaults.extendedColors
    val window = if (stats.weeks == 1) "this week" else "the last ${stats.weeks} weeks"

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "Streak",
                    value = stats.currentStreak.toDouble(),
                    format = { days(it.toInt()) },
                    icon = Icons.Rounded.LocalFireDepartment,
                    accent = extended.warning,
                    rows = listOf(MetricRow("Longest", days(stats.longestStreak))),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "This week",
                    value = stats.thisWeekRead.toDouble(),
                    format = { NexusFormat.number(it) },
                    icon = Icons.Rounded.Today,
                    accent = primary,
                    rows = listOf(MetricRow("Published", NexusFormat.number(stats.thisWeekPublished.toDouble()))),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            MetricCard(
                title = "Articles read",
                value = stats.totalRead.toDouble(),
                format = { NexusFormat.number(it) },
                icon = Icons.Rounded.AutoStories,
                accent = primary,
                rows = listOf(
                    MetricRow("Out of", "${NexusFormat.number(stats.totalArticles.toDouble())} articles kept"),
                    MetricRow("Per week, $window", NexusFormat.number(stats.averageReadPerWeek, 1)),
                    MetricRow("Published per week", NexusFormat.number(stats.averagePublishedPerWeek, 1)),
                ),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    title = "Active days",
                    value = stats.activeDays.toDouble(),
                    format = { "${it.toInt()} / ${stats.windowDays}" },
                    icon = Icons.Rounded.CalendarMonth,
                    accent = tertiary,
                    rows = emptyList(),
                    footnote = "Days with at least one article read, $window.",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "Busiest hour",
                    value = (stats.busiestHour?.hour ?: 0).toDouble(),
                    format = { if (stats.busiestHour == null) "—" else "%02d:00".format(it.toInt()) },
                    icon = Icons.Rounded.Schedule,
                    accent = tertiary,
                    rows = emptyList(),
                    footnote = stats.busiestHour?.let { "${it.count} articles read in that hour, $window." },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionHeader("By weekday", supporting = "Articles read on an average day, $window") }
        item {
            NexusCard {
                NexusChart(
                    series = listOf(
                        ChartSeries(
                            "Read",
                            stats.weekdayAverages.mapIndexed { index, value -> ChartPoint(index.toDouble(), value) },
                            primary,
                            SeriesStyle.Bars,
                        ),
                    ),
                    xFormatter = { Weekdays.getOrElse(it.toInt()) { "" } },
                    yFormatter = { NexusFormat.number(it) },
                    tooltipYFormatter = { "${NexusFormat.number(it, 1)} articles" },
                    xRange = 0.0..6.0,
                    xTicks = (0..6).map { it.toDouble() },
                    includeZero = true,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
        }

        if (stats.feeds.isNotEmpty()) {
            item { SectionHeader("Feeds", supporting = "What you read, $window, and how much of each feed that was") }
            item {
                NexusCard {
                    val top = stats.feeds.first().read
                    stats.feeds.take(10).forEach { feed -> FeedBar(feed, top) }
                }
            }
        }

        item { SectionHeader("When you read", supporting = "Hour of day by weekday, $window") }
        item { NexusCard { Heatmap(stats.readHeatmap, primary) } }
        item { SectionHeader("When articles arrive", supporting = "What your feeds published, $window") }
        item { NexusCard { Heatmap(stats.publishedHeatmap, tertiary) } }
    }
}

private fun days(count: Int) = if (count == 1) "1 day" else "$count days"

@Composable
private fun FeedBar(feed: FeedReads, top: Int) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(feed.feed, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                "${feed.read}" + if (feed.published > 0) " · ${NexusFormat.percent(feed.share)} of ${feed.published}" else "",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { feed.read.toFloat() / top.coerceAtLeast(1) },
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp),
        )
    }
}

/** Seven rows of 24 hours, each cell as strong as its share of the busiest. */
@Composable
private fun Heatmap(cells: List<List<Int>>, color: Color) {
    val max = cells.maxOf { row -> row.maxOrNull() ?: 0 }.coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        cells.forEachIndexed { day, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Weekdays[day],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(26.dp),
                )
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val gap = 2.dp.toPx()
                    val cell = (size.width - gap * 23) / 24
                    row.forEachIndexed { hour, count ->
                        drawRoundRect(
                            color = if (count == 0) empty else color.copy(alpha = 0.25f + 0.75f * count / max),
                            topLeft = Offset(hour * (cell + gap), 0f),
                            size = Size(cell, size.height),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }
            }
        }
        // Six-hour steps are evenly spaced, so spreading the labels lines them up.
        Row(Modifier.fillMaxWidth().padding(start = 26.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
