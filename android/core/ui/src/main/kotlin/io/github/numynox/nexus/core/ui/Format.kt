package io.github.numynox.nexus.core.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Numbers and dates the way the phone's locale writes them. The UI text is
 * English (glossary.md); separators follow the device, as Android users expect.
 */
object NexusFormat {
    private val locale: Locale get() = Locale.getDefault()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun number(value: Double, decimals: Int = 0): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)

    fun euro(value: Double, decimals: Int = 2): String = "${number(value, decimals)} €"
    fun km(value: Double): String = "${number(value)} km"
    fun liters(value: Double, decimals: Int = 1): String = "${number(value, decimals)} L"
    fun percent(ratio: Double): String = "${number(ratio * 100)} %"

    /** "+3.25 €", with a sign, for differences. */
    fun signedEuro(value: Double): String = (if (value >= 0) "+" else "−") + euro(abs(value))

    fun date(instant: Instant): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(instant.atZone(zone))

    fun date(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    fun time(instant: Instant): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

    fun dateTime(instant: Instant): String = "${date(instant)}, ${time(instant)}"

    fun monthShort(date: LocalDate): String =
        DateTimeFormatter.ofPattern("MMM yy", locale).format(date)

    fun weekdayDay(date: LocalDate): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(date)

    /** "2 h 15 min", "45 min". */
    fun duration(hours: Double): String {
        val totalMinutes = (hours * 60).toLong()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }
}
