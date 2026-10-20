package io.github.numynox.nexus.annona.data

import io.github.numynox.nexus.annona.reminders.ExpiryReminders
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryTest {
    private val today = LocalDate.of(2026, 10, 19)

    @Test
    fun urgencyBands() {
        assertEquals(Urgency.Expired, urgencyOf(today.minusDays(1), today))
        assertEquals(Urgency.Today, urgencyOf(today, today))
        assertEquals(Urgency.Week, urgencyOf(today.plusDays(7), today))
        assertEquals(Urgency.Month, urgencyOf(today.plusDays(8), today))
        assertEquals(Urgency.Month, urgencyOf(today.plusDays(30), today))
        assertEquals(Urgency.Later, urgencyOf(today.plusDays(31), today))
        assertEquals(Urgency.Undated, urgencyOf(null, today))
        assertEquals(ShelfLife.Week, shelfLifeOf(today, today))
    }

    @Test
    fun labels() {
        assertEquals("No date", expiryLabel(null, today))
        assertEquals("Expires today", expiryLabel(today, today))
        assertEquals("Tomorrow", expiryLabel(today.plusDays(1), today))
        assertEquals("Since yesterday", expiryLabel(today.minusDays(1), today))
        assertEquals("5 days ago", expiryLabel(today.minusDays(5), today))
        assertEquals("45 days left", expiryLabel(today.plusDays(45), today))
        assertEquals("4 months left", expiryLabel(today.plusDays(120), today))
        assertEquals("2 years left", expiryLabel(today.plusDays(800), today))
    }

    private fun item(id: Long, name: String, inDays: Long?) = Item(
        id = id, productId = id, productName = name, productBrand = null, productQuantity = null, imageUrl = null, category = null,
        expirationDate = inDays?.let { today.plusDays(it) }, locationId = null, locationName = null, isConsumed = false, createdAt = null,
    )

    @Test
    fun reminderListsWhatIsDueSoonestFirst() {
        val items = listOf(item(1, "Milk", 2), item(2, "Pasta", 300), item(3, "Yoghurt", -1), item(4, "Salt", null))
        val (title, lines) = ExpiryReminders.message(items, days = 3, today = today)!!
        assertEquals("2 things to use up, 1 already expired", title)
        assertEquals(listOf("Yoghurt — since yesterday", "Milk — 2 days left"), lines)
        assertEquals("Use up Milk", ExpiryReminders.message(listOf(item(1, "Milk", 2)), 3, today)!!.first)
        assertNull(ExpiryReminders.message(listOf(item(2, "Pasta", 300)), 3, today))
    }

    @Test
    fun reminderRunsAtTheNextChosenHour() {
        val morning = LocalDateTime.of(2026, 10, 19, 7, 30)
        assertEquals(Duration.ofMinutes(90), ExpiryReminders.delayUntil(9, morning))
        assertEquals(Duration.ofHours(23).plusMinutes(30), ExpiryReminders.delayUntil(7, morning))
    }

    @Test
    fun placesGroupByLocationWithNoLocationLast() {
        fun row(id: Long, product: Long, location: Long?, name: String, inDays: Long?) = ItemRow(
            id = id, productId = product, expirationDate = inDays?.let { today.plusDays(it) }, storageLocationId = location,
            product = ItemProductRef(name = "P$product"), location = location?.let { LocationRef(it, name, it.toInt()) },
        )
        val places = groupPlaces(
            listOf(row(1, 1, null, "", 3), row(2, 2, 2, "Freezer", 100), row(3, 1, 1, "Fridge", -2), row(4, 1, 1, "Fridge", 10), row(5, 3, 1, "Fridge", 40)),
            today,
        )
        assertEquals(listOf("Fridge", "Freezer", "No location"), places.map { it.name })
        val fridge = places.first().products
        assertEquals(listOf(1L, 3L), fridge.map { it.productId })
        assertEquals(2, fridge.first().items)
        assertEquals(1, fridge.first().expired)
        assertEquals(1, fridge.first().expiringSoon)
        assertEquals(today.minusDays(2), fridge.first().earliest)
    }
}
