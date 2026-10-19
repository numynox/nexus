package io.github.numynox.nexus.annona.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.Category
import io.github.numynox.nexus.annona.data.Item
import io.github.numynox.nexus.annona.data.ItemLogEntry
import io.github.numynox.nexus.annona.data.Product
import io.github.numynox.nexus.annona.data.ProductDraft
import io.github.numynox.nexus.annona.data.StorageLocation
import io.github.numynox.nexus.annona.ui.common.ProductFields
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProductPage(
    val product: Product,
    val items: List<Item>,
    val categories: List<Category>,
    val locations: List<StorageLocation>,
)

class ProductViewModel(container: AppContainer, private val productId: Long) : ViewModel() {
    private val repository = container.repository

    private val _page = MutableStateFlow<Loadable<ProductPage>>(Loadable.Loading)
    val page: StateFlow<Loadable<ProductPage>> = _page.asStateFlow()

    /** The activity of the item whose sheet is open. */
    private val _log = MutableStateFlow<Loadable<List<ItemLogEntry>>>(Loadable.Loading)
    val log: StateFlow<Loadable<List<ItemLogEntry>>> = _log.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Set when the product is gone, so the screen can close. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_page.value !is Loadable.Ready) _page.value = Loadable.Loading
            _page.value = loadable {
                coroutineScope {
                    val product = async { repository.product(productId) ?: error("This product no longer exists.") }
                    val items = async { repository.itemsForProduct(productId) }
                    val categories = async { repository.categories() }
                    val locations = async { repository.locations() }
                    ProductPage(product.await(), items.await(), categories.await(), locations.await())
                }
            }
        }
    }

    private fun act(done: String? = null, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    done?.let { _messages.tryEmit(it) }
                    onDone()
                }
                .onFailure { _messages.tryEmit(it.describe()) }
            if (!_deleted.value) load()
        }
    }

    fun setConsumed(item: Item, consumed: Boolean, onDone: () -> Unit) =
        act(if (consumed) "${item.displayName} used up" else "Back in stock", onDone) { repository.setConsumed(item.id, consumed) }

    fun saveItem(item: Item, expiration: LocalDate?, location: StorageLocation?, onDone: () -> Unit) = act(onDone = onDone) {
        if (expiration != item.expirationDate) repository.setExpiration(item, expiration)
        if (location?.id != item.locationId) repository.setLocation(item, location)
    }

    fun deleteItem(item: Item, onDone: () -> Unit) = act("Item deleted", onDone) { repository.deleteItem(item.id) }

    fun openLog(item: Item) {
        _log.value = Loadable.Loading
        viewModelScope.launch { _log.value = loadable { repository.itemLog(item.id) } }
    }

    fun comment(item: Item, message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.comment(item.id, message) }.onFailure { _messages.tryEmit(it.describe()) }
            openLog(item)
            load()
        }
    }

    fun saveProduct(fields: ProductFields, onDone: () -> Unit) = act("Saved", onDone) {
        repository.updateProduct(
            productId,
            ProductDraft(fields.name, fields.brand, fields.ean, fields.quantity, fields.categoryId, fields.imageUrl),
        )
    }

    fun deleteProduct(onDone: () -> Unit) = act(onDone = onDone) {
        repository.deleteProduct(productId)
        _deleted.value = true
    }
}
