package io.github.numynox.nexus.vibilia.ui.logbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalCarWash
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.theme.NexusThemeDefaults
import io.github.numynox.nexus.core.ui.theme.tabular
import io.github.numynox.nexus.vibilia.data.DistanceMonth
import io.github.numynox.nexus.vibilia.ui.AppViewModel
import io.github.numynox.nexus.vibilia.ui.common.CarSwitcher
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.vibilia.ui.common.Tag
import io.github.numynox.nexus.vibilia.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.valueOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The web app's Vehicle Logs: every refuel and expense of a car, newest first. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogbookScreen(app: AppViewModel) {
    val viewModel = containerViewModel { LogbookViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cars by app.cars.collectAsStateWithLifecycle()
    val car by app.selectedCar.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(car?.id, version) { car?.let { viewModel.load(it.id, version) } }
    BackHandler(enabled = menuOpen) { menuOpen = false }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                CarSwitcher(cars.valueOrNull.orEmpty(), car, onSelect = { app.selectCar(it.id) })
            })
        },
        floatingActionButton = {
            val selected = car ?: return@Scaffold
            FloatingActionButtonMenu(
                expanded = menuOpen,
                button = {
                    ToggleFloatingActionButton(checked = menuOpen, onCheckedChange = { menuOpen = it }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = if (menuOpen) "Close" else "Add entry",
                            modifier = Modifier.graphicsLayer { rotationZ = checkedProgress * 135f },
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        menuOpen = false
                        app.newExpense(selected.id)
                    },
                    icon = { Icon(Icons.Rounded.ReceiptLong, contentDescription = null) },
                    text = { Text("Expense") },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        menuOpen = false
                        app.newRefuel(selected.id)
                    },
                    icon = { Icon(Icons.Rounded.LocalGasStation, contentDescription = null) },
                    text = { Text("Refuel") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                cars is Loadable.Ready && car == null -> EmptyState(
                    Icons.Rounded.DirectionsCar,
                    "No vehicles yet",
                    "Add one under Settings to start your logbook.",
                )
                car == null -> LoadingState()
                else -> when (val logbook = state) {
                    Loadable.Loading -> LoadingState()
                    is Loadable.Failed -> ErrorState(logbook.message, onRetry = { viewModel.load(car!!.id, version, force = true) })
                    is Loadable.Ready -> PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = { viewModel.load(car!!.id, version, force = true) },
                    ) {
                        if (logbook.value.months.isEmpty()) {
                            EmptyState(
                                Icons.Rounded.History,
                                "No entries yet",
                                "Log your first refuel or expense with the + button.",
                            )
                        } else {
                            LogbookList(logbook.value, onRefuel = app::edit, onExpense = app::edit)
                        }
                    }
                }
            }
        }
    }
}

/** "≈ 612 km · 14 h 20 min driven"; ≈ while part of it is still the model's estimate. */
private fun monthDistanceLabel(month: DistanceMonth): String? {
    val km = month.kmDriven + month.estimatedKm
    return listOfNotNull(
        km.takeIf { it >= 1 }?.let { (if (month.estimatedKm > 0) "≈ " else "") + NexusFormat.km(it) },
        month.drivingHours.takeIf { it * 60 >= 1 }?.let { "${NexusFormat.duration(it)} driven" },
    ).joinToString(" · ").ifEmpty { null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LogbookList(
    logbook: Logbook,
    onRefuel: (io.github.numynox.nexus.vibilia.data.RefuelEvent) -> Unit,
    onExpense: (io.github.numynox.nexus.vibilia.data.CarExpense) -> Unit,
) {
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        logbook.months.forEach { month ->
            stickyHeader(key = month.month.toString()) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(monthFormat.format(month.month), style = MaterialTheme.typography.titleSmall)
                            month.distance?.let(::monthDistanceLabel)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            NexusFormat.euro(month.spent),
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(month.entries, key = { it.key }) { entry ->
                when (entry) {
                    is LogEntry.Refuel -> RefuelCard(entry, logbook.averageConsumption, Modifier.animateItem()) { onRefuel(entry.event) }
                    is LogEntry.Expense -> ExpenseCard(entry, Modifier.animateItem()) { onExpense(entry.expense) }
                }
            }
        }
    }
}

@Composable
private fun EntryIcon(icon: ImageVector, container: Color, content: Color) {
    Box(
        Modifier
            .size(44.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content)
    }
}

@Composable
private fun RefuelCard(entry: LogEntry.Refuel, averageConsumption: Double?, modifier: Modifier, onClick: () -> Unit) {
    val event = entry.event
    NexusCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EntryIcon(Icons.Rounded.LocalGasStation, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(NexusFormat.euro(event.totalPrice), style = MaterialTheme.typography.titleMedium.tabular(), modifier = Modifier.weight(1f))
                    Text(
                        NexusFormat.dateTime(event.fueledAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        event.fuelStation?.label?.takeIf { it.isNotBlank() } ?: "Other fuel station",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fact(NexusFormat.liters(event.liters, 2))
                    Fact("${NexusFormat.number(event.pricePerLiter, 3)} €/L")
                    Fact(NexusFormat.km(event.mileage.toDouble()))
                    entry.kmSincePrevious?.let { Fact("+${NexusFormat.km(it.toDouble())}") }
                    entry.consumption?.let { consumption ->
                        val good = averageConsumption == null || consumption <= averageConsumption
                        Tag(
                            "${NexusFormat.number(consumption, 1)} L/100km",
                            if (good) NexusThemeDefaults.extendedColors.success else NexusThemeDefaults.extendedColors.warning,
                        )
                    }
                    if (!event.isFullRefuel) Tag("Partial", MaterialTheme.colorScheme.tertiary)
                    if (event.missedPreviousRefuel) Tag("After a missed refuel", MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Fact(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.tabular(),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** An icon per category, so the log can be scanned without reading. */
fun expenseIcon(category: String): ImageVector = when (category) {
    "Service", "Maintenance" -> Icons.Rounded.Build
    "Repairs" -> Icons.Rounded.Handyman
    "Insurance" -> Icons.Rounded.Shield
    "Tax" -> Icons.Rounded.AccountBalance
    "Parking" -> Icons.Rounded.LocalParking
    "Toll" -> Icons.Rounded.Toll
    "Vehicle Wash" -> Icons.Rounded.LocalCarWash
    "Accessories" -> Icons.Rounded.ShoppingBag
    else -> Icons.Rounded.ReceiptLong
}

@Composable
private fun ExpenseCard(entry: LogEntry.Expense, modifier: Modifier, onClick: () -> Unit) {
    val expense = entry.expense
    NexusCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EntryIcon(expenseIcon(expense.category), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(NexusFormat.euro(expense.amount), style = MaterialTheme.typography.titleMedium.tabular(), modifier = Modifier.weight(1f))
                    Text(
                        NexusFormat.date(expense.expensedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(expense.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fact(expense.category)
                    expense.mileage?.takeIf { it > 0 }?.let { Fact(NexusFormat.km(it.toDouble())) }
                }
                expense.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
