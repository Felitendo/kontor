package de.felitendo.kontor.data.market

import androidx.datastore.core.DataStore
import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.Instrument
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.Metal
import de.felitendo.kontor.data.model.MetalHolding
import de.felitendo.kontor.data.model.Portfolio
import de.felitendo.kontor.data.model.Quote
import de.felitendo.kontor.data.model.SeriesPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Everything the UI needs from the market, normalized to EUR. */
class MarketRepository(
    private val api: MarketApi,
    private val store: DataStore<MarketCache>,
) {
    val cache: Flow<MarketCache> = store.data

    private val fx = ConcurrentHashMap<String, Pair<Long, Double>>()
    private val charts = ConcurrentHashMap<String, Pair<Long, List<SeriesPoint>>>()
    private val refreshLock = Mutex()
    private val limiter = Semaphore(4)

    suspend fun search(query: String): List<MarketApi.SearchHit> =
        runCatching { api.search(query.trim()) }.getOrDefault(emptyList())

    /**
     * Finds a Yahoo listing for the instrument whose EUR price matches the Onvista reference
     * (to avoid ticker collisions). Falls back to Onvista-only quotes when nothing fits.
     */
    suspend fun resolve(hit: MarketApi.SearchHit): Pair<Instrument, Quote?> = coroutineScope {
        val reference = async { runCatching { api.onvistaQuote(hit.instrument) }.getOrNull() }
        val yahooSymbols = async { runCatching { api.yahooSymbolsFor(hit.instrument.isin) }.getOrDefault(emptyList()) }
        val guesses = hit.onvistaSymbol?.let { listOf("$it.DE", "$it.F") }.orEmpty()
        val candidates = (guesses + yahooSymbols.await()).distinct().take(6)
        val ref = reference.await()?.let { toEur(it) }

        var fallback: Pair<String, Quote>? = null
        for (symbol in candidates) {
            val chart = runCatching { api.chart(symbol, "1d", "5m") }.getOrNull() ?: continue
            val quote = chartQuote(chart) ?: continue
            val matches = ref == null || abs(quote.price - ref.price) / ref.price < 0.06
            if (matches && chart.currency == "EUR") {
                return@coroutineScope hit.instrument.copy(yahooSymbol = symbol) to quote
            }
            if (matches && fallback == null) fallback = symbol to quote
        }
        fallback?.let { (symbol, quote) -> return@coroutineScope hit.instrument.copy(yahooSymbol = symbol) to quote }
        hit.instrument to ref
    }

    suspend fun quote(instrument: Instrument): Quote? {
        val symbol = instrument.yahooSymbol
        if (symbol != null) {
            val chart = runCatching { api.chart(symbol, "1d", "5m") }.getOrNull()
            chartQuote(chart)?.let { return it }
        }
        return runCatching { api.onvistaQuote(instrument) }.getOrNull()?.let { toEur(it) }
    }

    /** Refreshes all quotes referenced by the portfolio. Returns false when nothing could be loaded. */
    suspend fun refresh(portfolio: Portfolio): Boolean = refreshLock.withLock {
        val instruments = portfolio.assets.filterIsInstance<Depot>()
            .flatMap { d -> d.positions.map { it.instrument } }
            .distinctBy { it.isin }
        val metals = portfolio.assets.filterIsInstance<MetalHolding>().map { it.metal }.toSet() + Metal.GOLD

        val (quotes, metalQuotes) = coroutineScope {
            val q = instruments.map { i -> async { limiter.withPermit { i.isin to quote(i) } } }
            val m = metals.map { metal -> async { limiter.withPermit { metal to metalQuote(metal) } } }
            q.awaitAll() to m.awaitAll()
        }
        val ok = quotes.any { it.second != null } || metalQuotes.any { it.second != null }
        store.updateData { old ->
            old.copy(
                quotes = old.quotes + quotes.mapNotNull { (k, v) -> v?.let { k to it } },
                metals = old.metals + metalQuotes.mapNotNull { (k, v) -> v?.let { k to it } },
                updatedAt = if (ok) System.currentTimeMillis() else old.updatedAt,
            )
        }
        ok
    }

    /** Loads prices for every metal the cache does not know yet (used by the metal picker). */
    suspend fun ensureMetals() = coroutineScope {
        val missing = Metal.entries.filter { it !in store.data.first().metals }
        if (missing.isEmpty()) return@coroutineScope
        val loaded = missing.map { m -> async { m to metalQuote(m) } }.awaitAll()
        store.updateData { old -> old.copy(metals = old.metals + loaded.mapNotNull { (k, v) -> v?.let { k to it } }) }
    }

    suspend fun saveWealthSeries(points: List<SeriesPoint>) {
        store.updateData { it.copy(wealthSeries = points) }
    }

    suspend fun metalQuote(metal: Metal): Quote? = coroutineScope {
        val spot = async { runCatching { api.metalSpot(metal) }.getOrNull() }
        val future = async { runCatching { api.chart(metal.yahooFuture, "1d", "5m") }.getOrNull() }
        val f = future.await()
        val price = spot.await() ?: f?.let { chartQuote(it)?.price } ?: return@coroutineScope null
        val dayChange = f?.previousClose?.takeIf { it > 0 }?.let { f.price / it }
        Quote(
            price = price,
            previousClose = dayChange?.let { price / it },
            time = System.currentTimeMillis(),
            exchange = "Spotpreis",
            dayHigh = f?.dayHigh?.let { it / f.price * price },
            dayLow = f?.dayLow?.let { it / f.price * price },
            yearHigh = f?.yearHigh?.let { it / f.price * price },
            yearLow = f?.yearLow?.let { it / f.price * price },
        )
    }

    /** Price history of an instrument in EUR. */
    suspend fun instrumentSeries(instrument: Instrument, range: ChartRange): List<SeriesPoint> {
        val symbol = instrument.yahooSymbol ?: return emptyList()
        return cached("i:$symbol:$range", range) {
            val chart = api.chart(symbol, range) ?: return@cached emptyList()
            convertSeries(chart.points, chart.currency, range)
        }
    }

    /** Spot-anchored metal history in EUR per ounce. */
    suspend fun metalSeries(metal: Metal, range: ChartRange, spot: Double?): List<SeriesPoint> =
        cached("m:$metal:$range", range) {
            val chart = api.chart(metal.yahooFuture, range) ?: return@cached emptyList()
            val eur = convertSeries(chart.points, chart.currency, range)
            val last = eur.lastOrNull()?.v
            if (spot != null && last != null && last > 0) {
                val k = spot / last
                eur.map { it.copy(v = it.v * k) }
            } else eur
        }

    suspend fun instrumentChartMeta(instrument: Instrument, range: ChartRange): MarketApi.Chart? {
        val symbol = instrument.yahooSymbol ?: return null
        return runCatching { api.chart(symbol, range) }.getOrNull()
    }

    private suspend fun cached(key: String, range: ChartRange, load: suspend () -> List<SeriesPoint>): List<SeriesPoint> {
        val ttl = if (range == ChartRange.DAY || range == ChartRange.WEEK) 3 * 60_000L else 30 * 60_000L
        charts[key]?.let { (at, data) -> if (System.currentTimeMillis() - at < ttl && data.isNotEmpty()) return data }
        val data = runCatching { load() }.getOrDefault(emptyList())
        if (data.isNotEmpty()) charts[key] = System.currentTimeMillis() to data
        return data.ifEmpty { charts[key]?.second.orEmpty() }
    }

    private suspend fun chartQuote(chart: MarketApi.Chart?): Quote? {
        chart ?: return null
        val raw = Quote(
            price = chart.price,
            previousClose = chart.previousClose,
            currency = chart.currency,
            time = chart.time,
            exchange = chart.exchange,
            dayHigh = chart.dayHigh,
            dayLow = chart.dayLow,
            yearHigh = chart.yearHigh,
            yearLow = chart.yearLow,
        )
        return toEur(raw)
    }

    private suspend fun toEur(q: Quote): Quote? {
        if (q.currency == "EUR") return q
        val factor = eurFactor(q.currency) ?: return null
        fun c(v: Double?) = v?.times(factor)
        return q.copy(
            price = q.price * factor,
            previousClose = c(q.previousClose),
            dayHigh = c(q.dayHigh), dayLow = c(q.dayLow),
            yearHigh = c(q.yearHigh), yearLow = c(q.yearLow),
            currency = "EUR",
        )
    }

    private suspend fun convertSeries(points: List<SeriesPoint>, currency: String, range: ChartRange): List<SeriesPoint> {
        if (currency == "EUR" || points.isEmpty()) return points
        val (cur, scale) = if (currency == "GBp" || currency == "GBX") "GBP" to 0.01 else currency to 1.0
        val history = runCatching { api.chart("EUR$cur=X", range) }.getOrNull()?.points.orEmpty()
        val spot = eurFactor(currency) ?: return emptyList()
        if (history.isEmpty()) return points.map { it.copy(v = it.v * spot) }
        var j = 0
        return points.map { p ->
            while (j + 1 < history.size && history[j + 1].t <= p.t) j++
            p.copy(v = p.v * scale / history[j].v)
        }
    }

    /** Multiply a price in [currency] with this factor to get EUR. */
    private suspend fun eurFactor(currency: String): Double? {
        val (cur, scale) = if (currency == "GBp" || currency == "GBX") "GBP" to 0.01 else currency to 1.0
        if (cur == "EUR") return scale
        fx[cur]?.let { (at, rate) -> if (System.currentTimeMillis() - at < 15 * 60_000L) return scale / rate }
        val rate = runCatching { api.chart("EUR$cur=X", "1d", "1h") }.getOrNull()?.price ?: return fx[cur]?.second?.let { scale / it }
        fx[cur] = System.currentTimeMillis() to rate
        return scale / rate
    }
}
