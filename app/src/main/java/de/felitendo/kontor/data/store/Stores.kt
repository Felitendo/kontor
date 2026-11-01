package de.felitendo.kontor.data.store

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.Portfolio
import de.felitendo.kontor.data.model.Settings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

val KontorJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
    coerceInputValues = true
    allowSpecialFloatingPointValues = true
}

private class JsonSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        KontorJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(KontorJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

class Stores(context: Context) {
    private val dir = File(context.filesDir, "kontor")

    val portfolio: DataStore<Portfolio> = create("portfolio.json", Portfolio(), Portfolio.serializer())
    val settings: DataStore<Settings> = create("settings.json", Settings(), Settings.serializer())
    val market: DataStore<MarketCache> = create("market.json", MarketCache(), MarketCache.serializer())

    private fun <T> create(name: String, default: T, serializer: KSerializer<T>): DataStore<T> =
        DataStoreFactory.create(
            serializer = JsonSerializer(default, serializer),
            corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { default },
            produceFile = { File(dir, name) },
        )
}
