package io.github.numynox.nexus.abeona.reminders

import io.github.numynox.nexus.abeona.data.BahnCardDeadline
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BahnCardRemindersTest {
    private val today = LocalDate.of(2026, 11, 3)

    private fun deadline(name: String?, kind: Int, daysLeft: Int) = BahnCardDeadline(
        cardId = "c-$name", name = name, kind = kind,
        validTo = today.plusDays(daysLeft + 28L).toString(), deadline = today.plusDays(daysLeft.toLong()).toString(),
        daysLeft = daysLeft,
    )

    @Test
    fun speaksUpOnlyOnTheReminderDays() {
        assertNull(BahnCardReminders.message(listOf(deadline("Ada", 25, 13), deadline("Ben", 50, -2))))
        BahnCardReminders.NOTIFY_ON.forEach { days ->
            assertEquals("Ada (BahnCard 25) renews unless cancelled", BahnCardReminders.message(listOf(deadline("Ada", 25, days)))?.first)
        }
    }

    @Test
    fun namesEveryCardDueTodaySoonestFirst() {
        val (title, lines) = BahnCardReminders.message(listOf(deadline("Ada", 25, 7), deadline("Ben", 50, 1), deadline("Cy", 100, 5)))!!
        assertEquals("2 BahnCards renew unless cancelled", title)
        assertEquals(
            listOf("Ben (BahnCard 50): Cancel by tomorrow, 4 Nov 2026", "Ada (BahnCard 25): Cancel by 10 Nov 2026 (in 7 days)"),
            lines,
        )
    }

    @Test
    fun aCardWithoutANameIsCalledByItsKind() {
        assertEquals("BahnCard 50 renews unless cancelled", BahnCardReminders.message(listOf(deadline(null, 50, 1)))?.first)
    }

    @Test
    fun oneCardIsNamedInTheTitleOnly() {
        assertEquals(
            listOf("Cancel by 6 Nov 2026 (in 3 days)."),
            BahnCardReminders.message(listOf(deadline("Ada", 25, 3)))?.second,
        )
    }

    @Test
    fun saysWhenTheDeadlineHasPassed() {
        assertEquals("The cancellation deadline was 1 Nov 2026 (2 days ago)", BahnCardReminders.deadlineText(deadline("Ada", 25, -2)))
    }

    @Test
    fun runsNearNineInTheMorning() {
        assertEquals(60L, BahnCardReminders.delayUntil(9, LocalDateTime.of(2026, 11, 3, 8, 0)).toMinutes())
        assertEquals(23 * 60L, BahnCardReminders.delayUntil(9, LocalDateTime.of(2026, 11, 3, 10, 0)).toMinutes())
    }
}
