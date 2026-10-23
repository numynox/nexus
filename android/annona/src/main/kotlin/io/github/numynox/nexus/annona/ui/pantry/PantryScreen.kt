package io.github.numynox.nexus.annona.ui.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.annona.data.DashboardSummary
import io.github.numynox.nexus.annona.data.ShelfLife
import io.github.numynox.nexus.annona.data.shelfLifeOf
import io.github.numynox.nexus.annona.ui.AppViewModel
import io.github.numynox.nexus.annona.ui.Navigator
import io.github.numynox.nexus.annona.ui.ScanMode
import io.github.numynox.nexus.annona.ui.common.StockMenu
import io.github.numynox.nexus.annona.ui.common.StockRow
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.rememberPullRefresh
import io.github.numynox.nexus.core.ui.components.MetricCard
import io.github.numynox.nexus.core.ui.components.MetricRow
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.components.UpdateBanner
import io.github.numynox.nexus.core.ui.theme.NexusThemeDefaults
import io.github.numynox.nexus.core.ui.theme.tabular
import kotlinx.coroutines.launch

/** The web's dashboard, turned into "what to eat first": the counts, then stock by how soon it goes off. */
@Composable
fun PantryScreen(app: AppViewModel, navigator: Navigator) {
    val viewModel = containerViewModel { PantryViewModel(it) }
    val pantry by viewModel.pantry.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val update by app.update.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pull = rememberPullRefresh(viewModel::load)

    LaunchedEffect(version) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Pantry")
                    (pantry as? Loadable.Ready)?.value?.let {
                        Text(
                            "${it.summary.activeItems} items in stock",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            })
        },
        floatingActionButton = {
            StockMenu(
                onScanAdd = { navigator.scan(ScanMode.Add) },
                onScanConsume = { navigator.scan(ScanMode.Consume) },
                onAddByName = { navigator.add() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = pull.refreshing,
            onRefresh = pull.onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            when (val state = pantry) {
                Loadable.Loading -> PlaceholderList(count = 5, modifier = Modifier.padding(horizontal = 16.dp), lines = 1)
                is Loadable.Failed -> ErrorState(state.message, onRetry = viewModel::load)
                is Loadable.Ready -> {
                    val groups = state.value.items.groupBy { shelfLifeOf(it.expirationDate) }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "update") { UpdateBanner(update) }
                        item(key = "summary") { Summary(state.value.summary) }
                        if (state.value.items.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    icon = Icons.Rounded.Kitchen,
                                    title = "Nothing in stock",
                                    message = "Scan the groceries as you put them away; Annona tells you what to eat first.",
                                    action = {
                                        FilledTonalButton(onClick = { navigator.scan(ScanMode.Add) }) {
                                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                                            Text("Scan to add", modifier = Modifier.padding(start = 8.dp))
                                        }
                                    },
                                )
                            }
                        }
                        ShelfLife.entries.forEach { shelf ->
                            val items = groups[shelf].orEmpty()
                            if (items.isNotEmpty()) {
                                item(key = "head-$shelf") {
                                    SectionHeader(shelf.title, supporting = if (items.size == 1) "1 item" else "${items.size} items", modifier = Modifier.animateItem())
                                }
                                items(items, key = { it.id }) { item ->
                                    StockRow(
                                        item = item,
                                        onClick = { navigator.product(item.productId) },
                                        onConsume = {
                                            viewModel.consume(item, true, onError = { message -> scope.launch { snackbar.showSnackbar(message) } }) {
                                                app.changed()
                                                scope.launch {
                                                    val result = snackbar.showSnackbar("${item.displayName} used up", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        viewModel.consume(item, false, onError = {}) { app.changed() }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(summary: DashboardSummary) {
    val extended = NexusThemeDefaults.extendedColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Use soon",
                value = (summary.expired + summary.within7Days).toDouble(),
                format = { NexusFormat.number(it) },
                icon = Icons.Rounded.Schedule,
                accent = if (summary.expired > 0) MaterialTheme.colorScheme.error else extended.warning,
                rows = listOf(
                    MetricRow("Expired", "${summary.expired}"),
                    MetricRow("Within 7 days", "${summary.within7Days}"),
                    MetricRow("Within 30 days", "${summary.within30Days}"),
                ),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "In stock",
                value = summary.activeItems.toDouble(),
                format = { NexusFormat.number(it) },
                icon = Icons.Rounded.Inventory2,
                accent = MaterialTheme.colorScheme.primary,
                rows = listOf(
                    MetricRow("Products", "${summary.totalProducts}"),
                    MetricRow("No date", "${summary.noExpiration}"),
                    MetricRow("Used up", "${summary.consumed}"),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        if (summary.byLocation.isNotEmpty()) {
            NexusCard {
                Text("Where it is", style = MaterialTheme.typography.titleSmall)
                val top = summary.byLocation.maxOf { it.count }.coerceAtLeast(1)
                summary.byLocation.forEach { place ->
                    Column(Modifier.padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(place.location, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(
                                "${place.count}" + if (place.expired > 0) " · ${place.expired} expired" else "",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = if (place.expired > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { place.count.toFloat() / top },
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
                        )
                    }
                }
            }
        }
    }
}
