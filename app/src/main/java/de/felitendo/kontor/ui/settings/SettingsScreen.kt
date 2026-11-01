package de.felitendo.kontor.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.felitendo.kontor.BuildConfig
import de.felitendo.kontor.MainActivity
import de.felitendo.kontor.data.model.Settings
import de.felitendo.kontor.data.model.ThemeMode
import de.felitendo.kontor.data.model.UpdateChannel
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.components.PrimaryButton
import de.felitendo.kontor.ui.components.TopBar
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.lock.Biometrics
import de.felitendo.kontor.ui.theme.K
import de.felitendo.kontor.update.Release
import de.felitendo.kontor.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: NavController, activity: MainActivity, openUpdate: Boolean) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val update by vm.updateState.collectAsStateWithLifecycle()
    val s = settings ?: return
    val channel = s.updateChannel ?: vm.c.updater.buildChannel
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    // Set while the user grants "install unknown apps"; the install continues on return.
    var pendingInstall by remember { mutableStateOf<Release?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val release = pendingInstall ?: return@LifecycleEventEffect
        pendingInstall = null
        if (vm.c.updater.canInstall()) {
            activity.suppressNextLock = true
            vm.installUpdate(release)
        }
    }

    LaunchedEffect(openUpdate) { if (openUpdate || update is UpdateState.Idle) vm.checkUpdates(channel) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { target ->
        target ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = vm.c.portfolio.exportJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(target)?.use { it.write(json.encodeToByteArray()) } }
            }.isSuccess
            if (ok) haptics.success() else haptics.reject()
            message = if (ok) "Sicherung gespeichert." else "Sicherung fehlgeschlagen."
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        source ?: return@rememberLauncherForActivityResult
        scope.launch {
            pendingImport = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
        }
    }

    fun set(block: (Settings) -> Settings) = vm.updateSettings(block)

    Column(Modifier.fillMaxSize().background(c.background)) {
        TopBar("Einstellungen", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp).navigationBarsPadding()) {

            Group("Sicherheit") {
                SwitchRow("App-Sperre", "Gesichtserkennung, Fingerabdruck oder PIN beim Öffnen", s.lockEnabled) { on ->
                    if (on) {
                        if (Biometrics.availability(activity) != Biometrics.Availability.READY) {
                            message = "Richte zuerst Gesichtserkennung oder eine Display-Sperre in den Android-Einstellungen ein."
                            haptics.reject()
                        } else {
                            Biometrics.authenticate(activity, "App-Sperre aktivieren") { ok ->
                                if (ok) { activity.unlockedAt = android.os.SystemClock.elapsedRealtime(); haptics.success(); set { it.copy(lockEnabled = true) } }
                            }
                        }
                    } else {
                        Biometrics.authenticate(activity, "App-Sperre ausschalten") { ok ->
                            if (ok) { activity.unlockedAt = android.os.SystemClock.elapsedRealtime(); set { it.copy(lockEnabled = false) } }
                        }
                    }
                }
                if (s.lockEnabled) {
                    ChoiceRow("Sperren nach", listOf(0 to "Sofort", 30 to "30 s", 60 to "1 min", 300 to "5 min"), s.lockTimeout) { v -> set { it.copy(lockTimeout = v) } }
                }
            }

            Group("Darstellung") {
                ChoiceRow("Design", ThemeMode.entries.map { it to it.title }, s.theme) { v -> set { it.copy(theme = v) } }
                SwitchRow("Beträge verbergen", "Auch per Tipp auf das Gesamtvermögen", s.hideAmounts) { on -> set { it.copy(hideAmounts = on) } }
                SwitchRow("Haptisches Feedback", "Vibrationen beim Tippen und im Chart", s.haptics) { on -> set { it.copy(haptics = on) } }
                SwitchRow("Beträge im Widget", "Aus: Widgets zeigen nur Prozente", s.widgetShowAmounts) { on -> set { it.copy(widgetShowAmounts = on) } }
            }

            Group("Benachrichtigungen") {
                SwitchRow("Festgeld-Erinnerungen", "30 und 7 Tage vor Fälligkeit", s.maturityReminders) { on -> set { it.copy(maturityReminders = on) } }
            }

            Group("Updates") {
                ChoiceRow("Kanal", UpdateChannel.entries.map { it to it.title }, channel) { v ->
                    set { it.copy(updateChannel = v) }
                    vm.checkUpdates(v)
                }
                Text(
                    if (channel == UpdateChannel.DEV) "Dev-Versionen entstehen bei jeder Änderung am Code. Neu, aber nicht immer ausgereift."
                    else "Stabile, getestete Versionen.",
                    style = K.type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                UpdatePanel(update, channel, onCheck = { vm.checkUpdates(channel) }) { release ->
                    if (!vm.c.updater.canInstall()) {
                        activity.suppressNextLock = true
                        pendingInstall = release
                        context.startActivity(vm.c.updater.unknownSourcesIntent())
                    } else {
                        activity.suppressNextLock = true
                        vm.installUpdate(release)
                    }
                }
                SwitchRow("Automatisch suchen", "Beim Start und einmal täglich", s.autoUpdateCheck) { on -> set { it.copy(autoUpdateCheck = on) } }
            }

            Group("Daten") {
                LinkRow("Sicherung exportieren", "Alle Vermögenswerte als JSON-Datei") {
                    activity.suppressNextLock = true
                    export.launch("kontor-sicherung-${java.time.LocalDate.now()}.json")
                }
                LinkRow("Sicherung importieren", "Ersetzt die aktuellen Daten") {
                    activity.suppressNextLock = true
                    import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
                LinkRow("Alle Vermögenswerte löschen", null, destructive = true) { haptics.heavy(); confirmWipe = true }
            }

            Group("Über Kontor") {
                InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Build-Kanal", if (BuildConfig.CHANNEL == "stable") "Stable" else "Dev")
                LinkRow("Quellcode auf GitHub", "github.com/${BuildConfig.GITHUB_REPO}", external = true) { uri.openUri("https://github.com/${BuildConfig.GITHUB_REPO}") }
                LinkRow("Lizenz", "GNU General Public License v3.0", external = true) { uri.openUri("https://www.gnu.org/licenses/gpl-3.0.html") }
                Text(
                    "Kurse: Yahoo Finance, Onvista · Edelmetalle: gold-api.com · Logos: Parqet und die jeweiligen Banken (Marken ihrer Inhaber) · Schrift: Inter (OFL). Kontor speichert alles nur auf diesem Gerät.",
                    style = K.type.caption, color = c.textTertiary, modifier = Modifier.padding(20.dp),
                )
            }
        }
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton({ message = null }) { Text("OK", color = c.text) } },
            containerColor = c.surfaceHigh,
        )
    }
    pendingImport?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Sicherung importieren?") },
            text = { Text("Deine aktuellen Vermögenswerte werden durch die Sicherung ersetzt.") },
            confirmButton = {
                TextButton({
                    pendingImport = null
                    scope.launch {
                        val n = runCatching { vm.c.portfolio.importJson(json) }.getOrNull()
                        if (n != null) { haptics.success(); vm.refresh(force = true); message = "$n Vermögenswerte importiert." }
                        else { haptics.reject(); message = "Die Datei ist keine gültige Kontor-Sicherung." }
                    }
                }) { Text("Importieren", color = c.text) }
            },
            dismissButton = { TextButton({ pendingImport = null }) { Text("Abbrechen", color = c.textSecondary) } },
            containerColor = c.surfaceHigh,
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wirklich alles löschen?") },
            text = { Text("Alle Vermögenswerte werden von diesem Gerät entfernt. Exportiere vorher eine Sicherung, wenn du sie behalten willst.") },
            confirmButton = {
                TextButton({
                    confirmWipe = false
                    haptics.reject()
                    scope.launch { vm.c.portfolio.replaceAll(emptyList()); vm.refresh(force = true) }
                }) { Text("Löschen", color = c.negative) }
            },
            dismissButton = { TextButton({ confirmWipe = false }) { Text("Abbrechen", color = c.text) } },
            containerColor = c.surfaceHigh,
        )
    }
}

@Composable
private fun UpdatePanel(state: UpdateState, channel: UpdateChannel, onCheck: () -> Unit, onInstall: (Release) -> Unit) {
    val c = K.colors
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).padding(18.dp)) {
        AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }, label = "update") { st ->
            Column {
                when (st) {
                    UpdateState.Idle, UpdateState.Checking -> Text("Suche nach Updates…", style = K.type.body, color = c.textSecondary)
                    is UpdateState.UpToDate -> {
                        Text("Du bist auf dem neuesten Stand", style = K.type.bodyStrong, color = c.text)
                        val latest = st.latest
                        if (latest == null) {
                            Spacer(Modifier.height(4.dp))
                            Text("In diesem Kanal gibt es noch keine Veröffentlichung.", style = K.type.caption, color = c.textSecondary)
                        }
                        if (latest != null && latest.versionCode < BuildConfig.VERSION_CODE && channel == UpdateChannel.STABLE) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Die neueste Stable-Version (${latest.versionName}) ist älter als deine. Du bekommst die nächste Stable, sobald sie neuer ist.",
                                style = K.type.caption, color = c.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Erneut prüfen", style = K.type.label, color = c.text, modifier = Modifier.pressable(onClick = onCheck).padding(vertical = 4.dp))
                    }
                    is UpdateState.Available -> ReleaseCard(st.release, "Installieren") { onInstall(st.release) }
                    is UpdateState.Downloading -> {
                        Text("Lade ${st.release.versionName}…", style = K.type.bodyStrong, color = c.text)
                        Spacer(Modifier.height(12.dp))
                        val p by animateFloatAsState(st.progress, label = "dl")
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.surfaceHigh)) {
                            Box(Modifier.fillMaxWidth(p).height(6.dp).clip(CircleShape).background(c.text))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(Fmt.pct(st.progress * 100.0), style = K.type.caption, color = c.textSecondary)
                    }
                    is UpdateState.Installing -> Text("Installation läuft — bestätige im System-Dialog.", style = K.type.body, color = c.text)
                    is UpdateState.Failed -> {
                        Text(st.message, style = K.type.body, color = c.text)
                        Spacer(Modifier.height(10.dp))
                        Text("Nochmal versuchen", style = K.type.label, color = c.text, modifier = Modifier.pressable {
                            st.release?.let(onInstall) ?: onCheck()
                        }.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: Release, action: String, onAction: () -> Unit) {
    val c = K.colors
    Column {
        Text("Version ${release.versionName}", style = K.type.title, color = c.text)
        Text(
            (if (release.channel == UpdateChannel.DEV) "Dev-Build" else "Stable") +
                (if (release.publishedAt > 0) " · " + Fmt.relative(release.publishedAt) else "") +
                (if (release.apkSize > 0) " · " + Fmt.number(release.apkSize / 1_048_576.0) + " MB" else ""),
            style = K.type.caption, color = c.textSecondary,
        )
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                release.notes.lines().filter { it.isNotBlank() }.take(12)
                    .joinToString("\n") { it.trim().removePrefix("## ").removePrefix("### ").replace(Regex("^[-*] "), "• ").replace("`", "").replace("**", "") },
                style = K.type.caption, color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(action) { onAction() }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = K.type.label, color = K.colors.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 6.dp))
    content()
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = K.colors
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().pressable(scaleTo = 0.99f, haptic = false) { haptics.toggle(!checked); onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = K.type.bodyStrong, color = c.text)
            subtitle?.let { Text(it, style = K.type.caption, color = c.textSecondary) }
        }
        Spacer(Modifier.width(12.dp))
        Toggle(checked)
    }
}

/** Monochrome switch with a springy knob. */
@Composable
private fun Toggle(checked: Boolean) {
    val c = K.colors
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, spring(0.6f, 500f), label = "knob")
    val track by animateColorAsState(if (checked) c.text else c.surfaceHigh, label = "track")
    val knob by animateColorAsState(if (checked) c.background else c.textSecondary, label = "knobColor")
    Box(Modifier.size(48.dp, 28.dp).clip(CircleShape).background(track).padding(4.dp)) {
        Box(Modifier.offset(x = x).size(20.dp).clip(CircleShape).background(knob))
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = K.colors
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, style = K.type.bodyStrong, color = c.text)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                val active = value == selected
                val bg by animateColorAsState(if (active) c.surfaceHigh else c.surface, label = "seg")
                val fg by animateColorAsState(if (active) c.text else c.textSecondary, label = "segText")
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(11.dp)).background(bg)
                        .pressable(scaleTo = 0.95f, haptic = false) { if (!active) { haptics.selection(); onSelect(value) } },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = K.type.label, color = fg) }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, destructive: Boolean = false, external: Boolean = false, onClick: () -> Unit) {
    val c = K.colors
    Row(
        Modifier.fillMaxWidth().pressable(scaleTo = 0.99f, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = K.type.bodyStrong, color = if (destructive) c.negative else c.text)
            subtitle?.let { Text(it, style = K.type.caption, color = c.textSecondary) }
        }
        Icon(if (external) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    val c = K.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = K.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = K.type.bodyStrong, color = c.text)
    }
}
