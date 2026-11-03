package io.github.numynox.nexus.abeona.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.core.data.NexusJson
import io.github.numynox.nexus.core.testing.FakeSupabase
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A Supabase that answers everything Abeona asks with a plausible household:
 * a trip in under an hour with a delay, a tight change and its train binding
 * lifted, two more later on, a year of statistics, and an Edge Function that
 * finds stations and connections. Times are relative to now.
 */
object FakeBackend {
    val config = FakeSupabase.config("Abeona")
    const val HOUSEHOLD = "22222222-2222-4222-8222-222222222222"
    const val NEXT = "33333333-3333-4333-8333-000000000001"
    const val SAVED = "33333333-3333-4333-8333-000000000099"

    /** No trips at all: the empty state. */
    @Volatile var noTrips = false
    /** The network is gone: every database and function request fails. */
    @Volatile var offline = false

    private val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
    private fun at(minutes: Long) = now.plus(minutes, ChronoUnit.MINUTES).toString()

    private data class Place(val id: String, val name: String, val lat: Double, val lon: Double)

    private val frankfurt = Place("de-DELFI_de:06412:10", "Frankfurt(Main)Hbf", 50.1071, 8.6636)
    private val aschaffenburg = Place("asch", "Aschaffenburg Hbf", 49.9805, 9.1436)
    private val wuerzburg = Place("de-DELFI_de:09663:478", "Würzburg Hbf", 49.8018, 9.9355)
    private val schweinfurt = Place("sw", "Schweinfurt Hbf", 50.0353, 10.2125)
    private val hassfurt = Place("hf", "Haßfurt", 50.0311, 10.5104)
    private val bamberg = Place("de-DELFI_de:09461:4000", "Bamberg", 49.9007, 10.8994)
    private val nuernberg = Place("nbg", "Nürnberg Hbf", 49.4456, 11.0827)
    private val muenchen = Place("muc", "München Hbf", 48.1402, 11.5600)
    private val berlin = Place("ber", "Berlin Hbf", 52.5251, 13.3694)
    private val hamburg = Place("ham", "Hamburg Hbf", 53.5530, 10.0069)
    private val koeln = Place("koe", "Köln Hbf", 50.9430, 6.9589)
    private val amsterdam = Place("ams", "Amsterdam Centraal", 52.3791, 4.9003)

    private fun stop(p: Place, planned: Long, current: Long? = null, platform: String? = null, live: String? = null) =
        StopTime(p.id, p.name, p.lat, p.lon, at(planned), current?.let(::at), platform, live)

    private fun line(vararg places: Place) = places.map { listOf(it.lon, it.lat) }

    private fun ride(
        product: String,
        name: String,
        headsign: String,
        from: StopTime,
        to: StopTime,
        via: List<Pair<Place, Long>> = emptyList(),
        realtime: Boolean = true,
        operator: String = "DB Fernverkehr AG",
    ) = Leg(
        mode = LegMode.Train, product = product, lineName = name, tripRef = "ref-$name-${from.planned}", headsign = headsign,
        operator = operator, from = from, to = to,
        stopovers = via.map { (p, m) -> Stopover(p.id, p.name, p.lat, p.lon, at(m), if (realtime) at(m + 4) else null, at(m + 1), if (realtime) at(m + 5) else null) },
        polyline = listOf(listOf(from.lon!!, from.lat!!)) + via.map { (p, _) -> listOf(p.lon, p.lat) } + listOf(listOf(to.lon!!, to.lat!!)),
        realtime = realtime,
    )

    /** In 50 minutes: +7 out of Frankfurt, a tight change in Würzburg, +22 at Bamberg. */
    private val nextLegs = listOf(
        ride(
            "ICE", "ICE 25", "Wien Hbf",
            stop(frankfurt, 50, 57, "8"), stop(wuerzburg, 118, 125, "4"),
            via = listOf(aschaffenburg to 78L),
        ),
        ride(
            "RE", "RE 10", "Bamberg",
            stop(wuerzburg, 131, 131, "6", "5"), stop(bamberg, 203, 225, "3"),
            via = listOf(schweinfurt to 160L, hassfurt to 176L), operator = "DB Regio Bayern",
        ),
    )

    private val trips = listOf(
        Trip(
            NEXT, HOUSEHOLD, "Visit grandma in Bamberg", "planned", frankfurt.name, bamberg.name, at(50), at(203),
            io.github.numynox.nexus.abeona.data.FareType.Saver, true, 2, 3490, "$HOUSEHOLD/$NEXT/sparpreis.png", "tok-1", at(-1), nextLegs,
        ),
        Trip(
            "33333333-3333-4333-8333-000000000002", HOUSEHOLD, "Hamburg conference", "planned", berlin.name, hamburg.name, at(2 * 1440 + 120), at(2 * 1440 + 233),
            io.github.numynox.nexus.abeona.data.FareType.Flex, false, 1, 8990, null, "tok-2", null,
            listOf(ride("ICE", "ICE 1009", "Hamburg-Altona", stop(berlin, 2 * 1440 + 120, platform = "14"), stop(hamburg, 2 * 1440 + 233, platform = "7"), realtime = false)),
        ),
        Trip(
            "33333333-3333-4333-8333-000000000003", HOUSEHOLD, null, "planned", koeln.name, amsterdam.name, at(9 * 1440), at(9 * 1440 + 160),
            io.github.numynox.nexus.abeona.data.FareType.Saver, true, 2, 2790, null, "tok-3", null,
            listOf(ride("ICE", "ICE 126", "Amsterdam Centraal", stop(koeln, 9 * 1440, platform = "5"), stop(amsterdam, 9 * 1440 + 160, platform = "10b"), realtime = false, operator = "NS International")),
        ),
    )

    /** Frankfurt to Munich, in four ways. */
    private val journeys: List<Journey> by lazy {
        val base = 75L
        listOf(
            Journey(refreshToken = "j1", legs = listOf(
                ride("ICE", "ICE 599", "München Hbf", stop(frankfurt, base, base + 3, "9"), stop(muenchen, base + 212, base + 215, "18"), via = listOf(wuerzburg to base + 70, nuernberg to base + 130)),
            )),
            Journey(refreshToken = "j2", legs = listOf(
                ride("ICE", "ICE 1101", "Nürnberg Hbf", stop(frankfurt, base + 20, platform = "7"), stop(nuernberg, base + 150, platform = "8"), via = listOf(wuerzburg to base + 90)),
                Leg(mode = LegMode.Walk, from = stop(nuernberg, base + 150), to = stop(nuernberg, base + 154)),
                ride("ICE", "ICE 1007", "München Hbf", stop(nuernberg, base + 162, platform = "9"), stop(muenchen, base + 222, platform = "22")),
            )),
            Journey(refreshToken = "j3", legs = listOf(
                ride("ICE", "ICE 25", "Wien Hbf", stop(frankfurt, base + 45, base + 52, "8"), stop(wuerzburg, base + 113, base + 120, "4")),
                ride("RE", "RE 54", "Nürnberg Hbf", stop(wuerzburg, base + 125, platform = "6"), stop(nuernberg, base + 200, platform = "17"), operator = "DB Regio Bayern"),
                ride("RE", "RE 1", "München Hbf", stop(nuernberg, base + 212, platform = "20"), stop(muenchen, base + 320, platform = "27"), operator = "DB Regio Bayern"),
            )),
            Journey(refreshToken = "j4", legs = listOf(
                ride("IC", "IC 2295", "Stuttgart Hbf", stop(frankfurt, base + 60, platform = "12"), stop(muenchen, base + 330, platform = "13"), realtime = false, operator = "DB Fernverkehr AG"),
            )),
        )
    }

    private val savedTrip: Trip by lazy {
        val j = journeys.first()
        Trip(SAVED, HOUSEHOLD, null, "planned", j.legs.first().from.name, j.legs.last().to.name, j.legs.first().from.planned!!, j.legs.last().to.planned!!,
            io.github.numynox.nexus.abeona.data.FareType.Saver, true, 2, 4990, null, "j1", null, j.legs)
    }

    private val stations = listOf(frankfurt, aschaffenburg, wuerzburg, bamberg, nuernberg, muenchen, berlin, hamburg, koeln) +
        listOf(Place("fsud", "Frankfurt(Main)Süd", 50.0995, 8.6863), Place("ffl", "Frankfurt(M) Flughafen Fernbf", 50.0532, 8.5698), Place("mpas", "München Ost", 48.1273, 11.6046))

    fun engine(): HttpClientEngine {
        val inner = FakeSupabase.engine(::route)
        val handler = inner.config.requestHandlers.first()
        return MockEngine { request ->
            val path = request.url.encodedPath
            if (offline && (path.startsWith("/rest/") || path.startsWith("/functions/") || path.startsWith("/storage/"))) {
                throw IOException("No network")
            }
            handler(this, request)
        }
    }

    val shownTrips: List<Trip> get() = if (noTrips) emptyList() else trips

    private fun route(request: HttpRequestData, path: String): Any? {
        val params = request.url.parameters
        return when {
            FakeSupabase.isDownload(request, path, "abeona-tickets") -> FakeSupabase.StorageFile(ticketPng, "image/png")
            path.endsWith("/rpc/abeona_ensure_household") -> "\"$HOUSEHOLD\""
            path.endsWith("/rpc/abeona_save_trip") -> "\"$SAVED\""
            path.endsWith("/rpc/abeona_list_household_members") -> buildJsonArray {
                addJsonObject { put("user_id", FakeSupabase.USER_ID); put("email", FakeSupabase.EMAIL); put("full_name", "Jannis"); put("role", "owner") }
                addJsonObject { put("user_id", "u2"); put("email", "alex@example.com"); put("full_name", "Alex"); put("role", "member") }
            }
            path.endsWith("/rpc/abeona_statistics") -> statistics()
            path.endsWith("/rpc/abeona_update_trip_live") -> "null"
            path.endsWith("/rest/v1/abeona_travellers") -> buildJsonArray {
                addJsonObject { put("id", "tr1"); put("household_id", HOUSEHOLD); put("name", "Jannis"); put("user_id", FakeSupabase.USER_ID) }
                addJsonObject { put("id", "tr2"); put("household_id", HOUSEHOLD); put("name", "Alex"); put("user_id", "u2") }
                addJsonObject { put("id", "tr3"); put("household_id", HOUSEHOLD); put("name", "Mila") }
            }
            path.endsWith("/rest/v1/abeona_trips") && request.method == HttpMethod.Get -> {
                val id = params["id"]?.removePrefix("eq.")
                val all = shownTrips + savedTrip
                buildJsonArray {
                    (if (id != null) all.filter { it.id == id } else shownTrips).forEach { add(tripRow(it)) }
                }
            }
            path.endsWith("/functions/v1/abeona-transit") -> transit(NexusJson.decodeFromString(request.text()))
            else -> null
        }
    }

    private fun transit(body: JsonObject): Any? = when (body["action"]?.jsonPrimitive?.content) {
        "locations" -> {
            val q = body["query"]?.jsonPrimitive?.content.orEmpty().lowercase()
            buildJsonObject {
                putJsonArray("stations") {
                    stations.filter { it.name.lowercase().startsWith(q) || it.name.lowercase().contains(" $q") }.forEach {
                        addJsonObject { put("id", it.id); put("name", it.name); put("lat", it.lat); put("lon", it.lon); put("country", "DE") }
                    }
                }
            }
        }
        "journeys" -> buildJsonObject {
            put("journeys", NexusJson.encodeToJsonElement(journeys))
            put("next_cursor", "next-1")
            put("previous_cursor", "prev-1")
        }
        "refresh" -> {
            // The live values the trips already carry: what the provider says now.
            val token = body["refresh_token"]?.jsonPrimitive?.content
            val trip = (trips + savedTrip).firstOrNull { it.refreshToken == token }
            buildJsonObject {
                put("journey", null as String?)
                put("legs", NexusJson.encodeToJsonElement(trip?.legs.orEmpty()))
            }
        }
        else -> null
    }

    private fun statistics() = buildJsonObject {
        val month = LocalDate.now().monthValue
        put("trips", 38); put("km", 14210); put("minutes_on_train", 9420); put("rides", 61)
        put("delay_minutes", 412); put("punctual_share", 0.711); put("longest_km", 812); put("max_delay_minutes", 58); put("stations", 27)
        putJsonObject("top_route") { put("from", frankfurt.name); put("to", wuerzburg.name); put("count", 9) }
        putJsonObject("top_station") { put("name", frankfurt.name); put("count", 31) }
        putJsonObject("products") { put("ICE", 28); put("RE", 19); put("IC", 6); put("S", 5); put("RB", 3) }
        putJsonArray("by_month") {
            val trips = listOf(2, 3, 5, 3, 4, 6, 2, 4, 3, 5, 1, 0)
            (1..month).forEach { m ->
                addJsonObject { put("month", "%d-%02d".format(LocalDate.now().year, m)); put("trips", trips[m - 1]); put("km", trips[m - 1] * 370) }
            }
        }
    }

    /** A plausible DB ticket: header, route, and a big 2D code to scan. */
    val ticketPng: ByteArray by lazy {
        val w = 1080; val h = 1500
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(Color.WHITE)
        val red = Paint().apply { color = Color.rgb(0xEC, 0x00, 0x16) }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1E, 0x1E, 0x1E); textSize = 46f; typeface = Typeface.DEFAULT_BOLD }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x44, 0x44, 0x44); textSize = 34f }
        c.drawRect(60f, 60f, 200f, 150f, red)
        c.drawText("Online-Ticket", 230f, 125f, ink)
        c.drawText("Frankfurt(Main)Hbf → Bamberg", 60f, 240f, ink)
        c.drawText("Super Sparpreis · 2. Klasse · 1 Erwachsener", 60f, 300f, text)
        c.drawText("Zugbindung: ICE 25, RE 10", 60f, 350f, text)
        val random = Random(7)
        val cell = 24f; val size = 27; val left = (w - cell * size) / 2; val top = 440f
        val black = Paint().apply { color = Color.BLACK }
        for (y in 0 until size) for (x in 0 until size) {
            val ring = maxOf(kotlin.math.abs(x - size / 2), kotlin.math.abs(y - size / 2))
            val on = if (ring <= 6) ring % 2 == 0 else random.nextBoolean()
            if (on) c.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell, top + (y + 1) * cell, black)
        }
        c.drawText("Auftragsnummer 4F7Q2K · Gültig mit Lichtbildausweis", 60f, 1200f, text)
        c.drawRect(60f, 1260f, w - 60f, 1264f, text)
        c.drawText("Preis 34,90 €", 60f, 1330f, ink)
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}

private fun HttpRequestData.text(): String = when (val b = body) {
    is TextContent -> b.text
    is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
    else -> "{}"
}

/** A trip as PostgREST returns it: abeona_trips with its abeona_legs embedded. */
private fun tripRow(trip: Trip) = buildJsonObject {
    put("id", trip.id); put("household_id", trip.householdId); put("title", trip.title); put("status", trip.status)
    put("origin_name", trip.originName); put("destination_name", trip.destinationName)
    put("planned_departure", trip.plannedDeparture); put("planned_arrival", trip.plannedArrival)
    put("fare_type", trip.fareType.wire); put("train_binding", trip.trainBinding); put("travel_class", trip.travelClass)
    put("price_cents", trip.priceCents); put("ticket_path", trip.ticketPath); put("refresh_token", trip.refreshToken); put("refreshed_at", trip.refreshedAt)
    putJsonArray("abeona_legs") {
        trip.legs.forEachIndexed { idx, leg ->
            addJsonObject {
                put("idx", idx); put("mode", leg.mode.name.lowercase()); put("product", leg.product); put("line_name", leg.lineName)
                put("trip_ref", leg.tripRef); put("headsign", leg.headsign); put("operator", leg.operator)
                put("from_id", leg.from.id); put("from_name", leg.from.name); put("from_lat", leg.from.lat); put("from_lon", leg.from.lon)
                put("to_id", leg.to.id); put("to_name", leg.to.name); put("to_lat", leg.to.lat); put("to_lon", leg.to.lon)
                put("planned_departure", leg.from.planned); put("departure", leg.from.current)
                put("planned_arrival", leg.to.planned); put("arrival", leg.to.current)
                put("planned_departure_platform", leg.from.plannedPlatform); put("departure_platform", leg.from.platform)
                put("planned_arrival_platform", leg.to.plannedPlatform); put("arrival_platform", leg.to.platform)
                put("cancelled", leg.cancelled); put("realtime", leg.realtime)
                put("stopovers", NexusJson.encodeToJsonElement(leg.stopovers))
                put("polyline", NexusJson.encodeToJsonElement(leg.polyline))
            }
        }
    }
}
