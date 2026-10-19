package io.github.numynox.nexus.annona.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.numynox.nexus.annona.data.Item
import io.github.numynox.nexus.annona.data.Nutrition
import io.github.numynox.nexus.annona.data.Product
import io.github.numynox.nexus.annona.data.StorageLocation
import io.github.numynox.nexus.annona.ui.AppViewModel
import io.github.numynox.nexus.annona.ui.Navigator
import io.github.numynox.nexus.annona.ui.common.ExpiryBadge
import io.github.numynox.nexus.annona.ui.common.ExpiryPicker
import io.github.numynox.nexus.annona.ui.common.LocationPicker
import io.github.numynox.nexus.annona.ui.common.ProductFields
import io.github.numynox.nexus.annona.ui.common.ProductFieldsForm
import io.github.numynox.nexus.annona.ui.common.ProductImage
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.NexusFormat
import io.github.numynox.nexus.core.ui.components.EmptyState
import io.github.numynox.nexus.core.ui.components.ErrorState
import io.github.numynox.nexus.core.ui.components.LoadingState
import io.github.numynox.nexus.core.ui.components.NexusCard
import io.github.numynox.nexus.core.ui.components.SectionHeader
import io.github.numynox.nexus.core.ui.theme.tabular

/** One product: what it is, every package of it, and who did what to each. */
@Composable
fun ProductScreen(app: AppViewModel, productId: Long, navigator: Navigator) {
    val viewModel = containerViewModel(key = "product-$productId") { ProductViewModel(it, productId) }
    val page by viewModel.page.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val version by app.dataVersion.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editingItem by remember { mutableStateOf<Item?>(null) }
    var logItem by remember { mutableStateOf<Item?>(null) }
    var editingProduct by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(version) { viewModel.load() }
    LaunchedEffect(deleted) {
        if (deleted) {
            app.changed("Product deleted")
            navigator.back()
        }
    }

    val loaded = (page as? Loadable.Ready)?.value
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded?.product?.displayName ?: "Product", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (loaded != null) {
                        IconButton(onClick = { editingProduct = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit product") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete product") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val state = page) {
            Loadable.Loading -> LoadingState(Modifier.fillMaxSize().padding(padding))
            is Loadable.Failed -> ErrorState(state.message, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            is Loadable.Ready -> {
                val (stock, used) = state.value.items.partition { !it.isConsumed }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") { Header(state.value.product) }
                    item(key = "actions") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { navigator.add(productId = productId) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Add items", modifier = Modifier.padding(start = 6.dp))
                            }
                            FilledTonalButton(onClick = { navigator.consume(productId) }, enabled = stock.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Use up", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    item(key = "stock-head") {
                        SectionHeader("In stock", supporting = if (stock.isEmpty()) "None at the moment" else if (stock.size == 1) "1 item" else "${stock.size} items, soonest first")
                    }
                    if (stock.isEmpty()) {
                        item(key = "no-stock") { EmptyState(Icons.Rounded.Restaurant, "All used up", "Scan or add it again when you buy more.") }
                    }
                    items(stock, key = { it.id }) { item ->
                        ItemCard(
                            item,
                            onConsume = { viewModel.setConsumed(item, true) { app.changed() } },
                            onEdit = { editingItem = item },
                            onLog = { logItem = item; viewModel.openLog(item) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (!state.value.product.nutrition.isEmpty) {
                        item(key = "nutrition") { NutritionCard(state.value.product.nutrition, state.value.product.nutritionSource) }
                    }
                    if (used.isNotEmpty()) {
                        item(key = "used-head") { SectionHeader("Used up", supporting = "Kept so the activity survives") }
                        items(used.take(10), key = { it.id }) { item ->
                            ItemCard(
                                item,
                                onConsume = { viewModel.setConsumed(item, false) { app.changed() } },
                                onEdit = null,
                                onLog = { logItem = item; viewModel.openLog(item) },
                                modifier = Modifier.animateItem().alpha(0.7f),
                            )
                        }
                    }
                }
            }
        }
    }

    editingItem?.let { item ->
        ItemSheet(
            item = item,
            locations = loaded?.locations.orEmpty(),
            onSave = { date, location ->
                editingItem = null
                viewModel.saveItem(item, date, location) { app.changed() }
            },
            onDelete = {
                editingItem = null
                viewModel.deleteItem(item) { app.changed() }
            },
            onDismiss = { editingItem = null },
        )
    }
    logItem?.let { item ->
        val log by viewModel.log.collectAsStateWithLifecycle()
        ActivitySheet(item, log, onComment = { viewModel.comment(item, it) }, onDismiss = { logItem = null })
    }
    if (editingProduct && loaded != null) {
        val product = loaded.product
        ProductSheet(
            initial = ProductFields(product.name, product.brand.orEmpty(), product.quantity.orEmpty(), product.ean.orEmpty(), product.categoryId, product.imageUrl.orEmpty()),
            categories = loaded.categories,
            onSave = {
                editingProduct = false
                viewModel.saveProduct(it) { app.changed() }
            },
            onDismiss = { editingProduct = false },
        )
    }
    if (confirmDelete && loaded != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("Delete ${loaded.product.displayName}?") },
            text = { Text("The product goes from the household's catalogue with every item of it and their activity. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteProduct {} }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(product: Product) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProductImage(product.imageUrl, product.category, size = 96.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            product.brand?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            Text(product.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(product.quantity, product.category?.name).joinToString(" · ").ifBlank { "No category" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            product.ean?.let { Text("Barcode $it", style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ItemCard(item: Item, onConsume: () -> Unit, onEdit: (() -> Unit)?, onLog: () -> Unit, modifier: Modifier = Modifier) {
    NexusCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.expirationDate?.let(NexusFormat::date) ?: "No expiration date", style = MaterialTheme.typography.titleSmall.tabular())
                Text(
                    listOfNotNull(item.locationName ?: "No location", item.createdAt?.let { "added ${NexusFormat.date(it)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!item.isConsumed) ExpiryBadge(item.expirationDate)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.isConsumed) {
                TextButton(onClick = onConsume) {
                    Icon(Icons.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Back in stock", modifier = Modifier.padding(start = 6.dp))
                }
            } else {
                FilledTonalButton(onClick = onConsume) {
                    Icon(Icons.Rounded.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Used up", modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (onEdit != null) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Change", modifier = Modifier.padding(start = 6.dp))
                }
            }
            TextButton(onClick = onLog) {
                Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (item.commentCount > 0) "Activity · ${item.commentCount}" else "Activity", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun NutritionCard(nutrition: Nutrition, source: String?) {
    NexusCard {
        Text("Nutrition per 100 g", style = MaterialTheme.typography.titleSmall)
        val rows = listOf(
            "Energy" to nutrition.energyKcal?.let { "${NexusFormat.number(it)} kcal" },
            "Fat" to nutrition.fat?.let { "${NexusFormat.number(it, 1)} g" },
            "  of which saturates" to nutrition.saturatedFat?.let { "${NexusFormat.number(it, 1)} g" },
            "Carbohydrate" to nutrition.carbohydrates?.let { "${NexusFormat.number(it, 1)} g" },
            "  of which sugars" to nutrition.sugars?.let { "${NexusFormat.number(it, 1)} g" },
            "Protein" to nutrition.protein?.let { "${NexusFormat.number(it, 1)} g" },
            "Salt" to nutrition.salt?.let { "${NexusFormat.number(it, 2)} g" },
        ).filter { it.second != null }
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(value!!, style = MaterialTheme.typography.bodyMedium.tabular())
            }
        }
        Text(
            listOfNotNull(
                nutrition.nutriscore?.let { "Nutri-Score ${it.uppercase()}" },
                nutrition.servingSize?.let { "serving $it" },
                if (source == "openfoodfacts") "from Open Food Facts" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Change an item's date or place; each change lands in its activity. */
@Composable
private fun ItemSheet(item: Item, locations: List<StorageLocation>, onSave: (java.time.LocalDate?, StorageLocation?) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(item.expirationDate) }
    var location by remember { mutableStateOf(item.locationId) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.displayName, style = MaterialTheme.typography.headlineSmall)
            Text("Expires", style = MaterialTheme.typography.labelLarge)
            ExpiryPicker(date, onChange = { date = it })
            Text("Kept in", style = MaterialTheme.typography.labelLarge)
            LocationPicker(locations, location) { location = it }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) { Text("Delete item", color = MaterialTheme.colorScheme.error) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(date, locations.firstOrNull { it.id == location }) }) { Text("Save") }
                }
            }
        }
    }
}

/** The item's trail — created, moved, used up — and the household's comments on it. */
@Composable
private fun ActivitySheet(item: Item, log: Loadable<List<io.github.numynox.nexus.annona.data.ItemLogEntry>>, onComment: (String) -> Unit, onDismiss: () -> Unit) {
    var comment by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Activity", style = MaterialTheme.typography.headlineSmall)
            Text(item.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (log) {
                Loadable.Loading -> LoadingState(Modifier.fillMaxWidth().padding(24.dp))
                is Loadable.Failed -> Text(log.message, color = MaterialTheme.colorScheme.error)
                is Loadable.Ready -> log.value.forEach { entry ->
                    Surface(
                        color = if (entry.type == "comment") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(entry.userName, NexusFormat.dateTime(entry.createdAt)).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Add a comment") },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onComment(comment); comment = "" }, enabled = comment.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }
}

/** Editing a product's own fields. */
@Composable
fun ProductSheet(initial: ProductFields, categories: List<io.github.numynox.nexus.annona.data.Category>, onSave: (ProductFields) -> Unit, onDismiss: () -> Unit) {
    var fields by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit product", style = MaterialTheme.typography.headlineSmall)
            ProductFieldsForm(fields, categories, onChange = { fields = it })
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(fields) }, enabled = fields.name.isNotBlank()) { Text("Save") }
            }
        }
    }
}
