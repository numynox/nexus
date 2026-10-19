package io.github.numynox.nexus.annona.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.numynox.nexus.annona.AppContainer
import io.github.numynox.nexus.annona.data.Category
import io.github.numynox.nexus.annona.data.LookupOutcome
import io.github.numynox.nexus.annona.data.LookupProduct
import io.github.numynox.nexus.annona.data.Product
import io.github.numynox.nexus.annona.data.ProductDraft
import io.github.numynox.nexus.annona.data.StorageLocation
import io.github.numynox.nexus.annona.ui.AddRoute
import io.github.numynox.nexus.annona.ui.common.ProductFields
import io.github.numynox.nexus.core.data.describe
import io.github.numynox.nexus.core.ui.Loadable
import io.github.numynox.nexus.core.ui.loadable
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the new product's details came from. */
sealed interface Lookup {
    data object None : Lookup
    data object Running : Lookup
    data class Found(val product: LookupProduct) : Lookup
    data object Unknown : Lookup
    data class Failed(val reason: String) : Lookup
}

data class AddForm(
    /** The product the items are for; null while a new one is being described. */
    val product: Product? = null,
    val fields: ProductFields = ProductFields(),
    val lookup: Lookup = Lookup.None,
    val expiration: LocalDate? = null,
    val locationId: Long? = null,
    val count: Int = 1,
    val saving: Boolean = false,
    val error: String? = null,
)

data class AddChoices(val categories: List<Category>, val locations: List<StorageLocation>)

/**
 * The web's quick-add flow (QuickAddPanel.svelte): a known product goes
 * straight to the item details; an unknown barcode asks Open Food Facts first,
 * so the new-product form arrives filled in.
 */
class AddViewModel(container: AppContainer, private val route: AddRoute) : ViewModel() {
    private val repository = container.repository

    private val _choices = MutableStateFlow<Loadable<AddChoices>>(Loadable.Loading)
    val choices: StateFlow<Loadable<AddChoices>> = _choices.asStateFlow()

    private val _form = MutableStateFlow(AddForm())
    val form: StateFlow<AddForm> = _form.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _choices.value = loadable {
                coroutineScope {
                    val categories = async { repository.categories() }
                    val locations = async { repository.locations() }
                    val product = async {
                        route.productId?.let { repository.product(it) } ?: route.ean?.let { repository.productByEan(it) }
                    }
                    val known = product.await()
                    val places = locations.await()
                    _form.update {
                        it.copy(
                            product = known,
                            fields = ProductFields(
                                name = known?.name ?: route.name.orEmpty(),
                                brand = known?.brand.orEmpty(),
                                quantity = known?.quantity.orEmpty(),
                                ean = known?.ean ?: route.ean.orEmpty(),
                                categoryId = known?.categoryId,
                                imageUrl = known?.imageUrl.orEmpty(),
                            ),
                            locationId = it.locationId ?: places.firstOrNull()?.id,
                        )
                    }
                    if (known == null && route.ean != null) lookup(route.ean)
                    AddChoices(categories.await(), places)
                }
            }
        }
    }

    fun lookup(ean: String) {
        _form.update { it.copy(lookup = Lookup.Running) }
        viewModelScope.launch {
            val outcome = repository.lookup(ean)
            _form.update { form ->
                when (outcome) {
                    is LookupOutcome.Found -> form.copy(
                        lookup = Lookup.Found(outcome.product),
                        fields = form.fields.copy(
                            name = outcome.product.name,
                            brand = outcome.product.brand.orEmpty(),
                            quantity = outcome.product.quantity.orEmpty(),
                            imageUrl = outcome.product.imageUrl.orEmpty(),
                        ),
                    )
                    LookupOutcome.NotFound -> form.copy(lookup = Lookup.Unknown)
                    is LookupOutcome.Failed -> form.copy(lookup = Lookup.Failed(outcome.reason))
                }
            }
        }
    }

    fun edit(block: (AddForm) -> AddForm) = _form.update { block(it).copy(error = null) }

    fun save(onSaved: (String) -> Unit) {
        val form = _form.value
        if (form.product == null && form.fields.name.isBlank()) {
            _form.update { it.copy(error = "A new product needs a name.") }
            return
        }
        _form.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val productId = form.product?.id ?: run {
                    val found = (form.lookup as? Lookup.Found)?.product
                    repository.createProduct(
                        ProductDraft(
                            name = form.fields.name,
                            brand = form.fields.brand,
                            ean = form.fields.ean,
                            quantity = form.fields.quantity,
                            categoryId = form.fields.categoryId,
                            imageUrl = form.fields.imageUrl,
                            // Only when the values came from the lookup, so a
                            // hand-typed product is never labelled as sourced.
                            nutrition = found?.nutrition,
                            nutritionSource = found?.source,
                        ),
                    )
                }
                repository.addItems(productId, form.expiration, form.locationId, form.count)
            }.onSuccess {
                val name = form.product?.displayName ?: listOf(form.fields.brand, form.fields.name).filter { it.isNotBlank() }.joinToString(" ")
                onSaved(if (form.count == 1) "Added $name" else "Added ${form.count} × $name")
            }.onFailure { failure ->
                _form.update { it.copy(saving = false, error = failure.describe()) }
            }
        }
    }
}
