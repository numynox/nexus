@file:UseSerializers(InstantSerializer::class, LocalDateSerializer::class)

package io.github.numynox.nexus.annona.data

import io.github.numynox.nexus.core.data.InstantSerializer
import io.github.numynox.nexus.core.data.LocalDateSerializer
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Row shapes, hand-written like the web app's lib/data.ts
// (decisions/2026-09-04-permissive-supabase-client-generics.md). Everything
// here is shared by the whole household: there is no "mine"
// (decisions/2026-04-30-annona-shared-household-data.md).

@Serializable
data class Category(
    val id: Long,
    val name: String,
    /** A key from `CategoryColors`, e.g. "amber". */
    val color: String? = null,
    /** A key from `CategoryIcons`, e.g. "milk". */
    val icon: String? = null,
)

/** Glossary: "Location" — Fridge, Pantry, Freezer… */
@Serializable
data class StorageLocation(
    val id: Long,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

/** The EU nutrition declaration per 100 g/ml, as printed on the packet. */
@Serializable
data class Nutrition(
    @SerialName("energy_kcal_100g") val energyKcal: Double? = null,
    @SerialName("fat_100g") val fat: Double? = null,
    @SerialName("saturated_fat_100g") val saturatedFat: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates: Double? = null,
    @SerialName("sugars_100g") val sugars: Double? = null,
    @SerialName("protein_100g") val protein: Double? = null,
    @SerialName("salt_100g") val salt: Double? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    val nutriscore: String? = null,
) {
    val isEmpty: Boolean
        get() = listOf(energyKcal, fat, saturatedFat, carbohydrates, sugars, protein, salt).all { it == null } && nutriscore == null
}

/** Glossary: a Product is the kind of thing; an Item is one package of it. */
@Serializable
data class ProductRow(
    val id: Long,
    val name: String,
    val brand: String? = null,
    val ean: String? = null,
    val quantity: String? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("energy_kcal_100g") val energyKcal: Double? = null,
    @SerialName("fat_100g") val fat: Double? = null,
    @SerialName("saturated_fat_100g") val saturatedFat: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates: Double? = null,
    @SerialName("sugars_100g") val sugars: Double? = null,
    @SerialName("protein_100g") val protein: Double? = null,
    @SerialName("salt_100g") val salt: Double? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    val nutriscore: String? = null,
    @SerialName("nutrition_source") val nutritionSource: String? = null,
    @SerialName("annona_categories") val category: CategoryRef? = null,
    @SerialName("annona_items") val items: List<ItemRef> = emptyList(),
)

@Serializable
data class CategoryRef(val name: String? = null, val color: String? = null, val icon: String? = null)

@Serializable
data class ItemRef(
    val id: Long,
    @SerialName("is_consumed") val isConsumed: Boolean = false,
    @SerialName("expiration_date") val expirationDate: LocalDate? = null,
)

data class Product(
    val id: Long,
    val name: String,
    val brand: String?,
    val ean: String?,
    val quantity: String?,
    val categoryId: Long?,
    val category: CategoryRef?,
    val imageUrl: String?,
    val nutrition: Nutrition,
    val nutritionSource: String?,
    /** Expiration dates of the items in stock, soonest first; null dates last. */
    val stock: List<LocalDate?>,
) {
    /** "Barilla Farfalle", as the lists title a product. */
    val displayName: String get() = listOfNotNull(brand?.takeIf { it.isNotBlank() }, name).joinToString(" ")
    val earliestExpiration: LocalDate? get() = stock.firstOrNull { it != null }
}

internal fun ProductRow.toProduct() = Product(
    id = id,
    name = name,
    brand = brand?.takeIf { it.isNotBlank() },
    ean = ean?.takeIf { it.isNotBlank() },
    quantity = quantity?.takeIf { it.isNotBlank() },
    categoryId = categoryId,
    category = category,
    imageUrl = imageUrl?.takeIf { it.startsWith("http") },
    nutrition = Nutrition(energyKcal, fat, saturatedFat, carbohydrates, sugars, protein, salt, servingSize, nutriscore),
    nutritionSource = nutritionSource,
    stock = items.filter { !it.isConsumed }.map { it.expirationDate }.sortedWith(nullsLast(naturalOrder())),
)

/** What a product is saved with; `nutrition_source` only when the values came from a lookup. */
data class ProductDraft(
    val name: String,
    val brand: String?,
    val ean: String?,
    val quantity: String?,
    val categoryId: Long?,
    val imageUrl: String?,
    val nutrition: Nutrition? = null,
    val nutritionSource: String? = null,
)

@Serializable
data class ItemRow(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    @SerialName("expiration_date") val expirationDate: LocalDate? = null,
    @SerialName("storage_location_id") val storageLocationId: Long? = null,
    @SerialName("is_consumed") val isConsumed: Boolean = false,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("annona_products") val product: ItemProductRef? = null,
    @SerialName("annona_storage_locations") val location: LocationRef? = null,
)

@Serializable
data class ItemProductRef(
    val name: String? = null,
    val brand: String? = null,
    val quantity: String? = null,
    val ean: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("annona_categories") val category: CategoryRef? = null,
)

@Serializable
data class LocationRef(val id: Long? = null, val name: String? = null, @SerialName("sort_order") val sortOrder: Int? = null)

/** One package in stock (or used up), with its product and place. */
data class Item(
    val id: Long,
    val productId: Long,
    val productName: String,
    val productBrand: String?,
    val productQuantity: String?,
    val imageUrl: String?,
    val category: CategoryRef?,
    val expirationDate: LocalDate?,
    val locationId: Long?,
    val locationName: String?,
    val isConsumed: Boolean,
    val createdAt: Instant?,
    val commentCount: Int = 0,
) {
    val displayName: String get() = listOfNotNull(productBrand?.takeIf { it.isNotBlank() }, productName).joinToString(" ")
}

internal fun ItemRow.toItem(commentCount: Int = 0) = Item(
    id = id,
    productId = productId,
    productName = product?.name.orEmpty(),
    productBrand = product?.brand,
    productQuantity = product?.quantity,
    imageUrl = product?.imageUrl?.takeIf { it.startsWith("http") },
    category = product?.category,
    expirationDate = expirationDate,
    locationId = storageLocationId,
    locationName = location?.name,
    isConsumed = isConsumed,
    createdAt = createdAt,
    commentCount = commentCount,
)

/** Glossary: Activity / Comments — the append-only trail of who did what to an item. */
@Serializable
data class ItemLogEntry(
    val id: Long,
    @SerialName("item_id") val itemId: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("entry_type") val type: String,
    val message: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @kotlinx.serialization.Transient val userName: String? = null,
) {
    val label: String
        get() = when (type) {
            "created" -> "Added"
            "consumed" -> "Used up"
            "unconsumed" -> "Back in stock"
            "expiration_changed" -> message?.let { "Changed expiration: $it" } ?: "Changed expiration date"
            "location_changed" -> message?.let { "Moved to $it" } ?: "Changed storage location"
            "deleted" -> "Deleted"
            "comment" -> message.orEmpty()
            else -> type
        }
}

@Serializable
internal data class ProfileRow(val id: String, @SerialName("full_name") val fullName: String? = null, val email: String? = null)

@Serializable
data class DashboardSummary(
    @SerialName("total_products") val totalProducts: Int = 0,
    @SerialName("total_active_items") val activeItems: Int = 0,
    @SerialName("expired_items") val expired: Int = 0,
    @SerialName("expiring_within_7_days") val within7Days: Int = 0,
    @SerialName("expiring_within_30_days") val within30Days: Int = 0,
    @SerialName("no_expiration") val noExpiration: Int = 0,
    @SerialName("consumed_items") val consumed: Int = 0,
    @SerialName("items_by_category") val byCategory: List<CategoryCount> = emptyList(),
    @SerialName("items_by_location") val byLocation: List<LocationCount> = emptyList(),
)

@Serializable
data class CategoryCount(val category: String? = null, val count: Int = 0, @SerialName("expired_count") val expired: Int = 0)

@Serializable
data class LocationCount(val location: String = "", val count: Int = 0, @SerialName("expired_count") val expired: Int = 0)

/** One product's stock in one location, for the Places screen. */
data class PlaceProduct(
    val productId: Long,
    val name: String,
    val brand: String?,
    val quantity: String?,
    val category: CategoryRef?,
    val items: Int,
    val expired: Int,
    val expiringSoon: Int,
    val earliest: LocalDate?,
)

data class Place(val locationId: Long?, val name: String, val products: List<PlaceProduct>)

/** What Open Food Facts offered for a barcode — the lookup-food-product Edge Function's answer. */
@Serializable
data class LookupResponse(val found: Boolean = false, val ean: String? = null, val product: LookupProduct? = null, val error: String? = null)

@Serializable
data class LookupProduct(
    val name: String,
    val brand: String? = null,
    val quantity: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    val nutriscore: String? = null,
    @SerialName("energy_kcal_100g") val energyKcal: Double? = null,
    @SerialName("fat_100g") val fat: Double? = null,
    @SerialName("saturated_fat_100g") val saturatedFat: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates: Double? = null,
    @SerialName("sugars_100g") val sugars: Double? = null,
    @SerialName("protein_100g") val protein: Double? = null,
    @SerialName("salt_100g") val salt: Double? = null,
    val source: String = "openfoodfacts",
) {
    val nutrition: Nutrition get() = Nutrition(energyKcal, fat, saturatedFat, carbohydrates, sugars, protein, salt, servingSize, nutriscore)
}

/** A lookup that failed is not the same as a barcode nobody knows (lib/data.ts). */
sealed interface LookupOutcome {
    data class Found(val product: LookupProduct) : LookupOutcome
    data object NotFound : LookupOutcome
    data class Failed(val reason: String) : LookupOutcome
}
