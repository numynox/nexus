package io.github.numynox.nexus.abeona.live

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.numynox.nexus.abeona.data.Leg
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * What the train's own Wi-Fi says: the strongest sign that this phone is on
 * a given train, with its speed and next stop. Read from DB's ICE Portal or
 * ÖBB's Railnet while the phone is on that Wi-Fi; neither is a public API,
 * so every field is optional and anything unexpected reads as "not on board"
 * (decisions/2026-11-03-abeona-fare-rules-and-device-signals.md).
 */
data class OnboardStatus(
    /** "ICE Portal" or "ÖBB Railnet", for the hero's small print. */
    val portal: String,
    /** "ICE", "RJX". */
    val trainType: String?,
    /** "25", "860": the number on the board, not the trainset. */
    val trainNumber: String?,
    val speedKmh: Double?,
    val lat: Double?,
    val lon: Double?,
    val nextStop: String?,
    /** The next stop's expected arrival, else its planned one. */
    val nextStopAt: Instant?,
    val nextTrack: String?,
    /** When this was read. */
    val at: Instant,
) {
    val trainLabel: String? get() = listOfNotNull(trainType, trainNumber).joinToString(" ").ifEmpty { null }
}

/** Where the phone asks; replaced in screenshot tests. */
fun interface OnboardSource {
    suspend fun status(): OnboardStatus?
}

private val Lenient = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive
private fun JsonElement?.str(): String? = prim()?.takeIf { it.isString || it.content.isNotBlank() }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonElement?.num(): Double? = prim()?.let { it.doubleOrNull ?: it.content.trim().replace(',', '.').toDoubleOrNull() }
private fun JsonElement?.long(): Long? = prim()?.longOrNull

/** A train number without its padding: "0597" → "597". */
private fun number(value: String?): String? = value?.filter { it.isDigit() }?.trimStart('0')?.ifEmpty { null }

/**
 * DB's ICE Portal: `api1/rs/status` (speed, position, train type) and
 * `api1/rs/tripInfo/trip` (train number, stops). Null unless the status looks
 * like one, so a captive portal or an unrelated answer is never "on board".
 */
fun parseIcePortal(statusJson: String, tripJson: String?, at: Instant): OnboardStatus? {
    val status = runCatching { Lenient.parseToJsonElement(statusJson).obj() }.getOrNull() ?: return null
    val speed = status["speed"].num()
    val lat = status["latitude"].num()
    val lon = status["longitude"].num()
    if (speed == null && lat == null) return null
    val trip = tripJson?.let { runCatching { Lenient.parseToJsonElement(it).obj()?.get("trip").obj() }.getOrNull() }
    val nextEva = trip?.get("stopInfo").obj()?.let { it["actualNext"].str() ?: it["scheduledNext"].str() }
    val stops = (trip?.get("stops") as? JsonArray).orEmpty().mapNotNull { it.obj() }
    val next = stops.firstOrNull { it["station"].obj()?.get("evaNr").str() == nextEva }
        ?: stops.firstOrNull { it["info"].obj()?.get("passed")?.prim()?.content == "false" }
    val timetable = next?.get("timetable").obj()
    val arrivalMs = timetable?.let { it["actualArrivalTime"].long() ?: it["scheduledArrivalTime"].long() }
    return OnboardStatus(
        portal = "ICE Portal",
        trainType = trip?.get("trainType").str() ?: status["trainType"].str(),
        trainNumber = number(trip?.get("vzn").str()),
        speedKmh = speed,
        lat = lat,
        lon = lon,
        nextStop = next?.get("station").obj()?.get("name").str(),
        nextStopAt = arrivalMs?.let(Instant::ofEpochMilli),
        nextTrack = next?.get("track").obj()?.let { it["actual"].str() ?: it["scheduled"].str() },
        at = at,
    )
}

/** A Railnet name: a plain string or {"de": …, "en": …}. */
private fun JsonElement?.label(): String? = str() ?: obj()?.let { it["en"].str() ?: it["de"].str() ?: it.values.firstOrNull().str() }

/** "12:34" today in Austria, or an ISO instant. */
private fun railnetTime(value: String?, today: LocalDate, zone: ZoneId): Instant? {
    val v = value ?: return null
    runCatching { return Instant.parse(v) }
    return runCatching { LocalTime.parse(v).atDate(today).atZone(zone).toInstant() }.getOrNull()
}

/**
 * ÖBB's Railnet: `assets/media/fis/combined.json` (train, next station) and
 * `api/speed`. Null unless the train is named.
 */
fun parseRailnet(combinedJson: String, speedText: String?, at: Instant): OnboardStatus? {
    val fis = runCatching { Lenient.parseToJsonElement(combinedJson).obj() }.getOrNull() ?: return null
    val type = fis["trainType"].str()
    val number = number(fis["tripNumber"].str() ?: fis["trainNumber"].str())
    if (type == null && number == null) return null
    val zone = ZoneId.of("Europe/Vienna")
    val next = fis["nextStation"].obj()
    val arrival = next?.get("arrival").obj()
    val speed = speedText?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: fis["speed"].num() ?: fis["latestSpeed"].num()
    return OnboardStatus(
        portal = "ÖBB Railnet",
        trainType = type,
        trainNumber = number,
        speedKmh = speed,
        lat = fis["latitude"].num(),
        lon = fis["longitude"].num(),
        nextStop = next?.get("name").label(),
        nextStopAt = railnetTime(arrival?.get("forecast").str() ?: arrival?.get("scheduled").str(), at.atZone(zone).toLocalDate(), zone),
        nextTrack = next?.get("track").label(),
        at = at,
    )
}

/**
 * The ride of [legs] the on-board Wi-Fi names: same train number, and the
 * same type when both are known ("ICE 25" for ICE + 25). Null when none fits.
 */
fun matchOnboardLeg(legs: List<Leg>, status: OnboardStatus?): Int? {
    val wanted = number(status?.trainNumber) ?: return null
    val type = status?.trainType?.uppercase()
    return legs.indices.firstOrNull { i ->
        val leg = legs[i]
        if (leg.isWalk) return@firstOrNull false
        val name = leg.lineName ?: return@firstOrNull false
        val legNumber = number(name.substringAfterLast(' ').takeIf { ' ' in name } ?: name.dropWhile { !it.isDigit() })
        val legType = leg.product?.uppercase()
        legNumber == wanted && (type == null || legType == null || legType == type || type.startsWith(legType) || legType.startsWith(type))
    }
}

/**
 * Asks the portals over the Wi-Fi network itself — a train's Wi-Fi often has
 * no validated internet, so Android would send ordinary requests over mobile
 * data, where the portals do not answer. Short timeouts, at most one portal
 * answering, every failure is "not on board".
 */
class OnboardWifi(private val context: Context) : OnboardSource {
    override suspend fun status(): OnboardStatus? = withContext(Dispatchers.IO) {
        val network = wifi() ?: return@withContext null
        val now = Instant.now()
        runCatching {
            get(network, ICE_STATUS)?.let { status -> parseIcePortal(status, get(network, ICE_TRIP), now) }
        }.getOrNull() ?: runCatching {
            get(network, RAILNET_COMBINED)?.let { combined -> parseRailnet(combined, get(network, RAILNET_SPEED), now) }
        }.getOrNull()
    }

    private fun wifi(): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    private fun get(network: Network, url: String): String? {
        val connection = network.openConnection(URL(url)) as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) return null
            connection.inputStream.bufferedReader().use { it.readText().take(MAX_CHARS) }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 2_500
        private const val MAX_CHARS = 512 * 1024
        const val ICE_STATUS = "https://iceportal.de/api1/rs/status"
        const val ICE_TRIP = "https://iceportal.de/api1/rs/tripInfo/trip"
        const val RAILNET_COMBINED = "https://railnet.oebb.at/assets/media/fis/combined.json"
        const val RAILNET_SPEED = "https://railnet.oebb.at/api/speed"
    }
}
