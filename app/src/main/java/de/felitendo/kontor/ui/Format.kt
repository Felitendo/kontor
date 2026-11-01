package de.felitendo.kontor.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Fmt {
    val locale: Locale = Locale.GERMANY
    private val symbols = DecimalFormatSymbols(locale)
    private val money = DecimalFormat("#,##0.00", symbols)
    private val moneyRound = DecimalFormat("#,##0", symbols)
    private val pct = DecimalFormat("#,##0.00", symbols)
    private val rate = DecimalFormat("#,##0.0##", symbols)
    private val qty = DecimalFormat("#,##0.######", symbols)
    private val oz = DecimalFormat("#,##0.###", symbols)

    const val HIDDEN = "••••••"

    fun eur(v: Double, hidden: Boolean = false, decimals: Boolean = true): String =
        if (hidden) "$HIDDEN\u00A0€" else (if (decimals) money else moneyRound).format(v) + "\u00A0€"

    /** Number part only, e.g. "12.345,67". */
    fun number(v: Double): String = money.format(v)

    fun signedEur(v: Double, hidden: Boolean = false): String =
        if (hidden) "$HIDDEN\u00A0€" else (if (v >= 0) "+" else "−") + money.format(abs(v)) + "\u00A0€"

    fun pct(v: Double): String = pct.format(abs(v)) + "\u00A0%"

    fun signedPct(v: Double): String = (if (v >= 0) "+" else "−") + pct.format(abs(v)) + "\u00A0%"

    fun rate(v: Double): String = rate.format(v) + "\u00A0%"

    /** Averages: never more than two decimals. */
    fun avgRate(v: Double): String = pct.format(v) + "\u00A0%"

    fun qty(v: Double): String = qty.format(v)

    fun ounces(v: Double): String = oz.format(v) + "\u00A0oz"

    private val dateFmt = DateTimeFormatter.ofPattern("d. MMMM yyyy", locale)
    private val shortDate = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)
    private val dayTime = DateTimeFormatter.ofPattern("d. MMM, HH:mm", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFmt)
    fun shortDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDate)
    fun monthYear(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(monthYear)

    fun timestamp(millis: Long, intraday: Boolean): String {
        val z = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return if (intraday) z.format(dayTime) else z.toLocalDate().format(dateFmt)
    }

    fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(time)

    fun relative(millis: Long): String {
        val diff = (System.currentTimeMillis() - millis) / 1000
        return when {
            millis <= 0 -> "noch nie"
            diff < 60 -> "gerade eben"
            diff < 3600 -> "vor ${diff / 60} Min."
            diff < 86400 -> "vor ${diff / 3600} Std."
            else -> "am " + Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(shortDate)
        }
    }

    fun months(m: Int): String = when {
        m % 12 == 0 && m >= 12 -> if (m == 12) "1 Jahr" else "${m / 12} Jahre"
        m == 1 -> "1 Monat"
        else -> "$m Monate"
    }

    /** Parses keypad input like "1.234,5" into a double. */
    fun parse(input: String): Double? = input.replace(".", "").replace(',', '.').toDoubleOrNull()
}
