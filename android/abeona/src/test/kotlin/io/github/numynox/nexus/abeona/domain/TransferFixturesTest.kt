package io.github.numynox.nexus.abeona.domain

import io.github.numynox.nexus.abeona.data.FareType
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.toInstantOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared rules against app/abeona/src/lib/transfer.fixtures.json — the
 * same file the web's transfer.test.ts runs. Gradle copies it into the test
 * resources (build.gradle.kts, `transferFixtures`). Add cases there, not here.
 */
class TransferFixturesTest {
    @Serializable
    private data class Stop(
        val id: String? = null,
        val planned: String? = null,
        val current: String? = null,
        @SerialName("planned_platform") val plannedPlatform: String? = null,
        val platform: String? = null,
    ) {
        fun toStopTime() = StopTime(id = id, name = id ?: "?", planned = planned, current = current, plannedPlatform = plannedPlatform, platform = platform)
    }

    @Serializable
    private data class FixtureLeg(val mode: LegMode = LegMode.Train, val cancelled: Boolean = false, val from: Stop, val to: Stop) {
        fun toLeg() = Leg(mode = mode, cancelled = cancelled, from = from.toStopTime(), to = to.toStopTime())
    }

    @Serializable
    private data class ExpectedTransfer(
        val fromIndex: Int,
        val toIndex: Int,
        val availableMinutes: Int,
        val requiredMinutes: Int,
        val marginMinutes: Int,
        val walkMinutes: Int,
        val changesStation: Boolean,
        val confidence: String,
    )

    @Serializable
    private data class DelayCase(val name: String, val planned: String?, val current: String?, val expect: Int)

    @Serializable
    private data class PlatformCase(val value: String?, val expect: String?)

    @Serializable
    private data class TransferCase(val name: String, val legs: List<FixtureLeg>, val expect: List<ExpectedTransfer>, val overall: String?)

    @Serializable
    private data class FinalDelayCase(val name: String, val legs: List<FixtureLeg>, val expect: Int)

    @Serializable
    private data class BindingCase(val fareType: FareType, val trainBinding: Boolean, val arrivalDelayMinutes: Int, val expect: Boolean)

    @Serializable
    private data class Fixtures(
        val delayMinutes: List<DelayCase>,
        val normalisePlatform: List<PlatformCase>,
        val transfers: List<TransferCase>,
        val finalArrivalDelay: List<FinalDelayCase>,
        val trainBindingLifted: List<BindingCase>,
    )

    private val fixtures: Fixtures = Json { ignoreUnknownKeys = true }.decodeFromString(
        checkNotNull(javaClass.getResource("/transfer.fixtures.json")) { "transfer.fixtures.json is not on the test classpath" }.readText(),
    )

    /** The web's wire name for a confidence. */
    private val Confidence.wire: String
        get() = when (this) {
            Confidence.SAFE -> "safe"
            Confidence.TIGHT -> "tight"
            Confidence.AT_RISK -> "at_risk"
        }

    @Test
    fun delayMinutes() = fixtures.delayMinutes.forEach { c ->
        assertEquals(c.name, c.expect, delayMinutes(c.planned.toInstantOrNull(), c.current.toInstantOrNull()))
    }

    @Test
    fun normalisePlatform() = fixtures.normalisePlatform.forEach { c ->
        assertEquals("${c.value}", c.expect, normalisePlatform(c.value))
    }

    @Test
    fun transfers() = fixtures.transfers.forEach { c ->
        val legs = c.legs.map { it.toLeg() }
        val actual = transfers(legs).map {
            ExpectedTransfer(it.arriving, it.departing, it.availableMinutes, it.requiredMinutes, it.marginMinutes, it.walkMinutes, it.changesStation, it.confidence.wire)
        }
        assertEquals(c.name, c.expect, actual)
        assertEquals(c.name, c.overall, worstConfidence(legs)?.wire)
    }

    @Test
    fun finalArrivalDelay() = fixtures.finalArrivalDelay.forEach { c ->
        assertEquals(c.name, c.expect, finalArrivalDelay(c.legs.map { it.toLeg() }))
    }

    @Test
    fun trainBindingLifted() = fixtures.trainBindingLifted.forEach { c ->
        assertEquals("${c.fareType}, binding ${c.trainBinding}, +${c.arrivalDelayMinutes}", c.expect, trainBindingLifted(c.fareType, c.trainBinding, c.arrivalDelayMinutes))
    }
}
