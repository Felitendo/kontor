package de.felitendo.kontor

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import de.felitendo.kontor.data.PortfolioRepository
import de.felitendo.kontor.data.SettingsRepository
import de.felitendo.kontor.data.market.MarketApi
import de.felitendo.kontor.data.market.MarketRepository
import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.data.store.KontorJson
import de.felitendo.kontor.data.store.Stores
import de.felitendo.kontor.domain.composeHistory
import de.felitendo.kontor.update.Updater
import de.felitendo.kontor.widget.Widgets
import de.felitendo.kontor.work.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class KontorApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Work.schedule(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .diskCache { DiskCache.Builder().directory(File(cacheDir, "logos")).maxSizeBytes(30L * 1024 * 1024).build() }
            .crossfade(220)
            .build()
}

val Context.container: AppContainer get() = (applicationContext as KontorApp).container

class AppContainer(val context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val stores = Stores(context)
    val api = MarketApi(http, KontorJson)
    val market = MarketRepository(api, stores.market)
    val portfolio = PortfolioRepository(stores.portfolio)
    val settings = SettingsRepository(stores.settings)
    val updater = Updater(context, http)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val refreshMutex = Mutex()

    /** Pulls fresh quotes unless the cache is younger than [maxAgeMillis]. Updates widgets afterwards. */
    suspend fun refresh(maxAgeMillis: Long = 0): Boolean = refreshMutex.withLock {
        val cache = market.cache.first()
        if (maxAgeMillis > 0 && System.currentTimeMillis() - cache.updatedAt < maxAgeMillis) return true
        _refreshing.value = true
        try {
            val p = portfolio.current()
            val ok = market.refresh(p)
            val fresh = market.cache.first()
            runCatching {
                val series = composeHistory(p.assets, fresh, ChartRange.MONTH, market, maxPoints = 60)
                market.saveWealthSeries(series)
            }
            runCatching { market.refreshGoldSeries() }
            Widgets.updateAll(context)
            ok
        } finally {
            _refreshing.value = false
        }
    }

    fun refreshInBackground(maxAgeMillis: Long = 0) {
        scope.launch { refresh(maxAgeMillis) }
    }
}
