package io.github.numynox.nexus.abeona.domain.intake

// A DB Online-Ticket's text layer → fare, class, travellers, BahnCard,
// binding, rides and price. The Kotlin twin of
// app/abeona/src/lib/intake/dbTicket.ts; both run intake.fixtures.json.
// Tolerant of spacing: PdfBox leaves one space where pdftotext keeps columns.

private val ClassRe = Regex("""\b([12])\.\s*Klasse\b""")
private val ClassLine = Regex("""^Klasse\b""", RegexOption.IGNORE_CASE)
private val TravellersRe = Regex("""^Reisende\b(.*)$""", RegexOption.IGNORE_CASE)
private val Persons = Regex("""(\d+)\s+(?:Person|Personen|Erwachsene|Erwachsener|Reisende)\b""", RegexOption.IGNORE_CASE)
private val BahnCardRe = Regex("""(?:(\d+)\s*x?\s*)?(?:BC|BahnCard)\s?(25|50|100)\b""", RegexOption.IGNORE_CASE)
private val BindingStart = Regex("""^Zugbindung\b""", RegexOption.IGNORE_CASE)
private val BoundTrain = Regex("""([A-Z]{1,5}\s?\d{1,6}),\s*(\d{1,2}:\d{2})\s*Uhr(?:\s+am\s+(\d{1,2})\.(\d{1,2})\.(\d{4}))?""")
private val PriceRe = Regex("""(?:Gesamtpreis|Summe|Endpreis)\s*:?\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+,\d{2})\s*(?:€|EUR)""", RegexOption.IGNORE_CASE)
private val TableHead = Regex("""^Halt\s+Datum\s+Zeit\b""", RegexOption.IGNORE_CASE)
private val TableTitle = Regex("""Reiseverbindung.*?(Hinfahrt|Rückfahrt)?\s+am\s+(\d{1,2})\.(\d{1,2})\.(\d{4})""", RegexOption.IGNORE_CASE)
private val RowRe = Regex("""^(.+?)\s+(\d{1,2})\.(\d{1,2})\.(\d{2,4})?\s+(ab|an)\s+(\d{1,2}:\d{2})\s*(.*)$""")
private val RowRest = Regex("""^(?:(\d{1,3}[a-z]?)(?=\s|$)\s*)?(?:([A-Z][A-Za-z]{0,4}\s?\d{1,6})(?=\s|$)\s*)?(.*)$""")
private val Direction = Regex("""\((Hinfahrt|Rückfahrt)\)""", RegexOption.IGNORE_CASE)
private val AnyDate = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")

/** Whether the text looks like a DB ticket: a "Klasse" and a stop table or "Online-Ticket". */
fun isDbTicket(text: String): Boolean {
    val all = textLines(text)
    return all.any { ClassRe.containsMatchIn(it) } &&
        (all.any { TableHead.containsMatchIn(it.trim()) } || Regex("Online-Ticket|Fahrkarte", RegexOption.IGNORE_CASE).containsMatchIn(text))
}

private data class Row(
    val station: String,
    val date: String,
    val kind: String,
    val time: String,
    val platform: String?,
    val product: String?,
    val note: String?,
)

private fun rowDate(day: String, month: String, year: String, context: String?): String? {
    if (year.isNotEmpty()) return isoDate(day, month, year)
    if (context == null) return null
    val y = context.substring(0, 4).toInt()
    val contextMonth = context.substring(5, 7).toInt()
    return isoDate(day, month, (if (month.toInt() < contextMonth) y + 1 else y).toString())
}

/** Null when the text is not a ticket Abeona can read anything from. */
fun parseDbTicket(text: String): ParsedTicket? {
    val all = textLines(text).map { it.trim() }

    // The fare is the title right above "Klasse …".
    val classIdx = all.indexOfFirst { ClassLine.containsMatchIn(it) && ClassRe.containsMatchIn(it) }
    var fareLine: String? = null
    if (classIdx > 0) fareLine = (classIdx - 1 downTo 0).map { all[it] }.firstOrNull { it.isNotEmpty() }
    if (fareLine == null) fareLine = all.firstOrNull { it.length < 60 && fareOf(it).fareType != io.github.numynox.nexus.abeona.data.FareType.Other }
    val direction = fareLine?.let { Direction.find(it)?.groupValues?.get(1) }
    val fareName = fareLine?.let { squash(it.replace(Direction, "")) }
    val fare = fareOf(fareName)

    val classMatch = (if (classIdx >= 0) ClassRe.find(all[classIdx]) else null) ?: all.firstNotNullOfOrNull { ClassRe.find(it) }
    val travelClass = classMatch?.groupValues?.get(1)?.toInt()

    var travellers: Int? = null
    val discounts = mutableListOf<Int?>()
    all.firstNotNullOfOrNull { TravellersRe.find(it) }?.let { line ->
        val rest = line.groupValues[1]
        travellers = Persons.find(rest)?.groupValues?.get(1)?.toInt()
        // "mit 1 BC25, 1 BC50": every group, in print order. A BahnCard 100 is
        // a fare of its own, not a discount.
        BahnCardRe.findAll(rest).filter { it.groupValues[2] != "100" }.forEach { card ->
            repeat(card.groupValues[1].toIntOrNull() ?: 1) { discounts += card.groupValues[2].toInt() }
        }
        travellers?.let { n -> while (discounts.size < n) discounts += null }
    }

    // "Zugbindung  ICE 17, 15:14 Uhr am 16.11.2025" and the lines below it.
    val booked = mutableListOf<String>()
    var bindingDate: String? = null
    val bindingIdx = all.indexOfFirst { BindingStart.containsMatchIn(it) }
    if (bindingIdx >= 0) {
        for (i in bindingIdx until all.size) {
            val found = BoundTrain.findAll(all[i]).toList()
            if (found.isEmpty()) break
            for (m in found) {
                booked += squash(m.groupValues[1])
                if (bindingDate == null && m.groupValues[3].isNotEmpty()) {
                    bindingDate = isoDate(m.groupValues[3], m.groupValues[4], m.groupValues[5])
                }
            }
        }
    }

    val price = all.firstNotNullOfOrNull { PriceRe.find(it) }?.let { cents(it.groupValues[1]) }

    val tableDate = all.firstNotNullOfOrNull { TableTitle.find(it) }?.let { isoDate(it.groupValues[2], it.groupValues[3], it.groupValues[4]) }
    val context = tableDate ?: bindingDate ?: all.firstNotNullOfOrNull { AnyDate.find(it) }?.let {
        isoDate(it.groupValues[1], it.groupValues[2], it.groupValues[3])
    }

    val rows = mutableListOf<Row>()
    val headIdx = all.indexOfFirst { TableHead.containsMatchIn(it) }
    if (headIdx >= 0) {
        for (i in headIdx + 1 until all.size) {
            val m = RowRe.find(all[i])
            if (m == null) {
                if (rows.isNotEmpty()) break
                continue
            }
            val time = hhmm(m.groupValues[6]) ?: continue
            val date = rowDate(m.groupValues[2], m.groupValues[3], m.groupValues[4], context) ?: continue
            val rest = RowRest.find(m.groupValues[7].trim())
            rows += Row(
                station = squash(m.groupValues[1]),
                date = date,
                kind = m.groupValues[5],
                time = time,
                platform = rest?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.let(::platformOf),
                product = rest?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.let(::squash),
                note = rest?.groupValues?.get(3)?.let(::squash)?.takeIf { it.isNotEmpty() },
            )
        }
    }

    val legs = mutableListOf<ParsedLeg>()
    var open: Row? = null
    for (row in rows) {
        if (row.kind == "ab") {
            open = row
            continue
        }
        val start = open ?: continue
        val line = start.product ?: booked.getOrNull(legs.size) ?: ""
        val arrivalDate = if (row.date < start.date || (row.date == start.date && row.time < start.time)) nextDay(start.date) else row.date
        legs += ParsedLeg(
            line = line,
            product = Regex("^([A-Z]{1,5})").find(line)?.groupValues?.get(1),
            from = start.station,
            to = row.station,
            date = start.date,
            departure = start.time,
            arrivalDate = arrivalDate,
            arrival = row.time,
            fromPlatform = start.platform,
            toPlatform = row.platform,
            reservation = start.note,
        )
        open = null
    }

    if (fareName == null && legs.isEmpty()) return null
    val reservations = legs.filter { it.reservation != null }.map { "${it.line}: ${it.reservation}" }
    return ParsedTicket(
        fareName = fareName,
        fareType = fare.fareType,
        direction = direction?.let { if (it.equals("hinfahrt", ignoreCase = true)) "outward" else "return" },
        travelClass = travelClass,
        travellers = travellers,
        discounts = discounts,
        trainBinding = booked.isNotEmpty() || fare.binding,
        bookedLines = booked,
        origin = legs.firstOrNull()?.from,
        destination = legs.lastOrNull()?.to,
        date = legs.firstOrNull()?.date ?: tableDate ?: bindingDate,
        legs = legs,
        priceCents = price,
        // One half of a return booking prints the booking's total: the user says what this trip cost.
        priceScope = if (direction != null) PriceScope.Booking else PriceScope.Trip,
        reservation = reservations.takeIf { it.isNotEmpty() }?.joinToString("; "),
    )
}
