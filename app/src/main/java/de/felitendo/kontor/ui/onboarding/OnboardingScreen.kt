package de.felitendo.kontor.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.felitendo.kontor.MainActivity
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.AssetKind
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.domain.byKind
import de.felitendo.kontor.domain.displayName
import de.felitendo.kontor.domain.value
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.assets.AddMoreRow
import de.felitendo.kontor.ui.assets.AssetFlow
import de.felitendo.kontor.ui.assets.AssetLogo
import de.felitendo.kontor.ui.assets.CheckDot
import de.felitendo.kontor.ui.assets.ChoiceTile
import de.felitendo.kontor.ui.assets.PositionRow
import de.felitendo.kontor.ui.components.AnimatedLogo
import de.felitendo.kontor.ui.components.CountUpAmount
import de.felitendo.kontor.ui.components.KindIcon
import de.felitendo.kontor.ui.components.PrimaryButton
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.instrument.PositionFlow
import de.felitendo.kontor.ui.lock.Biometrics
import de.felitendo.kontor.ui.theme.K

private sealed interface Phase {
    data object Welcome : Phase
    data object Security : Phase
    data object Kinds : Phase
    data class Entry(val index: Int) : Phase
    data class More(val index: Int) : Phase
    data class Positions(val index: Int, val depotId: String) : Phase
    data object Notify : Phase
    data object Done : Phase
}

/** First launch: welcome, app lock, then a guided tour that captures every asset. */
@Composable
fun OnboardingScreen(activity: MainActivity, onFinish: () -> Unit) {
    val vm = LocalVm.current
    val context = LocalContext.current
    var phase by remember { mutableStateOf<Phase>(Phase.Welcome) }
    val kinds = remember { mutableStateListOf<AssetKind>() }
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val assets = portfolio?.assets.orEmpty()

    val needsNotify = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun afterKind(index: Int) {
        phase = if (index + 1 < kinds.size) Phase.Entry(index + 1) else if (needsNotify) Phase.Notify else Phase.Done
    }

    BackHandler(phase !is Phase.Welcome) {
        phase = when (val p = phase) {
            Phase.Security -> Phase.Welcome
            Phase.Kinds -> Phase.Security
            is Phase.Entry -> if (p.index == 0) Phase.Kinds else Phase.More(p.index - 1)
            is Phase.More -> Phase.Entry(p.index)
            is Phase.Positions -> Phase.More(p.index)
            Phase.Notify, Phase.Done -> if (kinds.isEmpty()) Phase.Kinds else Phase.More(kinds.lastIndex)
            Phase.Welcome -> Phase.Welcome
        }
    }

    AnimatedContent(
        targetState = phase,
        transitionSpec = { (fadeIn(tween(380, 120)) + slideInVertically(tween(420)) { it / 14 }) togetherWith fadeOut(tween(200)) },
        label = "onboarding",
        modifier = Modifier.fillMaxSize().background(K.colors.background),
    ) { p ->
        when (p) {
            Phase.Welcome -> Welcome { phase = Phase.Security }
            Phase.Security -> Security(activity) { phase = Phase.Kinds }
            Phase.Kinds -> KindPicker(kinds) { if (kinds.isEmpty()) phase = if (needsNotify) Phase.Notify else Phase.Done else phase = Phase.Entry(0) }
            is Phase.Entry -> {
                val kind = kinds[p.index]
                AssetFlow(kind, null, onSaved = { asset ->
                    vm.save(asset)
                    phase = if (asset is Depot) Phase.Positions(p.index, asset.id) else Phase.More(p.index)
                }, onExit = { phase = if (p.index == 0) Phase.Kinds else Phase.More(p.index - 1) })
            }
            is Phase.More -> MoreOf(kinds[p.index], assets.filter { it.kind == kinds[p.index] }, onAnother = { phase = Phase.Entry(p.index) }) { afterKind(p.index) }
            is Phase.Positions -> DepotPositions(p.depotId) { phase = Phase.More(p.index) }
            Phase.Notify -> Notify { phase = Phase.Done }
            Phase.Done -> Done(assets) {
                vm.updateSettings { it.copy(onboardingDone = true, lockAsked = true) }
                onFinish()
            }
        }
    }
}

@Composable
private fun Page(horizontal: androidx.compose.ui.unit.Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = horizontal, vertical = 24.dp), content = content)
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    val c = K.colors
    val haptics = LocalHaptics.current
    var drawn by remember { mutableStateOf(false) }
    Page {
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedLogo(Modifier.size(120.dp)) { drawn = true; haptics.success() }
        }
        Spacer(Modifier.height(28.dp))
        AnimatedVisibility(drawn, enter = fadeIn(tween(500)) + slideInVertically(tween(600)) { it / 2 }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Kontor", style = K.type.hero, color = c.text)
                Spacer(Modifier.height(8.dp))
                Text("Dein ganzes Vermögen.\nAuf einen Blick.", style = K.type.title, color = c.textSecondary, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.weight(1.3f))
        AnimatedVisibility(drawn, enter = fadeIn(tween(500, 400))) {
            Column {
                PrimaryButton("Los geht's") { onNext() }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Alles bleibt auf deinem Gerät. Open Source unter GPLv3.",
                    style = K.type.caption, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Security(activity: MainActivity, onNext: () -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    var availability by remember { mutableStateOf(Biometrics.availability(activity)) }
    // Coming back from the Android security settings may have changed things.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { availability = Biometrics.availability(activity) }
    var success by remember { mutableStateOf(false) }

    Page {
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { FaceScan(success, Modifier.size(150.dp)) }
        Spacer(Modifier.height(36.dp))
        Text("Schütze dein Vermögen", style = K.type.headline, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(
            if (availability == Biometrics.Availability.READY) "Soll sich Kontor mit Gesichtserkennung sperren? Dann sieht niemand deine Zahlen, auch wenn das Handy entsperrt herumliegt."
            else "Auf diesem Handy ist noch keine Gesichtserkennung oder PIN eingerichtet. Du kannst die Sperre später in den Einstellungen aktivieren.",
            style = K.type.body, color = c.textSecondary,
        )
        Spacer(Modifier.weight(1.2f))
        if (availability == Biometrics.Availability.READY) {
            PrimaryButton("Mit Gesichtserkennung sperren") {
                Biometrics.authenticate(activity, "Sperre einrichten", "Bestätige einmal, damit Kontor dich erkennt") { ok ->
                    if (ok) {
                        activity.unlockedAt = android.os.SystemClock.elapsedRealtime()
                        success = true
                        haptics.success()
                        vm.updateSettings { it.copy(lockEnabled = true, lockAsked = true) }
                        onNext()
                    } else haptics.reject()
                }
            }
        } else {
            PrimaryButton("Einrichten") {
                activity.suppressNextLock = true
                val intent = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_BIOMETRIC_ENROLL) else Intent(Settings.ACTION_SECURITY_SETTINGS)
                runCatching { activity.startActivity(intent) }
            }
        }
        Box(Modifier.fillMaxWidth().height(52.dp).pressable { vm.updateSettings { it.copy(lockAsked = true) }; onNext() }, contentAlignment = Alignment.Center) {
            Text("Nicht jetzt", style = K.type.label, color = c.textSecondary)
        }
    }
}

/** Face ID style frame: four corners breathe while a scan line sweeps; turns into a check on success. */
@Composable
private fun FaceScan(success: Boolean, modifier: Modifier) {
    val c = K.colors
    val t = rememberInfiniteTransition(label = "scan")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val inset = s * (0.06f + 0.03f * breathe)
            val len = s * 0.22f
            val stroke = Stroke(width = s * 0.035f, cap = StrokeCap.Round)
            val r = s * 0.12f
            val l = inset; val tp = inset; val rt = s - inset; val b = s - inset
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(c.text, Offset(x, y + dy * r), Offset(x, y + dy * len), stroke.width, StrokeCap.Round)
                drawLine(c.text, Offset(x + dx * r, y), Offset(x + dx * len, y), stroke.width, StrokeCap.Round)
                drawArc(c.text, startAngle = when { dx > 0 && dy > 0 -> 180f; dx < 0 && dy > 0 -> 270f; dx < 0 -> 0f; else -> 90f }, sweepAngle = 90f,
                    useCenter = false, topLeft = Offset(if (dx > 0) x else x - 2 * r, if (dy > 0) y else y - 2 * r), size = Size(2 * r, 2 * r), style = stroke)
            }
            corner(l, tp, 1f, 1f); corner(rt, tp, -1f, 1f); corner(l, b, 1f, -1f); corner(rt, b, -1f, -1f)
            if (!success) {
                // simple face
                val eyeY = s * 0.42f
                drawLine(c.text, Offset(s * 0.38f, eyeY - s * 0.03f), Offset(s * 0.38f, eyeY + s * 0.03f), stroke.width, StrokeCap.Round)
                drawLine(c.text, Offset(s * 0.62f, eyeY - s * 0.03f), Offset(s * 0.62f, eyeY + s * 0.03f), stroke.width, StrokeCap.Round)
                drawLine(c.text, Offset(s * 0.5f, eyeY), Offset(s * 0.47f, s * 0.54f), stroke.width, StrokeCap.Round)
                drawArc(c.text, 20f, 140f, false, Offset(s * 0.36f, s * 0.48f), Size(s * 0.28f, s * 0.16f), style = stroke)
                val y = inset + (s - 2 * inset) * scan
                drawRoundRect(c.text.copy(alpha = 0.12f), Offset(inset, y - s * 0.02f), Size(s - 2 * inset, s * 0.04f), CornerRadius(s * 0.02f))
            }
        }
        if (success) CheckDot(true, 64.dp)
    }
}

@Composable
private fun KindPicker(selected: MutableList<AssetKind>, onNext: () -> Unit) {
    val c = K.colors
    val haptics = LocalHaptics.current
    val text = mapOf(
        AssetKind.FIXED_DEPOSIT to "Bankname, Betrag, Zinssatz",
        AssetKind.SAVINGS to "Mit Zins und monatlicher Sparrate",
        AssetKind.METAL to "In Unzen — der Preis kommt live",
        AssetKind.DEPOT to "Aktien & ETFs per WKN suchen",
        AssetKind.PENSION to "Riester-Guthaben",
        AssetKind.CASH to "Zu Hause, im Tresor, …",
        AssetKind.OTHER to "Immobilie, Auto, Schmuck …",
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Was gehört zu deinem Vermögen?", style = K.type.headline, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text("Wähle alles aus — wir gehen es danach Schritt für Schritt durch.", style = K.type.body, color = c.textSecondary)
            Spacer(Modifier.height(20.dp))
            AssetKind.entries.forEachIndexed { i, kind ->
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { enter.animateTo(1f, tween(420, i * 55, FastOutSlowInEasing)) }
                ChoiceTile(
                    kind.title, text[kind], selected = kind in selected,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 60f },
                    leading = { KindIcon(kind) },
                ) {
                    if (kind in selected) { selected.remove(kind); haptics.toggle(false) } else { selected.add(kind); haptics.toggle(true) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.padding(20.dp)) {
            PrimaryButton(if (selected.isEmpty()) "Später hinzufügen" else "Weiter mit ${selected.size}") {
                selected.sortBy { it.ordinal }
                onNext()
            }
        }
    }
}

@Composable
private fun MoreOf(kind: AssetKind, items: List<Asset>, onAnother: () -> Unit, onNext: () -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    val cache by vm.market.collectAsStateWithLifecycle()
    Page(horizontal = 0.dp) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
            CheckDot(true, 56.dp)
            Spacer(Modifier.height(20.dp))
            Text("${kind.title} gespeichert", style = K.type.headline, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(
                if (kind == AssetKind.DEPOT) "Hast du noch ein weiteres Depot?" else "Hast du noch mehr ${kind.title}?",
                style = K.type.body, color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            items.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssetLogo(a)
                    Spacer(Modifier.width(14.dp))
                    Text(a.displayName, style = K.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                    Text(Fmt.eur(a.value(cache)), style = K.type.bodyStrong, color = c.text)
                }
            }
            Spacer(Modifier.height(4.dp))
            AddMoreRow(if (kind == AssetKind.DEPOT) "Weiteres Depot" else "Weitere hinzufügen", onAnother)
        }
        PrimaryButton("Weiter", Modifier.padding(horizontal = 24.dp)) { onNext() }
    }
}

@Composable
private fun DepotPositions(depotId: String, onNext: () -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val depot = portfolio?.assets?.filterIsInstance<Depot>()?.firstOrNull { it.id == depotId }
    var adding by remember { mutableStateOf(depot?.positions?.isEmpty() == true) }

    if (adding) {
        PositionFlow(depotId = depotId, onDone = { adding = false }, onExit = { adding = false })
        return
    }
    Page(horizontal = 0.dp) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(depot?.name ?: "Depot", style = K.type.headline, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text("Füge alle Wertpapiere hinzu, die in diesem Depot liegen.", style = K.type.body, color = c.textSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            depot?.positions?.forEach { p -> PositionRow(p, cache, false) {} }
            AddMoreRow("Wertpapier hinzufügen") { adding = true }
        }
        PrimaryButton(if (depot?.positions.isNullOrEmpty()) "Später" else "Fertig mit diesem Depot", Modifier.padding(horizontal = 24.dp)) { onNext() }
    }
}

@Composable
private fun Notify(onNext: () -> Unit) {
    val c = K.colors
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNext() }
    Page {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Rounded.NotificationsActive, null, tint = c.text, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(28.dp))
        Text("Sollen wir dich erinnern?", style = K.type.headline, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text("Kontor meldet sich, wenn ein Festgeld ausläuft und wenn es eine neue Version gibt. Sonst nie.", style = K.type.body, color = c.textSecondary)
        Spacer(Modifier.weight(1.2f))
        PrimaryButton("Erinnerungen erlauben") {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onNext()
        }
        Box(Modifier.fillMaxWidth().height(52.dp).pressable(onClick = onNext), contentAlignment = Alignment.Center) {
            Text("Nein danke", style = K.type.label, color = c.textSecondary)
        }
    }
}

@Composable
private fun Done(assets: List<Asset>, onFinish: () -> Unit) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val cache by vm.market.collectAsStateWithLifecycle()
    val total = assets.sumOf { it.value(cache) }
    var counted by remember { mutableStateOf(false) }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(counted) { if (counted) glow.animateTo(1f, tween(600)) }

    Page {
        Spacer(Modifier.weight(1f))
        Text("Dein Vermögen", style = K.type.title, color = c.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CountUpAmount(total, K.type.hero, c.text, durationMillis = 1600) { counted = true; haptics.celebrate() }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = glow.value }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            assets.byKind(cache).forEach { k ->
                Row(Modifier.fillMaxWidth(0.8f)) {
                    Text(k.kind.title, style = K.type.label, color = c.textSecondary, modifier = Modifier.weight(1f))
                    Text(Fmt.eur(k.value, decimals = false), style = K.type.label, color = c.text)
                }
            }
        }
        Spacer(Modifier.weight(1.3f))
        AnimatedVisibility(counted, enter = fadeIn(tween(400))) {
            PrimaryButton("Zu meinem Kontor") { haptics.success(); onFinish() }
        }
    }
}
