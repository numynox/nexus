package io.github.numynox.nexus.vibilia.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.numynox.nexus.core.ui.components.ConnectedChoiceRow
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.signin.SignInScreen
import io.github.numynox.nexus.core.ui.theme.NexusTheme
import io.github.numynox.nexus.core.ui.theme.ThemeMode
import io.github.numynox.nexus.vibilia.R
import io.github.numynox.nexus.vibilia.data.CarExpense
import io.github.numynox.nexus.vibilia.data.CarStatistics
import io.github.numynox.nexus.vibilia.data.DailyDistance
import io.github.numynox.nexus.vibilia.data.DistanceMonth
import io.github.numynox.nexus.vibilia.data.DriveModel
import io.github.numynox.nexus.vibilia.data.DrivePeriod
import io.github.numynox.nexus.vibilia.data.FuelLevel
import io.github.numynox.nexus.vibilia.data.FuelTypes
import io.github.numynox.nexus.vibilia.data.LastRefuelPrice
import io.github.numynox.nexus.vibilia.data.OpeningTime
import io.github.numynox.nexus.vibilia.data.PlotEvent
import io.github.numynox.nexus.vibilia.data.PriceBucket
import io.github.numynox.nexus.vibilia.data.RefuelEvent
import io.github.numynox.nexus.vibilia.data.StationPrice
import io.github.numynox.nexus.vibilia.data.StationSummary
import io.github.numynox.nexus.vibilia.data.WeeklyMinimum
import io.github.numynox.nexus.vibilia.ui.common.FuelGaugeCard
import io.github.numynox.nexus.vibilia.ui.common.LiveDriveBanner
import io.github.numynox.nexus.vibilia.ui.common.Loadable
import io.github.numynox.nexus.vibilia.ui.common.StationCard
import io.github.numynox.nexus.vibilia.ui.logbook.LogbookList
import io.github.numynox.nexus.vibilia.ui.logbook.LogbookViewModel
import io.github.numynox.nexus.vibilia.ui.prices.PriceHistory
import io.github.numynox.nexus.vibilia.ui.prices.PriceHistoryChart
import io.github.numynox.nexus.vibilia.ui.prices.PriceHistoryLegend
import io.github.numynox.nexus.vibilia.ui.statistics.CarOverview
import io.github.numynox.nexus.vibilia.ui.statistics.Plots
import io.github.numynox.nexus.vibilia.ui.statistics.StatisticsContent
import io.github.numynox.nexus.vibilia.ui.statistics.StatisticsState
import io.github.numynox.nexus.vibilia.ui.statistics.StatsRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens with sample data, so the design can be reviewed
 * without a device: `./gradlew :vibilia:recordRoborazziDebug`, images in
 * vibilia/build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h1400dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NexusTheme(VibiliaPalette, if (dark) ThemeMode.Dark else ThemeMode.Light) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    private val stations = listOf(
        StationPrice("1", "ARAL", "ERLANGEN", "Hauptstraße", "3", 1.789, wholeDay = true,
            openingTimes = listOf(OpeningTime("Mo-Fr", "06:00:00", "22:00:00"))),
        StationPrice("2", "Shell", "erlangen", "Nürnberger Str.", "45", 1.819, discount = 0.02),
        StationPrice("3", "JET", "Tennenlohe", "Am Wetterkreuz", "1", 1.749),
        StationPrice("4", "Esso", "Bubenreuth", "Bayreuther Str.", "8", null),
    )

    private fun buckets(): List<PriceBucket> {
        val today = LocalDate.now(zone)
        return (0..3).flatMap { offset ->
            (0 until 24 * 6).map { slot ->
                val minute = slot * 10
                val base = 1.72 + 0.01 * offset
                PriceBucket(today.minusDays(offset.toLong()), minute, base + 0.07 * (1 + sin(minute / 1440.0 * 6.28 * 2)) / 2 + if (minute in 1080..1260) -0.03 else 0.0)
            }.filter { offset > 0 || it.bucketMinute <= 14 * 60 }
        }
    }

    private val level = FuelLevel(
        method = FuelLevel.Method.Drive, lastRefuelAt = now.minusSeconds(5 * 86_400), lastMileage = 45_230, tankCapacity = 50.0,
        startingLiters = 50.0, remainingLiters = 31.5, drivingHours = 3.4, estimatedKm = 230.0, kmPerHour = 68.0,
        litersPerHour = 5.4, litersPerDay = null,
    )

    @Composable
    private fun PricesPage() {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConnectedChoiceRow(listOf("History", "Nearby"), "History", {}, { it }, Modifier)
            ConnectedChoiceRow(FuelTypes, "E10", {}, { it }, Modifier, showCheck = false)
            LiveDriveBanner(visible = true, carName = "Golf", startedAt = now.minusSeconds(24 * 60))
            FuelGaugeCard(level, "Golf")
            NexusCard {
                PriceHistoryChart(PriceHistory(buckets(), 3, LastRefuelPrice(now.minusSeconds(3 * 86_400), 1.759)))
                PriceHistoryLegend("E10")
            }
            stations.forEach { StationCard(it, lowestPrice = 1.749) }
        }
    }

    @Test fun prices_light() = capture("prices", dark = false) { PricesPage() }
    @Test fun prices_dark() = capture("prices", dark = true) { PricesPage() }

    @Test
    fun sign_in() = capture("sign-in", dark = false) {
        SignInScreen("Vibilia", "Fuel prices, refuels and what your car really costs.", painterResource(R.drawable.vibilia_logo), { _, _ -> })
    }

    private fun refuel(id: Long, daysAgo: Long, mileage: Int, liters: Double, price: Double, full: Boolean = true) = RefuelEvent(
        id, "c", null, now.minusSeconds(daysAgo * 86_400), mileage, liters, liters * price, price, if (full) 50.0 else 35.0, full, false,
        null, StationSummary("s", "ARAL", "Erlangen", "Hauptstraße", "3"),
    )

    @Composable
    private fun LogbookPage() {
        val logbook = LogbookViewModel.build(
            listOf(
                refuel(5, 2, 45_230, 41.2, 1.749),
                refuel(4, 19, 44_610, 43.9, 1.799),
                refuel(3, 41, 43_980, 30.1, 1.829, full = false),
                refuel(2, 63, 43_420, 45.0, 1.779),
                refuel(1, 90, 42_800, 44.0, 1.759),
            ),
            listOf(CarExpense(1, "c", now.minusSeconds(30 * 86_400), "Annual inspection", 189.90, 44_000, "Service", "Brakes checked")),
        )
        Box(Modifier.fillMaxSize()) { LogbookList(logbook, onRefuel = {}, onExpense = {}) }
    }

    @Test fun logbook_light() = capture("logbook", dark = false) { LogbookPage() }
    @Test fun logbook_dark() = capture("logbook", dark = true) { LogbookPage() }

    @Composable
    private fun StatisticsPage() {
        val today = LocalDate.now(zone)
        val months = (11 downTo 0).map { i ->
            val m = today.withDayOfMonth(1).minusMonths(i.toLong())
            DistanceMonth(m, kmDriven = if (i == 0) 0.0 else 600.0 + 250 * sin(i.toDouble()), estimatedKm = if (i == 0) 240.0 else 0.0)
        }
        val events = (12 downTo 0).map { i ->
            PlotEvent(now.minusSeconds(i * 28L * 86_400), 39_000 + (12 - i) * 520, 1.72 + 0.05 * sin(i / 2.0), 6.2 + 0.6 * sin(i.toDouble()))
        }
        val weekly = (52 downTo 0).map { i -> WeeklyMinimum(today.minusWeeks(i.toLong()), 1.68 + 0.05 * sin(i / 5.0)) }
        val overview = CarOverview(
            statistics = CarStatistics(
                latestMileage = 45_230, drivenTotalKm = 18_430.0, drivenThisYearKm = 6_210.0, drivenLastYearKm = 7_480.0,
                drivenAvgMonthKm = 615.0, drivenAvgDayKm = 20.2, fuelUsedTotalL = 1_190.0, fuelUsedThisYearL = 398.0,
                fuelUsedLastYearL = 470.0, fuelUsedAvgMonthL = 39.6, fuelUsedAvgDayL = 1.3, fuelCostTotalEur = 2_105.0,
                fuelCostThisYearEur = 702.0, fuelCostLastYearEur = 842.0, fuelCostAvgMonthEur = 70.1, fuelCostAvgDayEur = 2.31,
                avgConsumption = 6.46, minConsumption = 5.61, maxConsumption = 7.92, avgCostPerKm = 0.114, minCostPerKm = 0.096,
                maxCostPerKm = 0.141, expenseTotalEur = 1_420.0, expenseThisYearEur = 190.0, expenseLastYearEur = 560.0, expenseAvgYearEur = 473.0,
            ),
            years = listOf(2026, 2025, 2024),
            model = DriveModel(now.minusSeconds(80 * 86_400), now.minusSeconds(3_600), 4, 41.5, 2_830.0, 68.2, 4.4, 6.45),
            recentDays = (29 downTo 0).map { i ->
                DailyDistance(today.minusDays(i.toLong()), km = if (i > 2 && i % 3 != 0) 25.0 + 18 * sin(i.toDouble()) else 0.0, estimatedKm = if (i <= 2) 31.0 else 0.0)
            },
            recentDrives = listOf(
                DrivePeriod(now.minusSeconds(3_600 * 5), now.minusSeconds(3_600 * 4 + 1_200), 2),
                DrivePeriod(now.minusSeconds(86_400 + 3_600 * 3), now.minusSeconds(86_400 + 3_600 * 2), 1),
            ),
        )
        StatisticsContent(
            overview,
            StatisticsState(Loadable.Ready(overview), Loadable.Ready(Plots(events, months, weekly)), StatsRange.OneYear),
            "E10",
        ) {}
    }

    @Test fun statistics_light() = capture("statistics", dark = false) { StatisticsPage() }
    @Test fun statistics_dark() = capture("statistics", dark = true) { StatisticsPage() }
}
