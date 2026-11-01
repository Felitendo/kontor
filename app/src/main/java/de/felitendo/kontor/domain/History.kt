package de.felitendo.kontor.domain

import de.felitendo.kontor.data.market.MarketRepository
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.MetalHolding
import de.felitendo.kontor.data.model.SeriesPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Reconstructs how the given assets were worth over [range]: today's holdings priced with
 * historical quotes, interest-bearing assets with their formula at each date. Not a ledger,
 * but exactly what you would have seen holding today's portfolio, like most brokers do.
 */
suspend fun composeHistory(
    assets: List<Asset>,
    cache: MarketCache,
    range: ChartRange,
    market: MarketRepository,
    maxPoints: Int = 240,
): List<SeriesPoint> = coroutineScope {
    val positions = assets.filterIsInstance<Depot>().flatMap { it.positions }
    val metals = assets.filterIsInstance<MetalHolding>()
    val others = assets.filter { it !is Depot && it !is MetalHolding }

    val instrumentSeries = positions.map { it.instrument }.distinctBy { it.isin }
        .map { i -> async { i.isin to market.instrumentSeries(i, range) } }
    val metalSeries = metals.map { it.metal }.distinct()
        .map { m -> async { m to market.metalSeries(m, range, cache.metals[m]?.price) } }
    val bySin = instrumentSeries.awaitAll().toMap()
    val byMetal = metalSeries.awaitAll().toMap()

    val now = System.currentTimeMillis()
    val all = (bySin.values + byMetal.values).filter { it.isNotEmpty() }
    var timeline: List<Long> = all.flatMap { s -> s.map { it.t } }.distinct().sorted()
    if (timeline.size < 2) {
        val start = now - range.days * 86_400_000L
        timeline = (0..60).map { start + (now - start) * it / 60 }
    }
    timeline = downsample(timeline, maxPoints)

    fun lookup(series: List<SeriesPoint>?, t: Long, fallback: Double?): Double? {
        if (series.isNullOrEmpty()) return fallback
        var lo = 0; var hi = series.size - 1
        if (t < series[0].t) return series[0].v
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (series[mid].t <= t) lo = mid else hi = mid - 1
        }
        return series[lo].v
    }

    val points = timeline.map { t ->
        val day = epochDayOf(t)
        var v = 0.0
        positions.forEach { p ->
            val price = lookup(bySin[p.instrument.isin], t, cache.quotes[p.instrument.isin]?.price ?: p.buyPrice)
            v += (price ?: 0.0) * p.quantity
        }
        metals.forEach { m ->
            val price = lookup(byMetal[m.metal], t, cache.metals[m.metal]?.price)
            v += price?.let { it * m.ounces } ?: (m.purchasePrice ?: 0.0)
        }
        others.forEach { v += it.value(cache, day) }
        SeriesPoint(t, v)
    }
    // Anchor the end to the live total so header and chart agree.
    val live = assets.sumOf { it.value(cache) }
    points + SeriesPoint(now, live)
}

private fun downsample(t: List<Long>, max: Int): List<Long> {
    if (t.size <= max) return t
    val step = t.size.toDouble() / max
    return (0 until max).map { t[(it * step).toInt()] } + t.last()
}
