package de.felitendo.kontor.data.market

import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.data.model.Instrument
import de.felitendo.kontor.data.model.InstrumentType
import de.felitendo.kontor.data.model.Metal
import de.felitendo.kontor.data.model.Quote
import de.felitendo.kontor.data.model.SeriesPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime

/**
 * Thin clients for the public market data endpoints Kontor relies on:
 * - Onvista: search by WKN / ISIN / name (German market focus), fallback quotes
 * - Yahoo Finance: quotes and chart history
 * - gold-api.com: precious metal spot prices in EUR
 */
class MarketApi(private val http: OkHttpClient, private val json: Json) {

    data class SearchHit(
        val instrument: Instrument,
        val onvistaSymbol: String?,
    )

    data class Chart(
        val symbol: String,
        val currency: String,
        val price: Double,
        val previousClose: Double?,
        val time: Long,
        val exchange: String?,
        val dayHigh: Double?,
        val dayLow: Double?,
        val yearHigh: Double?,
        val yearLow: Double?,
        val points: List<SeriesPoint>,
    )

    suspend fun search(query: String): List<SearchHit> {
        val url = "https://api.onvista.de/api/v1/instruments/query".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "15")
            .addQueryParameter("searchValue", query)
            .build()
        val root = getJson(url.toString()) as? JsonObject ?: return emptyList()
        val list = root["list"] as? JsonArray ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val isin = o.str("isin") ?: return@mapNotNull null
            val entityType = o.str("entityType")
            if (entityType == "INDEX" || entityType == "CURRENCY" || entityType == "PRECIOUS_METAL") return@mapNotNull null
            SearchHit(
                instrument = Instrument(
                    isin = isin,
                    wkn = o.str("wkn"),
                    name = cleanName(o.str("name") ?: isin),
                    type = InstrumentType.fromOnvista(entityType, o.str("entitySubType")),
                    onvistaType = entityType,
                    onvistaId = o.str("entityValue"),
                ),
                onvistaSymbol = o.str("symbol"),
            )
        }
    }

    /** Yahoo symbols listed for an ISIN, best German EUR listings first. */
    suspend fun yahooSymbolsFor(isin: String): List<String> {
        val url = "https://query1.finance.yahoo.com/v1/finance/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", isin)
            .addQueryParameter("quotesCount", "10")
            .addQueryParameter("newsCount", "0")
            .build()
        val root = getJson(url.toString(), yahoo = true) as? JsonObject ?: return emptyList()
        val quotes = root["quotes"] as? JsonArray ?: return emptyList()
        return quotes.mapNotNull { (it as? JsonObject)?.str("symbol") }
            .sortedBy { symbolRank(it) }
    }

    suspend fun chart(symbol: String, range: ChartRange): Chart? =
        chart(symbol, range.yahooRange, range.yahooInterval)

    suspend fun chart(symbol: String, range: String, interval: String): Chart? {
        val path = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol.replace("=", "%3D").replace("^", "%5E")
        val url = path.toHttpUrl().newBuilder()
            .addQueryParameter("range", range)
            .addQueryParameter("interval", interval)
            .addQueryParameter("includePrePost", "false")
            .build()
        val root = getJson(url.toString(), yahoo = true) as? JsonObject ?: return null
        val result = ((root["chart"] as? JsonObject)?.get("result") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val meta = result["meta"] as? JsonObject ?: return null
        val timestamps = (result["timestamp"] as? JsonArray)?.map { (it as? JsonPrimitive)?.longOrNull } ?: emptyList()
        val closes = (((result["indicators"] as? JsonObject)?.get("quote") as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("close") as? JsonArray
        val points = buildList {
            timestamps.forEachIndexed { i, t ->
                val v = (closes?.getOrNull(i) as? JsonPrimitive)?.doubleOrNull
                if (t != null && v != null && v > 0) add(SeriesPoint(t * 1000, v))
            }
        }
        val price = meta.dbl("regularMarketPrice") ?: points.lastOrNull()?.v ?: return null
        return Chart(
            symbol = symbol,
            currency = meta.str("currency") ?: "EUR",
            price = price,
            previousClose = if (range == "1d") meta.dbl("previousClose") ?: meta.dbl("chartPreviousClose") else meta.dbl("chartPreviousClose"),
            time = (meta.lng("regularMarketTime") ?: 0) * 1000,
            exchange = meta.str("fullExchangeName") ?: meta.str("exchangeName"),
            dayHigh = meta.dbl("regularMarketDayHigh"),
            dayLow = meta.dbl("regularMarketDayLow"),
            yearHigh = meta.dbl("fiftyTwoWeekHigh"),
            yearLow = meta.dbl("fiftyTwoWeekLow"),
            points = points,
        )
    }

    /** Spot price in EUR per troy ounce. */
    suspend fun metalSpot(metal: Metal): Double? {
        val root = getJson("https://api.gold-api.com/price/${metal.symbol}/EUR") as? JsonObject ?: return null
        return root.dbl("price")
    }

    /** Fallback when Yahoo has no listing: Onvista snapshot of the instrument. */
    suspend fun onvistaQuote(instrument: Instrument): Quote? {
        val type = instrument.onvistaType ?: return null
        val id = instrument.onvistaId ?: return null
        val root = getJson("https://api.onvista.de/api/v1/instruments/$type/$id/snapshot") as? JsonObject ?: return null
        val quote = root["quote"] as? JsonObject ?: return null
        val price = quote.dbl("last") ?: return null
        val market = quote["market"] as? JsonObject
        return Quote(
            price = price,
            previousClose = quote.dbl("previousLast"),
            currency = quote.str("isoCurrency") ?: "EUR",
            time = quote.str("datetimeLast")?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0,
            exchange = market?.str("name"),
            dayHigh = quote.dbl("high"),
            dayLow = quote.dbl("low"),
            yearHigh = quote.dbl("highPrice1Year"),
            yearLow = quote.dbl("lowPrice1Year"),
        )
    }

    private suspend fun getJson(url: String, yahoo: Boolean = false): JsonElement? = withContext(Dispatchers.IO) {
        val urls = if (yahoo) listOf(url, url.replace("query1.", "query2.")) else listOf(url)
        for (u in urls) {
            val request = Request.Builder().url(u)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            val result = runCatching {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else json.parseToJsonElement(response.body.string())
                }
            }.getOrNull()
            if (result != null) return@withContext result
        }
        null
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) Kontor"

        private val germanSuffixes = listOf(".DE", ".F", ".MU", ".SG", ".DU", ".BE", ".HM", ".HA")

        fun symbolRank(symbol: String): Int {
            val i = germanSuffixes.indexOfFirst { symbol.endsWith(it) }
            return when {
                i >= 0 -> i
                symbol.contains('.') -> 20
                else -> 10
            }
        }

        private fun cleanName(name: String) = name
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
internal fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
internal fun JsonObject.lng(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
