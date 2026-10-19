package io.github.numynox.nexus.annona.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.numynox.nexus.core.data.NexusJson
import io.github.numynox.nexus.core.data.describe
import io.ktor.client.statement.bodyAsText
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Seeded on first use, as the website does (glossary: Category). */
val DefaultCategories = listOf(
    Triple("Bakery", "amber", "cookie"),
    Triple("Beverages", "sky", "coffee"),
    Triple("Condiments & Dressings", "coral", "flame"),
    Triple("Cooking & Baking", "yellow", "wheat"),
    Triple("Dairy & Cheese", "blue", "milk"),
    Triple("Frozen Foods", "teal", "snowflake"),
    Triple("Meat", "rose", "beef"),
    Triple("Pasta", "brown", "wheat"),
    Triple("Seafood", "green", "fish"),
    Triple("Snacks", "pink", "candy"),
    Triple("Soups & Canned Goods", "violet", "soup"),
    Triple("Spices", "lime", "flame"),
)

/**
 * The Android counterpart of app/annona/src/lib/data.ts: every query Annona
 * makes, and the only code that talks to Supabase. Every authenticated user
 * reads and writes everything; the item log records who did what.
 */
class AnnonaRepository(private val supabase: SupabaseClient) {

    private val userId: String
        get() = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in.")

    // ── Categories ──────────────────────────────────────────────────────────

    suspend fun categories(): List<Category> =
        supabase.from("annona_categories").select { order("name", Order.ASCENDING) }.decodeList()

    suspend fun seedDefaultCategories() {
        if (categories().isNotEmpty()) return
        supabase.from("annona_categories").insert(
            JsonArray(DefaultCategories.map { (name, color, icon) -> buildJsonObject { put("name", name); put("color", color); put("icon", icon) } }),
        )
    }

    suspend fun saveCategory(id: Long?, name: String, color: String?, icon: String?) {
        val row = buildJsonObject { put("name", name.trim()); put("color", color); put("icon", icon) }
        if (id == null) supabase.from("annona_categories").insert(row)
        else supabase.from("annona_categories").update(row) { filter { eq("id", id) } }
    }

    suspend fun deleteCategory(id: Long) {
        supabase.from("annona_categories").delete { filter { eq("id", id) } }
    }

    // ── Locations ───────────────────────────────────────────────────────────

    suspend fun locations(): List<StorageLocation> =
        supabase.from("annona_storage_locations").select {
            order("sort_order", Order.ASCENDING)
            order("name", Order.ASCENDING)
        }.decodeList()

    suspend fun saveLocation(id: Long?, name: String) {
        if (id != null) {
            supabase.from("annona_storage_locations").update(buildJsonObject { put("name", name.trim()) }) { filter { eq("id", id) } }
            return
        }
        val next = (locations().maxOfOrNull { it.sortOrder } ?: -1) + 1
        supabase.from("annona_storage_locations").insert(buildJsonObject { put("name", name.trim()); put("sort_order", next) })
    }

    suspend fun deleteLocation(id: Long) {
        supabase.from("annona_storage_locations").delete { filter { eq("id", id) } }
    }

    suspend fun reorderLocations(ordered: List<StorageLocation>) {
        ordered.forEachIndexed { index, location ->
            if (location.sortOrder != index) {
                supabase.from("annona_storage_locations").update(buildJsonObject { put("sort_order", index) }) { filter { eq("id", location.id) } }
            }
        }
    }

    // ── Products ────────────────────────────────────────────────────────────

    /** The catalogue, products in stock first by soonest expiry, then the rest by name. */
    suspend fun products(): List<Product> =
        supabase.from("annona_products")
            .select(Columns.raw("*,annona_categories(name,color,icon),annona_items(id,is_consumed,expiration_date)")) {
                order("brand", Order.ASCENDING, nullsFirst = false)
                order("name", Order.ASCENDING)
            }
            .decodeList<ProductRow>()
            .map { it.toProduct() }
            .sortedWith(productOrder)

    suspend fun product(id: Long): Product? =
        supabase.from("annona_products")
            .select(Columns.raw("*,annona_categories(name,color,icon),annona_items(id,is_consumed,expiration_date)")) { filter { eq("id", id) } }
            .decodeList<ProductRow>().firstOrNull()?.toProduct()

    /** An existing product always wins over a lookup: a product corrected by hand is never overwritten. */
    suspend fun productByEan(ean: String): Product? =
        supabase.from("annona_products")
            .select(Columns.raw("*,annona_categories(name,color,icon),annona_items(id,is_consumed,expiration_date)")) {
                filter { eq("ean", ean) }
                limit(1)
            }
            .decodeList<ProductRow>().firstOrNull()?.toProduct()

    /** Name, brand or barcode containing [query], for the search under the scanner. */
    suspend fun searchProducts(query: String): List<Product> {
        val q = query.trim().replace(Regex("[,()*%]"), " ").trim()
        if (q.isEmpty()) return emptyList()
        return supabase.from("annona_products")
            .select(Columns.raw("*,annona_categories(name,color,icon),annona_items(id,is_consumed,expiration_date)")) {
                filter { or { ilike("name", "%$q%"); ilike("brand", "%$q%"); ilike("ean", "%$q%") } }
                order("name", Order.ASCENDING)
                limit(10)
            }
            .decodeList<ProductRow>().map { it.toProduct() }
    }

    /**
     * Asks Open Food Facts about a barcode Annona has no product for, through
     * the lookup-food-product Edge Function. Failure and "unknown" stay apart.
     */
    suspend fun lookup(ean: String): LookupOutcome = try {
        val response = supabase.functions.invoke(function = "lookup-food-product", body = buildJsonObject { put("ean", ean) })
        val result = NexusJson.decodeFromString<LookupResponse>(response.bodyAsText())
        when {
            result.found && result.product != null -> LookupOutcome.Found(result.product)
            else -> LookupOutcome.NotFound
        }
    } catch (failure: RestException) {
        LookupOutcome.Failed(failure.description?.takeIf { it.isNotBlank() } ?: failure.error)
    } catch (failure: Exception) {
        LookupOutcome.Failed(failure.describe())
    }

    suspend fun createProduct(draft: ProductDraft): Long =
        supabase.from("annona_products").insert(draft.toJson()) { select(Columns.list("id")) }
            .decodeSingle<IdRow>().id

    suspend fun updateProduct(id: Long, draft: ProductDraft) {
        supabase.from("annona_products").update(draft.toJson(includeNutrition = draft.nutrition != null) + ("updated_at" to JsonPrimitive(Instant.now().toString()))) {
            filter { eq("id", id) }
        }
    }

    suspend fun deleteProduct(id: Long) {
        supabase.from("annona_products").delete { filter { eq("id", id) } }
    }

    private fun ProductDraft.toJson(includeNutrition: Boolean = true): JsonObject = buildJsonObject {
        put("name", name.trim())
        put("brand", brand?.trim()?.ifBlank { null })
        put("ean", ean?.trim()?.ifBlank { null })
        put("quantity", quantity?.trim()?.ifBlank { null })
        put("category_id", categoryId)
        put("image_url", imageUrl?.trim()?.ifBlank { null })
        val values = nutrition
        if (includeNutrition && values != null && nutritionSource != null) {
            put("energy_kcal_100g", values.energyKcal)
            put("fat_100g", values.fat)
            put("saturated_fat_100g", values.saturatedFat)
            put("carbohydrates_100g", values.carbohydrates)
            put("sugars_100g", values.sugars)
            put("protein_100g", values.protein)
            put("salt_100g", values.salt)
            put("serving_size", values.servingSize)
            put("nutriscore", values.nutriscore)
            put("nutrition_source", nutritionSource)
            put("nutrition_updated_at", Instant.now().toString())
        }
    }

    private operator fun JsonObject.plus(pair: Pair<String, JsonPrimitive>) = JsonObject(this + mapOf(pair))

    // ── Items ───────────────────────────────────────────────────────────────

    /** A product's items, in stock first by expiry, with how many comments each has. */
    suspend fun itemsForProduct(productId: Long): List<Item> {
        val rows = supabase.from("annona_items")
            .select(Columns.raw("*,annona_products($ItemProduct),annona_storage_locations(id,name,sort_order)")) {
                filter { eq("product_id", productId) }
                order("is_consumed", Order.ASCENDING)
                order("expiration_date", Order.ASCENDING, nullsFirst = false)
            }
            .decodeList<ItemRow>()
        if (rows.isEmpty()) return emptyList()
        val counts = supabase.from("annona_item_log")
            .select(Columns.list("item_id")) {
                filter { eq("entry_type", "comment"); isIn("item_id", rows.map { it.id }) }
            }
            .decodeList<ItemIdRow>()
            .groupingBy { it.itemId }.eachCount()
        return rows.map { it.toItem(counts[it.id] ?: 0) }
    }

    /** Everything in stock, soonest expiry first, undated last. */
    suspend fun activeItems(): List<Item> =
        supabase.from("annona_items")
            .select(Columns.raw("*,annona_products($ItemProduct),annona_storage_locations(id,name,sort_order)")) {
                filter { eq("is_consumed", false) }
                order("expiration_date", Order.ASCENDING, nullsFirst = false)
            }
            .decodeList<ItemRow>()
            .map { it.toItem() }

    /** Stock by location, then product — the web's fetchItemsGroupedByLocation. */
    suspend fun places(today: LocalDate = LocalDate.now()): List<Place> {
        val rows = supabase.from("annona_items")
            .select(Columns.raw("id,product_id,storage_location_id,expiration_date,annona_products(name,brand,quantity,annona_categories(name,color,icon)),annona_storage_locations(id,name,sort_order)")) {
                filter { eq("is_consumed", false) }
            }
            .decodeList<ItemRow>()
        return groupPlaces(rows, today)
    }

    suspend fun addItems(productId: Long, expiration: LocalDate?, locationId: Long?, count: Int) {
        val uid = userId
        repeat(count.coerceIn(1, 50)) {
            val id = supabase.from("annona_items").insert(
                buildJsonObject {
                    put("product_id", productId)
                    put("expiration_date", expiration?.toString())
                    put("storage_location_id", locationId)
                },
            ) { select(Columns.list("id")) }.decodeSingle<IdRow>().id
            log(id, uid, "created", null)
        }
    }

    suspend fun setConsumed(itemId: Long, consumed: Boolean) {
        supabase.from("annona_items").update(buildJsonObject { put("is_consumed", consumed) }) { filter { eq("id", itemId) } }
        log(itemId, userId, if (consumed) "consumed" else "unconsumed", null)
    }

    suspend fun setExpiration(item: Item, date: LocalDate?) {
        supabase.from("annona_items").update(buildJsonObject { put("expiration_date", date?.toString()) }) { filter { eq("id", item.id) } }
        log(item.id, userId, "expiration_changed", "${item.expirationDate ?: "none"} → ${date ?: "none"}")
    }

    suspend fun setLocation(item: Item, location: StorageLocation?) {
        supabase.from("annona_items").update(buildJsonObject { put("storage_location_id", location?.id) }) { filter { eq("id", item.id) } }
        log(item.id, userId, "location_changed", location?.name)
    }

    suspend fun deleteItem(itemId: Long) {
        supabase.from("annona_items").delete { filter { eq("id", itemId) } }
    }

    // ── Activity ────────────────────────────────────────────────────────────

    /** An item's trail, newest first, with who did each thing. */
    suspend fun itemLog(itemId: Long): List<ItemLogEntry> {
        val entries = supabase.from("annona_item_log").select {
            filter { eq("item_id", itemId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<ItemLogEntry>()
        if (entries.isEmpty()) return entries
        val names = supabase.from("profiles")
            .select(Columns.list("id", "full_name", "email")) { filter { isIn("id", entries.map { it.userId }.distinct()) } }
            .decodeList<ProfileRow>()
            .associate { it.id to (it.fullName?.takeIf(String::isNotBlank) ?: it.email ?: it.id.take(8)) }
        return entries.map { it.copy(userName = names[it.userId]) }
    }

    suspend fun comment(itemId: Long, message: String) = log(itemId, userId, "comment", message.trim())

    private suspend fun log(itemId: Long, uid: String, type: String, message: String?) {
        supabase.from("annona_item_log").insert(
            buildJsonObject {
                put("item_id", itemId)
                put("user_id", uid)
                put("entry_type", type)
                put("message", message)
            },
        )
    }

    // ── Overview ────────────────────────────────────────────────────────────

    suspend fun summary(): DashboardSummary =
        NexusJson.decodeFromString(supabase.postgrest.rpc("annona_dashboard_summary").data)

    private companion object {
        const val ItemProduct = "name,brand,quantity,ean,image_url,annona_categories(name,color,icon)"

        /** In stock first, soonest expiry first; then the empty ones by brand and name (lib/data.ts). */
        val productOrder = Comparator<Product> { a, b ->
            val aStock = a.stock.isNotEmpty()
            val bStock = b.stock.isNotEmpty()
            val byName = compareBy<Product>({ it.brand.orEmpty().lowercase() }, { it.name.lowercase() })
            when {
                aStock != bStock -> if (aStock) -1 else 1
                !aStock -> byName.compare(a, b)
                else -> compareBy<Product, LocalDate?>(nullsLast(naturalOrder())) { it.earliestExpiration }.then(byName).compare(a, b)
            }
        }
    }
}

@kotlinx.serialization.Serializable
internal data class IdRow(val id: Long)

@kotlinx.serialization.Serializable
internal data class ItemIdRow(@kotlinx.serialization.SerialName("item_id") val itemId: Long)

/** Groups stock rows by location, then product; "No location" last (fetchItemsGroupedByLocation). */
internal fun groupPlaces(rows: List<ItemRow>, today: LocalDate): List<Place> {
    val soon = today.plusDays(30)
    return rows.groupBy { it.storageLocationId }
        .map { (locationId, items) ->
            val ref = items.first().location
            val products = items.groupBy { it.productId }.map { (productId, stock) ->
                val product = stock.first().product
                PlaceProduct(
                    productId = productId,
                    name = product?.name.orEmpty(),
                    brand = product?.brand,
                    quantity = product?.quantity,
                    category = product?.category,
                    items = stock.size,
                    expired = stock.count { it.expirationDate?.isBefore(today) == true },
                    expiringSoon = stock.count { d -> d.expirationDate?.let { !it.isBefore(today) && !it.isAfter(soon) } == true },
                    earliest = stock.mapNotNull { it.expirationDate }.minOrNull(),
                )
            }.sortedWith(
                compareByDescending<PlaceProduct> { it.expired }
                    .thenBy { it.category?.name.orEmpty().lowercase() }
                    .thenBy { it.brand.orEmpty().lowercase() }
                    .thenBy { it.name.lowercase() },
            )
            Triple(locationId, ref, products)
        }
        .sortedWith(compareBy({ it.first == null }, { it.second?.sortOrder ?: Int.MAX_VALUE }, { it.second?.name.orEmpty() }))
        .map { (id, ref, products) -> Place(id, if (id == null) "No location" else ref?.name ?: "Location", products) }
}
