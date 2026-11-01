package de.felitendo.kontor.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Portfolio(
    val assets: List<Asset> = emptyList(),
    val schema: Int = 1,
)

enum class ThemeMode(val title: String) { SYSTEM("System"), DARK("Schwarz"), LIGHT("Weiß") }

enum class UpdateChannel(val title: String) { STABLE("Stable"), DEV("Dev") }

enum class ChartRange(val label: String, val yahooRange: String, val yahooInterval: String, val days: Int) {
    DAY("1T", "1d", "5m", 1),
    WEEK("1W", "5d", "30m", 7),
    MONTH("1M", "1mo", "60m", 31),
    HALF_YEAR("6M", "6mo", "1d", 183),
    YEAR("1J", "1y", "1d", 365),
    FIVE_YEARS("5J", "5y", "1wk", 5 * 365),
    MAX("Max", "max", "1mo", 30 * 365),
}

@Serializable
data class Settings(
    val onboardingDone: Boolean = false,
    val lockAsked: Boolean = false,
    val lockEnabled: Boolean = false,
    /** Seconds in background before the lock comes back. */
    val lockTimeout: Int = 30,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val hideAmounts: Boolean = false,
    val haptics: Boolean = true,
    /** null = the channel this build was published on. */
    val updateChannel: UpdateChannel? = null,
    val autoUpdateCheck: Boolean = true,
    val maturityReminders: Boolean = true,
    val widgetShowAmounts: Boolean = true,
    val homeRange: ChartRange = ChartRange.MONTH,
    /** Assumed yearly return for securities in the forecast, percent. */
    val forecastStockReturn: Double = 5.0,
    val notifiedMaturities: Set<String> = emptySet(),
    val dismissedUpdateCode: Int = 0,
)

@Serializable
data class Quote(
    val price: Double,
    val previousClose: Double? = null,
    /** Currency of [price] after conversion — always EUR in the cache. */
    val currency: String = "EUR",
    val time: Long = 0,
    val exchange: String? = null,
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    val yearHigh: Double? = null,
    val yearLow: Double? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val change: Double? get() = previousClose?.let { price - it }
    val changePct: Double? get() = previousClose?.takeIf { it != 0.0 }?.let { (price - it) / it * 100 }
}

@Serializable
data class SeriesPoint(val t: Long, val v: Double)

@Serializable
data class MarketCache(
    /** Keyed by ISIN. */
    val quotes: Map<String, Quote> = emptyMap(),
    /** Keyed by metal; EUR per troy ounce. */
    val metals: Map<Metal, Quote> = emptyMap(),
    /** Last computed total-wealth series for widgets (1M). */
    val wealthSeries: List<SeriesPoint> = emptyList(),
    val updatedAt: Long = 0,
)
