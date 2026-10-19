package io.github.numynox.nexus.annona.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.Item
import io.github.numynox.nexus.annona.data.Product
import io.github.numynox.nexus.annona.ui.AppViewModel
import io.github.numynox.nexus.annona.ui.Navigator
import io.github.numynox.nexus.annona.ui.common.ExpiryBadge
import io.github.numynox.nexus.annona.ui.common.ProductImage
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConsumeViewModel(container: AppContainer, private val productId: Long) : ViewModel() {
    private val repository = container.repository
    private val _state = MutableStateFlow<Loadable<Pair<Product, List<Item>>>>(Loadable.Loading)
    val state: StateFlow<Loadable<Pair<Product, List<Item>>>> = _state.asStateFlow()
    private val _used = MutableStateFlow<Set<Long>>(emptySet())
    val used: StateFlow<Set<Long>> = _used.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = loadable {
                val product = repository.product(productId) ?: error("This product no longer exists.")
                product to repository.itemsForProduct(productId).filter { !it.isConsumed }
            }
        }
    }

    fun toggle(item: Item, onError: (String) -> Unit, onDone: () -> Unit) {
        val consume = item.id !in _used.value
        _used.update { if (consume) it + item.id else it - item.id }
        viewModelScope.launch {
            runCatching { repository.setConsumed(item.id, consume) }
                .onSuccess { onDone() }
                .onFailure {
                    _used.update { set -> if (consume) set - item.id else set + item.id }
                    onError(it.describe())
                }
        }
    }
}

/** The web's Consume page: the scanned product's packages, oldest first, one tap each. */
@Composable
fun ConsumeScreen(app: AppViewModel, productId: Long, navigator: Navigator) {
    val viewModel = containerViewModel(key = "consume-$productId") { ConsumeViewModel(it, productId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val used by viewModel.used.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Use up") },
                navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = navigator.back) { Text("Done") } },
            )
        },
    ) { padding ->
        when (val current = state) {
            Loadable.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is Loadable.Failed -> ErrorState(current.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is Loadable.Ready -> {
                val (product, items) = current.value
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProductImage(product.imageUrl, product.category, size = 64.dp)
                            Column {
                                Text(product.displayName, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "${items.size - used.size} of ${items.size} left — the oldest first",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (items.isEmpty()) item { EmptyState(Icons.Rounded.Restaurant, "None in stock", "Nothing of this product is waiting to be used up.") }
                    items(items, key = { it.id }) { item ->
                        val done = item.id in used
                        NexusCard(modifier = Modifier.alpha(if (done) 0.55f else 1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.expirationDate?.let(NexusFormat::date) ?: "No date", style = MaterialTheme.typography.titleSmall)
                                    Text(item.locationName ?: "No location", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!done) ExpiryBadge(item.expirationDate)
                                FilledTonalButton(onClick = { viewModel.toggle(item, onError = app::say) { app.changed() } }) {
                                    Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(if (done) "Undo" else "Use up", modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
