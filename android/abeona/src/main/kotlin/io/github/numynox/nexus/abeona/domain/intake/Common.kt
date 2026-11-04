package io.github.numynox.nexus.abeona.domain.intake

import java.time.LocalDate

// Small text rules both intake parsers share; app/abeona/src/lib/intake/common.ts.

/** Lines without form feeds, non-breaking spaces or trailing blanks; empty lines are kept. */
internal fun textLines(text: String): List<String> =
    text.replace("\r\n", "\n").replace('\r', '\n').replace('\u000c', '\n').replace(' ', ' ')
        .split('\n').map { it.trimEnd() }

private val PlatformRe = Regex("""^\s*(\d{1,3}[a-z]?|[A-Z]{1,2})""")

/** "Gleis 4 C-F" → "4", "12a" → "12a": the platform without its sectors. */
fun platformOf(raw: String?): String? = raw?.trim()?.let { PlatformRe.find(it)?.groupValues?.get(1) }

private val ProductRe = Regex("""^([A-Za-zÄÖÜäöü]{1,5})(?=\s|\d|$)""")

/** "RE19" → "RE", "ICE 728" → "ICE"; null without letters. */
fun productOf(line: String): String? = ProductRe.find(line.trim())?.groupValues?.get(1)?.uppercase()

/** Upper case, no spaces: "RE19", "RE 19" and "re19" are the same line. */
fun lineKey(line: String?): String = (line ?: "").uppercase().replace(Regex("""\s+"""), "")

internal fun isoDate(day: String, month: String, year: String): String? {
    val d = day.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val y = (if (year.length == 2) "20$year" else year).toIntOrNull() ?: return null
    if (d !in 1..31 || m !in 1..12 || y < 2000) return null
    return "%04d-%02d-%02d".format(y, m, d)
}

internal fun hhmm(raw: String): String? {
    val match = Regex("""^(\d{1,2}):(\d{2})$""").find(raw.trim()) ?: return null
    val h = match.groupValues[1].toInt()
    val m = match.groupValues[2].toInt()
    if (h > 23 || m > 59) return null
    return "%02d:%s".format(h, match.groupValues[2])
}

internal fun nextDay(date: String): String = LocalDate.parse(date).plusDays(1).toString()

/** "117,40" / "1.117,40" → 11740. */
internal fun cents(raw: String): Int? {
    val match = Regex("""^(\d{1,3}(?:\.\d{3})*|\d+),(\d{2})$""").find(raw.trim()) ?: return null
    return match.groupValues[1].replace(".", "").toInt() * 100 + match.groupValues[2].toInt()
}

internal fun squash(text: String): String = text.replace(Regex("""\s+"""), " ").trim()
