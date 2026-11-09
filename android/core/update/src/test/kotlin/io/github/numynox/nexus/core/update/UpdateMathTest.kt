package io.github.numynox.nexus.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateMathTest {
    @Test
    fun percentOfAKnownSize() {
        assertEquals(0, percentOf(0, 2_000))
        assertEquals(42, percentOf(840, 2_000))
        assertEquals(100, percentOf(2_000, 2_000))
        // Above the announced size it stays full rather than overflowing the bar.
        assertEquals(100, percentOf(3_000, 2_000))
    }

    @Test
    fun percentOfLargeApksDoesNotOverflow() {
        assertEquals(50, percentOf(60_000_000_000, 120_000_000_000))
    }

    @Test
    fun unknownSizeIsIndeterminate() {
        assertNull(percentOf(500, -1))
        assertNull(percentOf(500, 0))
    }

    private val key = byteArrayOf(1, 2, 3)
    private val otherKey = byteArrayOf(9, 9, 9)

    private fun verdict(
        apkPackage: String? = "app",
        apkVersionCode: Long = 11,
        apkSigners: List<ByteArray>? = listOf(key.copyOf()),
    ) = apkVerdict("app", 10, listOf(key), apkPackage, apkVersionCode, apkSigners)

    @Test
    fun installsOnlyThisAppNewerWithTheSameKey() {
        assertEquals(ApkVerdict.Install, verdict())
        assertEquals(ApkVerdict.Rejected, verdict(apkPackage = "app.debug"))
        assertEquals(ApkVerdict.Rejected, verdict(apkPackage = null))
        assertEquals(ApkVerdict.Rejected, verdict(apkSigners = listOf(otherKey)))
        assertEquals(ApkVerdict.Rejected, verdict(apkSigners = listOf(key, otherKey)))
    }

    @Test
    fun unreadableSignersLeaveTheKeyToAndroid() {
        assertEquals(ApkVerdict.Install, verdict(apkSigners = null))
        assertEquals(ApkVerdict.Install, verdict(apkSigners = emptyList()))
    }

    @Test
    fun theSameOrAnOlderBuildIsNotNewerNotRejected() {
        assertEquals(ApkVerdict.NotNewer, verdict(apkVersionCode = 10))
        assertEquals(ApkVerdict.NotNewer, verdict(apkVersionCode = 3))
        // Another key outweighs "not newer": that is a real problem, not a rerun.
        assertEquals(ApkVerdict.Rejected, verdict(apkVersionCode = 10, apkSigners = listOf(otherKey)))
    }
}
