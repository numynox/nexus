@file:UseSerializers(InstantSerializer::class)

package io.github.numynox.nexus.vibilia.tracking

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.numynox.nexus.core.data.NexusJson
import io.github.numynox.nexus.vibilia.data.BluetoothLink
import io.github.numynox.nexus.core.data.InstantSerializer
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

private val Context.trackerStore by preferencesDataStore(name = "drive_tracker")
private val StateKey = stringPreferencesKey("state")

fun DriveTracker(context: Context): DriveTracker = DriveTracker(context.trackerStore)

/** A connection that is still open, or closed but within the reconnect grace. */
@Serializable
data class OpenDrive(
    val id: String,
    val carId: String,
    val address: String,
    val startedAt: Instant,
    /** Last moment the drive is known to have been running: the heartbeat, or the disconnect. */
    val lastSeenAt: Instant,
)

@Serializable
data class FinishedDrive(
    val id: String,
    val carId: String,
    val startedAt: Instant,
    val endedAt: Instant,
) {
    val hours: Double get() = Duration.between(startedAt, endedAt).toMillis() / 3_600_000.0
}

/** The car this phone was last connected to — what the refuel form preselects. */
@Serializable
data class LastConnection(val carId: String, val at: Instant, val connected: Boolean)

@Serializable
data class TrackerState(
    val open: OpenDrive? = null,
    /** Disconnected less than [DriveTracker.ReconnectGrace] ago; a reconnect resumes it. */
    val closing: OpenDrive? = null,
    /** Finished drives waiting for upload. */
    val pending: List<FinishedDrive> = emptyList(),
    val lastConnection: LastConnection? = null,
)

/**
 * Turns Bluetooth connect and disconnect events into drives
 * (decisions/2026-10-19-bluetooth-drive-tracking.md). Everything is persisted
 * immediately: the process is usually dead between events, and the receiver
 * that calls in here lives for a few seconds at most.
 *
 *  * A disconnect does not end a drive at once. A reconnect to the same car
 *    within two minutes — a flaky head unit, a stall — continues it.
 *  * Connections shorter than a minute are dropped: that is a phone brushing
 *    past the car, not a drive.
 *  * After a reboot an open drive is closed at its last heartbeat, since the
 *    disconnect was never seen.
 *
 * Nothing here talks to the network; [DriveSyncWorker] uploads [TrackerState.pending].
 */
class DriveTracker(private val store: DataStore<Preferences>) {

    private val mutex = Mutex()

    val state: Flow<TrackerState> = store.data.map { prefs ->
        prefs[StateKey]?.let { runCatching { NexusJson.decodeFromString<TrackerState>(it) }.getOrNull() } ?: TrackerState()
    }

    suspend fun current(): TrackerState = state.first()

    private suspend fun update(transform: (TrackerState) -> TrackerState): TrackerState = mutex.withLock {
        var next = TrackerState()
        store.edit { prefs ->
            val now = prefs[StateKey]?.let { runCatching { NexusJson.decodeFromString<TrackerState>(it) }.getOrNull() } ?: TrackerState()
            next = transform(now)
            prefs[StateKey] = NexusJson.encodeToString(next)
        }
        next
    }

    /** Returns true when the address belongs to a linked car. */
    suspend fun onConnected(address: String, links: List<BluetoothLink>, recordDrives: Boolean, now: Instant = Instant.now()): Boolean {
        val carId = links.firstOrNull { it.address.equals(address, ignoreCase = true) }?.carId ?: return false
        update { state ->
            val connection = LastConnection(carId, now, connected = true)
            val closing = state.closing
            when {
                !recordDrives -> state.copy(lastConnection = connection)
                state.open?.address.equals(address, ignoreCase = true) -> state.copy(lastConnection = connection)
                closing != null && closing.address.equals(address, ignoreCase = true) &&
                    Duration.between(closing.lastSeenAt, now) <= ReconnectGrace ->
                    state.copy(open = closing.copy(lastSeenAt = now), closing = null, lastConnection = connection)
                else -> {
                    val finished = listOfNotNull(state.open, closing).mapNotNull { it.finish(it.lastSeenAt) }
                    state.copy(
                        open = OpenDrive(UUID.randomUUID().toString(), carId, address.uppercase(), now, now),
                        closing = null,
                        pending = state.pending + finished,
                        lastConnection = connection,
                    )
                }
            }
        }
        return true
    }

    suspend fun onDisconnected(address: String, links: List<BluetoothLink>, now: Instant = Instant.now()) {
        val carId = links.firstOrNull { it.address.equals(address, ignoreCase = true) }?.carId
        update { state ->
            val open = state.open
            val connection = carId?.let { LastConnection(it, now, connected = false) } ?: state.lastConnection
            if (open != null && open.address.equals(address, ignoreCase = true)) {
                state.copy(open = null, closing = open.copy(lastSeenAt = now), lastConnection = connection)
            } else {
                state.copy(lastConnection = connection)
            }
        }
    }

    suspend fun heartbeat(now: Instant = Instant.now()) {
        update { state -> state.open?.let { state.copy(open = it.copy(lastSeenAt = now)) } ?: state }
    }

    /** The disconnect of a drive running at shutdown was never seen; end it at its last heartbeat. */
    suspend fun onBoot() {
        update { state ->
            val finished = listOfNotNull(state.open, state.closing).mapNotNull { it.finish(it.lastSeenAt) }
            state.copy(
                open = null,
                closing = null,
                pending = state.pending + finished,
                lastConnection = state.lastConnection?.copy(connected = false),
            )
        }
    }

    /** Moves a drive whose reconnect grace has passed into the upload queue. */
    suspend fun settle(now: Instant = Instant.now()): TrackerState = update { state ->
        val closing = state.closing
        if (closing != null && Duration.between(closing.lastSeenAt, now) > ReconnectGrace) {
            state.copy(closing = null, pending = state.pending + listOfNotNull(closing.finish(closing.lastSeenAt)))
        } else {
            state
        }
    }

    suspend fun markUploaded(ids: Set<String>) {
        update { state -> state.copy(pending = state.pending.filterNot { it.id in ids }) }
    }

    /** Turning tracking off ends the current drive where it stands. */
    suspend fun stopRecording(now: Instant = Instant.now()) {
        update { state ->
            val finished = listOfNotNull(state.open?.finish(now), state.closing?.let { it.finish(it.lastSeenAt) })
            state.copy(open = null, closing = null, pending = state.pending + finished)
        }
    }

    private fun OpenDrive.finish(endedAt: Instant): FinishedDrive? {
        val length = Duration.between(startedAt, endedAt)
        return if (length < MinimumDrive || length > MaximumDrive) null else FinishedDrive(id, carId, startedAt, endedAt)
    }

    companion object {
        val ReconnectGrace: Duration = Duration.ofMinutes(2)
        val MinimumDrive: Duration = Duration.ofMinutes(1)
        /** The database refuses longer intervals; a longer one is a missed disconnect. */
        val MaximumDrive: Duration = Duration.ofHours(18)
    }
}

/**
 * This phone's latest finished drive of a car, which the server may not have
 * yet: one in its reconnect grace, or one waiting for upload.
 */
fun TrackerState.latestLocalDrive(carId: String): Pair<Instant, Instant>? {
    val closingDrive = closing
        ?.takeIf { it.carId == carId && Duration.between(it.startedAt, it.lastSeenAt) >= DriveTracker.MinimumDrive }
        ?.let { it.startedAt to it.lastSeenAt }
    val queued = pending.filter { it.carId == carId }.map { it.startedAt to it.endedAt }
    return (listOfNotNull(closingDrive) + queued).maxByOrNull { it.second }
}

/**
 * Driving time this phone knows about for a car after a moment, that the
 * server does not have yet: the drive in progress and the upload queue.
 */
fun TrackerState.localDrivingHoursAfter(carId: String, after: Instant, now: Instant = Instant.now()): Double {
    fun overlap(start: Instant, end: Instant): Double {
        val from = maxOf(start, after)
        return if (end.isAfter(from)) Duration.between(from, end).toMillis() / 3_600_000.0 else 0.0
    }
    val live = listOfNotNull(open, closing).filter { it.carId == carId }
        .sumOf { overlap(it.startedAt, if (it == open) now else it.lastSeenAt) }
    val queued = pending.filter { it.carId == carId }.sumOf { overlap(it.startedAt, it.endedAt) }
    return live + queued
}

/**
 * Whether a drive left the upload queue between two states — uploaded, since
 * nothing else removes one. Until the server is asked again, it is then known
 * to neither the phone's queue nor the screen's last answer from the server.
 */
fun uploadedBetween(before: TrackerState, after: TrackerState): Boolean {
    val remaining = after.pending.mapTo(HashSet()) { it.id }
    return before.pending.any { it.id !in remaining }
}
