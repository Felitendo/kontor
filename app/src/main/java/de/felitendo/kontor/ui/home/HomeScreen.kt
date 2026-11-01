package de.felitendo.kontor.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.AssetKind
import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.FixedDeposit
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.Metal
import de.felitendo.kontor.data.model.MetalHolding
import de.felitendo.kontor.data.model.SeriesPoint
import de.felitendo.kontor.domain.byKind
import de.felitendo.kontor.domain.composeHistory
import de.felitendo.kontor.domain.dayChange
import de.felitendo.kontor.domain.maturityDay
import de.felitendo.kontor.domain.today
import de.felitendo.kontor.domain.value
import de.felitendo.kontor.domain.yearlyInterest
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.Routes
import de.felitendo.kontor.ui.components.ChangeLabel
import de.felitendo.kontor.ui.components.Donut
import de.felitendo.kontor.ui.components.IconCircleButton
import de.felitendo.kontor.ui.components.KindIcon
import de.felitendo.kontor.ui.components.LineChart
import de.felitendo.kontor.ui.components.PrimaryButton
import de.felitendo.kontor.ui.components.RangeSelector
import de.felitendo.kontor.ui.components.RollingText
import de.felitendo.kontor.ui.components.SectionHeader
import de.felitendo.kontor.ui.components.kindVector
import de.felitendo.kontor.ui.components.metalColor
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.instrument.rangeText
import de.felitendo.kontor.ui.search.Attribution
import de.felitendo.kontor.ui.theme.K
import de.felitendo.kontor.update.UpdateState
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val update by vm.updateState.collectAsStateWithLifecycle()
    val assets = portfolio?.assets.orEmpty()
    val s = settings ?: return
    val hidden = s.hideAmounts

    var range by remember { mutableStateOf(s.homeRange) }
    var series by remember { mutableStateOf<List<SeriesPoint>>(emptyList()) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refresh()
        if (s.autoUpdateCheck) vm.checkUpdates()
    }
    LaunchedEffect(assets, range, cache.updatedAt) {
        series = composeHistory(assets, cache, range, vm.c.market)
    }

    val total = assets.sumOf { it.value(cache) }
    val dayChange = assets.sumOf { it.dayChange(cache) }
    val values = series.map { it.v }
    val scrubValue = scrub?.let { values.getOrNull(it) }
    val shown = scrubValue ?: total
    val base = if (range == ChartRange.DAY) total - dayChange else values.firstOrNull() ?: total
    val change = shown - base

    val pull = rememberPullToRefreshState()
    LaunchedEffect(pull) {
        var armed = false
        snapshotFlow { pull.distanceFraction }.collect { f ->
            if (f >= 1f && !armed) { armed = true; haptics.heavy() }
            if (f < 0.5f) armed = false
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh(force = true) },
            state = pull,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pull, isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                    containerColor = c.surfaceHigh, color = c.text,
                )
            },
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HomeTopBar(
                    hidden = hidden,
                    onToggleHidden = { haptics.toggle(!hidden); vm.updateSettings { it.copy(hideAmounts = !it.hideAmounts) } },
                    onSettings = { nav.navigate(Routes.Settings()) },
                )

                if (assets.isEmpty()) {
                    EmptyHome { showAdd = true }
                    return@Column
                }

                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Gesamtvermögen", style = K.type.label, color = c.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.pressable(scaleTo = 0.98f, haptic = false) {
                        haptics.toggle(!hidden); vm.updateSettings { it.copy(hideAmounts = !it.hideAmounts) }
                    }) {
                        RollingText(Fmt.eur(shown, hidden), K.type.hero, c.text, animate = scrub == null)
                    }
                    ChangeLabel(
                        change, if (base != 0.0) change / base * 100 else null, hidden,
                        suffix = scrub?.let { series.getOrNull(it)?.let { p -> Fmt.timestamp(p.t, range.days <= 31) } } ?: rangeText(range),
                        animate = scrub == null,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(230.dp)) {
                    if (values.size > 1) {
                        LineChart(
                            values, Modifier.fillMaxSize(),
                            baseline = if (range == ChartRange.DAY) base else null,
                            live = range == ChartRange.DAY,
                            onScrub = { scrub = it },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                RangeSelector(range, {
                    range = it
                    vm.updateSettings { st -> st.copy(homeRange = it) }
                })

                AnimatedVisibility(update is UpdateState.Available, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    val rel = (update as? UpdateState.Available)?.release
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)).background(c.text)
                            .pressable { nav.navigate(Routes.Settings(openUpdate = true)) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, null, tint = c.background)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Update verfügbar", style = K.type.bodyStrong, color = c.background)
                            Text("Version ${rel?.versionName.orEmpty()} · Tippen zum Installieren", style = K.type.caption, color = c.background.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = c.background)
                    }
                }

                InsightRow(assets, cache, hidden, nav)

                Allocation(assets, cache, hidden) { nav.navigate(Routes.Kind(it)) }

                SectionHeader("Vermögenswerte")
                assets.byKind(cache).forEach { kt ->
                    KindRow(kt.kind, assets.filter { it.kind == kt.kind }, cache, hidden) { nav.navigate(Routes.Kind(kt.kind)) }
                }

                NextMaturity(assets, hidden) { nav.navigate(Routes.Detail(it)) }

                Spacer(Modifier.height(24.dp))
                Text(
                    "Kurse " + Fmt.relative(cache.updatedAt) + " aktualisiert",
                    style = K.type.caption, color = c.textTertiary, modifier = Modifier.padding(horizontal = 20.dp),
                )
                Attribution(Modifier.padding(bottom = 80.dp))
            }
        }

        // Content scrolls under the transparent status bar; fade it out there.
        Box(
            Modifier.fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.background, c.background, c.background.copy(alpha = 0f))),
            ).statusBarsPadding().height(12.dp),
        )
        AddButton(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)) { showAdd = true }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface,
            dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.textTertiary)) },
        ) {
            AddSheet { kind ->
                showAdd = false
                if (kind == AssetKind.DEPOT && assets.any { it is Depot }) nav.navigate(Routes.Search())
                else nav.navigate(Routes.Add(kind))
            }
        }
    }
}

@Composable
private fun HomeTopBar(hidden: Boolean, onToggleHidden: () -> Unit, onSettings: () -> Unit) {
    val c = K.colors
    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        else -> "Guten Abend"
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(greeting, style = K.type.title, color = c.text, modifier = Modifier.weight(1f))
        IconCircleButton(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Beträge verbergen", size = 40.dp, onClick = onToggleHidden)
        Spacer(Modifier.width(8.dp))
        IconCircleButton(Icons.Rounded.Tune, "Einstellungen", size = 40.dp, onClick = onSettings)
    }
}

@Composable
private fun AddButton(modifier: Modifier, onClick: () -> Unit) {
    val c = K.colors
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(500, 250, FastOutSlowInEasing)) }
    Row(
        modifier
            .graphicsLayer { translationY = (1 - enter.value) * 120f; alpha = enter.value }
            .pressable(scaleTo = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(28.dp))
            .background(c.accent)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.onAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Hinzufügen", style = K.type.bodyStrong, color = c.onAccent)
    }
}

@Composable
fun AddSheet(onPick: (AssetKind) -> Unit) {
    val c = K.colors
    val descriptions = mapOf(
        AssetKind.FIXED_DEPOSIT to "Festgeld mit Laufzeit und Zins",
        AssetKind.SAVINGS to "Tagesgeld mit Sparrate",
        AssetKind.METAL to "Gold, Silber oder Platin",
        AssetKind.DEPOT to "Aktien, ETFs und Fonds per WKN",
        AssetKind.PENSION to "Riester-Vertrag",
        AssetKind.CASH to "Bargeld zu Hause oder im Tresor",
        AssetKind.OTHER to "Immobilie, Auto, Schmuck …",
    )
    Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
        Text("Was möchtest du hinzufügen?", style = K.type.title, color = c.text, modifier = Modifier.padding(20.dp))
        AssetKind.entries.forEach { kind ->
            Row(
                Modifier.fillMaxWidth().pressable(scaleTo = 0.98f) { onPick(kind) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KindIcon(kind)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(kind.title, style = K.type.bodyStrong, color = c.text)
                    Text(descriptions[kind].orEmpty(), style = K.type.caption, color = c.textSecondary)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = c.textTertiary)
            }
        }
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit) {
    val c = K.colors
    Column(Modifier.fillMaxWidth().padding(24.dp).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Noch leer hier", style = K.type.headline, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text("Füge dein erstes Vermögen hinzu – Festgeld, Gold, Aktien oder Bargeld.", style = K.type.body, color = c.textSecondary)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Vermögen hinzufügen") { onAdd() }
    }
}

@Composable
private fun InsightRow(assets: List<Asset>, cache: MarketCache, hidden: Boolean, nav: NavController) {
    val c = K.colors
    val interest = assets.sumOf { it.yearlyInterest(cache) }
    val gold = cache.metals[Metal.GOLD]
    val ownsMetal = assets.any { it is MetalHolding }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (interest > 0) {
            InsightCard("Zinsen", Fmt.eur(interest / 12, hidden), "pro Monat · ${Fmt.eur(interest, hidden, decimals = false)} im Jahr", kindVector(AssetKind.SAVINGS)) {
                nav.navigate(Routes.Forecast)
            }
        }
        if (gold != null) {
            InsightCard(
                "Gold", Fmt.eur(gold.price), "je Unze" + (gold.changePct?.let { " · " + Fmt.signedPct(it) } ?: ""),
                kindVector(AssetKind.METAL), iconTint = metalColor(Metal.GOLD),
            ) { if (ownsMetal) nav.navigate(Routes.Kind(AssetKind.METAL)) else nav.navigate(Routes.Add(AssetKind.METAL)) }
        }
        InsightCard("Prognose", "In 10 Jahren", "Wohin dein Vermögen wachsen kann", Icons.AutoMirrored.Rounded.TrendingUp) {
            nav.navigate(Routes.Forecast)
        }
    }
}

@Composable
private fun InsightCard(
    label: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = K.colors.text,
    onClick: () -> Unit,
) {
    val c = K.colors
    Column(
        Modifier.widthIn(min = 150.dp, max = 220.dp)
            .pressable(scaleTo = 0.96f, onClick = onClick)
            .clip(RoundedCornerShape(22.dp)).background(c.surface).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = K.type.label, color = c.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Text(value, style = K.type.title, color = c.text, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(detail, style = K.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Allocation(assets: List<Asset>, cache: MarketCache, hidden: Boolean, onKind: (AssetKind) -> Unit) {
    val c = K.colors
    val kinds = assets.byKind(cache).filter { it.value > 0 }
    if (kinds.size < 2) return
    var selected by remember { mutableStateOf<Int?>(null) }
    SectionHeader("Aufteilung")
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Donut(kinds.map { it.share }, c.ramp, selected, { selected = it }, Modifier.fillMaxSize())
            val sel = selected?.let { kinds.getOrNull(it) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(sel?.let { Fmt.pct(it.share * 100.0) } ?: kinds.size.toString(), style = K.type.title, color = c.text)
                Text(sel?.kind?.title ?: "Anlagearten", style = K.type.caption, color = c.textSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            kinds.forEachIndexed { i, k ->
                Row(
                    Modifier.fillMaxWidth().pressable(scaleTo = 0.97f) { if (selected == i) onKind(k.kind) else selected = i }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.ramp[i % c.ramp.size]))
                    Spacer(Modifier.width(10.dp))
                    Text(k.kind.title, style = K.type.label, color = if (selected == null || selected == i) c.text else c.textSecondary, modifier = Modifier.weight(1f))
                    Text(Fmt.pct(k.share * 100.0), style = K.type.label, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
fun KindRow(kind: AssetKind, list: List<Asset>, cache: MarketCache, hidden: Boolean, onClick: () -> Unit) {
    val c = K.colors
    val value = list.sumOf { it.value(cache) }
    val change = list.sumOf { it.dayChange(cache) }
    val subtitle = when (kind) {
        AssetKind.DEPOT -> {
            val n = list.filterIsInstance<Depot>().sumOf { it.positions.size }
            if (n == 1) "1 Position" else "$n Positionen"
        }
        AssetKind.METAL -> list.filterIsInstance<MetalHolding>().groupBy { it.metal }
            .map { (m, l) -> Fmt.ounces(l.sumOf { it.ounces }) + " " + m.title }.joinToString(" · ")
        AssetKind.FIXED_DEPOSIT, AssetKind.SAVINGS, AssetKind.PENSION -> {
            val interest = list.sumOf { it.yearlyInterest(cache) }
            val rate = if (value > 0) interest / value * 100 else 0.0
            (if (list.size == 1) list.size.toString() + " Konto" else list.size.toString() + " Konten") + if (rate > 0) " · Ø " + Fmt.avgRate(rate) else ""
        }
        else -> if (list.size == 1) "1 Eintrag" else "${list.size} Einträge"
    }
    Row(
        Modifier.fillMaxWidth().pressable(scaleTo = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindIcon(kind, metal = list.filterIsInstance<MetalHolding>().firstOrNull()?.metal)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(kind.title, style = K.type.bodyStrong, color = c.text)
            Text(subtitle, style = K.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.eur(value, hidden), style = K.type.bodyStrong, color = c.text)
            if (kind == AssetKind.DEPOT || kind == AssetKind.METAL) {
                val base = value - change
                val pct = if (base != 0.0) change / base * 100 else 0.0
                Text(Fmt.signedPct(pct), style = K.type.caption, color = if (change >= 0) c.positive else c.negative)
            } else if (change > 0.005) {
                Text("+" + Fmt.eur(change, hidden) + " heute", style = K.type.caption, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun NextMaturity(assets: List<Asset>, hidden: Boolean, onOpen: (String) -> Unit) {
    val c = K.colors
    val now = today()
    val next = assets.filterIsInstance<FixedDeposit>()
        .mapNotNull { fd -> fd.maturityDay?.takeIf { it >= now }?.let { fd to it } }
        .minByOrNull { it.second } ?: return
    val (fd, day) = next
    val days = day - now
    if (days > 120) return
    SectionHeader("Demnächst fällig")
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface)
            .pressable(scaleTo = 0.98f) { onOpen(fd.id) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Event, null, tint = c.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Festgeld ${fd.bank.name}", style = K.type.bodyStrong, color = c.text, maxLines = 1)
            Text(
                when (days) { 0L -> "Heute fällig"; 1L -> "Morgen fällig"; else -> "In $days Tagen · ${Fmt.shortDate(day)}" },
                style = K.type.caption, color = c.textSecondary,
            )
        }
        Text(Fmt.eur(fd.amount, hidden, decimals = false), style = K.type.bodyStrong, color = c.text)
    }
}
