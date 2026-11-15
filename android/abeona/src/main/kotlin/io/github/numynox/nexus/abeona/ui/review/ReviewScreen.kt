package io.github.numynox.nexus.abeona.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.abeona.AppContainer
import io.github.numynox.nexus.abeona.data.ClaimsOverview
import io.github.numynox.nexus.abeona.data.Statistics
import io.github.numynox.nexus.abeona.data.TravelMapLine
import io.github.numynox.nexus.abeona.ui.AbeonaType
import io.github.numynox.nexus.abeona.ui.AppViewModel
import io.github.numynox.nexus.abeona.ui.Navigator
import io.github.numynox.nexus.abeona.ui.brand
import io.github.numynox.nexus.abeona.ui.card
import io.github.numynox.nexus.abeona.ui.hairline
import io.github.numynox.nexus.abeona.ui.muted
import io.github.numynox.nexus.abeona.ui.burgundy
import io.github.numynox.nexus.abeona.ui.watch
import io.github.numynox.nexus.abeona.ui.well
import io.github.numynox.nexus.abeona.ui.common.EmptyState
import io.github.numynox.nexus.abeona.ui.common.Fact
import io.github.numynox.nexus.abeona.ui.common.PrimaryButton
import io.github.numynox.nexus.abeona.ui.common.Segmented
import io.github.numynox.nexus.abeona.ui.common.SurfaceCard
import io.github.numynox.nexus.abeona.ui.common.TripFormat
import io.github.numynox.nexus.abeona.ui.common.containerViewModel
import io.github.numynox.nexus.abeona.ui.scene.Scene
import io.github.numynox.nexus.abeona.ui.scene.SceneSize
import io.github.numynox.nexus.abeona.ui.scene.SceneState
import io.github.numynox.nexus.abeona.ui.trip.TripMap
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.loadable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
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

    private val _map = MutableStateFlow<List<TravelMapLine>>(emptyList())
    /** The year's lines for the map card; empty until loaded, or when it could not load. */
    val map: StateFlow<List<TravelMapLine>> = _map.asStateFlow()

    private val _claims = MutableStateFlow<ClaimsOverview?>(null)
    /** This year's claims, for the bar under the stories; null until loaded or when it could not load. */
    val claims: StateFlow<ClaimsOverview?> = _claims.asStateFlow()

    fun load(year: Int = _year.value) = viewModelScope.launch {
        _year.value = year
        _stats.value = Loadable.Loading
        launch { _claims.value = runCatching { container.repository.claimsOverview(LocalDate.now().year) }.getOrNull() }
        launch { _map.value = runCatching { container.repository.travelMap(year) }.getOrDefault(emptyList()) }
        _stats.value = loadable { container.repository.statistics(year) }
    }
}

/** A story card's height: the scene's 200 and room for the figure, its line and the foot, as on the web. */
private val StoryHeight = 460.dp

private const val EARTH_KM = 40_075.0
private const val HAMBURG_MUNICH_KM = 775.0

/** Today at [hour]: the sky a story card's scene is drawn under, as on the web. */
private fun todayAt(hour: Int): Instant = LocalDate.now().atTime(LocalTime.of(hour, 0)).atZone(ZoneId.systemDefault()).toInstant()

/** The year on rails as a deck of story cards, swiped through one at a time. */
@Composable
fun ReviewScreen(app: AppViewModel, navigator: Navigator) {
    val viewModel = containerViewModel { ReviewViewModel(it) }
    val year by viewModel.year.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val claims by viewModel.claims.collectAsStateWithLifecycle()
    val map by viewModel.map.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val thisYear = LocalDate.now().year

    LaunchedEffect(version) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Year in review") },
                actions = {
                    Segmented(listOf(thisYear - 1, thisYear), year, { viewModel.load(it) }, { "$it" }, Modifier.padding(end = 12.dp), description = "Year")
                },
            )
        },
    ) { padding ->
        // The stories, then the claims bar right under them; scrolls on a short screen.
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                when (val state = stats) {
                    Loadable.Loading -> LoadingState(Modifier.fillMaxWidth().height(StoryHeight))
                    is Loadable.Failed -> ErrorState(state.message, onRetry = { viewModel.load() })
                    is Loadable.Ready -> if (state.value.trips == 0) {
                        // Trips count here once they have arrived.
                        EmptyState(
                            if (year == thisYear) "Your year is still ahead" else "No trips in $year",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            PrimaryButton("Plan a trip", navigator::search, icon = Icons.Rounded.Search)
                        }
                    } else {
                        StoryPager(state.value, year, map, thisYear)
                    }
                }
            }
            claims?.let { ClaimsBar(it, onClick = navigator::claims) }
        }
    }
}

@Composable
private fun StoryPager(stats: Statistics, year: Int, map: List<TravelMapLine>, thisYear: Int) {
    val cards = storyCards(stats, year, map, thisYear)
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            cards[page]()
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
            repeat(cards.size) { index ->
                val selected = index == pager.currentPage
                Box(
                    Modifier
                        .clickable(interactionSource = null, indication = null) { scope.launch { pager.animateScrollToPage(index) } }
                        .semantics { contentDescription = "Story ${index + 1}" }
                        .padding(horizontal = 3.dp, vertical = 8.dp)
                        .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) burgundy.container else hairline),
                )
            }
        }
    }
}

private fun storyCards(stats: Statistics, year: Int, map: List<TravelMapLine>, thisYear: Int): List<@Composable () -> Unit> {
    val hours = stats.minutesOnTrain / 60.0
    return listOfNotNull(
        {
            StoryCard(top = { Scene(SceneState.Idle, size = SceneSize.Full) }) {
                Figure(NexusFormat.number(stats.trips.toDouble()))
                Line("${if (stats.trips == 1) "trip" else "trips"} by train in $year${if (year == thisYear) " so far" else ""}")
                Foot {
                    Fact(Icons.Rounded.Train, "Rides", NexusFormat.number(stats.rides.toDouble()))
                    Fact(Icons.Rounded.Place, "Stations", NexusFormat.number(stats.stations.toDouble()))
                }
            }
        },
        {
            StoryCard(top = { Scene(SceneState.Riding, size = SceneSize.Full, at = todayAt(11)) }) {
                Figure(NexusFormat.number(stats.km), "km")
                Line(
                    if (stats.km >= EARTH_KM) {
                        "That is ${NexusFormat.number(stats.km / EARTH_KM, 1)} times around the Earth."
                    } else {
                        "That is ${NexusFormat.number(stats.km / EARTH_KM * 100)} % of the way around the Earth, or Hamburg to Munich ${NexusFormat.number(stats.km / HAMBURG_MUNICH_KM, 1)} times."
                    },
                )
                Foot {
                    Small(
                        stats.longestTrip?.let { "Longest trip ${NexusFormat.km(it.km)} · ${it.from} → ${it.to}" }
                            ?: "Longest trip ${NexusFormat.km(stats.longestKm)}",
                    )
                }
            }
        },
        if (map.isNotEmpty()) {
            {
                StoryCard(top = { TripMap(map.map { it.toLeg() }, Modifier.fillMaxSize()) }, topWeight = true) {
                    Figure(NexusFormat.number(map.size.toDouble()))
                    Line(
                        "${if (map.size == 1) "stretch" else "stretches"} of track" +
                            if (stats.countries.size > 1) ", in ${stats.countries.size} countries" else "",
                    )
                }
            }
        } else null,
        if (stats.co2SavedKg > 0) {
            {
                StoryCard(top = { Scene(SceneState.Idle, size = SceneSize.Full, at = todayAt(9)) }) {
                    Figure(NexusFormat.number(stats.co2SavedKg, if (stats.co2SavedKg < 10) 1 else 0), "kg CO₂")
                    Line("saved against the car." + (trees(stats.co2SavedKg)?.let { " $it" } ?: ""))
                    Foot {
                        Small(
                            "By train ${NexusFormat.number(stats.co2TrainKg)} kg · by car ${NexusFormat.number(stats.co2CarKg)} kg · per traveller. " +
                                (stats.co2Factors?.source ?: "Approximate."),
                        )
                    }
                }
            }
        } else null,
        if (stats.countries.isNotEmpty()) {
            {
                StoryCard(top = { Scene(SceneState.Parked, size = SceneSize.Full, at = todayAt(15)) }) {
                    Figure("${stats.countries.size}")
                    Line(if (stats.countries.size == 1) "country by train" else "countries by train")
                    Foot {
                        stats.countries.take(6).forEach { code ->
                            Surface(color = well.container, contentColor = well.content, shape = CircleShape) {
                                Text(
                                    "${flag(code)} ${Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { code }}",
                                    style = AbeonaType.meta,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else null,
        {
            StoryCard(top = { Scene(SceneState.Riding, size = SceneSize.Full, at = todayAt(23)) }) {
                Figure(NexusFormat.number(hours, if (hours < 10) 1 else 0), "h")
                Line(
                    "on board. " + if (hours >= 48) "Over ${NexusFormat.number(hours / 24, 1)} full days of watching the country go by."
                    else "Enough for ${NexusFormat.number((hours / 2).coerceAtLeast(1.0))} feature films — or one very good book.",
                )
            }
        },
        {
            val share = stats.punctualShare
            StoryCard(top = { Punctuality(share) }) {
                Figure(share?.let { NexusFormat.number(it * 100) } ?: "–", "%")
                Line("of your trips arrived less than 6 minutes late")
                Foot {
                    Small("Late in total ${NexusFormat.number(stats.delayMinutes)} min")
                    if (stats.maxDelayMinutes > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Small("Longest wait ")
                            Text("+${NexusFormat.number(stats.maxDelayMinutes)}", style = AbeonaType.meta.copy(fontWeight = FontWeight.Bold), color = watch.soft.content)
                        }
                    }
                }
            }
        },
        if (stats.topRoute != null || stats.topStation != null) {
            {
                StoryCard(top = { Scene(SceneState.Arrived, size = SceneSize.Full, at = todayAt(13)) }) {
                    val route = stats.topRoute
                    val station = stats.topStation
                    if (route != null) {
                        Figure("${route.count}", "×")
                        Line("${route.from} ↔ ${route.to}, your route of the year")
                        if (station != null) Foot { Small("The station you passed through most: ${station.name}, ${station.count} times") }
                    } else if (station != null) {
                        Figure("${station.count}")
                        Line("times through ${station.name}, the station you passed through most")
                    }
                }
            }
        } else null,
        if (stats.products.isNotEmpty()) {
            {
                val sorted = stats.products.entries.sortedByDescending { it.value }
                StoryCard(top = { ProductBars(sorted.take(7).map { it.key to it.value }) }) {
                    Figure(NexusFormat.number(sorted.first().value.toDouble()))
                    Line("${if (sorted.first().value == 1) "ride" else "rides"} by ${sorted.first().key}, what you rode most")
                    Foot { Small("${stats.products.values.sum()} rides in all") }
                }
            }
        } else null,
        if (stats.byMonth.isNotEmpty()) {
            {
                val busiest = stats.byMonth.maxByOrNull { it.trips }
                StoryCard(top = { MonthBars(stats, year) }) {
                    Figure(NexusFormat.number((busiest?.trips ?: 0).toDouble()))
                    Line(
                        busiest?.let {
                            "${if (it.trips == 1) "trip" else "trips"} in ${Month.of(it.month.takeLast(2).toInt()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, your busiest month"
                        } ?: "$year",
                    )
                    busiest?.let { Foot { Small(NexusFormat.km(it.km)) } }
                }
            }
        } else null,
    )
}

/** "DE" → 🇩🇪 */
private fun flag(code: String): String =
    if (code.length == 2 && code.all { it in 'A'..'Z' }) {
        code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    } else ""

/** A beech takes up roughly 12.5 kg of CO₂ a year (an often-quoted rule of thumb); the web says the same. */
private fun trees(kg: Double): String? {
    val n = Math.round(kg / 12.5).toInt()
    return if (n >= 1) "About what ${NexusFormat.number(n.toDouble())} ${if (n == 1) "beech tree takes" else "beech trees take"} up in a year." else null
}

/**
 * A story card: the scene or a chart on top (200, or the room left for the
 * map with [topWeight]), then one figure, one line, and what else it knew
 * small at the bottom. Card on paper in both themes; the scene is the picture.
 */
@Composable
private fun StoryCard(top: @Composable () -> Unit, topWeight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(StoryHeight),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(if (topWeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(SceneSize.Full.height.dp)) { top() }
            Column(
                Modifier.fillMaxWidth().then(if (topWeight) Modifier else Modifier.weight(1f)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

/** The card's one figure, its unit beside it smaller. */
@Composable
private fun Figure(value: String, unit: String? = null) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = AbeonaType.numeral, modifier = Modifier.alignByBaseline())
        if (unit != null) Text(unit, style = AbeonaType.title, color = muted, modifier = Modifier.alignByBaseline())
    }
}

/** The one line under the figure. */
@Composable
private fun Line(text: String) = Text(text, style = AbeonaType.body)

/** What else the card knew, small, at the card's foot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Foot(content: @Composable () -> Unit) {
    Spacer(Modifier.weight(1f))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Small(text: String) = Text(text, style = AbeonaType.meta, color = muted)

/** The share on time as a ring: burgundy on the well. */
@Composable
private fun Punctuality(share: Double?) {
    val ring = burgundy.container
    val track = well.container
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(144.dp)) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val box = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            drawArc(ring, -90f, 360f * (share ?: 0.0).toFloat().coerceIn(0f, 1f), false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/** Bars in burgundy: the strongest full, the others paler, so the eye goes to the top one. */
@Composable
private fun ProductBars(products: List<Pair<String, Int>>) {
    val top = products.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val fill = burgundy.container
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        products.forEach { (product, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product, style = AbeonaType.meta.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(48.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(well.container)) {
                    Box(
                        Modifier.fillMaxWidth(count.toFloat() / top).fillMaxHeight().clip(CircleShape)
                            .background(if (count == top) fill else fill.copy(alpha = 0.4f)),
                    )
                }
                Text("$count", style = AbeonaType.meta, color = muted, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
            }
        }
    }
}

/** Trips per month as columns, January to December; the busiest full burgundy, the rest paler. */
@Composable
private fun MonthBars(stats: Statistics, year: Int) {
    val counts = (1..12).map { month -> stats.byMonth.firstOrNull { it.month == "%d-%02d".format(year, month) }?.trips ?: 0 }
    val top = counts.max().coerceAtLeast(1)
    val fill = burgundy.container
    Row(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
            .semantics { contentDescription = "Trips per month" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { index, count ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (count > 0) Text("$count", style = AbeonaType.meta, color = muted, maxLines = 1)
                    Box(
                        Modifier.padding(top = 2.dp).fillMaxWidth().fillMaxHeight((count.toFloat() / top).coerceAtLeast(0.02f) * 0.85f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (count == top) fill else if (count > 0) fill.copy(alpha = 0.4f) else hairline),
                    )
                }
                Text(
                    Month.of(index + 1).getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = AbeonaType.meta,
                    color = muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Under the stories, a quiet row: what came back this year and what is still
 * to claim; opens the claims list.
 */
@Composable
private fun ClaimsBar(overview: ClaimsOverview, onClick: () -> Unit) {
    SurfaceCard(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        onClick = onClick,
        padding = PaddingValues(start = 16.dp, end = 8.dp),
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Compensation", style = AbeonaType.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Fact(Icons.Rounded.Euro, "Earned back", TripFormat.price(overview.earnedCents))
            overview.toClaim.size.takeIf { it > 0 }?.let { Fact(Icons.Rounded.Event, "To claim", "$it", color = brand) }
            overview.waiting.size.takeIf { it > 0 }?.let { Fact(Icons.Rounded.HourglassEmpty, "Waiting for DB", "$it") }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = muted)
        }
    }
}
