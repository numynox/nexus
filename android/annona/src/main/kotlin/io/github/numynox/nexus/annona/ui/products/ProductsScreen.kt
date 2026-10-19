package io.github.numynox.nexus.annona.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.Category
import io.github.numynox.nexus.annona.data.Product
import io.github.numynox.nexus.annona.ui.AppViewModel
import io.github.numynox.nexus.annona.ui.Navigator
import io.github.numynox.nexus.annona.ui.ScanMode
import io.github.numynox.nexus.annona.ui.common.ExpiryBadge
import io.github.numynox.nexus.annona.ui.common.ProductImage
import io.github.numynox.nexus.annona.ui.common.StockMenu
import io.github.numynox.nexus.annona.ui.common.categoryIcon
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.loadable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Catalogue(val products: List<Product>, val categories: List<Category>)

class ProductsViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val _catalogue = MutableStateFlow<Loadable<Catalogue>>(Loadable.Loading)
    val catalogue: StateFlow<Loadable<Catalogue>> = _catalogue.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_catalogue.value !is Loadable.Ready) _catalogue.value = Loadable.Loading
            _catalogue.value = loadable {
                coroutineScope {
                    val products = async { repository.products() }
                    val categories = async { repository.categories() }
                    Catalogue(products.await(), categories.await())
                }
            }
        }
    }
}

/** The shared catalogue: what is in stock first, by soonest expiry; then everything ever bought. */
@Composable
fun ProductsScreen(app: AppViewModel, navigator: Navigator) {
    val viewModel = containerViewModel { ProductsViewModel(it) }
    val catalogue by viewModel.catalogue.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Long?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(version) { viewModel.load() }
    LaunchedEffect(catalogue) { if (catalogue !is Loadable.Loading) refreshing = false }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Products") }) },
        floatingActionButton = {
            StockMenu(
                onScanAdd = { navigator.scan(ScanMode.Add) },
                onScanConsume = { navigator.scan(ScanMode.Consume) },
                onAddByName = { navigator.add() },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; viewModel.load() },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            when (val state = catalogue) {
                Loadable.Loading -> LoadingState()
                is Loadable.Failed -> ErrorState(state.message, onRetry = viewModel::load)
                is Loadable.Ready -> {
                    val needle = query.trim().lowercase()
                    val shown = state.value.products.filter { product ->
                        (category == null || product.categoryId == category) &&
                            (needle.isEmpty() || product.displayName.lowercase().contains(needle) || product.ean?.contains(needle) == true)
                    }
                    val (stocked, empty) = shown.partition { it.stock.isNotEmpty() }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "search") {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                                },
                                placeholder = { Text("Name, brand or barcode") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item(key = "categories") { CategoryChips(state.value.categories, category) { category = it } }
                        if (shown.isEmpty()) {
                            item(key = "none") {
                                EmptyState(Icons.Rounded.ShoppingBasket, "No products", if (needle.isEmpty() && category == null) "Products appear here once something has been added." else "Nothing matches that.")
                            }
                        }
                        if (stocked.isNotEmpty()) {
                            item(key = "in-stock") { SectionHeader("In stock", supporting = "${stocked.size} products, soonest to expire first") }
                            items(stocked, key = { it.id }) { ProductRow(it, onClick = { navigator.product(it.id) }, Modifier.animateItem()) }
                        }
                        if (empty.isNotEmpty()) {
                            item(key = "out-of-stock") { SectionHeader("Not in stock", supporting = "Bought before — scan them again to restock") }
                            items(empty, key = { it.id }) { ProductRow(it, onClick = { navigator.product(it.id) }, Modifier.animateItem()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(categories: List<Category>, selected: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") }) }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selected == category.id,
                onClick = { onSelect(if (selected == category.id) null else category.id) },
                label = { Text(category.name) },
                leadingIcon = { Icon(categoryIcon(category.icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

@Composable
fun ProductRow(product: Product, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).alpha(if (product.stock.isEmpty()) 0.7f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProductImage(product.imageUrl, product.category, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(product.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        product.quantity,
                        product.category?.name,
                        when (product.stock.size) {
                            0 -> null
                            1 -> "1 in stock"
                            else -> "${product.stock.size} in stock"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (product.stock.isNotEmpty()) ExpiryBadge(product.earliestExpiration)
        }
    }
}
