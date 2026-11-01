package de.felitendo.kontor.data.transfer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import de.felitendo.kontor.data.PortfolioRepository
import de.felitendo.kontor.data.SettingsRepository
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.data.model.ThemeMode
import de.felitendo.kontor.data.model.UpdateChannel
import de.felitendo.kontor.data.store.KontorJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

@Serializable
data class Backup(
    val app: String = "kontor",
    val schema: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val assets: List<Asset>,
    /** Since schema 2: preferences travel along to the new phone. */
    val settings: PortableSettings? = null,
)

/** The settings that make sense on another phone (no update state, no reminder bookkeeping). */
@Serializable
data class PortableSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val hideAmounts: Boolean = false,
    val haptics: Boolean = true,
    val hapticsStrong: Boolean = false,
    val shakeToHide: Boolean = true,
    val lockEnabled: Boolean = false,
    val lockTimeout: Int = 30,
    val updateChannel: UpdateChannel? = null,
    val autoUpdateCheck: Boolean = true,
    val maturityReminders: Boolean = true,
    val widgetShowAmounts: Boolean = true,
    val homeRange: ChartRange = ChartRange.MONTH,
    val forecastStockReturn: Double = 5.0,
)

/** Everything that moves data between phones. Nothing here ever leaves the device by itself. */
class BackupManager(
    private val context: Context,
    private val portfolio: PortfolioRepository,
    private val settings: SettingsRepository,
) {
    suspend fun export(): String {
        val s = settings.current()
        val backup = Backup(
            assets = portfolio.current().assets,
            settings = PortableSettings(
                theme = s.theme, hideAmounts = s.hideAmounts, haptics = s.haptics, hapticsStrong = s.hapticsStrong,
                shakeToHide = s.shakeToHide, lockEnabled = s.lockEnabled, lockTimeout = s.lockTimeout,
                updateChannel = s.updateChannel, autoUpdateCheck = s.autoUpdateCheck, maturityReminders = s.maturityReminders,
                widgetShowAmounts = s.widgetShowAmounts, homeRange = s.homeRange, forecastStockReturn = s.forecastStockReturn,
            ),
        )
        return KontorJson.encodeToString(Backup.serializer(), backup)
    }

    /** Throws when [json] is not a Kontor backup. */
    fun parse(json: String): Backup {
        val backup = KontorJson.decodeFromString(Backup.serializer(), json)
        require(backup.app == "kontor") { "Keine Kontor-Sicherung" }
        return backup
    }

    /**
     * Replaces the data on this phone. The app lock only carries over when this phone can
     * actually unlock (otherwise the user would lock themselves out).
     */
    suspend fun apply(backup: Backup, lockPossible: Boolean) {
        portfolio.restore(backup.assets)
        settings.update { old ->
            val p = backup.settings ?: return@update old.copy(onboardingDone = true, lockAsked = true)
            old.copy(
                onboardingDone = true, lockAsked = true,
                theme = p.theme, hideAmounts = p.hideAmounts, haptics = p.haptics, hapticsStrong = p.hapticsStrong,
                shakeToHide = p.shakeToHide, lockEnabled = p.lockEnabled && lockPossible, lockTimeout = p.lockTimeout,
                updateChannel = p.updateChannel, autoUpdateCheck = p.autoUpdateCheck, maturityReminders = p.maturityReminders,
                widgetShowAmounts = p.widgetShowAmounts, homeRange = p.homeRange, forecastStockReturn = p.forecastStockReturn,
            )
        }
    }

    /** Writes the backup into the app cache and returns a shareable content:// uri. */
    suspend fun shareableFile(): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName())
        file.writeText(export())
        FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    fun fileName() = "kontor-sicherung-${LocalDate.now()}.json"
}

/**
 * Moves a backup through a short loop of QR codes, phone to phone, without any network.
 *
 * Frame: `KTR1/<session>/<count>/<crc>/<index>/<payload>` where the payloads together are the
 * Base64 of the deflated backup. The receiver collects frames in any order and checks the CRC.
 */
object QrTransfer {
    private const val PREFIX = "KTR1"
    private const val CHUNK = 420

    fun frames(json: String): List<String> {
        val compressed = deflate(json.encodeToByteArray())
        val crc = CRC32().apply { update(compressed) }.value.toString(16)
        val data = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        val chunks = data.chunked(CHUNK)
        val session = (1..4).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")
        return chunks.mapIndexed { i, chunk -> "$PREFIX/$session/${chunks.size}/$crc/$i/$chunk" }
    }

    class Assembler {
        private var session: String? = null
        private var crc: String? = null
        private val parts = sortedMapOf<Int, String>()
        var count = 0
            private set
        val received: Set<Int> get() = parts.keys

        /** Returns true when the frame was new. Codes from another app are ignored. */
        fun offer(text: String): Boolean {
            val p = text.split('/', limit = 6)
            if (p.size != 6 || p[0] != PREFIX) return false
            val n = p[2].toIntOrNull() ?: return false
            val i = p[4].toIntOrNull() ?: return false
            if (i !in 0 until n) return false
            if (session != p[1]) {
                // A new sender (or a restarted one): start over.
                session = p[1]; crc = p[3]; count = n; parts.clear()
            }
            if (i in parts) return false
            parts[i] = p[5]
            return true
        }

        val complete: Boolean get() = count > 0 && parts.size == count

        /** The backup JSON; throws when the data is damaged. */
        fun result(): String {
            check(complete)
            val compressed = java.util.Base64.getUrlDecoder().decode(parts.values.joinToString(""))
            val actual = CRC32().apply { update(compressed) }.value.toString(16)
            check(actual == crc) { "Prüfsumme stimmt nicht" }
            return inflate(compressed).decodeToString()
        }
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(bytes); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inf = Inflater(true).apply { setInput(bytes) }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }
}
