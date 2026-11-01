package de.felitendo.kontor.domain

import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.AssetKind
import de.felitendo.kontor.data.model.CashHolding
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.FixedDeposit
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.MetalHolding
import de.felitendo.kontor.data.model.OtherAsset
import de.felitendo.kontor.data.model.PensionContract
import de.felitendo.kontor.data.model.Position
import de.felitendo.kontor.data.model.SavingsAccount
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow

fun today(): Long = LocalDate.now().toEpochDay()
fun epochDayOf(millis: Long): Long = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

// ---------- Festgeld ----------

val FixedDeposit.maturityDay: Long?
    get() = termMonths.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(startDay).plusMonths(it.toLong()).toEpochDay() }

/** Simple interest within the first year, yearly compounding beyond (typical for German Festgeld). */
fun FixedDeposit.valueAt(day: Long): Double {
    val end = maturityDay
    val effective = when {
        day < startDay -> startDay
        end != null && day > end -> end
        else -> day
    }
    val years = (effective - startDay) / 365.0
    val r = rate / 100
    val whole = years.toInt()
    return amount * (1 + r).pow(whole) * (1 + r * (years - whole))
}

val FixedDeposit.maturityValue: Double? get() = maturityDay?.let { valueAt(it) }

fun FixedDeposit.progress(day: Long = today()): Float {
    val end = maturityDay ?: return 0f
    if (end <= startDay) return 1f
    return ((day - startDay).toFloat() / (end - startDay)).coerceIn(0f, 1f)
}

// ---------- Tagesgeld / Riester ----------

/**
 * Projects a balance from [fromDay] to [day]: monthly interest credit plus a monthly deposit.
 * Interest of the running month is accrued pro rata so the value moves smoothly.
 */
fun projectBalance(balance: Double, ratePct: Double, monthly: Double, fromDay: Long, day: Long, yearlyCompounding: Boolean = false): Double {
    if (day <= fromDay) return balance
    val r = ratePct / 100
    var b = balance
    var cursor = LocalDate.ofEpochDay(fromDay)
    val target = LocalDate.ofEpochDay(day)
    var pendingInterest = 0.0
    while (true) {
        val next = cursor.plusMonths(1)
        if (next.isAfter(target)) break
        if (yearlyCompounding) {
            pendingInterest += b * r / 12
            if (next.monthValue == 1) { b += pendingInterest; pendingInterest = 0.0 }
        } else {
            b += b * r / 12
        }
        b += monthly
        cursor = next
    }
    val partial = (target.toEpochDay() - cursor.toEpochDay()) / 365.0
    return b + pendingInterest + b * r * partial
}

fun SavingsAccount.valueAt(day: Long) = projectBalance(balance, rate, monthlyDeposit, balanceDay, day)

fun PensionContract.valueAt(day: Long) = projectBalance(balance, rate ?: 0.0, monthlyContribution, balanceDay, day, yearlyCompounding = true)

// ---------- Wertpapiere / Edelmetalle ----------

fun Position.value(cache: MarketCache): Double? = cache.quotes[instrument.isin]?.price?.let { it * quantity }

fun Position.dayChange(cache: MarketCache): Double? = cache.quotes[instrument.isin]?.change?.let { it * quantity }

fun Position.gain(cache: MarketCache): Double? {
    val buy = buyPrice ?: return null
    return value(cache)?.minus(buy * quantity)
}

/** Positions without a quote fall back to their buy-in so the total does not collapse offline. */
fun Depot.value(cache: MarketCache): Double = positions.sumOf { it.value(cache) ?: ((it.buyPrice ?: 0.0) * it.quantity) }

fun Depot.dayChange(cache: MarketCache): Double = positions.sumOf { it.dayChange(cache) ?: 0.0 }

fun Depot.invested(): Double? = positions.takeIf { p -> p.isNotEmpty() && p.all { it.buyPrice != null } }?.sumOf { it.buyPrice!! * it.quantity }

fun MetalHolding.value(cache: MarketCache): Double? = cache.metals[metal]?.price?.let { it * ounces }

fun MetalHolding.dayChange(cache: MarketCache): Double = cache.metals[metal]?.change?.let { it * ounces } ?: 0.0

// ---------- Allgemein ----------

fun Asset.value(cache: MarketCache, day: Long = today()): Double = when (this) {
    is FixedDeposit -> valueAt(day)
    is SavingsAccount -> valueAt(day)
    is PensionContract -> valueAt(day)
    is Depot -> value(cache)
    is MetalHolding -> value(cache) ?: purchasePrice ?: 0.0
    is CashHolding -> amount
    is OtherAsset -> value
}

/** Change since yesterday: market moves plus one day of interest (deposits are not a gain). */
fun Asset.dayChange(cache: MarketCache, day: Long = today()): Double = when (this) {
    is Depot -> dayChange(cache)
    is MetalHolding -> dayChange(cache)
    is FixedDeposit -> value(cache, day) - value(cache, day - 1)
    is SavingsAccount -> if (day >= balanceDay) valueAt(day) * rate / 100 / 365 else 0.0
    is PensionContract -> if (day >= balanceDay) valueAt(day) * (rate ?: 0.0) / 100 / 365 else 0.0
    else -> 0.0
}

/** Yearly interest income the asset produces at today's balance. */
fun Asset.yearlyInterest(cache: MarketCache): Double = when (this) {
    is FixedDeposit -> if ((maturityDay ?: Long.MAX_VALUE) >= today()) amount * rate / 100 else 0.0
    is SavingsAccount -> valueAt(today()) * rate / 100
    is PensionContract -> valueAt(today()) * (rate ?: 0.0) / 100
    else -> 0.0
}

val Asset.displayName: String
    get() = when (this) {
        is FixedDeposit -> bank.name
        is SavingsAccount -> bank.name
        is PensionContract -> provider.name
        is Depot -> name
        is MetalHolding -> label ?: metal.title
        is CashHolding -> label
        is OtherAsset -> label
    }

data class KindTotal(val kind: AssetKind, val value: Double, val share: Float, val count: Int)

fun List<Asset>.byKind(cache: MarketCache): List<KindTotal> {
    val total = sumOf { it.value(cache) }.takeIf { it > 0 } ?: 1.0
    return groupBy { it.kind }
        .map { (kind, list) ->
            val v = list.sumOf { it.value(cache) }
            KindTotal(kind, v, (v / total).toFloat(), list.size)
        }
        .sortedByDescending { it.value }
}
