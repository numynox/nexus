package io.github.numynox.nexus.abeona.domain.intake

// A Trenitalia ticket's text layer → fare, class, travellers, rides and price.
// The Kotlin twin of app/abeona/src/lib/intake/trenitaliaTicket.ts; both run
// intake.fixtures.json ("trenitalia"). One PDF holds one ticket per
// passenger, a page each with its own price: the rides they share are one
// ride, the prices add up. Read by labels, not columns, because PdfBox puts
// the station row, coach and seat in another order than pdftotext does
// (decisions/2026-11-12-abeona-trenitalia-and-two-way-tickets.md).

private val Title = Regex(
    """\b(?:REISE|VIAGGIO|JOURNEY|TRIP)\s+(?:von|da|from)\s+(.+?)\s+(?:nach|a|to)\s+(.+?)\s+(?:am|il|on)\s+(\d{1,2})/(\d{1,2})/(\d{4})\b""",
    RegexOption.IGNORE_CASE,
)
private val Train = Regex("""\b(?:Zug|Treno|Train)\s*:\s*([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ. ]*?)\s+(\d{1,5})\b""", RegexOption.IGNORE_CASE)
private val Time = Regex("""\b(?:Uhrzeit|Ora|Orario|Time)\s*:?\s*(\d{1,2}:\d{2})\s*-\s*(\d{1,2})/(\d{1,2})/(\d{4})""", RegexOption.IGNORE_CASE)
private val Coach = Regex("""\b(?:Wagen|Carrozza|Coach)\s*:\s*(\w{1,3})\b""", RegexOption.IGNORE_CASE)
private val Seats = Regex("""\b(?:Sitzpl[äa]tze?|Posti|Posto|Seats?)\s*:\s*(\d{1,3}[A-Z]?(?:\s*,\s*\d{1,3}[A-Z]?)*)""", RegexOption.IGNORE_CASE)
private val Service = Regex("""\b(?:Serviceleistung|Servizio|Service)\s*:\s*(?:([12])\s*°\s*)?([A-Za-z]+)?""", RegexOption.IGNORE_CASE)
private val Amount = Regex(
    """\b(?:Betrag|Importo|Amount|Prezzo|Price)\s*\*?\s*:\s*(\d{1,3}(?:[.,]\d{3})*[.,]\d{2}|\d+[.,]\d{2})\s*(?:€|EUR)""",
    RegexOption.IGNORE_CASE,
)
// The offers on the sample and their siblings; anything else is read as "other".
private val Offer = Regex("""\b(Super Economy|Economy|Base)\b""")

/** Trenitalia's train categories → the short names the timetable uses ("FR 9715"). */
private val Products = listOf(
    Regex("^frecciarossa", RegexOption.IGNORE_CASE) to "FR",
    Regex("^frecciargento", RegexOption.IGNORE_CASE) to "FA",
    Regex("^frecciabianca", RegexOption.IGNORE_CASE) to "FB",
    Regex("""^intercity\s*notte""", RegexOption.IGNORE_CASE) to "ICN",
    Regex("^intercity", RegexOption.IGNORE_CASE) to "IC",
    Regex("^eurocity", RegexOption.IGNORE_CASE) to "EC",
    Regex("^euronight", RegexOption.IGNORE_CASE) to "EN",
    Regex("""^regionale\s*veloce""", RegexOption.IGNORE_CASE) to "RV",
    Regex("^regionale", RegexOption.IGNORE_CASE) to "R",
)

private fun trainProduct(category: String): String {
    val name = squash(category)
    return Products.firstOrNull { it.first.containsMatchIn(name) }?.second ?: name.uppercase()
}

/** Whether the text looks like a Trenitalia ticket: the operator's name, a journey title and a train. */
fun isTrenitaliaTicket(text: String): Boolean =
    Regex("trenitalia", RegexOption.IGNORE_CASE).containsMatchIn(text) && Title.containsMatchIn(text) && Train.containsMatchIn(text)

/** One passenger's ticket: one page of the PDF. */
private class Block(
    val leg: ParsedLeg,
    val coach: String?,
    val seats: List<String>,
    val offer: String?,
    val travelClass: Int?,
    val cents: Int?,
)

private fun classOf(digit: String, level: String): Int? = when {
    digit.isNotEmpty() -> digit.toInt()
    level.matches(Regex("executive|business|prima", RegexOption.IGNORE_CASE)) -> 1
    level.matches(Regex("standard|seconda", RegexOption.IGNORE_CASE)) -> 2
    else -> null
}

private fun readBlock(text: String): Block? {
    val title = Title.find(text) ?: return null
    val train = Train.find(text) ?: return null
    val times = Time.findAll(text).toList()
    val departure = times.getOrNull(0)?.let { hhmm(it.groupValues[1]) } ?: return null
    val arrival = times.getOrNull(1)?.let { hhmm(it.groupValues[1]) } ?: return null
    val date = isoDate(times[0].groupValues[2], times[0].groupValues[3], times[0].groupValues[4]) ?: return null
    val arrivalDate = isoDate(times[1].groupValues[2], times[1].groupValues[3], times[1].groupValues[4])
        ?: if (arrival < departure) nextDay(date) else date

    val product = trainProduct(train.groupValues[1])
    val number = train.groupValues[2]
    val service = Service.find(text)
    return Block(
        leg = ParsedLeg(
            line = "$product $number",
            product = product,
            number = number,
            from = squash(title.groupValues[1]),
            to = squash(title.groupValues[2]),
            date = date,
            departure = departure,
            arrivalDate = arrivalDate,
            arrival = arrival,
        ),
        coach = Coach.find(text)?.groupValues?.get(1),
        seats = Seats.find(text)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList(),
        offer = Offer.find(text)?.groupValues?.get(1),
        travelClass = service?.let { classOf(it.groupValues[1], it.groupValues[2]) },
        // "15.90" or "1.234,50": the last two digits are the cents, whatever separates them.
        cents = Amount.find(text)?.groupValues?.get(1)?.filter { it.isDigit() }?.toInt(),
    )
}

/** "Coach 7, seats 10C, 10D". */
private fun reservationOf(coach: String?, seats: List<String>): String? {
    val parts = buildList {
        coach?.let { add("Coach $it") }
        if (seats.isNotEmpty()) add("${if (seats.size == 1) "seat" else "seats"} ${seats.joinToString(", ")}")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private class Ride(val leg: ParsedLeg, var coach: String?, val seats: MutableList<String>, var passengers: Int)

/** Null when the text is not a Trenitalia ticket Abeona can read a ride from. */
fun parseTrenitaliaTicket(text: String): ParsedTicket? {
    // Every journey title starts a passenger's ticket.
    val all = textLines(text)
    val starts = all.indices.filter { Title.containsMatchIn(all[it]) }
    val blocks = starts.mapIndexedNotNull { n, start ->
        readBlock(all.subList(start, starts.getOrElse(n + 1) { all.size }).joinToString("\n"))
    }
    if (blocks.isEmpty()) return null

    // The same ride on several passengers' tickets is one ride.
    val rides = LinkedHashMap<String, Ride>()
    for (b in blocks) {
        val key = "${b.leg.line}|${b.leg.date}|${b.leg.departure}"
        val ride = rides[key]
        if (ride == null) {
            rides[key] = Ride(b.leg, b.coach, b.seats.toMutableList(), 1)
        } else {
            ride.passengers++
            b.seats.filter { it !in ride.seats }.forEach { ride.seats += it }
            if (ride.coach == null) ride.coach = b.coach
        }
    }
    val legs = rides.values
        .map { it.leg.copy(reservation = reservationOf(it.coach, it.seats)) }
        .sortedBy { "${it.date} ${it.departure}" }
    val travellers = rides.values.maxOf { it.passengers }

    val fareName = blocks.firstNotNullOfOrNull { it.offer }
    val fare = fareOf(fareName)
    val priced = blocks.mapNotNull { it.cents }
    val reservations = legs.filter { it.reservation != null }.map { "${it.line}: ${it.reservation}" }

    return ParsedTicket(
        operator = "trenitalia",
        fareName = fareName,
        fareType = fare.fareType,
        travelClass = blocks.firstNotNullOfOrNull { it.travelClass },
        travellers = travellers,
        // No BahnCards on Trenitalia: one passenger each, none with a discount.
        discounts = List(travellers) { null },
        trainBinding = fare.binding,
        bookedLines = if (fare.binding) legs.map { it.line } else emptyList(),
        origin = legs.first().from,
        destination = legs.last().to,
        date = legs.first().date,
        legs = legs,
        // What the PDF cost: every passenger's ticket in it.
        priceCents = priced.takeIf { it.isNotEmpty() }?.sum(),
        reservation = reservations.takeIf { it.isNotEmpty() }?.joinToString("; "),
    )
}
