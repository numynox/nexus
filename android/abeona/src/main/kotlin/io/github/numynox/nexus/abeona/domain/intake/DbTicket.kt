package io.github.numynox.nexus.abeona.domain.intake

// A DB Online-Ticket's text layer → fare, class, travellers, BahnCard,
// binding, rides and price. The Kotlin twin of
// app/abeona/src/lib/intake/dbTicket.ts; both run intake.fixtures.json.
// Tolerant of spacing: PdfBox leaves one space where pdftotext keeps columns.
// A ticket for both directions has a stop table for each and is read as two
// trips that share the ticket (parseDbTickets).

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
/** What the fare name says about the direction, removed from the name: also "(Hin- und Rückfahrt)". */
private val FareDirection = Regex("""\((?:Hinfahrt|Rückfahrt|Hin-\s*und\s*Rückfahrt)\)""", RegexOption.IGNORE_CASE)
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

/** Null when the text is not a ticket Abeona can read anything from. The first trip of [parseDbTickets]. */
fun parseDbTicket(text: String): ParsedTicket? = parseDbTickets(text).firstOrNull()

/** One stop table: the rides of one direction. */
private class Table(val direction: String?, val date: String?, val rows: MutableList<Row> = mutableListOf())

private class Booked(val line: String, val date: String?)

/**
 * Every trip the ticket is for: one, or two for a ticket with both directions
 * (a stop table each). They share the fare, class, travellers and the PDF;
 * the price is split between them. Empty when there is nothing to read.
 */
fun parseDbTickets(text: String): List<ParsedTicket> {
    val all = textLines(text).map { it.trim() }

    // The fare is the title right above "Klasse …".
    val classIdx = all.indexOfFirst { ClassLine.containsMatchIn(it) && ClassRe.containsMatchIn(it) }
    var fareLine: String? = null
    if (classIdx > 0) fareLine = (classIdx - 1 downTo 0).map { all[it] }.firstOrNull { it.isNotEmpty() }
    if (fareLine == null) fareLine = all.firstOrNull { it.length < 60 && fareOf(it).fareType != io.github.numynox.nexus.abeona.data.FareType.Other }
    val direction = fareLine?.let { Direction.find(it)?.groupValues?.get(1) }
    val fareName = fareLine?.let { squash(it.replace(FareDirection, "")) }
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

    // "Zugbindung  ICE 17, 15:14 Uhr am 16.11.2025" and the lines below it; a
    // ticket for both directions has a "Zugbindung" for each.
    val booked = mutableListOf<Booked>()
    var start = 0
    while (start < all.size) {
        if (BindingStart.containsMatchIn(all[start])) {
            var i = start
            while (i < all.size) {
                val found = BoundTrain.findAll(all[i]).toList()
                if (found.isEmpty()) break
                for (m in found) {
                    val date = if (m.groupValues[3].isNotEmpty()) isoDate(m.groupValues[3], m.groupValues[4], m.groupValues[5]) else null
                    booked += Booked(squash(m.groupValues[1]), date)
                }
                start = i
                i++
            }
        }
        start++
    }
    val bindingDate = booked.firstNotNullOfOrNull { it.date }

    val price = all.firstNotNullOfOrNull { PriceRe.find(it) }?.let { cents(it.groupValues[1]) }
    val anyDate = all.firstNotNullOfOrNull { AnyDate.find(it) }?.let { isoDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }

    // The stop tables, each under a title that gives its direction and date.
    val tables = mutableListOf<Table>()
    var titleDirection: String? = null
    var titleDate: String? = null
    var index = 0
    while (index < all.size) {
        val t = TableTitle.find(all[index])
        if (t != null) {
            titleDirection = t.groupValues[1].takeIf { it.isNotEmpty() }?.let { if (it.equals("hinfahrt", ignoreCase = true)) "outward" else "return" }
            titleDate = isoDate(t.groupValues[2], t.groupValues[3], t.groupValues[4])
            index++
            continue
        }
        if (!TableHead.containsMatchIn(all[index])) {
            index++
            continue
        }
        val table = Table(titleDirection, titleDate)
        val context = table.date ?: bindingDate ?: anyDate
        var i = index + 1
        while (i < all.size) {
            val m = RowRe.find(all[i])
            if (m == null) {
                if (table.rows.isNotEmpty()) break
                i++
                continue
            }
            i++
            val time = hhmm(m.groupValues[6]) ?: continue
            val date = rowDate(m.groupValues[2], m.groupValues[3], m.groupValues[4], context) ?: continue
            val rest = RowRest.find(m.groupValues[7].trim())
            table.rows += Row(
                station = squash(m.groupValues[1]),
                date = date,
                kind = m.groupValues[5],
                time = time,
                platform = rest?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.let(::platformOf),
                product = rest?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.let(::squash),
                note = rest?.groupValues?.get(3)?.let(::squash)?.takeIf { it.isNotEmpty() },
            )
        }
        tables += table
        titleDirection = null
        titleDate = null
        index = i
    }
    // A ticket without a stop table still says what it is.
    if (tables.isEmpty()) tables += Table(null, null)

    val two = tables.size > 1
    return tables.mapIndexedNotNull { n, table ->
        // With two directions, each binds to the trains booked for its day, or that it rides.
        val own = if (two) {
            val rides = legsOf(table.rows, booked.map { it.line })
            booked.filter { b -> if (b.date != null) rides.any { it.date == b.date } else rides.any { it.line == b.line } }.map { it.line }
        } else {
            booked.map { it.line }
        }
        val legs = legsOf(table.rows, own)
        if (fareName == null && legs.isEmpty()) return@mapIndexedNotNull null
        val reservations = legs.filter { it.reservation != null }.map { "${it.line}: ${it.reservation}" }
        ParsedTicket(
            fareName = fareName,
            fareType = fare.fareType,
            direction = if (two) table.direction else direction?.let { if (it.equals("hinfahrt", ignoreCase = true)) "outward" else "return" },
            travelClass = travelClass,
            travellers = travellers,
            discounts = discounts.toList(),
            trainBinding = own.isNotEmpty() || fare.binding,
            bookedLines = own,
            origin = legs.firstOrNull()?.from,
            destination = legs.lastOrNull()?.to,
            date = legs.firstOrNull()?.date ?: table.date ?: bindingDate,
            legs = legs,
            // The whole booking's price, split evenly between the directions; the first takes an odd cent.
            priceCents = if (price == null || !two) price else price / tables.size + (if (n == 0) price % tables.size else 0),
            reservation = reservations.takeIf { it.isNotEmpty() }?.joinToString("; "),
        )
    }
}

/** "ab" and "an" rows → rides; a ride without a product in its row takes the next booked train. */
private fun legsOf(rows: List<Row>, booked: List<String>): List<ParsedLeg> {
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
    return legs
}
