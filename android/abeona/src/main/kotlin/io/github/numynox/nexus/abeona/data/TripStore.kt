package io.github.numynox.nexus.abeona.data

import android.content.Context
import io.github.numynox.nexus.core.data.NexusJson
import io.github.numynox.nexus.core.data.isNetworkFailure
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** A value, and when it was saved if it came from the offline pack instead of the network. */
data class Fetched<T>(val value: T, val offlineSince: Instant? = null)

@Serializable
private data class Pack(val savedAt: String, val trips: List<Trip>)

/**
 * The offline trip pack: the upcoming trips with their legs as one JSON file,
 * and each one's ticket in app-private storage (`files/tickets/<trip_id>/`),
 * so the trip list, a trip and its ticket open on a train with no signal.
 */
class OfflinePack(context: Context) {
    private val root = context.filesDir
    private val packFile get() = File(root, "offline/trips.json")
    private val ticketsDir get() = File(root, "tickets")
    private val lock = Mutex()

    suspend fun read(): Pair<Instant, List<Trip>>? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val pack = NexusJson.decodeFromString<Pack>(packFile.readText())
                Instant.parse(pack.savedAt) to pack.trips
            }.getOrNull()
        }
    }

    suspend fun write(trips: List<Trip>, now: Instant = Instant.now()) = withContext(Dispatchers.IO) {
        lock.withLock {
            packFile.parentFile?.mkdirs()
            val temp = File(packFile.parentFile, "trips.json.tmp")
            temp.writeText(NexusJson.encodeToString(Pack(now.toString(), trips)))
            temp.renameTo(packFile)
            // Tickets of trips no longer in the pack go with them.
            val keep = trips.map { it.id }.toSet()
            ticketsDir.listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
        }
    }

    /** Puts a refreshed trip back into the pack, keeping the pack's own save time. */
    suspend fun update(trip: Trip) {
        val (savedAt, trips) = read() ?: return
        if (trips.none { it.id == trip.id }) return
        write(trips.map { if (it.id == trip.id) trip else it }, savedAt)
    }

    suspend fun remove(id: String) {
        val (savedAt, trips) = read() ?: return
        write(trips.filter { it.id != id }, savedAt)
    }

    /** The trip's ticket on this phone, if it has been downloaded. */
    fun ticketFile(trip: Trip): File? {
        val name = trip.ticketFileName ?: return null
        return File(ticketsDir, "${trip.id}/$name").takeIf { it.isFile }
    }

    suspend fun saveTicket(trip: Trip, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val dir = File(ticketsDir, trip.id)
        dir.deleteRecursively()
        dir.mkdirs()
        File(dir, trip.ticketFileName ?: "ticket.pdf").apply { writeBytes(bytes) }
    }
}

/**
 * Trips from the network, falling back to the offline pack when there is
 * none — the one place that decides which. Screens, the widget and the live
 * companion all read trips through here.
 */
class TripStore(
    private val repository: AbeonaRepository,
    private val pack: OfflinePack,
    private val scope: CoroutineScope,
) {
    suspend fun upcoming(): Fetched<List<Trip>> = try {
        val trips = repository.upcomingTrips()
        pack.write(trips)
        prefetchTickets(trips)
        Fetched(trips)
    } catch (failure: Exception) {
        val cached = pack.read()
        if (failure.isNetworkFailure() && cached != null) Fetched(cached.second, cached.first) else throw failure
    }

    suspend fun trip(id: String): Fetched<Trip?> = try {
        Fetched(repository.trip(id))
    } catch (failure: Exception) {
        val cached = pack.read()
        val trip = cached?.second?.firstOrNull { it.id == id }
        if (failure.isNetworkFailure() && trip != null) Fetched(trip, cached.first) else throw failure
    }

    /** Live values from the provider, written to the database and the pack. */
    suspend fun refresh(trip: Trip): Trip = repository.refreshTrip(trip).also { pack.update(it) }

    suspend fun delete(trip: Trip) {
        repository.deleteTrip(trip)
        pack.remove(trip.id)
    }

    /** The ticket file: from the phone when it is there, downloaded (and kept) otherwise. */
    suspend fun ticket(trip: Trip): File {
        pack.ticketFile(trip)?.let { return it }
        val path = trip.ticketPath ?: error("This trip has no ticket.")
        return pack.saveTicket(trip, repository.downloadTicket(path))
    }

    suspend fun attachTicket(trip: Trip, fileName: String, mimeType: String, bytes: ByteArray): Trip {
        val path = repository.uploadTicket(trip, fileName, mimeType, bytes)
        val updated = trip.copy(ticketPath = path)
        pack.saveTicket(updated, bytes)
        pack.update(updated)
        return updated
    }

    /** Downloads the tickets of upcoming trips in the background, for the ride without signal. */
    private fun prefetchTickets(trips: List<Trip>) {
        scope.launch {
            trips.filter { it.ticketPath != null && pack.ticketFile(it) == null }.forEach { trip ->
                runCatching { ticket(trip) }
            }
        }
    }
}
