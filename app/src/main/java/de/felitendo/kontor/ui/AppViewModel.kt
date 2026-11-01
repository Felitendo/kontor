package de.felitendo.kontor.ui

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.felitendo.kontor.container
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.Portfolio
import de.felitendo.kontor.data.model.Position
import de.felitendo.kontor.data.model.Settings
import de.felitendo.kontor.data.model.UpdateChannel
import de.felitendo.kontor.update.Release
import de.felitendo.kontor.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state. The app is small enough that one view model keeps things simple. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val c = app.container

    val portfolio: StateFlow<Portfolio?> = c.portfolio.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<Settings?> = c.settings.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val market: StateFlow<MarketCache> = c.market.cache.stateIn(viewModelScope, SharingStarted.Eagerly, MarketCache())
    val refreshing = c.refreshing
    val updateState = c.updater.state

    /** Set while the app is locked behind biometrics. */
    val locked = MutableStateFlow(false)

    fun refresh(force: Boolean = false) {
        viewModelScope.launch { c.refresh(if (force) 0 else 2 * 60_000L) }
    }

    fun updateSettings(block: (Settings) -> Settings) {
        viewModelScope.launch {
            c.settings.update(block)
            Widgets.updateAll(getApplication())
        }
    }

    fun save(asset: Asset, refreshAfter: Boolean = true) {
        viewModelScope.launch {
            c.portfolio.upsert(asset)
            if (refreshAfter) c.refresh() else Widgets.updateAll(getApplication())
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { c.portfolio.delete(id); Widgets.updateAll(getApplication()) }
    }

    fun savePosition(depotId: String, position: Position) {
        viewModelScope.launch { c.portfolio.upsertPosition(depotId, position); c.refresh() }
    }

    fun deletePosition(depotId: String, positionId: String) {
        viewModelScope.launch { c.portfolio.deletePosition(depotId, positionId); Widgets.updateAll(getApplication()) }
    }

    fun checkUpdates(channel: UpdateChannel? = null) {
        viewModelScope.launch {
            val ch = channel ?: settings.value?.updateChannel ?: c.updater.buildChannel
            c.updater.check(ch)
        }
    }

    fun installUpdate(release: Release) {
        viewModelScope.launch { c.updater.downloadAndInstall(release) }
    }
}

val LocalVm = staticCompositionLocalOf<AppViewModel> { error("no vm") }
