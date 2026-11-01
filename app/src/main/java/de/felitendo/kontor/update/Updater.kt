package de.felitendo.kontor.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import de.felitendo.kontor.BuildConfig
import de.felitendo.kontor.data.market.dbl
import de.felitendo.kontor.data.market.str
import de.felitendo.kontor.data.model.UpdateChannel
import de.felitendo.kontor.data.store.KontorJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.OffsetDateTime

data class Release(
    val versionCode: Int,
    val versionName: String,
    val channel: UpdateChannel,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val channel: UpdateChannel, val latest: Release?) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Self-updater fed by GitHub Releases:
 * - stable: the latest regular release (tag vX.Y.Z)
 * - dev: the rolling pre-release tagged "dev", rebuilt on every push to main
 * Both are signed with the same key, so switching channels updates in place.
 */
class Updater(private val context: Context, private val http: OkHttpClient) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val installedCode: Int get() = BuildConfig.VERSION_CODE
    val buildChannel: UpdateChannel get() = if (BuildConfig.CHANNEL == "stable") UpdateChannel.STABLE else UpdateChannel.DEV

    /** Thrown when GitHub cannot be reached; a channel without any release yields null instead. */
    class Unreachable : Exception()

    suspend fun fetch(channel: UpdateChannel): Release? = withContext(Dispatchers.IO) {
        val path = if (channel == UpdateChannel.STABLE) "releases/latest" else "releases/tags/dev"
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/$path")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Kontor/${BuildConfig.VERSION_NAME}")
            .build()
        val body = try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body.string()
                    response.code == 404 -> return@withContext null
                    else -> throw Unreachable()
                }
            }
        } catch (e: java.io.IOException) {
            throw Unreachable()
        }
        val o = runCatching { KontorJson.parseToJsonElement(body) as JsonObject }.getOrNull() ?: return@withContext null
        val notes = o.str("body").orEmpty()
        val code = Regex("""versionCode:\s*(\d+)""").find(notes)?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
        val asset = (o["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("name")?.endsWith(".apk") == true } ?: return@withContext null
        Release(
            versionCode = code,
            versionName = Regex("""versionName:\s*(\S+)""").find(notes)?.groupValues?.get(1) ?: o.str("name") ?: "",
            channel = channel,
            notes = notes.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "").trim(),
            apkUrl = asset.str("browser_download_url") ?: return@withContext null,
            apkSize = asset.dbl("size")?.toLong() ?: 0,
            publishedAt = o.str("published_at")?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0,
            pageUrl = o.str("html_url").orEmpty(),
        )
    }

    /** Checks the channel; returns the release when it is newer than what is installed. */
    suspend fun check(channel: UpdateChannel): Release? {
        if (BuildConfig.DEBUG) {
            // Debug builds have their own package name and key; they cannot take release APKs.
            _state.value = UpdateState.Failed("Debug-Builds aktualisieren sich nicht selbst.")
            return null
        }
        _state.value = UpdateState.Checking
        val latest = try {
            fetch(channel)
        } catch (e: Unreachable) {
            _state.value = UpdateState.Failed("GitHub ist gerade nicht erreichbar.")
            return null
        }
        val newer = latest?.takeIf { it.versionCode > installedCode }
        _state.value = if (newer != null) UpdateState.Available(newer) else UpdateState.UpToDate(channel, latest)
        return newer
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    suspend fun downloadAndInstall(release: Release) {
        val file = runCatching { download(release) }.getOrElse {
            _state.value = UpdateState.Failed("Download fehlgeschlagen.", release)
            return
        }
        _state.value = UpdateState.Installing(release)
        runCatching { install(file) }.onFailure {
            _state.value = UpdateState.Failed("Installation konnte nicht gestartet werden.", release)
        }
    }

    private suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "kontor-${release.versionCode}.apk")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "Kontor").build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSize
            response.body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (read - lastEmit > 128 * 1024 || read == total) {
                            lastEmit = read
                            _state.value = UpdateState.Downloading(release, if (total > 0) read.toFloat() / total else 0f)
                        }
                    }
                }
            }
            check(total <= 0 || target.length() == total) { "Unvollständiger Download" }
        }
        target
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("kontor.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java).setAction(InstallReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        if (status == PackageInstaller.STATUS_SUCCESS) return
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) return
        val release = (state.value as? UpdateState.Installing)?.release
        _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
        } else {
            UpdateState.Failed(
                when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "Die neue Version ist nicht mit dieser Installation kompatibel (andere Signatur?)."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Nicht genug Speicherplatz."
                    else -> message ?: "Installation fehlgeschlagen."
                },
                release,
            )
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(confirm)
        }
        val app = context.applicationContext as? de.felitendo.kontor.KontorApp ?: return
        app.container.updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }

    companion object {
        const val ACTION = "de.felitendo.kontor.INSTALL_RESULT"
    }
}
