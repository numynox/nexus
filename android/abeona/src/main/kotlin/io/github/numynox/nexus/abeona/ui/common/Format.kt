package io.github.numynox.nexus.abeona.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Times and dates the way a station board writes them: 24-hour "08:12",
 * whatever the phone's clock style — every railway in Abeona's coverage does,
 * and a board-style time is what people compare against on the platform.
 */
object TripFormat {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun time(instant: Instant?): String = instant?.atZone(zone)?.format(clock) ?: "--:--"

    /** "Today", "Tomorrow", "Yesterday", else "Tue 3 Nov". */
    fun day(instant: Instant, today: LocalDate = LocalDate.now(zone)): String {
        val date = instant.atZone(zone).toLocalDate()
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.ENGLISH).format(date)
        }
    }

    /** "Tuesday, 3 November" — a header's date. */
    fun longDay(instant: Instant): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH).format(instant.atZone(zone))

    /** "2 h 05 min", "45 min". */
    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h ${"%02d".format(m)} min"
        }
    }

    /** "in 12 min", "in 2 h 05 min", "now". */
    fun relative(minutes: Long): String = if (minutes <= 0) "now" else "in ${duration(minutes)}"

    fun platform(platform: String?): String? = platform?.trim()?.takeIf { it.isNotEmpty() }?.let { "Pl. $it" }

    fun price(cents: Int): String = "%.2f €".format(Locale.GERMANY, cents / 100.0)
}

/**
 * DB Navigator: where Deutschlandtickets, digital BahnCards and live seat
 * reservations live. Opens the app, else its store page, else the web page.
 */
object DbNavigator {
    const val PACKAGE = "de.hafas.android.db"

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        val attempts = listOfNotNull(
            launch,
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")),
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next way in.
            }
        }
    }
}
