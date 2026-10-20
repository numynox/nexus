package io.github.numynox.nexus.annona.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.Product
import io.github.numynox.nexus.annona.scan.BarcodeScanner
import io.github.numynox.nexus.annona.ui.AppViewModel
import io.github.numynox.nexus.annona.ui.Navigator
import io.github.numynox.nexus.annona.ui.ScanMode
import io.github.numynox.nexus.annona.ui.common.containerViewModel
import io.github.numynox.nexus.annona.ui.products.ProductRow
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.components.SectionHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val _results = MutableStateFlow<List<Product>>(emptyList())
    val results: StateFlow<List<Product>> = _results.asStateFlow()
    private var search: Job? = null

    /** Search as you type, a beat after the last key — the web's 200 ms debounce. */
    fun search(query: String) {
        search?.cancel()
        if (query.trim().length < 2) {
            _results.value = emptyList()
            return
        }
        search = viewModelScope.launch {
            delay(200)
            _results.value = runCatching { repository.searchProducts(query) }.getOrDefault(emptyList())
        }
    }

    /** A scanned or typed barcode: the product it belongs to, if Annona knows it. */
    fun resolve(ean: String, onResult: (Product?) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.productByEan(ean) }.onSuccess(onResult).onFailure { onError(it.describe()) }
        }
    }
}

private val Barcode = Regex("^\\d{8,14}$")

/**
 * Scan to add or to use up. The camera fills the top; under it a search for
 * products by name, brand or barcode, for packets without one.
 */
@Composable
fun ScanScreen(app: AppViewModel, mode: ScanMode, navigator: Navigator) {
    val viewModel = containerViewModel(key = "scan-$mode") { ScanViewModel(it) }
    val results by viewModel.results.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(query) { viewModel.search(query) }

    fun open(product: Product) = if (mode == ScanMode.Add) navigator.add(productId = product.id, fromScanner = true) else navigator.consume(product.id, fromScanner = true)

    fun barcode(code: String) {
        if (busy) return
        busy = true
        viewModel.resolve(code, onResult = { product ->
            busy = false
            when {
                product != null -> open(product)
                mode == ScanMode.Add -> navigator.add(ean = code, fromScanner = true)
                else -> app.say("Nothing in Annona has the barcode $code.")
            }
        }, onError = {
            busy = false
            app.say(it)
        })
    }

    fun submit() {
        val text = query.trim()
        when {
            text.matches(Barcode) -> barcode(text)
            mode == ScanMode.Add && text.isNotEmpty() -> navigator.add(name = text, fromScanner = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == ScanMode.Add) "Scan to add" else "Scan to use up") },
                navigationIcon = { IconButton(onClick = navigator.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "camera") {
                BarcodeScanner(onScanned = ::barcode, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            }
            item(key = "query") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text(if (mode == ScanMode.Add) "Barcode, or a product's name" else "Barcode or product") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (results.isNotEmpty()) {
                item(key = "results") { SectionHeader("Products") }
                items(results, key = { it.id }) { product -> ProductRow(product, onClick = { open(product) }) }
            }
            if (mode == ScanMode.Add && query.isNotBlank() && !query.trim().matches(Barcode)) {
                item(key = "new") {
                    TextButton(onClick = { submit() }) { Text("New product “${query.trim()}”") }
                }
            }
        }
    }
}
