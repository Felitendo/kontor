package de.felitendo.kontor.ui.transfer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.felitendo.kontor.MainActivity
import de.felitendo.kontor.data.transfer.Backup
import de.felitendo.kontor.data.transfer.QrTransfer
import de.felitendo.kontor.domain.byKind
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.Routes
import de.felitendo.kontor.ui.assets.CheckDot
import de.felitendo.kontor.ui.components.IconCircleButton
import de.felitendo.kontor.ui.components.PrimaryButton
import de.felitendo.kontor.ui.components.TopBar
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.lock.Biometrics
import de.felitendo.kontor.ui.theme.K
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Moving to a new phone. Modes:
 * - "menu": both directions (from the settings)
 * - "new": only the receiving side (from the welcome screen of a fresh install)
 * - "send" / "receive": straight into the QR codes or the scanner
 */
@Composable
fun TransferScreen(nav: NavController, activity: MainActivity, mode: String) {
    var screen by remember { mutableStateOf(mode) }
    var found by remember { mutableStateOf<Backup?>(null) }

    val finish: () -> Unit = {
        nav.navigate(Routes.Home) { popUpTo(0) { inclusive = true } }
    }
    val back: () -> Unit = {
        if (screen != mode && found == null) screen = mode else if (found != null) found = null else nav.popBackStack()
    }
    // System back steps through the same stages as the arrow instead of leaving the whole flow.
    BackHandler(enabled = screen != mode || found != null, onBack = back)

    AnimatedContent(
        targetState = if (found != null) "review" else screen,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        label = "transfer",
        modifier = Modifier.fillMaxSize().background(K.colors.background),
    ) { s ->
        when (s) {
            "review" -> found?.let { Review(it, activity, onCancel = { found = null }, onDone = finish) }
            "send" -> Send(onBack = back)
            "receive" -> Receive(activity, onBack = back, onFound = { found = it })
            else -> Menu(activity, onlyNew = s == "new", onBack = back, open = { screen = it }, onFound = { found = it })
        }
    }
}

@Composable
private fun Menu(activity: MainActivity, onlyNew: Boolean, onBack: () -> Unit, open: (String) -> Unit, onFound: (Backup) -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val backup = json?.let { runCatching { vm.c.backups.parse(it) }.getOrNull() }
            if (backup == null) { haptics.reject(); error = "Diese Datei ist keine Kontor-Sicherung." } else { haptics.success(); onFound(backup) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (onlyNew) "Daten übernehmen" else "Umzug aufs neue Handy", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(
                if (onlyNew) "Hol dir dein Kontor vom alten Handy." else "Nimm dein Kontor mit aufs neue Handy.",
                style = K.type.headline, color = c.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Alles läuft direkt zwischen deinen Geräten. Kein Konto, kein Server, keine Cloud.",
                style = K.type.body, color = c.textSecondary,
            )
            if (!onlyNew) {
                Group("Dieses Handy ist das alte")
                Option(Icons.Rounded.QrCode2, "QR-Codes anzeigen", "Das neue Handy scannt sie mit der Kamera") { open("send") }
                Option(Icons.Rounded.Share, "Als Datei senden", "Per Quick Share, Kabel oder E-Mail an dich selbst") {
                    scope.launch {
                        val uri = vm.c.backups.shareableFile()
                        activity.suppressNextLock = true
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Kontor-Sicherung")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Kontor-Sicherung senden"))
                    }
                }
            }
            Group(if (onlyNew) "So geht's" else "Dieses Handy ist das neue")
            Option(Icons.Rounded.QrCodeScanner, "QR-Codes scannen", "Am alten Handy: Einstellungen → Umzug → QR-Codes anzeigen") { open("receive") }
            Option(Icons.Rounded.Description, "Sicherungsdatei öffnen", "Eine zuvor gesendete oder gespeicherte Datei") {
                activity.suppressNextLock = true
                pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            }
            error?.let { Text(it, style = K.type.label, color = c.negative, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = K.type.label, color = K.colors.textSecondary, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = K.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).pressable(scaleTo = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(20.dp)).background(c.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = K.type.bodyStrong, color = c.text)
            Text(subtitle, style = K.type.caption, color = c.textSecondary)
        }
    }
}

/** Old phone: cycles through the QR frames, screen bright and awake. */
@Composable
private fun Send(onBack: () -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    var frames by remember { mutableStateOf<List<String>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { frames = withContext(Dispatchers.Default) { QrTransfer.frames(vm.c.backups.export()) } }
    LaunchedEffect(frames) {
        if (frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(750)
            index = (index + 1) % frames.size
            haptics.tick()
        }
    }
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val before = window?.attributes?.screenBrightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply { screenBrightness = 1f }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("QR-Codes anzeigen", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Scanne diesen Code mit dem neuen Handy", style = K.type.headline, color = c.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Dort Kontor öffnen und „Von altem Handy übernehmen“ oder Einstellungen → Umzug → QR-Codes scannen wählen.",
                style = K.type.body, color = c.textSecondary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, 360.dp)
                Box(
                    Modifier.size(side).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (frames.isNotEmpty()) QrCode(frames[index % frames.size], Modifier.fillMaxSize().aspectRatio(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
            if (frames.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    frames.indices.forEach { i ->
                        val color by animateColorAsState(if (i == index) c.text else c.surfaceHigh, label = "frameDot")
                        Box(Modifier.size(width = if (i == index) 22.dp else 8.dp, height = 8.dp).clip(CircleShape).background(color))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Teil ${index + 1} von ${frames.size}\u00A0· wechselt automatisch", style = K.type.caption, color = c.textSecondary)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** New phone: camera with a viewfinder, collects frames until the backup is complete. */
@Composable
private fun Receive(activity: MainActivity, onBack: () -> Unit, onFound: (Backup) -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }
    LaunchedEffect(Unit) { if (!granted) { activity.suppressNextLock = true; ask.launch(Manifest.permission.CAMERA) } }

    val assembler = remember { QrTransfer.Assembler() }
    var received by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            QrScanner(onText = { text ->
                if (assembler.complete) return@QrScanner
                if (assembler.offer(text)) {
                    received = assembler.received.size
                    total = assembler.count
                    haptics.tick()
                    if (assembler.complete) {
                        val backup = runCatching { vm.c.backups.parse(assembler.result()) }.getOrNull()
                        if (backup != null) { haptics.success(); onFound(backup) } else { haptics.reject(); error = "Die Daten waren beschädigt. Bitte noch einmal scannen." }
                    }
                }
            }, modifier = Modifier.fillMaxSize())
            Viewfinder(Modifier.fillMaxSize())
        }

        Row(Modifier.statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text("QR-Codes scannen", style = K.type.title, color = Color.White)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp)
                .clip(RoundedCornerShape(24.dp)).background(c.surface).padding(20.dp),
        ) {
            when {
                !granted -> {
                    Text("Kontor braucht die Kamera, um die Codes zu lesen.", style = K.type.bodyStrong, color = c.text)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(if (denied) "In den Einstellungen erlauben" else "Kamera erlauben") {
                        activity.suppressNextLock = true
                        if (denied) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                        else ask.launch(Manifest.permission.CAMERA)
                    }
                }
                error != null -> Text(error!!, style = K.type.bodyStrong, color = c.negative)
                total == 0 -> {
                    Text("Halte die Kamera auf den Code am alten Handy.", style = K.type.bodyStrong, color = c.text)
                    Spacer(Modifier.height(4.dp))
                    Text("Am alten Handy: Einstellungen → Umzug aufs neue Handy → QR-Codes anzeigen", style = K.type.caption, color = c.textSecondary)
                }
                else -> {
                    Text("$received von $total Teilen empfangen", style = K.type.bodyStrong, color = c.text)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0 until total).forEach { i ->
                            val color by animateColorAsState(if (i in assembler.received) c.text else c.surfaceHigh, label = "part")
                            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(color))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Weiter draufhalten, die Codes wechseln von selbst.", style = K.type.caption, color = c.textSecondary)
                }
            }
        }
    }
}

/** Darkens everything except a rounded window, with corner brackets and a scan line. */
@Composable
private fun Viewfinder(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "finder")
    val line by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val side = size.minDimension * 0.72f
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2.4f
        val r = side * 0.08f
        drawRect(Color.Black.copy(alpha = 0.6f))
        drawRoundRect(Color.Transparent, Offset(left, top), Size(side, side), CornerRadius(r), blendMode = BlendMode.Clear)
        val len = side * 0.16f
        val w = 4.dp.toPx()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val bracket = Path().apply {
                moveTo(x, y + dy * len)
                lineTo(x, y + dy * r)
                quadraticTo(x, y, x + dx * r, y)
                lineTo(x + dx * len, y)
            }
            drawPath(bracket, Color.White, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        corner(left, top, 1f, 1f); corner(left + side, top, -1f, 1f)
        corner(left, top + side, 1f, -1f); corner(left + side, top + side, -1f, -1f)
        val y = top + side * (0.1f + 0.8f * line)
        drawLine(Color.White.copy(alpha = 0.55f), Offset(left + side * 0.12f, y), Offset(left + side * 0.88f, y), 2.dp.toPx(), StrokeCap.Round)
    }
}

/** What was found, and the decision to take it over. */
@Composable
private fun Review(backup: Backup, activity: MainActivity, onCancel: () -> Unit, onDone: () -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val existing = portfolio?.assets.orEmpty()
    var busy by remember { mutableStateOf(false) }
    val kinds = remember(backup) { backup.assets.byKind(cache) }
    val date = remember(backup) {
        Instant.ofEpochMilli(backup.exportedAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d. MMMM yyyy, HH:mm", Fmt.locale))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        CheckDot(true, 56.dp)
        Spacer(Modifier.height(20.dp))
        Text("Daten gefunden", style = K.type.headline, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text("Stand vom $date", style = K.type.body, color = c.textSecondary)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            kinds.forEach { k ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(k.kind.title, style = K.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                    Text(if (k.count == 1) "1 Eintrag" else "${k.count} Einträge", style = K.type.body, color = c.textSecondary)
                }
            }
            if (backup.assets.isEmpty()) Text("Die Sicherung enthält keine Vermögenswerte.", style = K.type.body, color = c.textSecondary)
            if (backup.settings != null) {
                Text("Deine Einstellungen kommen mit.", style = K.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (existing.isNotEmpty()) {
            Text(
                "Die ${existing.size} Vermögenswerte auf diesem Handy werden dabei ersetzt.",
                style = K.type.label, color = c.negative, modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        PrimaryButton("Übernehmen", loading = busy) {
            busy = true
            scope.launch {
                vm.c.backups.apply(backup, lockPossible = Biometrics.availability(activity) == Biometrics.Availability.READY)
                vm.refresh(force = true)
                haptics.celebrate()
                onDone()
            }
        }
        Box(Modifier.fillMaxWidth().height(52.dp).pressable(onClick = onCancel), contentAlignment = Alignment.Center) {
            Text("Abbrechen", style = K.type.label, color = c.textSecondary)
        }
    }
}
