package io.github.numynox.nexus.abeona.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import io.github.numynox.nexus.abeona.data.BahnCard
import io.github.numynox.nexus.abeona.data.Journey
import io.github.numynox.nexus.abeona.data.Leg
import io.github.numynox.nexus.abeona.data.LegMode
import io.github.numynox.nexus.abeona.data.StopTime
import io.github.numynox.nexus.abeona.data.Stopover
import io.github.numynox.nexus.abeona.data.Trip
import io.github.numynox.nexus.abeona.live.OnboardStatus
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.jsonObject
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
    const val CARD_JANNIS = "44444444-4444-4444-8444-000000000001"
    const val PAST_LATE = "33333333-3333-4333-8333-000000000011"
    const val PAST_MISSED = "33333333-3333-4333-8333-000000000012"
    const val PAST_DTICKET = "33333333-3333-4333-8333-000000000013"
    const val PAST_NOPRICE = "33333333-3333-4333-8333-000000000014"

    /** No trips at all: the empty state. */
    @Volatile var noTrips = false
    /** The network is gone: every database and function request fails. */
    @Volatile var offline = false
    /** What the train's Wi-Fi says; null is "not on a train's Wi-Fi". */
    @Volatile var onboard: OnboardStatus? = null

    /**
     * The timetable intake asks while set: station ids by exact name, and
     * journeys by "fromId>toId@instant" — a "matching" case of
     * intake.fixtures.json (IntakeScreenshots).
     */
    @Volatile var intakeTimetable: Pair<Map<String, String>, Map<String, List<Journey>>>? = null

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

    /**
     * After the ride: yesterday's trip to grandma, 72 minutes late and final
     * (eligible); last week's, where the RE was missed and an IC taken (claimed);
     * a Deutschlandticket ride (€1.50, under the minimum, to pool) and a Flexpreis
     * without a price.
     */
    private val pastTrips: List<Trip> by lazy {
        val y = -1440L
        val late = listOf(
            ride("ICE", "ICE 25", "Wien Hbf", stop(frankfurt, y + 50, y + 52, "8"), stop(wuerzburg, y + 118, y + 121, "4")),
            ride("RE", "RE 10", "Bamberg", stop(wuerzburg, y + 131, y + 133, "6"), stop(bamberg, y + 203, y + 275, "3"), operator = "DB Regio Bayern"),
        )
        val w = -6 * 1440L
        val missedLegs = listOf(
            ride("ICE", "ICE 25", "Wien Hbf", stop(frankfurt, w + 50, w + 58, "8"), stop(wuerzburg, w + 118, w + 140, "4")),
            ride("RE", "RE 10", "Bamberg", stop(wuerzburg, w + 131, w + 131, "6"), stop(bamberg, w + 203, w + 203, "3"), operator = "DB Regio Bayern"),
        )
        val replacement = listOf(ride("IC", "IC 2063", "Nürnberg Hbf", stop(wuerzburg, w + 200, w + 204, "3"), stop(bamberg, w + 256, w + 268, "4")))
        val d = -3 * 1440L
        listOf(
            Trip(PAST_LATE, HOUSEHOLD, "Visit grandma in Bamberg", "planned", frankfurt.name, bamberg.name, at(y + 50), at(y + 203),
                io.github.numynox.nexus.abeona.data.FareType.Saver, true, 2, 3490, null, "tok-p1", at(y + 290), late,
                finalizedAt = at(y + 290)),
            Trip(PAST_MISSED, HOUSEHOLD, "Back from grandma", "planned", frankfurt.name, bamberg.name, at(w + 50), at(w + 203),
                io.github.numynox.nexus.abeona.data.FareType.Saver, true, 2, 4290, null, "tok-p2", at(w + 290), missedLegs,
                finalizedAt = at(w + 290), missedLegIdx = 1, replacementLegs = replacement),
            Trip(PAST_DTICKET, HOUSEHOLD, null, "planned", nuernberg.name, bamberg.name, at(d + 600), at(d + 660),
                io.github.numynox.nexus.abeona.data.FareType.Deutschlandticket, false, 2, null, null, null, at(d + 735),
                listOf(ride("RE", "RE 42", "Bamberg", stop(nuernberg, d + 600, d + 604, "17"), stop(bamberg, d + 660, d + 726, "5"), operator = "DB Regio Bayern")),
                finalizedAt = at(d + 735)),
            Trip(PAST_NOPRICE, HOUSEHOLD, "Cologne meeting", "planned", frankfurt.name, koeln.name, at(-2 * 1440 + 480), at(-2 * 1440 + 545),
                io.github.numynox.nexus.abeona.data.FareType.Flex, false, 2, null, null, null, at(-2 * 1440 + 640),
                listOf(ride("ICE", "ICE 120", "Köln Hbf", stop(frankfurt, -2 * 1440 + 480, -2 * 1440 + 482, "18"), stop(koeln, -2 * 1440 + 545, -2 * 1440 + 640, "4"))),
                finalizedAt = at(-2 * 1440 + 640)),
        )
    }

    private fun compensation(id: String) = buildJsonObject {
        fun base(verdict: String, amount: Int?, percent: Int?, delay: Int, trip: Trip) {
            put("trip_id", id); put("verdict", verdict); put("amount_cents", amount); put("percent", percent)
            put("delay_minutes", delay); put("delay_source", if (trip.replacementLegs != null) "replacement" else "final")
            put("planned_arrival", trip.plannedArrival)
            put("actual_arrival", (trip.replacementLegs ?: trip.legs).last().to.current)
            put("threshold_minutes", 60)
            put("deadline", trip.arrivalAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusMonths(3).toString())
            put("deadline_passed", false)
            put("claim_url", "https://www.bahn.de/service/informationen-buchung/fahrgastrechte")
        }
        val trip = pastTrips.first { it.id == id }
        when (id) {
            PAST_LATE -> {
                base("eligible", 873, 25, 72, trip); put("poolable", false)
                putJsonArray("reasons") { add("From 60 minutes late at the destination, 25 % of the fare is paid back.") }
                putJsonArray("hints") {}
                put("summary_de", "Fahrgastrechte: Verspätung am Zielbahnhof\nReise: Frankfurt(Main)Hbf → Bamberg\nVerspätung am Ziel: 72 Minuten\nFahrpreis: 34,90 €")
            }
            PAST_MISSED -> {
                base("eligible", 1073, 25, 65, trip); put("poolable", false)
                putJsonArray("reasons") { add("From 60 minutes late at the destination, 25 % of the fare is paid back.") }
                putJsonArray("hints") {}
                put("summary_de", "Fahrgastrechte: Verspätung am Zielbahnhof\nStattdessen gefahren: IC 2063")
            }
            PAST_DTICKET -> {
                base("below_minimum", 150, null, 66, trip); put("poolable", true)
                putJsonArray("reasons") {
                    add("A Deutschlandticket gets €1.50 for each arrival 60 minutes or more late."); add("Amounts under €4 are not paid out.")
                }
                putJsonArray("hints") {
                    add("At most 25 % of the ticket's monthly price in all.")
                    add("With a season ticket, collect several delays and claim them together once they add up to €4.")
                }
                put("summary_de", "Fahrgastrechte: Verspätung am Zielbahnhof\nFahrkarte: Deutschlandticket, 2. Klasse")
            }
            else -> {
                base("unknown_price", null, 25, 95, trip); put("poolable", false)
                putJsonArray("reasons") {
                    add("From 60 minutes late at the destination, 25 % of the fare is paid back."); add("Add what the ticket cost to work out the amount.")
                }
                putJsonArray("hints") {}
                put("summary_de", "Fahrgastrechte: Verspätung am Zielbahnhof\nReise: Frankfurt(Main)Hbf → Köln Hbf")
            }
        }
    }

    private fun claimEntry(trip: Trip, verdict: String, amount: Int?, delay: Int, status: String) = buildJsonObject {
        val deadline = trip.arrivalAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusMonths(3)
        put("trip_id", trip.id); put("title", trip.title); put("origin_name", trip.originName); put("destination_name", trip.destinationName)
        put("planned_departure", trip.plannedDeparture); put("delay_minutes", delay); put("verdict", verdict); put("amount_cents", amount)
        put("deadline", deadline.toString()); put("days_left", java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline)); put("status", status)
    }

    private fun claimsOverview() = buildJsonObject {
        val byId = pastTrips.associateBy { it.id }
        putJsonArray("to_claim") {
            add(claimEntry(byId.getValue(PAST_LATE), "eligible", 873, 72, "open"))
            add(claimEntry(byId.getValue(PAST_NOPRICE), "unknown_price", null, 95, "open"))
            add(claimEntry(byId.getValue(PAST_DTICKET), "below_minimum", 150, 66, "open"))
        }
        putJsonArray("waiting") { add(claimEntry(byId.getValue(PAST_MISSED), "eligible", 1073, 65, "claimed")) }
        put("earned_cents", 2350); put("paid", 2)
    }

    private fun claimFor(tripId: String?) = buildJsonArray {
        if (tripId == PAST_MISSED) {
            addJsonObject {
                put("id", "55555555-5555-4555-8555-000000000001"); put("trip_id", PAST_MISSED); put("status", "claimed")
                put("amount_cents", 1073); put("claimed_at", at(-5 * 1440))
            }
        }
    }

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

    private val today = LocalDate.now()

    /**
     * Jannis's BahnCard 50 renews unless cancelled within twelve days (the
     * Trips banner); Alex's BahnCard 25 ran out yesterday (a trip warning);
     * Mila has none.
     */
    val bahnCards = listOf(
        BahnCard(
            CARD_JANNIS, "tr1", 50, 2, today.minusDays(325).toString(), today.plusDays(40).toString(), "7081 4111 2233 4455",
            subscription = true, noticeWeeks = 4, filePath = "$HOUSEHOLD/bahncards/$CARD_JANNIS/bahncard-50.png",
        ),
        BahnCard(
            "44444444-4444-4444-8444-000000000002", "tr2", 25, 2, today.minusYears(1).toString(), today.minusDays(1).toString(),
            subscription = false,
        ),
    )

    private val deadlines = buildJsonArray {
        addJsonObject {
            put("card_id", CARD_JANNIS); put("traveller_id", "tr1"); put("traveller_name", "Jannis"); put("kind", 50)
            put("travel_class", 2); put("valid_to", today.plusDays(40).toString()); put("notice_weeks", 4)
            put("deadline", today.plusDays(12).toString()); put("days_left", 12)
        }
    }

    /** What abeona_trip_warnings says about the next trip, in its own words. */
    private val warnings = buildJsonArray {
        addJsonObject {
            put("kind", "expired"); put("traveller_id", "tr2"); put("traveller_name", "Alex")
            put("card_id", "44444444-4444-4444-8444-000000000002")
            put("message", "Alex's BahnCard 25 expires on ${java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH).format(today.minusDays(1))}, before this trip.")
        }
        addJsonObject {
            put("kind", "no_card"); put("traveller_id", "tr3"); put("traveller_name", "Mila"); put("message", "Mila has no BahnCard 25.")
        }
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
            FakeSupabase.isDownload(request, path, "abeona-tickets") && "/bahncards/" in path -> FakeSupabase.StorageFile(bahnCardPng, "image/png")
            FakeSupabase.isDownload(request, path, "abeona-tickets") -> FakeSupabase.StorageFile(ticketPng, "image/png")
            path.endsWith("/rpc/abeona_bahncard_deadlines") -> deadlines
            path.endsWith("/rpc/abeona_compensation") -> pastTrips.firstOrNull { request.text().contains(it.id) }?.let { compensation(it.id) } ?: "null"
            path.endsWith("/rpc/abeona_claims_overview") -> claimsOverview()
            path.endsWith("/rest/v1/abeona_claims") && request.method == HttpMethod.Get -> claimFor(params["trip_id"]?.removePrefix("eq."))
            path.endsWith("/rest/v1/abeona_claims") -> buildJsonObject {
                put("id", "55555555-5555-4555-8555-000000000002"); put("trip_id", PAST_LATE); put("status", "claimed"); put("claimed_at", at(0))
            }
            path.endsWith("/rpc/abeona_trip_warnings") -> if (request.text().contains(NEXT)) warnings else "[]"
            path.endsWith("/rpc/abeona_save_trip_travellers") -> "null"
            path.endsWith("/rest/v1/abeona_trip_travellers") -> buildJsonArray {
                // Everyone on the trip to grandma, on one ticket with a BahnCard 25,
                // a BahnCard 50 and one without; the saver alone on the others.
                val riders = if (params["trip_id"]?.removePrefix("eq.") == NEXT) listOf("tr1" to 25, "tr2" to 50, "tr3" to null) else listOf("tr1" to null)
                riders.forEach { (id, discount) -> addJsonObject { put("traveller_id", id); put("bahncard_discount", discount) } }
            }
            path.endsWith("/rest/v1/abeona_bahncards") && request.method == HttpMethod.Get -> NexusJson.encodeToJsonElement(bahnCards)
            path.endsWith("/rest/v1/abeona_bahncards") && request.method != HttpMethod.Delete -> NexusJson.encodeToJsonElement(bahnCards.first())
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
                val all = shownTrips + savedTrip + pastTrips
                buildJsonArray {
                    (if (id != null) all.filter { it.id == id } else shownTrips).forEach { add(tripRow(it)) }
                }
            }
            path.endsWith("/functions/v1/abeona-transit") -> transit(NexusJson.decodeFromString(request.text()))
            path.endsWith("/functions/v1/abeona-import") -> imported()
            path.endsWith("/rpc/abeona_travel_map") -> travelMap()
            path.endsWith("/rest/v1/abeona_share_links") && request.method == HttpMethod.Get -> buildJsonArray {
                addJsonObject {
                    put("token", "3f9c2a7e1b5d4c8a9e0f1a2b3c4d5e6f7a8b"); put("trip_id", NEXT); put("kind", "status")
                    put("expires_at", at(60 * 30))
                }
            }
            path.endsWith("/rest/v1/abeona_share_links") && request.method == HttpMethod.Post -> buildJsonObject {
                val kind = if (request.text().contains("\"ics\"")) "ics" else "status"
                put("token", "9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f"); put("trip_id", NEXT); put("kind", kind)
                put("expires_at", at(60 * 24 * 32))
            }
            path.endsWith("/rest/v1/abeona_share_links") -> "[]"
            else -> null
        }
    }

    private fun intakeTransit(timetable: Pair<Map<String, String>, Map<String, List<Journey>>>, body: JsonObject): Any? =
        when (body["action"]?.jsonPrimitive?.content) {
            "locations" -> buildJsonObject {
                val q = body["query"]?.jsonPrimitive?.content.orEmpty()
                putJsonArray("stations") {
                    timetable.first[q]?.let { id -> addJsonObject { put("id", id); put("name", q); put("lat", 0.0); put("lon", 0.0) } }
                }
            }
            "journeys" -> buildJsonObject {
                val from = body["from"]?.jsonPrimitive?.content
                val to = body["to"]?.jsonPrimitive?.content
                val time = body["time"]?.jsonPrimitive?.content?.let(Instant::parse)
                val found = timetable.second.entries.firstOrNull { (key, _) ->
                    val (route, at) = key.split("@")
                    route == "$from>$to" && Instant.parse(at) == time
                }?.value.orEmpty()
                put("journeys", NexusJson.encodeToJsonElement(found))
                put("next_cursor", null as String?)
                put("previous_cursor", null as String?)
            }
            else -> null
        }

    private fun transit(body: JsonObject): Any? = intakeTimetable?.let { intakeTransit(it, body) } ?: when (body["action"]?.jsonPrimitive?.content) {
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
        "alternatives" -> alternatives()
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

    /**
     * From Würzburg to Bamberg after the change: the saver's binding is lifted
     * (+22), so IC and RE count; a FlixTrain never does; a bus Abeona cannot
     * classify is "check".
     */
    private fun alternatives() = buildJsonObject {
        putJsonObject("allowance") {
            put("trip_id", NEXT); put("fare_type", "saver"); put("known", true); put("long_distance", true)
            putJsonArray("excluded_products") { add("FLX") }
            put("train_bound", false); put("binding_lifted", true); put("arrival_delay_minutes", 22)
            putJsonArray("booked_lines") { add("ICE 25"); add("RE 10") }
            put("hint", "A Sparpreis is bound to the booked trains. Once DB expects you 20 minutes late or cancels a train, any train on your route counts. Check the terms on your ticket.")
        }
        val bus = Place("wue-bus", "Würzburg Busbahnhof", 49.8011, 9.9349)
        val options = listOf(
            "valid" to "Covered: the train binding is lifted" to listOf(ride("RE", "RE 10", "Bamberg", stop(wuerzburg, 131, 131, "6", "5"), stop(bamberg, 203, 225, "3"), operator = "DB Regio Bayern")),
            "valid" to "Covered: the train binding is lifted" to listOf(ride("IC", "IC 2063", "Nürnberg Hbf", stop(wuerzburg, 140, platform = "3"), stop(bamberg, 196, platform = "4"), realtime = false)),
            "invalid" to "Not covered by your ticket: FLX 1243" to listOf(ride("FLX", "FLX 1243", "Berlin Hbf", stop(wuerzburg, 151, platform = "1"), stop(bamberg, 199, platform = "2"), operator = "FlixTrain")),
            "check" to "Abeona cannot tell what kind of train this is. Check the terms on your ticket." to listOf(
                Leg(mode = LegMode.Walk, from = stop(wuerzburg, 135), to = stop(bus, 140)),
                Leg(mode = LegMode.Bus, lineName = "8045", from = stop(bus, 145), to = stop(bamberg, 250), operator = "OVF"),
            ),
        )
        putJsonArray("alternatives") {
            options.forEach { (meta, legs) ->
                addJsonObject {
                    put("provider", "transitous"); put("refresh_token", null as String?)
                    put("legs", NexusJson.encodeToJsonElement(legs))
                    put("validity", meta.first); put("validity_note", meta.second)
                }
            }
        }
    }

    /** The year's map: a handful of stretches, the commute most often. */
    private fun travelMap() = buildJsonArray {
        fun line(a: Place, b: Place, count: Int, product: String, bend: Double = 0.0) = addJsonObject {
            putJsonObject("from") { put("name", a.name); put("lat", a.lat); put("lon", a.lon) }
            putJsonObject("to") { put("name", b.name); put("lat", b.lat); put("lon", b.lon) }
            put("product", product); put("count", count)
            putJsonArray("polyline") {
                (0..12).forEach { i ->
                    val t = i / 12.0
                    val lift = bend * kotlin.math.sin(t * Math.PI)
                    addJsonArray { add(a.lon + (b.lon - a.lon) * t); add(a.lat + (b.lat - a.lat) * t + lift) }
                }
            }
        }
        line(frankfurt, wuerzburg, 18, "ICE", 0.08)
        line(wuerzburg, bamberg, 9, "RE", 0.05)
        line(bamberg, nuernberg, 4, "RE", -0.03)
        line(nuernberg, muenchen, 3, "ICE", 0.1)
        line(frankfurt, koeln, 3, "ICE", 0.12)
        line(frankfurt, berlin, 2, "ICE", 0.3)
        line(berlin, hamburg, 1, "ICE", 0.1)
        line(muenchen, Place("sbg", "Salzburg Hbf", 47.8131, 13.0458), 1, "RJX", 0.05)
    }

    /** Träwelling's check-ins as abeona-import answers them: two new trips, one saved already. */
    private fun imported() = buildJsonObject {
        fun ride(line: String, product: String, a: Place, b: Place, dep: Long, arr: Long, late: Long) = buildJsonObject {
            put("mode", "train"); put("product", product); put("line_name", line)
            putJsonObject("from") { put("name", a.name); put("lat", a.lat); put("lon", a.lon); put("planned", at(dep)); put("current", at(dep + 1)) }
            putJsonObject("to") { put("name", b.name); put("lat", b.lat); put("lon", b.lon); put("planned", at(arr)); put("current", at(arr + late)) }
            put("distance_km", 120.5)
        }
        fun trip(id: String, legs: List<JsonObject>, duplicate: Boolean, delay: Int?) = buildJsonObject {
            put("external_id", "traewelling:$id")
            put("origin", legs.first()["from"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            put("destination", legs.last()["to"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            put("planned_departure", legs.first()["from"]!!.jsonObject["planned"]!!.jsonPrimitive.content)
            put("planned_arrival", legs.last()["to"]!!.jsonObject["planned"]!!.jsonPrimitive.content)
            put("arrival_delay_minutes", delay)
            put("legs", JsonArray(legs)); put("duplicate", duplicate)
        }
        val d = -60L * 24 * 40
        putJsonArray("trips") {
            add(trip("101,102", listOf(ride("ICE 25", "ICE", frankfurt, wuerzburg, d, d + 68, 0), ride("RE 10", "RE", wuerzburg, bamberg, d + 80, d + 140, 17)), false, 17))
            add(trip("117", listOf(ride("ICE 597", "ICE", muenchen, nuernberg, d + 2900, d + 2962, 0)), false, null))
            add(trip("120", listOf(ride("ICE 28", "ICE", wuerzburg, frankfurt, d + 4400, d + 4468, 4)), true, 4))
        }
        put("statuses", 4); put("skipped", 0); put("truncated", false)
    }

    private fun statistics() = buildJsonObject {
        val month = LocalDate.now().monthValue
        put("trips", 38); put("km", 14210); put("minutes_on_train", 9420); put("rides", 61)
        put("delay_minutes", 412); put("punctual_share", 0.711); put("longest_km", 812); put("max_delay_minutes", 58); put("stations", 27)
        putJsonObject("top_route") { put("from", frankfurt.name); put("to", wuerzburg.name); put("count", 9) }
        putJsonObject("top_station") { put("name", frankfurt.name); put("count", 31) }
        putJsonObject("products") { put("ICE", 28); put("RE", 19); put("IC", 6); put("S", 5); put("RB", 3) }
        put("co2_saved_kg", 1679.4); put("co2_train_kg", 679.5); put("co2_car_kg", 2358.9)
        putJsonObject("co2_factors") { put("car", 166); put("source", "Umweltbundesamt (TREMOD), reference year 2022; approximate") }
        putJsonArray("countries") { add("DE"); add("AT") }
        putJsonObject("longest_trip") { put("trip_id", NEXT); put("from", berlin.name); put("to", muenchen.name); put("km", 812); put("date", "2026-07-18") }
        putJsonArray("by_month") {
            val trips = listOf(2, 3, 5, 3, 4, 6, 2, 4, 3, 5, 1, 0)
            (1..month).forEach { m ->
                addJsonObject { put("month", "%d-%02d".format(LocalDate.now().year, m)); put("trips", trips[m - 1]); put("km", trips[m - 1] * 370) }
            }
        }
    }

    /** A plausible digital BahnCard: the card in DB's colours, its number and a 2D code. */
    val bahnCardPng: ByteArray by lazy {
        val w = 1080; val h = 1300
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(Color.WHITE)
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xEC, 0x00, 0x16) }
        c.drawRoundRect(60f, 60f, w - 60f, 640f, 48f, 48f, card)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 120f; typeface = Typeface.DEFAULT_BOLD }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 40f }
        c.drawText("BahnCard 50", 120f, 260f, white)
        c.drawText("2. Klasse · Jannis", 120f, 340f, small)
        c.drawText("7081 4111 2233 4455", 120f, 560f, small)
        val random = Random(11)
        val cell = 20f; val size = 27; val left = (w - cell * size) / 2; val top = 720f
        val black = Paint().apply { color = Color.BLACK }
        for (y in 0 until size) for (x in 0 until size) {
            val ring = maxOf(kotlin.math.abs(x - size / 2), kotlin.math.abs(y - size / 2))
            val on = if (ring <= 6) ring % 2 == 0 else random.nextBoolean()
            if (on) c.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell, top + (y + 1) * cell, black)
        }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
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
    put("bahncard_discount", trip.bahncardDiscount)
    put("finalized_at", trip.finalizedAt); put("missed_leg_idx", trip.missedLegIdx); put("force_majeure", trip.forceMajeure)
    put("replacement_legs", trip.replacementLegs?.let { NexusJson.encodeToJsonElement(it) } ?: kotlinx.serialization.json.JsonNull)
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
