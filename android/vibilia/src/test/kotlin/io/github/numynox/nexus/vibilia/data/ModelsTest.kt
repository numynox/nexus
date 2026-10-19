package io.github.numynox.nexus.vibilia.data

import io.github.numynox.nexus.core.data.NexusJson
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rows as PostgREST actually sends them, including columns the app does not know. */
class ModelsTest {
    @Test
    fun `a refuel row with its joined station decodes`() {
        val json = """
            {"id": 42, "car_id": "c1", "user_id": "u1", "timestamp": null,
             "fueled_at": "2026-02-03T10:15:00+00:00", "mileage": 10600, "liters": 48.5,
             "total_price": 72.70, "price_per_liter_calculated": 1.499, "fuel_level_after": 50,
             "is_full_refuel": true, "missed_previous_refuel": false, "fuel_station_id": "s1",
             "created_at": "2026-02-03T10:16:00.123456+00:00",
             "fuel_station": {"id": "s1", "brand": "ARAL", "place": "Erlangen", "street": "Hauptstraße", "house_number": "3"}}
        """.trimIndent()
        val event = NexusJson.decodeFromString<RefuelEvent>(json)
        assertEquals(42L, event.id)
        assertEquals(Instant.parse("2026-02-03T10:15:00Z"), event.fueledAt)
        assertEquals("ARAL Erlangen (Hauptstraße 3)", event.fuelStation?.label)
    }

    @Test
    fun `a missing price per litre is derived`() {
        val event = NexusJson.decodeFromString<RefuelEvent>(
            """{"id": 1, "car_id": "c", "fueled_at": "2026-01-01T00:00:00+00:00", "mileage": 1, "liters": 40, "total_price": 60}""",
        )
        assertEquals(1.5, event.pricePerLiter, 1e-9)
    }

    @Test
    fun `month and day keys decode as dates`() {
        val month = NexusJson.decodeFromString<DistanceMonth>("""{"month_key": "2026-01-01", "km_driven": 200.0}""")
        assertEquals(LocalDate.of(2026, 1, 1), month.month)
        assertEquals(0.0, month.estimatedKm, 0.0)
    }

    @Test
    fun `drafts send nulls, so clearing a field clears the column`() {
        val json = NexusJson.encodeToString(CarDraft(name = "Golf", tankCapacity = 50.0, plate = null))
        assertEquals(true, json.contains("\"plate\":null"))
    }

    @Test
    fun `opening times parse from the raw JSON array`() {
        val row = NexusJson.decodeFromString<CurrentPriceRow>(
            """{"id": "s", "brand": "Shell", "opening_times": [{"text": "Mo-Fr", "start": "06:00:00", "end": "22:00:00"}], "current_price": 1.789, "discount": 0.02}""",
        )
        val station = row.toStationPrice()
        assertEquals("06:00 – 22:00", station.openingTimes.single().range)
        assertEquals(1.769, station.effectivePrice!!, 1e-9)
        assertNull(station.distanceKm)
    }
}
