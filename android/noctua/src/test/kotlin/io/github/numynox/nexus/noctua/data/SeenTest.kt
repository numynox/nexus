package io.github.numynox.nexus.noctua.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SeenTest {
    @Test
    fun appendsInTheOrderSeen() {
        assertEquals(listOf(5L, 9L, 2L, 7L), withSeen(listOf(5L, 9L), listOf(2L, 7L, 2L), max = 10))
    }

    @Test
    fun seeingAgainChangesNothing() {
        val order = listOf(5L, 9L)
        assertSame(order, withSeen(order, listOf(9L), max = 10))
    }

    @Test
    fun aFullListLetsGoOfWhatWasSeenLongestAgo() {
        // 3 was seen first, then 2, then 1: 3 and 2 go, whatever their ids.
        assertEquals(listOf(1L, 4L, 5L), withSeen(listOf(3L, 2L, 1L), listOf(4L, 5L), max = 3))
    }

    /** A liveblog keeps its old, low id while it tops today's feed; it must stay seen. */
    @Test
    fun aLowIdSeenNowIsKept() {
        val full = (1_000L..1_004L).toList()
        assertEquals(listOf(1_001L, 1_002L, 1_003L, 1_004L, 90L), withSeen(full, listOf(90L), max = 5))
    }
}
