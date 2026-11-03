package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.StopTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same cases as app/abeona/src/lib/compensation.test.ts. */
class CompensationTest {
    @Test
    fun headlines() {
        assertEquals("€12.50 to claim", CompensationText.headline("eligible", "€12.50"))
        assertEquals("€1.50, under DB's €4 minimum", CompensationText.headline("below_minimum", "€1.50"))
        assertEquals("Compensation due: add the fare", CompensationText.headline("unknown_price", null))
        assertEquals("No compensation", CompensationText.headline("not_eligible", null))
    }

    @Test
    fun deadlineText() {
        assertEquals("Claim by 14 Jan 2027 (in 72 days)", CompensationText.deadline("14 Jan 2027", 72))
        assertEquals("Claim by tomorrow, 14 Jan 2027", CompensationText.deadline("14 Jan 2027", 1))
        assertEquals("The claim deadline was 14 Jan 2027", CompensationText.deadline("14 Jan 2027", -2))
    }

    @Test
    fun manualReplacementRunsFromTheMissedStopToTheDestination() {
        val missed = Leg(
            lineName = "RE 10", tripRef = "r",
            from = StopTime(name = "Würzburg Hbf", planned = "2026-10-14T10:48:00Z"),
            to = StopTime(name = "Bamberg", planned = "2026-10-14T12:00:00Z"),
        )
        val r = manualReplacement(missed, missed, " ic 2063 ", "2026-10-14T13:05:00Z")
        assertEquals("ic 2063", r.lineName)
        assertEquals("IC", r.product)
        assertEquals("Würzburg Hbf", r.from.name)
        assertEquals("Bamberg", r.to.name)
        assertEquals("2026-10-14T13:05:00Z", r.to.current)
        assertNull(r.tripRef)
    }
}
