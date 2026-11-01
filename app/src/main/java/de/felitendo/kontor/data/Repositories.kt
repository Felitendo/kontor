package de.felitendo.kontor.data

import androidx.datastore.core.DataStore
import de.felitendo.kontor.data.banks.Banks
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.BankRef
import de.felitendo.kontor.data.model.FixedDeposit
import de.felitendo.kontor.data.model.PORTFOLIO_SCHEMA
import de.felitendo.kontor.data.model.PensionContract
import de.felitendo.kontor.data.model.SavingsAccount
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.Portfolio
import de.felitendo.kontor.data.model.Position
import de.felitendo.kontor.data.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PortfolioRepository(private val store: DataStore<Portfolio>) {
    val data: Flow<Portfolio> = store.data

    suspend fun current(): Portfolio = store.data.first()

    /**
     * Brings stored data up to the current schema.
     * 2: banks typed by hand get linked to the catalog (logo), e.g. "Chase" or "Kreissparkasse Köln".
     */
    suspend fun migrate() = store.updateData { p ->
        if (p.schema >= PORTFOLIO_SCHEMA) p else p.copy(assets = p.assets.map(::linkBanks), schema = PORTFOLIO_SCHEMA)
    }

    private fun link(ref: BankRef): BankRef =
        if (ref.id != null) ref else Banks.match(ref.name)?.let { ref.copy(id = it.id) } ?: ref

    private fun linkBanks(a: Asset): Asset = when (a) {
        is FixedDeposit -> a.copy(bank = link(a.bank))
        is SavingsAccount -> a.copy(bank = link(a.bank))
        is PensionContract -> a.copy(provider = link(a.provider))
        is Depot -> a.copy(bank = a.bank?.let(::link))
        else -> a
    }

    suspend fun upsert(asset: Asset) = store.updateData { p ->
        val exists = p.assets.any { it.id == asset.id }
        p.copy(assets = if (exists) p.assets.map { if (it.id == asset.id) asset else it } else p.assets + asset)
    }

    suspend fun delete(id: String) = store.updateData { p -> p.copy(assets = p.assets.filterNot { it.id == id }) }

    suspend fun upsertPosition(depotId: String, position: Position) = store.updateData { p ->
        p.copy(assets = p.assets.map { a ->
            if (a is Depot && a.id == depotId) {
                val exists = a.positions.any { it.id == position.id }
                a.copy(positions = if (exists) a.positions.map { if (it.id == position.id) position else it } else a.positions + position)
            } else a
        })
    }

    suspend fun deletePosition(depotId: String, positionId: String) = store.updateData { p ->
        p.copy(assets = p.assets.map { a ->
            if (a is Depot && a.id == depotId) a.copy(positions = a.positions.filterNot { it.id == positionId }) else a
        })
    }

    suspend fun replaceAll(assets: List<Asset>) = store.updateData { it.copy(assets = assets) }

    /** Replaces everything with the assets of a backup or transfer. */
    suspend fun restore(assets: List<Asset>) = store.updateData {
        Portfolio(assets = assets.map(::linkBanks), schema = PORTFOLIO_SCHEMA)
    }
}

class SettingsRepository(private val store: DataStore<Settings>) {
    val data: Flow<Settings> = store.data
    suspend fun current(): Settings = store.data.first()
    suspend fun update(block: (Settings) -> Settings) = store.updateData(block)
}
