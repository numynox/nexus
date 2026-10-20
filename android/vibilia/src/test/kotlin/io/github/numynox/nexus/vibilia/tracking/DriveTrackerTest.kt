package io.github.numynox.nexus.vibilia.tracking

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.numynox.nexus.vibilia.data.BluetoothLink
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveTrackerTest {
    private val car = "car-1"
    private val address = "AA:BB:CC:DD:EE:FF"
    private val links = listOf(BluetoothLink(car, address, "Golf"))
    private val t0: Instant = Instant.parse("2026-10-19T08:00:00Z")

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var tracker: DriveTracker

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("tracker").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        tracker = DriveTracker(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "t.preferences_pb") })
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun at(minutes: Long): Instant = t0.plus(Duration.ofMinutes(minutes))

    @Test
    fun `a connection becomes one finished drive once the grace has passed`() = runTest {
        assertTrue(tracker.onConnected(address, links, recordDrives = true, now = at(0)))
        tracker.onDisconnected(address, links, now = at(30))
        assertTrue("still within the reconnect grace", tracker.settle(now = at(31)).pending.isEmpty())

        val state = tracker.settle(now = at(33))
        assertNull(state.closing)
        assertEquals(1, state.pending.size)
        assertEquals(at(0), state.pending.single().startedAt)
        assertEquals(at(30), state.pending.single().endedAt)
    }

    @Test
    fun `reconnecting within two minutes continues the same drive`() = runTest {
        tracker.onConnected(address, links, recordDrives = true, now = at(0))
        val id = tracker.current().open!!.id
        tracker.onDisconnected(address, links, now = at(20))
        tracker.onConnected(address, links, recordDrives = true, now = at(21))
        tracker.onDisconnected(address, links, now = at(45))

        val state = tracker.settle(now = at(50))
        assertEquals(1, state.pending.size)
        assertEquals(id, state.pending.single().id)
        assertEquals(Duration.ofMinutes(45), Duration.between(state.pending.single().startedAt, state.pending.single().endedAt))
    }

    @Test
    fun `reconnecting after the grace starts a new drive`() = runTest {
        tracker.onConnected(address, links, recordDrives = true, now = at(0))
        tracker.onDisconnected(address, links, now = at(20))
        tracker.onConnected(address, links, recordDrives = true, now = at(25))

        val state = tracker.current()
        assertEquals("the first drive is queued", 1, state.pending.size)
        assertNotNull(state.open)
        assertEquals(at(25), state.open!!.startedAt)
    }

    @Test
    fun `connections shorter than a minute are dropped`() = runTest {
        tracker.onConnected(address, links, recordDrives = true, now = t0)
        tracker.onDisconnected(address, links, now = t0.plusSeconds(40))
        assertTrue(tracker.settle(now = at(5)).pending.isEmpty())
    }

    @Test
    fun `devices that are not a linked car are ignored`() = runTest {
        assertFalse(tracker.onConnected("11:22:33:44:55:66", links, recordDrives = true, now = t0))
        assertNull(tracker.current().open)
        assertNull(tracker.current().lastConnection)
    }

    @Test
    fun `without recording the car is still remembered for the refuel form`() = runTest {
        tracker.onConnected(address, links, recordDrives = false, now = t0)
        val state = tracker.current()
        assertNull(state.open)
        assertEquals(car, state.lastConnection?.carId)
        assertTrue(state.lastConnection!!.connected)
    }

    @Test
    fun `a reboot ends an open drive at its last heartbeat`() = runTest {
        tracker.onConnected(address, links, recordDrives = true, now = at(0))
        tracker.heartbeat(now = at(15))
        tracker.heartbeat(now = at(30))
        tracker.onBoot()

        val state = tracker.current()
        assertNull(state.open)
        assertEquals(at(30), state.pending.single().endedAt)
        assertFalse(state.lastConnection!!.connected)
    }

    @Test
    fun `uploaded drives leave the queue`() = runTest {
        tracker.onConnected(address, links, recordDrives = true, now = at(0))
        tracker.onDisconnected(address, links, now = at(10))
        val id = tracker.settle(now = at(15)).pending.single().id
        tracker.markUploaded(setOf(id))
        assertTrue(tracker.current().pending.isEmpty())
    }

    @Test
    fun `local driving counts only what lies after the given moment`() = runTest {
        tracker.onConnected(address, links, recordDrives = true, now = at(0))
        tracker.onDisconnected(address, links, now = at(60))
        tracker.settle(now = at(65))
        tracker.onConnected(address, links, recordDrives = true, now = at(120))

        val hours = tracker.current().localDrivingHoursAfter(car, after = at(30), now = at(150))
        // 30 minutes of the queued drive after the cut-off, plus 30 minutes live.
        assertEquals(1.0, hours, 1e-9)
    }

    @Test
    fun anUploadIsADriveLeavingTheQueue() {
        val morning = FinishedDrive("a", car, t0, t0.plus(Duration.ofMinutes(20)))
        val evening = FinishedDrive("b", car, t0.plus(Duration.ofHours(9)), t0.plus(Duration.ofHours(9).plusMinutes(25)))
        val queued = TrackerState(pending = listOf(morning))
        // A drive joining the queue is not an upload; one leaving it is.
        assertFalse(uploadedBetween(TrackerState(), queued))
        assertFalse(uploadedBetween(queued, queued.copy(pending = listOf(morning, evening))))
        assertTrue(uploadedBetween(queued, TrackerState()))
        assertTrue(uploadedBetween(TrackerState(pending = listOf(morning, evening)), TrackerState(pending = listOf(evening))))
    }
}
