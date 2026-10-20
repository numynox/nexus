package io.github.numynox.nexus.annona.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How urgent an expiration date is — what colours its badge. */
enum class Urgency { Expired, Today, Week, Month, Later, Undated }

fun urgencyOf(date: LocalDate?, today: LocalDate = LocalDate.now()): Urgency {
    if (date == null) return Urgency.Undated
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> Urgency.Expired
        days == 0L -> Urgency.Today
        days <= 7 -> Urgency.Week
        days <= 30 -> Urgency.Month
        else -> Urgency.Later
    }
}

/**
 * "3 days left", "2 months left", "5 days ago", "Expires today", "Tomorrow" —
 * the web's badge (ProductDetail.svelte), worded out for a phone.
 */
fun expiryLabel(date: LocalDate?, today: LocalDate = LocalDate.now()): String {
    if (date == null) return "No date"
    val days = ChronoUnit.DAYS.between(today, date)
    val span = when (val abs = kotlin.math.abs(days)) {
        in 0..59 -> if (abs == 1L) "1 day" else "$abs days"
        in 60..729 -> "${abs / 30} months"
        else -> "${abs / 365} years"
    }
    return when {
        days == 0L -> "Expires today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Since yesterday"
        days < 0 -> "$span ago"
        else -> "$span left"
    }
}

/** Groups for the pantry's "use soon" list, in the order they are shown. */
enum class ShelfLife(val title: String) {
    Expired("Expired"),
    Week("This week"),
    Month("This month"),
    Later("Later"),
    Undated("No date"),
}

fun shelfLifeOf(date: LocalDate?, today: LocalDate = LocalDate.now()): ShelfLife = when (urgencyOf(date, today)) {
    Urgency.Expired -> ShelfLife.Expired
    Urgency.Today, Urgency.Week -> ShelfLife.Week
    Urgency.Month -> ShelfLife.Month
    Urgency.Later -> ShelfLife.Later
    Urgency.Undated -> ShelfLife.Undated
}

/** The web's add form picks a month, and the date is its first day; Android keeps that, plus exact dates. */
fun firstOfMonth(year: Int, month: Int): LocalDate = LocalDate.of(year, month, 1)
