package io.github.numynox.nexus.abeona.ui.common

import io.github.numynox.nexus.abeona.data.bahnCardName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketNeedsTextTest {
    @Test
    fun saysTheCountAndEveryCard() {
        assertEquals("2 travellers, BahnCard 25 and BahnCard 50", ticketNeedsDescription(2, listOf(50, 25)))
        assertEquals("3 travellers, BahnCard 25, BahnCard 25 and BahnCard 50", ticketNeedsDescription(3, listOf(25, 50, 25)))
        assertEquals("1 traveller", ticketNeedsDescription(1, emptyList()))
        assertEquals("BahnCard 50", ticketNeedsDescription(null, listOf(50)))
    }

    @Test
    fun saysNothingWhenNothingIsKnown() {
        assertNull(ticketNeedsDescription(null, emptyList()))
    }

    @Test
    fun shortensTheCards() {
        assertEquals("BC25 · BC50", bahnCardsShort(listOf(50, 25)))
    }

    @Test
    fun aCardWithoutANameIsItsKind() {
        assertEquals("BahnCard 25", bahnCardName(null, 25))
        assertEquals("BahnCard 50", bahnCardName("  ", 50))
        assertEquals("Jannis", bahnCardName(" Jannis ", 50))
    }
}
