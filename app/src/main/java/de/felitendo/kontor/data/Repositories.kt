package de.felitendo.kontor.data

import androidx.datastore.core.DataStore
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.Portfolio
import de.felitendo.kontor.data.model.Position
import de.felitendo.kontor.data.model.Settings
import de.felitendo.kontor.data.store.KontorJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

class PortfolioRepository(private val store: DataStore<Portfolio>) {
    val data: Flow<Portfolio> = store.data

    suspend fun current(): Portfolio = store.data.first()

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

    suspend fun exportJson(): String = KontorJson.encodeToString(Backup.serializer(), Backup(assets = current().assets))

    /** Returns the number of imported assets. Throws on unreadable input. */
    suspend fun importJson(json: String): Int {
        val backup = KontorJson.decodeFromString(Backup.serializer(), json)
        require(backup.app == "kontor") { "Keine Kontor-Sicherung" }
        replaceAll(backup.assets)
        return backup.assets.size
    }
}

@Serializable
data class Backup(
    val app: String = "kontor",
    val schema: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val assets: List<Asset>,
)

class SettingsRepository(private val store: DataStore<Settings>) {
    val data: Flow<Settings> = store.data
    suspend fun current(): Settings = store.data.first()
    suspend fun update(block: (Settings) -> Settings) = store.updateData(block)
}
