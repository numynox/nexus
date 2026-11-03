package io.github.numynox.nexus.abeona.ui.review

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Euro
import io.github.numynox.nexus.abeona.data.ClaimsOverview
import io.github.numynox.nexus.abeona.ui.Navigator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.R
import io.github.numynox.nexus.abeona.data.Statistics
import io.github.numynox.nexus.abeona.ui.AbeonaColors
import io.github.numynox.nexus.abeona.ui.AbeonaPalette
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.common.Illustration
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.chart.ChartPoint
import io.github.numynox.nexus.core.ui.chart.ChartSeries
import io.github.numynox.nexus.core.ui.chart.NexusChart
import io.github.numynox.nexus.core.ui.chart.SeriesStyle
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.loadable
import io.github.numynox.nexus.core.ui.theme.tabular
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReviewViewModel(private val container: AppContainer) : ViewModel() {
    private val _year = MutableStateFlow(LocalDate.now().year)
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _stats = MutableStateFlow<Loadable<Statistics>>(Loadable.Loading)
    val stats: StateFlow<Loadable<Statistics>> = _stats.asStateFlow()

    private val _claims = MutableStateFlow<ClaimsOverview?>(null)
    /** This year's claims, for the bar under the stories; null until loaded or when it could not load. */
    val claims: StateFlow<ClaimsOverview?> = _claims.asStateFlow()

    fun load(year: Int = _year.value) = viewModelScope.launch {
        _year.value = year
        _stats.value = Loadable.Loading
        launch { _claims.value = runCatching { container.repository.claimsOverview(LocalDate.now().year) }.getOrNull() }
        _stats.value = loadable { container.repository.statistics(year) }
    }
}

/** One story card's colours: a background and the text on it. */
private data class CardTone(val background: Color, val content: Color, val accent: Color)

private val Night = CardTone(AbeonaColors.Ink, Color.White, AbeonaColors.Amber)
private val Green = CardTone(AbeonaColors.Green, Color.White, AbeonaColors.Amber)
private val Sky = CardTone(AbeonaColors.Sky, AbeonaColors.Ink, AbeonaColors.Green)
private val Mint = CardTone(AbeonaColors.Mint, AbeonaColors.Ink, AbeonaColors.Green)
private val Amber = CardTone(AbeonaColors.Amber, AbeonaColors.Ink, AbeonaColors.Ink)

private const val EARTH_KM = 40_075.0
private const val HAMBURG_MUNICH_KM = 775.0

/** The year on rails as a deck of story cards, swiped through one at a time. */
@Composable
fun ReviewScreen(app: AppViewModel, navigator: Navigator) {
    val viewModel = containerViewModel { ReviewViewModel(it) }
    val year by viewModel.year.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val claims by viewModel.claims.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val thisYear = LocalDate.now().year

    LaunchedEffect(version) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Year in review") },
                actions = {
                    listOf(thisYear - 1, thisYear).forEach { option ->
                        FilterChip(
                            selected = option == year,
                            onClick = { viewModel.load(option) },
                            label = { Text("$option") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val state = stats) {
                    Loadable.Loading -> LoadingState(Modifier.fillMaxSize())
                    is Loadable.Failed -> ErrorState(state.message, onRetry = { viewModel.load() })
                    is Loadable.Ready -> StoryPager(state.value, year)
                }
            }
            claims?.let { ClaimsBar(it, onClick = navigator::claims) }
        }
    }
}

@Composable
private fun StoryPager(stats: Statistics, year: Int) {
    val cards = storyCards(stats, year)
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            cards[page]()
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(cards.size) { index ->
                val selected = index == pager.currentPage
                Box(
                    Modifier
                        .clickable(interactionSource = null, indication = null) { scope.launch { pager.animateScrollToPage(index) } }
                        .semantics { contentDescription = "Story ${index + 1}" }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

private fun storyCards(stats: Statistics, year: Int): List<@Composable () -> Unit> {
    if (stats.trips == 0) {
        return listOf({
            StoryCard(Night, illustration = R.drawable.illus_year_review) {
                Kicker("$year on rails", Night)
                Headline("No rides yet", Night)
                Body("Trips count here once they have arrived. Save your next connection, and this deck fills itself.", Night)
            }
        })
    }
    val hours = stats.minutesOnTrain / 60.0
    return listOfNotNull(
        {
            StoryCard(Night, illustration = R.drawable.illus_year_review) {
                Kicker("$year on rails", Night)
                Big(NexusFormat.number(stats.trips.toDouble()), Night)
                Headline(if (stats.trips == 1) "trip" else "trips", Night)
                Body("${stats.rides} rides, ${stats.stations} stations along the way.", Night)
            }
        },
        {
            StoryCard(Green) {
                Kicker("Distance", Green)
                Big(NexusFormat.number(stats.km), Green)
                Headline("kilometres", Green)
                Body(
                    if (stats.km >= EARTH_KM) {
                        "That is ${NexusFormat.number(stats.km / EARTH_KM, 1)} times around the Earth."
                    } else {
                        "That is ${NexusFormat.number(stats.km / EARTH_KM * 100)} % of the way around the Earth, or Hamburg to Munich ${NexusFormat.number(stats.km / HAMBURG_MUNICH_KM, 1)} times."
                    },
                    Green,
                )
                Body("Longest trip: ${NexusFormat.km(stats.longestKm)}.", Green)
            }
        },
        {
            StoryCard(Sky) {
                Kicker("Time on board", Sky)
                Big(NexusFormat.number(hours, if (hours < 10) 1 else 0), Sky)
                Headline("hours on trains", Sky)
                Body(
                    if (hours >= 48) "Over ${NexusFormat.number(hours / 24, 1)} full days of watching the country go by."
                    else "Enough for ${NexusFormat.number((hours / 2).coerceAtLeast(1.0))} feature films — or one very good book.",
                    Sky,
                )
            }
        },
        {
            StoryCard(Amber) {
                Kicker("Delays", Amber)
                Big("${NexusFormat.number(stats.delayMinutes)} min", Amber)
                Headline("late in total", Amber)
                stats.punctualShare?.let { Body("${NexusFormat.percent(it)} of your trips arrived less than 6 minutes late.", Amber) }
                if (stats.maxDelayMinutes > 0) Body("The longest wait: +${NexusFormat.number(stats.maxDelayMinutes)} min.", Amber)
            }
        },
        if (stats.topRoute != null || stats.topStation != null) {
            {
                StoryCard(Mint, illustration = R.drawable.illus_station) {
                    Kicker("Favourites", Mint)
                    stats.topRoute?.let {
                        Headline("${it.from} ↔ ${it.to}", Mint)
                        Body("Your route of the year, ${it.count} ${if (it.count == 1) "time" else "times"}.", Mint)
                    }
                    stats.topStation?.let {
                        Spacer(Modifier.height(8.dp))
                        Headline(it.name, Mint)
                        Body("The station you passed through most: ${it.count} times.", Mint)
                    }
                }
            }
        } else null,
        if (stats.products.isNotEmpty()) {
            { StoryCard(Sky) { ProductSplit(stats.products) } }
        } else null,
        if (stats.byMonth.isNotEmpty()) {
            { StoryCard(Night) { MonthChart(stats, year) } }
        } else null,
    )
}

@Composable
private fun StoryCard(tone: CardTone, illustration: Int? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = tone.background,
        contentColor = tone.content,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            illustration?.let { Illustration(it, height = 240.dp, corner = 0.dp) }
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                content = content,
            )
        }
    }
}

@Composable
private fun Kicker(text: String, tone: CardTone) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp), color = tone.accent)

@Composable
private fun Big(text: String, tone: CardTone) =
    Text(text, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 64.sp).tabular(), color = tone.content)

@Composable
private fun Headline(text: String, tone: CardTone) =
    Text(text, style = MaterialTheme.typography.headlineSmall, color = tone.content)

@Composable
private fun Body(text: String, tone: CardTone) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = tone.content.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp))

@Composable
private fun ColumnScope.ProductSplit(products: Map<String, Int>) {
    val tone = Sky
    Kicker("What you rode", tone)
    Headline("${products.values.sum()} rides", tone)
    Spacer(Modifier.height(12.dp))
    val top = products.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    products.entries.sortedByDescending { it.value }.take(7).forEach { (product, count) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
            Text(product, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
            Box(
                Modifier
                    .weight(count.toFloat() / top)
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (product in setOf("ICE", "IC", "EC")) AbeonaColors.Ink else AbeonaColors.Green),
            )
            if (count < top) Spacer(Modifier.weight(1f - count.toFloat() / top))
            Text("$count", style = MaterialTheme.typography.titleSmall.tabular(), modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun ColumnScope.MonthChart(stats: Statistics, year: Int) {
    val tone = Night
    Kicker("Month by month", tone)
    val busiest = stats.byMonth.maxByOrNull { it.trips }
    Headline(
        busiest?.let { "Busiest: ${Month.of(it.month.takeLast(2).toInt()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)}" } ?: "$year",
        tone,
    )
    busiest?.let { Body("${it.trips} trips, ${NexusFormat.km(it.km)}.", tone) }
    Spacer(Modifier.height(16.dp))
    val points = (1..12).map { month ->
        val stat = stats.byMonth.firstOrNull { it.month == "%d-%02d".format(year, month) }
        ChartPoint(month.toDouble(), (stat?.trips ?: 0).toDouble())
    }
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
        // The card is light in both themes, so the chart's labels are too.
        MaterialTheme(colorScheme = AbeonaPalette.light) { NexusChart(
            series = listOf(ChartSeries("Trips", points, AbeonaColors.Green, style = SeriesStyle.Bars)),
            xFormatter = { Month.of(it.toInt().coerceIn(1, 12)).getDisplayName(TextStyle.NARROW, Locale.ENGLISH) },
            yFormatter = { NexusFormat.number(it) },
            xTicks = (1..12).map { it.toDouble() },
            includeZero = true,
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) }
    }
}

/** Under the stories: what came back this year and what is still to claim; opens the claims list. */
@Composable
private fun ClaimsBar(overview: ClaimsOverview, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Euro, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Compensation", style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        "${io.github.numynox.nexus.abeona.ui.common.TripFormat.price(overview.earnedCents)} earned back",
                        overview.toClaim.size.takeIf { it > 0 }?.let { "$it to claim" },
                        overview.waiting.size.takeIf { it > 0 }?.let { "$it waiting" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}
