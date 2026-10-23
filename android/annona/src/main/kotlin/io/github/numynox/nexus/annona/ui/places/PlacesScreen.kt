package io.github.numynox.nexus.annona.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.Place
import io.github.numynox.nexus.annona.data.PlaceProduct
import io.github.numynox.nexus.annona.ui.AppViewModel
import io.github.numynox.nexus.annona.ui.Navigator
import io.github.numynox.nexus.annona.ui.ScanMode
import io.github.numynox.nexus.annona.ui.common.CategoryAvatar
import io.github.numynox.nexus.annona.ui.common.ExpiryBadge
import io.github.numynox.nexus.annona.ui.common.StockMenu
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.PlaceholderList
import io.github.numynox.nexus.core.ui.components.rememberPullRefresh
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.loadable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlacesViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val _places = MutableStateFlow<Loadable<List<Place>>>(Loadable.Loading)
    val places: StateFlow<Loadable<List<Place>>> = _places.asStateFlow()

    fun load(): Job = viewModelScope.launch {
        if (_places.value !is Loadable.Ready) _places.value = Loadable.Loading
        _places.value = loadable { repository.places() }
    }
}

/** The web's Locations page: what is in the fridge, the freezer, the pantry. */
@Composable
fun PlacesScreen(app: AppViewModel, navigator: Navigator) {
    val viewModel = containerViewModel { PlacesViewModel(it) }
    val places by viewModel.places.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val pull = rememberPullRefresh(viewModel::load)
    LaunchedEffect(version) { viewModel.load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Places") }) },
        floatingActionButton = {
            StockMenu(
                onScanAdd = { navigator.scan(ScanMode.Add) },
                onScanConsume = { navigator.scan(ScanMode.Consume) },
                onAddByName = { navigator.add() },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = pull.refreshing,
            onRefresh = pull.onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            when (val state = places) {
                Loadable.Loading -> PlaceholderList(count = 4, modifier = Modifier.padding(horizontal = 16.dp), lines = 1)
                is Loadable.Failed -> ErrorState(state.message, onRetry = viewModel::load)
                is Loadable.Ready -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.value.isEmpty()) {
                        item { EmptyState(Icons.Rounded.Place, "Nothing stored", "Items show up here by where they are kept. Add places in Settings.") }
                    }
                    items(state.value, key = { it.locationId ?: -1 }) { place ->
                        val count = place.products.sumOf { it.items }
                        val expired = place.products.sumOf { it.expired }
                        Column(Modifier.animateItem()) {
                            SectionHeader(
                                place.name,
                                supporting = listOfNotNull(if (count == 1) "1 item" else "$count items", expired.takeIf { it > 0 }?.let { "$it expired" }).joinToString(" · "),
                            )
                            NexusCard {
                                place.products.forEachIndexed { index, product ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    PlaceRow(product) { navigator.product(product.productId) }
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
private fun PlaceRow(product: PlaceProduct, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryAvatar(product.category, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(product.brand, product.name).joinToString(" "), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(product.quantity, "× ${product.items}").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpiryBadge(product.earliest)
    }
}
