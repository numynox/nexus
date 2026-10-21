package io.github.numynox.nexus.vibilia.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.chart.ChartPoint
import io.github.numynox.nexus.core.ui.chart.ChartSeries
import io.github.numynox.nexus.core.ui.chart.NexusChart
import io.github.numynox.nexus.core.ui.chart.ReferenceLine
import io.github.numynox.nexus.core.ui.chart.SeriesStyle
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.MetricCard
import io.github.numynox.nexus.core.ui.components.MetricRow
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.theme.tabular
import io.github.numynox.nexus.vibilia.data.CarStatistics
import io.github.numynox.nexus.vibilia.data.DriveModel
import io.github.numynox.nexus.vibilia.ui.AppViewModel
import io.github.numynox.nexus.vibilia.ui.common.CarSwitcher
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.vibilia.ui.common.Tag
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.valueOrNull
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun StatisticsScreen(app: AppViewModel) {
    val viewModel = containerViewModel { StatisticsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cars by app.cars.collectAsStateWithLifecycle()
    val car by app.selectedCar.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()

    LaunchedEffect(car?.id, version, settings.fuelType) {
        car?.let { viewModel.load(it.id, version, settings.fuelType) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { CarSwitcher(cars.valueOrNull.orEmpty(), car, onSelect = { app.selectCar(it.id) }) })
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                cars is Loadable.Ready && car == null ->
                    EmptyState(Icons.Rounded.DirectionsCar, "No vehicles yet", "Add one under Settings to see its statistics.")
                car == null -> PlaceholderList(count = 4, modifier = Modifier.padding(horizontal = 16.dp))
                else -> when (val overview = state.overview) {
                    Loadable.Loading -> PlaceholderList(count = 4, modifier = Modifier.padding(horizontal = 16.dp))
                    is Loadable.Failed -> ErrorState(overview.message, onRetry = { viewModel.load(car!!.id, version, settings.fuelType, force = true) })
                    is Loadable.Ready -> StatisticsContent(overview.value, state, settings.fuelType, viewModel::setRange)
                }
            }
        }
    }
}

@Composable
internal fun StatisticsContent(overview: CarOverview, state: StatisticsState, fuelType: String, onRange: (StatsRange) -> Unit) {
    val stats = overview.statistics ?: CarStatistics()
    val ranges = remember(overview.years) {
        listOf(StatsRange.OneYear, StatsRange.ThreeYears, StatsRange.FiveYears, StatsRange.Overall) + overview.years.map { StatsRange.Year(it) }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "metrics") { Metrics(stats, overview.model) }
        overview.model?.let { model ->
            item(key = "driving") { DrivingSection(overview, model) }
        }
        item(key = "ranges") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ranges, key = { it.label }) { range ->
                    FilterChip(selected = range == state.range, onClick = { onRange(range) }, label = { Text(range.label) })
                }
            }
        }
        when (val plots = state.plots) {
            Loadable.Loading -> item(key = "plots-loading") { LoadingState() }
            is Loadable.Failed -> item(key = "plots-error") { Text(plots.message, color = MaterialTheme.colorScheme.error) }
            is Loadable.Ready -> {
                item(key = "consumption") { ConsumptionChart(plots.value) }
                item(key = "prices") { PriceChart(plots.value, fuelType) }
                item(key = "months") { MonthsChart(plots.value, estimated = overview.model != null) }
                item(key = "mileage") { MileageChart(plots.value) }
            }
        }
    }
}

// ── Metrics ──────────────────────────────────────────────────────────────────

@Composable
private fun Metrics(stats: CarStatistics, model: DriveModel?) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            MetricCard(
                "Distance", stats.drivenTotalKm, NexusFormat::km, Icons.Rounded.Route, primary,
                listOf(
                    MetricRow("This year", NexusFormat.km(stats.drivenThisYearKm)),
                    MetricRow("Last year", NexusFormat.km(stats.drivenLastYearKm)),
                    MetricRow("Per month", NexusFormat.km(stats.drivenAvgMonthKm)),
                    MetricRow("Per day", NexusFormat.km(stats.drivenAvgDayKm)),
                ),
                m,
                footnote = "Odometer ${NexusFormat.km(stats.latestMileage.toDouble())}",
            )
        },
        { m ->
            MetricCard(
                "Fuel used", stats.fuelUsedTotalL, { NexusFormat.liters(it, 0) }, Icons.Rounded.WaterDrop, tertiary,
                listOf(
                    MetricRow("This year", NexusFormat.liters(stats.fuelUsedThisYearL)),
                    MetricRow("Last year", NexusFormat.liters(stats.fuelUsedLastYearL)),
                    MetricRow("Per month", NexusFormat.liters(stats.fuelUsedAvgMonthL)),
                    MetricRow("Per day", NexusFormat.liters(stats.fuelUsedAvgDayL)),
                ),
                m,
            )
        },
        { m ->
            MetricCard(
                "Consumption", stats.avgConsumption, { "${NexusFormat.number(it, 2)} L/100" }, Icons.Rounded.LocalGasStation, secondary,
                listOf(
                    MetricRow("Lowest", "${NexusFormat.number(stats.minConsumption, 2)} L"),
                    MetricRow("Highest", "${NexusFormat.number(stats.maxConsumption, 2)} L"),
                ),
                m,
            )
        },
        { m ->
            MetricCard(
                "Fuel cost", stats.fuelCostTotalEur, { NexusFormat.euro(it, 0) }, Icons.Rounded.Euro, primary,
                listOf(
                    MetricRow("This year", NexusFormat.euro(stats.fuelCostThisYearEur)),
                    MetricRow("Last year", NexusFormat.euro(stats.fuelCostLastYearEur)),
                    MetricRow("Per month", NexusFormat.euro(stats.fuelCostAvgMonthEur)),
                    MetricRow("Per day", NexusFormat.euro(stats.fuelCostAvgDayEur)),
                ),
                m,
            )
        },
        { m ->
            MetricCard(
                "Cost per km", stats.avgCostPerKm, { "${NexusFormat.number(it, 3)} €" }, Icons.Rounded.Speed, secondary,
                listOf(
                    MetricRow("Lowest", "${NexusFormat.number(stats.minCostPerKm, 3)} €"),
                    MetricRow("Highest", "${NexusFormat.number(stats.maxCostPerKm, 3)} €"),
                ),
                m,
            )
        },
        { m ->
            MetricCard(
                "Expenses", stats.expenseTotalEur, { NexusFormat.euro(it, 0) }, Icons.Rounded.ReceiptLong, tertiary,
                listOf(
                    MetricRow("This year", NexusFormat.euro(stats.expenseThisYearEur)),
                    MetricRow("Last year", NexusFormat.euro(stats.expenseLastYearEur)),
                    MetricRow("Per year", NexusFormat.euro(stats.expenseAvgYearEur)),
                ),
                m,
            )
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.chunked(2).forEach { pair ->
            // Equal heights per pair, so the grid reads as a grid.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Driving ──────────────────────────────────────────────────────────────────

/**
 * What Bluetooth tracking has learned: the model, the last 30 days spread over
 * the days actually driven, and the recent drives (several phones' reports
 * merged into one).
 */
@Composable
private fun DrivingSection(overview: CarOverview, model: DriveModel) {
    val primary = MaterialTheme.colorScheme.primary
    NexusCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text("Driving", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            model.trackingSince?.let {
                Text("since ${NexusFormat.date(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val assumed = model.sampleCount == 0
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModelFigure("Speed", model.kmPerHour?.let { "${if (assumed) "≈ " else ""}${NexusFormat.number(it, 0)} km/h" } ?: "Learning", Modifier.weight(1f))
            ModelFigure("Fuel", model.litersPerHour?.let { "${NexusFormat.number(it, 1)} L/h" } ?: "—", Modifier.weight(1f))
            ModelFigure("Learned from", if (assumed) "Assumed" else "${model.sampleCount} tanks", Modifier.weight(1f))
        }
        if (model.kmPerHour == null || assumed) {
            Text(
                "Speeds are assumed (30 km/h for short drives, rising to 100 km/h for long ones) until one tank has been tracked from refuel to refuel. It learns this car from the tanks after that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val days = overview.recentDays
        if (days.any { it.km > 0 || it.estimatedKm > 0 }) {
            SectionHeader("Last 30 days", supporting = "Distance on the days it was driven; since the last refuel estimated")
            val zone = ZoneId.systemDefault()
            val measured = days.map { ChartPoint(it.day.toEpochDay().toDouble(), it.km) }
            val estimated = days.map { ChartPoint(it.day.toEpochDay().toDouble(), it.estimatedKm) }
            val today = LocalDate.now(zone).toEpochDay().toDouble()
            NexusChart(
                series = listOf(
                    ChartSeries("Driven", measured, primary, SeriesStyle.Bars),
                    ChartSeries("Estimated", estimated, primary.copy(alpha = 0.4f), SeriesStyle.Bars),
                ),
                xFormatter = { NexusFormat.weekdayDay(LocalDate.ofEpochDay(it.toLong())) },
                yFormatter = { NexusFormat.number(it) },
                tooltipYFormatter = { NexusFormat.km(it) },
                xRange = (today - 29)..today,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        }
        if (overview.recentDrives.isNotEmpty()) {
            SectionHeader("Recent drives")
            overview.recentDrives.take(8).forEach { drive ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${NexusFormat.date(drive.startedAt)}, ${NexusFormat.time(drive.startedAt)} – ${NexusFormat.time(drive.endedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (drive.reporterCount > 1) Tag("${drive.reporterCount} phones", MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.size(8.dp))
                    Text(NexusFormat.duration(drive.hours), style = MaterialTheme.typography.labelLarge.tabular())
                }
            }
        }
    }
}

@Composable
private fun ModelFigure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.tabular())
    }
}

// ── Charts ───────────────────────────────────────────────────────────────────

private fun epochDay(instant: java.time.Instant): Double =
    instant.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay().toDouble()

private fun dayLabel(value: Double): String = NexusFormat.monthShort(LocalDate.ofEpochDay(value.toLong()))
private fun dayTooltip(value: Double): String = NexusFormat.date(LocalDate.ofEpochDay(value.toLong()))

@Composable
private fun ChartCard(title: String, subtitle: String, empty: Boolean, content: @Composable () -> Unit) {
    NexusCard {
        SectionHeader(title, supporting = subtitle)
        Spacer(Modifier.height(8.dp))
        if (empty) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("Nothing to plot in this range", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            content()
        }
    }
}

private fun average(points: List<ChartPoint>, label: String, color: Color): List<ReferenceLine> =
    points.takeIf { it.isNotEmpty() }?.let { listOf(ReferenceLine(it.map { p -> p.y }.average(), label, color)) }.orEmpty()

@Composable
private fun ConsumptionChart(plots: Plots) {
    val color = MaterialTheme.colorScheme.secondary
    val points = plots.events.mapNotNull { e -> e.consumption?.takeIf { it > 0 }?.let { ChartPoint(epochDay(e.fueledAt), it) } }
    ChartCard("Consumption", "Litres per 100 km between refuels", points.isEmpty()) {
        NexusChart(
            series = listOf(ChartSeries("Consumption", points, color, SeriesStyle.Line, fill = true)),
            xFormatter = ::dayLabel,
            tooltipXFormatter = ::dayTooltip,
            yFormatter = { NexusFormat.number(it, 1) },
            tooltipYFormatter = { "${NexusFormat.number(it, 2)} L/100km" },
            referenceLines = average(points, "Ø", MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
    }
}

@Composable
private fun PriceChart(plots: Plots, fuelType: String) {
    val paidColor = MaterialTheme.colorScheme.primary
    val marketColor = MaterialTheme.colorScheme.tertiary
    val paid = plots.events.mapNotNull { e -> e.pricePerLiter?.let { ChartPoint(epochDay(e.fueledAt), it) } }
    val market = plots.weeklyMinima.map { ChartPoint(it.weekStart.toEpochDay().toDouble(), it.minPrice) }
    ChartCard("Fuel prices", "What you paid per litre, and the weekly $fuelType market low", paid.isEmpty() && market.isEmpty()) {
        NexusChart(
            series = listOf(
                ChartSeries("Market low", market, marketColor, SeriesStyle.Line, strokeWidth = 1.5.dp, dashed = true),
                ChartSeries("Paid", paid, paidColor, SeriesStyle.Line, strokeWidth = 2.5.dp),
            ),
            xFormatter = ::dayLabel,
            tooltipXFormatter = ::dayTooltip,
            yFormatter = { NexusFormat.number(it, 2) },
            tooltipYFormatter = { "${NexusFormat.number(it, 3)} €" },
            referenceLines = average(paid, "Ø", MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
    }
}

@Composable
private fun MonthsChart(plots: Plots, estimated: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val measured = plots.months.map { ChartPoint(it.month.toEpochDay().toDouble(), it.kmDriven) }
    val guessed = plots.months.map { ChartPoint(it.month.toEpochDay().toDouble(), it.estimatedKm) }
    val totals = plots.months.map { ChartPoint(it.month.toEpochDay().toDouble(), it.kmDriven + it.estimatedKm) }
    ChartCard(
        "Kilometres per month",
        if (estimated) "Spread over the days driven; the current month estimated from driving time" else "Distance between refuels, by month",
        measured.isEmpty(),
    ) {
        NexusChart(
            series = listOf(
                ChartSeries("Driven", measured, color, SeriesStyle.Bars),
                ChartSeries("Estimated", guessed, color.copy(alpha = 0.4f), SeriesStyle.Bars, showInTooltip = estimated),
            ),
            xFormatter = ::dayLabel,
            yFormatter = { NexusFormat.number(it) },
            tooltipYFormatter = { NexusFormat.km(it) },
            referenceLines = average(totals.filter { it.y > 0 }, "Ø", MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
    }
}

@Composable
private fun MileageChart(plots: Plots) {
    val color = MaterialTheme.colorScheme.tertiary
    val points = plots.events.mapNotNull { e -> e.mileage?.let { ChartPoint(epochDay(e.fueledAt), it.toDouble()) } }
    ChartCard("Odometer", "Mileage at each refuel", points.isEmpty()) {
        NexusChart(
            series = listOf(ChartSeries("Odometer", points, color, SeriesStyle.SteppedLine, fill = true)),
            xFormatter = ::dayLabel,
            tooltipXFormatter = ::dayTooltip,
            yFormatter = { NexusFormat.number(it / 1000, 0) + "k" },
            tooltipYFormatter = { NexusFormat.km(it) },
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}
