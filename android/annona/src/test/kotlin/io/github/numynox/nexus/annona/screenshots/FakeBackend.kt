package io.github.numynox.nexus.annona.screenshots

import io.github.numynox.nexus.core.testing.FakeSupabase
import io.github.numynox.nexus.core.testing.FakeSupabase.select
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A Supabase that answers every query Annona makes with a plausible
 * household: a fridge, a freezer and a pantry, a dozen products, things
 * expired, due this week and good for months. Dates are relative to today.
 */
object FakeBackend {
    val config = FakeSupabase.config("Annona")

    /** Unknown to Annona, known to the fake Open Food Facts. */
    const val NEW_BARCODE = "4008400401621"

    private val today = LocalDate.now()
    private val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)

    private data class Cat(val id: Long, val name: String, val color: String, val icon: String)

    private val categories = listOf(
        Cat(1, "Bakery", "amber", "cookie"), Cat(2, "Beverages", "sky", "coffee"), Cat(3, "Dairy & Cheese", "blue", "milk"),
        Cat(4, "Frozen Foods", "teal", "snowflake"), Cat(5, "Meat", "rose", "beef"), Cat(6, "Pasta", "brown", "wheat"),
        Cat(7, "Snacks", "pink", "candy"), Cat(8, "Soups & Canned Goods", "violet", "soup"), Cat(9, "Fruit & Vegetables", "green", "carrot"),
    )

    private data class Loc(val id: Long, val name: String)

    private val locations = listOf(Loc(1, "Fridge"), Loc(2, "Freezer"), Loc(3, "Pantry"))

    private data class Prod(val id: Long, val name: String, val brand: String?, val quantity: String, val category: Long, val ean: String, val image: Boolean = true)

    private val products = listOf(
        Prod(1, "Fresh milk 3.5%", "Weihenstephan", "1 l", 3, "4008452011004"),
        Prod(2, "Greek yoghurt", "Fage", "500 g", 3, "5201054017364"),
        Prod(3, "Gouda slices", "Milram", "150 g", 3, "4036300005199", image = false),
        Prod(4, "Farfalle", "Barilla", "500 g", 6, "8076802085981"),
        Prod(5, "Chopped tomatoes", "Mutti", "400 g", 8, "8005110551014"),
        Prod(6, "Spinach leaves", "Iglo", "750 g", 4, "4250241201149"),
        Prod(7, "Chicken breast", null, "400 g", 5, "2001234000007", image = false),
        Prod(8, "Sourdough loaf", "Bäckerei Fuchs", "750 g", 1, "2004567000003", image = false),
        Prod(9, "Dark chocolate 70%", "Lindt", "100 g", 7, "3046920022606"),
        Prod(10, "Orange juice", "Hohes C", "1 l", 2, "4048517102049"),
        Prod(11, "Red lentil soup", "Erasco", "800 g", 8, "4037300104783"),
        Prod(12, "Carrots", null, "1 kg", 9, "2009876000001", image = false),
    )

    private data class It(val id: Long, val product: Long, val inDays: Long?, val location: Long?, val consumed: Boolean = false)

    private val items = listOf(
        It(101, 1, -1, 1), It(102, 2, 2, 1), It(103, 2, 9, 1), It(104, 3, 0, 1), It(105, 7, 1, 1), It(106, 8, 3, 3),
        It(107, 12, 6, 1), It(108, 10, 12, 1), It(109, 6, 120, 2), It(110, 6, 150, 2), It(111, 4, 400, 3), It(112, 4, 420, 3),
        It(113, 5, 700, 3), It(114, 9, 60, 3), It(115, 11, 540, 3), It(116, 1, 6, 1), It(117, 4, null, 3),
        It(118, 1, -20, 1, consumed = true), It(119, 1, -40, 1, consumed = true),
    )

    fun engine() = FakeSupabase.engine { request, path ->
        val params = request.url.parameters
        val select = request.select()
        when {
            path.endsWith("/functions/v1/lookup-food-product") -> buildJsonObject {
                put("found", true); put("ean", NEW_BARCODE)
                putJsonObject("product") {
                    put("name", "Haferdrink Barista"); put("brand", "Oatly"); put("quantity", "1 l")
                    put("image_url", "https://images.openfoodfacts.org/oatly.jpg"); put("nutriscore", "b")
                    put("energy_kcal_100g", 59); put("fat_100g", 3.0); put("saturated_fat_100g", 0.3); put("carbohydrates_100g", 6.6)
                    put("sugars_100g", 3.4); put("protein_100g", 1.1); put("salt_100g", 0.1); put("source", "openfoodfacts")
                }
            }
            path.endsWith("/rpc/annona_dashboard_summary") -> summary()
            path.endsWith("/rest/v1/annona_categories") -> buildJsonArray {
                categories.forEach { addJsonObject { put("id", it.id); put("name", it.name); put("color", it.color); put("icon", it.icon) } }
            }
            path.endsWith("/rest/v1/annona_storage_locations") -> buildJsonArray {
                locations.forEachIndexed { index, it -> addJsonObject { put("id", it.id); put("name", it.name); put("sort_order", index) } }
            }
            path.endsWith("/rest/v1/annona_products") -> {
                val id = params["id"]?.removePrefix("eq.")?.toLongOrNull()
                val ean = params["ean"]?.removePrefix("eq.")
                val search = params["or"]?.substringAfter("%")?.substringBefore("%")?.lowercase()
                buildJsonArray {
                    products
                        .filter { id == null || it.id == id }
                        .filter { ean == null || it.ean == ean }
                        .filter { search == null || "${it.brand} ${it.name} ${it.ean}".lowercase().contains(search) }
                        .forEach { add(productJson(it)) }
                }
            }
            path.endsWith("/rest/v1/annona_items") -> {
                val product = params["product_id"]?.removePrefix("eq.")?.toLongOrNull()
                buildJsonArray {
                    items.filter { if (product != null) it.product == product else !it.consumed }
                        .sortedWith(compareBy<It>({ it.consumed }, { it.inDays ?: Long.MAX_VALUE }))
                        .forEach { add(itemJson(it)) }
                }
            }
            path.endsWith("/rest/v1/annona_item_log") && select == "item_id" -> buildJsonArray {
                repeat(2) { addJsonObject { put("item_id", 102) } }
            }
            path.endsWith("/rest/v1/annona_item_log") -> buildJsonArray {
                val item = params["item_id"]?.removePrefix("eq.")?.toLongOrNull() ?: 102
                addJsonObject { put("id", 3); put("item_id", item); put("user_id", "u2"); put("entry_type", "comment"); put("message", "Opened on Sunday, finish it first"); put("created_at", now.minus(1, ChronoUnit.DAYS).toString()) }
                addJsonObject { put("id", 2); put("item_id", item); put("user_id", FakeSupabase.USER_ID); put("entry_type", "location_changed"); put("message", "Fridge"); put("created_at", now.minus(3, ChronoUnit.DAYS).toString()) }
                addJsonObject { put("id", 1); put("item_id", item); put("user_id", FakeSupabase.USER_ID); put("entry_type", "created"); put("created_at", now.minus(4, ChronoUnit.DAYS).toString()) }
            }
            path.endsWith("/rest/v1/profiles") -> buildJsonArray {
                addJsonObject { put("id", FakeSupabase.USER_ID); put("full_name", "Jannis") }
                addJsonObject { put("id", "u2"); put("full_name", "Alex") }
            }
            else -> null
        }
    }

    private fun categoryJson(id: Long): JsonObject = categories.first { it.id == id }.let { c ->
        buildJsonObject { put("name", c.name); put("color", c.color); put("icon", c.icon) }
    }

    private fun productJson(p: Prod) = buildJsonObject {
        put("id", p.id); put("name", p.name); put("brand", p.brand); put("quantity", p.quantity); put("ean", p.ean); put("category_id", p.category)
        if (p.image) put("image_url", "https://images.openfoodfacts.org/${p.ean}.jpg")
        if (p.id == 2L) {
            put("energy_kcal_100g", 132); put("fat_100g", 10.0); put("saturated_fat_100g", 7.0); put("carbohydrates_100g", 3.0)
            put("sugars_100g", 3.0); put("protein_100g", 6.0); put("salt_100g", 0.1); put("nutriscore", "c"); put("nutrition_source", "openfoodfacts")
        }
        put("annona_categories", categoryJson(p.category))
        putJsonArray("annona_items") {
            items.filter { it.product == p.id }.forEach { item ->
                addJsonObject { put("id", item.id); put("is_consumed", item.consumed); put("expiration_date", item.inDays?.let { today.plusDays(it).toString() }) }
            }
        }
    }

    private fun itemJson(item: It) = buildJsonObject {
        val p = products.first { it.id == item.product }
        put("id", item.id); put("product_id", p.id); put("is_consumed", item.consumed)
        put("expiration_date", item.inDays?.let { today.plusDays(it).toString() })
        put("storage_location_id", item.location)
        put("created_at", now.minus(5 + item.id % 7, ChronoUnit.DAYS).toString())
        putJsonObject("annona_products") {
            put("name", p.name); put("brand", p.brand); put("quantity", p.quantity); put("ean", p.ean)
            if (p.image) put("image_url", "https://images.openfoodfacts.org/${p.ean}.jpg")
            put("annona_categories", categoryJson(p.category))
        }
        item.location?.let { id -> putJsonObject("annona_storage_locations") { put("id", id); put("name", locations.first { it.id == id }.name); put("sort_order", id - 1) } }
    }

    private fun summary() = buildJsonObject {
        val active = items.filter { !it.consumed }
        put("total_products", products.size); put("total_active_items", active.size)
        put("expired_items", active.count { (it.inDays ?: 0) < 0 })
        put("expiring_within_7_days", active.count { it.inDays != null && it.inDays in 0..7 })
        put("expiring_within_30_days", active.count { it.inDays != null && it.inDays in 0..30 })
        put("no_expiration", active.count { it.inDays == null }); put("consumed_items", items.count { it.consumed })
        putJsonArray("items_by_category") {}
        putJsonArray("items_by_location") {
            locations.forEach { loc ->
                val here = active.filter { it.location == loc.id }
                addJsonObject { put("location", loc.name); put("count", here.size); put("expired_count", here.count { (it.inDays ?: 0) < 0 }) }
            }
        }
    }
}
