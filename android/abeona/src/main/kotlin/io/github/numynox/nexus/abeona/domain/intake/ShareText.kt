package io.github.numynox.nexus.abeona.domain.intake

// DB Navigator's "Teilen" text → the rides it lists. The Kotlin twin of
// app/abeona/src/lib/intake/shareText.ts (the shape is documented there);
// both run the cases in intake.fixtures.json.

private val Depart = Regex("""^(?:ab|dep\.?|departure)\s+(\d{1,2}:\d{2})\s+(.+)$""", RegexOption.IGNORE_CASE)
private val Arrive = Regex("""^(?:an|arr\.?|arrival)\s+(\d{1,2}:\d{2})\s+(.+)$""", RegexOption.IGNORE_CASE)
private val Headsign = Regex("""^(?:nach|to|towards|direction)\s+(.+)$""", RegexOption.IGNORE_CASE)
private val PlatformSuffix = Regex("""^(.*?),\s*(?:gleis|platform|track|pl\.|bstg\.?)\s+(.+)$""", RegexOption.IGNORE_CASE)
private val DateRe = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")
private val LinkRe = Regex("""https?://\S+""")
private val Header = Regex("""^(.+?)\s*(?:→|->|➞|⇒)\s*(.+)$""")
private val Numbered = Regex("""^(.*?)\s*\((\d{1,6})\)\s*$""")
private val Vbid = Regex("""[?&#]vbid=([^&#\s]+)""")

private fun stationAndPlatform(rest: String): Pair<String, String?> =
    PlatformSuffix.find(rest)?.let { squash(it.groupValues[1]) to platformOf(it.groupValues[2]) } ?: (squash(rest) to null)

/** Whether the text looks like a DB Navigator share: an "Ab …" and an "An …" line. */
fun isShareText(text: String): Boolean {
    val all = textLines(text).map { it.trim() }
    return all.any { Depart.matches(it) } && all.any { Arrive.matches(it) }
}

/** Null when the text has no ride in it. */
fun parseShareText(text: String): ParsedShare? {
    var origin: String? = null
    var destination: String? = null
    var date: String? = null
    var link: String? = null
    val legs = mutableListOf<ParsedLeg>()
    var label: String? = null
    var headsign: String? = null
    var departure: Triple<String, String, String?>? = null
    // Times only move forward; one that goes back has crossed midnight.
    var day: String? = null
    var lastTime = ""

    fun advance(time: String): String {
        if (day != null && lastTime.isNotEmpty() && time < lastTime) day = nextDay(day!!)
        lastTime = time
        return day ?: ""
    }

    for (line in textLines(text).map { it.trim() }) {
        if (line.isEmpty()) continue
        LinkRe.find(line)?.let { url ->
            if (link == null) link = url.value
            continue
        }
        if (origin == null && legs.isEmpty() && departure == null) {
            val header = Header.find(line)
            if (header != null) {
                origin = squash(header.groupValues[1])
                destination = squash(header.groupValues[2])
                continue
            }
        }
        if (date == null) {
            val d = DateRe.find(line)
            if (d != null && !Depart.matches(line) && !Arrive.matches(line)) {
                date = isoDate(d.groupValues[1], d.groupValues[2], d.groupValues[3])
                day = date
                continue
            }
        }
        val dep = Depart.find(line)
        if (dep != null) {
            val time = hhmm(dep.groupValues[1]) ?: continue
            val (station, platform) = stationAndPlatform(dep.groupValues[2])
            departure = Triple(time, station, platform)
            continue
        }
        val arr = Arrive.find(line)
        if (arr != null) {
            val time = hhmm(arr.groupValues[1])
            val open = departure
            val name = label
            if (time == null || open == null || name == null) {
                departure = null
                continue
            }
            val (station, platform) = stationAndPlatform(arr.groupValues[2])
            val numbered = Numbered.find(name)
            val lineName = numbered?.let { squash(it.groupValues[1]) } ?: squash(name)
            val depDate = advance(open.first)
            val arrDate = advance(time)
            legs += ParsedLeg(
                line = lineName,
                product = productOf(lineName),
                number = numbered?.groupValues?.get(2),
                headsign = headsign,
                from = open.second,
                to = station,
                date = depDate,
                departure = open.first,
                arrivalDate = arrDate,
                arrival = time,
                fromPlatform = open.third,
                toPlatform = platform,
            )
            label = null
            headsign = null
            departure = null
            continue
        }
        val towards = Headsign.find(line)
        if (towards != null && label != null && departure == null) {
            headsign = squash(towards.groupValues[1])
            continue
        }
        // Anything else names the next ride.
        if (departure == null) {
            label = line
            headsign = null
        }
    }

    val start = date ?: return null
    if (legs.isEmpty()) return null
    return ParsedShare(
        origin = origin ?: legs.first().from,
        destination = destination ?: legs.last().to,
        date = start,
        legs = legs,
        link = link,
        vbid = link?.let { Vbid.find(it)?.groupValues?.get(1) },
    )
}
