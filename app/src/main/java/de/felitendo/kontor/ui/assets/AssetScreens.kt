package de.felitendo.kontor.ui.assets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.AssetKind
import de.felitendo.kontor.data.model.CashHolding
import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.FixedDeposit
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.Metal
import de.felitendo.kontor.data.model.MetalHolding
import de.felitendo.kontor.data.model.OtherAsset
import de.felitendo.kontor.data.model.PensionContract
import de.felitendo.kontor.data.model.Position
import de.felitendo.kontor.data.model.SavingsAccount
import de.felitendo.kontor.data.model.SeriesPoint
import de.felitendo.kontor.domain.composeHistory
import de.felitendo.kontor.domain.dayChange
import de.felitendo.kontor.domain.displayName
import de.felitendo.kontor.domain.invested
import de.felitendo.kontor.domain.maturityDay
import de.felitendo.kontor.domain.maturityValue
import de.felitendo.kontor.domain.progress
import de.felitendo.kontor.domain.projectBalance
import de.felitendo.kontor.domain.today
import de.felitendo.kontor.domain.value
import de.felitendo.kontor.domain.yearlyInterest
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.Routes
import de.felitendo.kontor.ui.components.BankLogo
import de.felitendo.kontor.ui.components.ChangeLabel
import de.felitendo.kontor.ui.components.IconCircleButton
import de.felitendo.kontor.ui.components.InstrumentLogo
import de.felitendo.kontor.ui.components.KindIcon
import de.felitendo.kontor.ui.components.LineChart
import de.felitendo.kontor.ui.components.Pill
import de.felitendo.kontor.ui.components.PrimaryButton
import de.felitendo.kontor.ui.components.RangeSelector
import de.felitendo.kontor.ui.components.RollingText
import de.felitendo.kontor.ui.components.SectionHeader
import de.felitendo.kontor.ui.components.StatGrid
import de.felitendo.kontor.ui.components.TopBar
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.instrument.rangeText
import de.felitendo.kontor.ui.search.Attribution
import de.felitendo.kontor.ui.theme.K
import java.time.LocalDate

// ---------------------------------------------------------------- Kategorie

@Composable
fun KindScreen(nav: NavController, kind: AssetKind) {
    val vm = LocalVm.current
    val c = K.colors
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hidden = settings?.hideAmounts == true
    val list = portfolio?.assets.orEmpty().filter { it.kind == kind }
    val total = list.sumOf { it.value(cache) }
    val market = kind == AssetKind.DEPOT || kind == AssetKind.METAL
    val scroll = rememberScrollState()
    val showTitle by remember { derivedStateOf { scroll.value > 120 } }

    var range by remember { mutableStateOf(ChartRange.MONTH) }
    var series by remember { mutableStateOf<List<SeriesPoint>>(emptyList()) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    if (market) {
        LaunchedEffect(list, range, cache.updatedAt) { series = composeHistory(list, cache, range, vm.c.market) }
    }
    val values = series.map { it.v }
    val dayChange = list.sumOf { it.dayChange(cache) }
    val shown = scrub?.let { values.getOrNull(it) } ?: total
    val base = if (range == ChartRange.DAY) total - dayChange else values.firstOrNull() ?: total

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(kind.title, showTitle, onBack = { nav.popBackStack() })
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(kind.title, style = K.type.label, color = c.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    RollingText(Fmt.eur(shown, hidden), K.type.hero, c.text, animate = scrub == null)
                    if (market) {
                        val ch = shown - base
                        ChangeLabel(ch, if (base != 0.0) ch / base * 100 else null, hidden,
                            suffix = scrub?.let { series.getOrNull(it)?.let { p -> Fmt.timestamp(p.t, range.days <= 31) } } ?: rangeText(range), animate = scrub == null)
                    } else {
                        val interest = list.sumOf { it.yearlyInterest(cache) }
                        if (interest > 0) Text("+" + Fmt.eur(interest, hidden) + " Zinsen pro Jahr", style = K.type.label, color = c.positive)
                    }
                }
                if (market && values.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    LineChart(
                        values, Modifier.fillMaxWidth().height(200.dp),
                        baseline = if (range == ChartRange.DAY) base else null,
                        live = range == ChartRange.DAY, onScrub = { scrub = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    RangeSelector(range, { range = it })
                }
                Spacer(Modifier.height(12.dp))

                if (kind == AssetKind.DEPOT) {
                    list.filterIsInstance<Depot>().forEach { d -> DepotBlock(d, cache, hidden, nav) }
                } else if (list.isEmpty()) {
                    Text(
                        "Hier ist noch nichts erfasst.",
                        style = K.type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                } else {
                    SectionHeader(if (list.size == 1) "Eintrag" else "Einträge")
                    list.forEach { a -> AssetRow(a, cache, hidden) { nav.navigate(Routes.Detail(a.id)) } }
                }
                if (kind == AssetKind.DEPOT) Attribution()
                Spacer(Modifier.height(100.dp))
            }
        }
        PrimaryButton(
            if (kind == AssetKind.DEPOT) "Neues Depot" else "${kind.singular} hinzufügen",
            Modifier.align(Alignment.BottomCenter).padding(20.dp).navigationBarsPadding(),
        ) { nav.navigate(Routes.Add(kind)) }
    }
}

@Composable
private fun DepotBlock(d: Depot, cache: MarketCache, hidden: Boolean, nav: NavController) {
    val c = K.colors
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().pressable(scaleTo = 0.98f) { nav.navigate(Routes.Detail(d.id)) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankLogo(d.bank, 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(d.name, style = K.type.title, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Fmt.eur(d.value(cache), hidden), style = K.type.bodyStrong, color = c.text)
    }
    d.positions.sortedByDescending { it.instrument.isin.let { i -> (cache.quotes[i]?.price ?: 0.0) * it.quantity } }.forEach { p ->
        PositionRow(p, cache, hidden) {
            nav.navigate(Routes.Instrument(isin = p.instrument.isin, name = p.instrument.name, wkn = p.instrument.wkn, type = p.instrument.type.name, depotId = d.id, positionId = p.id))
        }
    }
    AddMoreRow("Wertpapier hinzufügen") { nav.navigate(Routes.Search(d.id)) }
}

@Composable
fun PositionRow(p: Position, cache: MarketCache, hidden: Boolean, onClick: () -> Unit) {
    val c = K.colors
    val q = cache.quotes[p.instrument.isin]
    Row(
        Modifier.fillMaxWidth().pressable(scaleTo = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(p.instrument.isin, p.instrument.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.instrument.name, style = K.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Fmt.qty(p.quantity)} Stk." + (q?.let { " · " + Fmt.eur(it.price) } ?: ""), style = K.type.caption, color = c.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(q?.let { Fmt.eur(it.price * p.quantity, hidden) } ?: "—", style = K.type.bodyStrong, color = c.text)
            q?.changePct?.let { Text(Fmt.signedPct(it), style = K.type.caption, color = if (it >= 0) c.positive else c.negative) }
        }
    }
}

@Composable
fun AssetRow(a: Asset, cache: MarketCache, hidden: Boolean, onClick: () -> Unit) {
    val c = K.colors
    val subtitle = when (a) {
        is FixedDeposit -> Fmt.rate(a.rate) + (a.maturityDay?.let { " · bis " + Fmt.shortDate(it) } ?: "")
        is SavingsAccount -> Fmt.rate(a.rate) + if (a.monthlyDeposit > 0) " · +" + Fmt.eur(a.monthlyDeposit, hidden, false) + "/Monat" else ""
        is PensionContract -> listOfNotNull(a.rate?.let { Fmt.rate(it) }, a.monthlyContribution.takeIf { it > 0 }?.let { Fmt.eur(it, hidden, false) + "/Monat" }).joinToString(" · ").ifEmpty { "Riester" }
        is MetalHolding -> Fmt.ounces(a.ounces) + " " + a.metal.title
        is Depot -> "${a.positions.size} Positionen"
        is CashHolding -> "Bargeld"
        is OtherAsset -> "Sonstiges"
    }
    Row(
        Modifier.fillMaxWidth().pressable(scaleTo = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetLogo(a)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.displayName, style = K.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = K.type.caption, color = c.textSecondary, maxLines = 1)
        }
        Text(Fmt.eur(a.value(cache), hidden), style = K.type.bodyStrong, color = c.text)
    }
}

@Composable
fun AssetLogo(a: Asset, size: androidx.compose.ui.unit.Dp = 44.dp) = when (a) {
    is FixedDeposit -> BankLogo(a.bank, size)
    is SavingsAccount -> BankLogo(a.bank, size)
    is PensionContract -> BankLogo(a.provider, size)
    is Depot -> BankLogo(a.bank, size)
    is MetalHolding -> KindIcon(AssetKind.METAL, size, metal = a.metal)
    else -> KindIcon(a.kind, size)
}

// ---------------------------------------------------------------- Detail

@Composable
fun AssetDetailScreen(nav: NavController, id: String) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hidden = settings?.hideAmounts == true
    val asset = portfolio?.assets?.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    var updateBalance by remember { mutableStateOf(false) }

    if (asset == null) {
        // Deleted (possibly while this screen animates out) — never pop from here, that
        // would close the screen below as well.
        Column(Modifier.fillMaxSize().background(c.background)) {
            TopBar(onBack = { nav.popBackStack() })
            Text("Dieser Eintrag existiert nicht mehr.", style = K.type.body, color = c.textSecondary, modifier = Modifier.padding(20.dp))
        }
        return
    }
    val scroll = rememberScrollState()
    val showTitle by remember { derivedStateOf { scroll.value > 140 } }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(asset.displayName, showTitle, onBack = { nav.popBackStack() }) {
                IconCircleButton(Icons.Rounded.Edit, "Bearbeiten", size = 40.dp) { nav.navigate(Routes.Edit(asset.id)) }
                IconCircleButton(Icons.Rounded.DeleteOutline, "Löschen", size = 40.dp) { haptics.heavy(); confirmDelete = true }
            }
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssetLogo(asset, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(asset.displayName, style = K.type.title, color = c.text)
                        Text(asset.kind.singular, style = K.type.caption, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(20.dp))
                when (asset) {
                    is FixedDeposit -> FixedDepositDetail(asset, hidden)
                    is SavingsAccount -> ProjectionDetail(
                        value = asset.value(cache), hidden = hidden, label = "Geschätzter Kontostand",
                        project = { d -> projectBalance(asset.balance, asset.rate, asset.monthlyDeposit, asset.balanceDay, d) },
                        stats = listOf(
                            "Zinssatz" to Fmt.rate(asset.rate),
                            "Sparrate" to if (asset.monthlyDeposit > 0) Fmt.eur(asset.monthlyDeposit, hidden) else "—",
                            "Zinsen pro Jahr" to Fmt.eur(asset.yearlyInterest(cache), hidden),
                            "Zuletzt bestätigt" to Fmt.shortDate(asset.balanceDay),
                        ),
                        note = "Kontor rechnet ab dem ${Fmt.shortDate(asset.balanceDay)} mit monatlicher Zinsgutschrift und Sparrate weiter.",
                        onUpdate = { updateBalance = true },
                    )
                    is PensionContract -> ProjectionDetail(
                        value = asset.value(cache), hidden = hidden, label = "Angespart",
                        project = { d -> projectBalance(asset.balance, asset.rate ?: 0.0, asset.monthlyContribution, asset.balanceDay, d, yearlyCompounding = true) },
                        stats = listOf(
                            "Zinssatz" to (asset.rate?.let { Fmt.rate(it) } ?: "—"),
                            "Eigenbeitrag" to if (asset.monthlyContribution > 0) Fmt.eur(asset.monthlyContribution, hidden) + "/Monat" else "—",
                            "Stand vom" to Fmt.shortDate(asset.balanceDay),
                            "Anbieter" to asset.provider.name,
                        ),
                        note = null,
                        onUpdate = { updateBalance = true },
                    )
                    is MetalHolding -> MetalDetail(asset, cache, hidden)
                    is Depot -> DepotDetail(asset, cache, hidden, nav)
                    is CashHolding, is OtherAsset -> {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text("Wert", style = K.type.label, color = c.textSecondary)
                            RollingText(Fmt.eur(asset.value(cache), hidden), K.type.hero, c.text)
                        }
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (updateBalance) {
        BalanceUpdateOverlay(asset, onDone = { updateBalance = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${asset.kind.singular} löschen?") },
            text = { Text("„${asset.displayName}“ wird aus Kontor entfernt. Das lässt sich nicht rückgängig machen.") },
            confirmButton = {
                TextButton({ haptics.reject(); confirmDelete = false; vm.delete(asset.id); nav.popBackStack() }) { Text("Löschen", color = c.negative) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen", color = c.text) } },
            containerColor = c.surfaceHigh,
        )
    }
}

@Composable
private fun FixedDepositDetail(fd: FixedDeposit, hidden: Boolean) {
    val c = K.colors
    val now = today()
    val value = fd.value(MarketCache(), now)
    val end = fd.maturityDay
    val progress = fd.progress(now)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(progress) { sweep.animateTo(progress, tween(1200, easing = FastOutSlowInEasing)) }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Aktueller Wert inkl. Zinsen", style = K.type.label, color = c.textSecondary)
        RollingText(Fmt.eur(value, hidden), K.type.hero, c.text)
        Text("+" + Fmt.eur(value - fd.amount, hidden) + " Zinsen bisher", style = K.type.label, color = c.positive)
    }
    if (end != null) {
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val d = size.minDimension - stroke
                    val tl = Offset(stroke / 2, stroke / 2)
                    drawArc(c.surfaceHigh, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                    drawArc(c.text, -90f, 360f * sweep.value, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val left = (end - now).coerceAtLeast(0)
                    Text(if (left == 0L) "Fällig" else left.toString(), style = K.type.display, color = c.text)
                    Text(if (left == 0L) Fmt.shortDate(end) else if (left == 1L) "Tag übrig" else "Tage übrig", style = K.type.caption, color = c.textSecondary)
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    StatGrid(
        listOfNotNull(
            "Anlagebetrag" to Fmt.eur(fd.amount, hidden),
            "Zinssatz" to Fmt.rate(fd.rate),
            "Angelegt am" to Fmt.shortDate(fd.startDay),
            end?.let { "Fällig am" to Fmt.shortDate(it) },
            "Laufzeit" to Fmt.months(fd.termMonths),
            fd.maturityValue?.let { "Bei Fälligkeit" to Fmt.eur(it, hidden) },
        ),
    )
}

@Composable
private fun ProjectionDetail(
    value: Double,
    hidden: Boolean,
    label: String,
    project: (Long) -> Double,
    stats: List<Pair<String, String>>,
    note: String?,
    onUpdate: () -> Unit,
) {
    val c = K.colors
    val haptics = LocalHaptics.current
    var years by remember { mutableStateOf(5) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    val now = LocalDate.now()
    val months = years * 12
    val points = remember(years, value) { (0..months).map { m -> project(now.plusMonths(m.toLong()).toEpochDay()) } }
    val shown = scrub?.let { points.getOrNull(it) }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(if (shown != null) "Prognose " + Fmt.monthYear(now.plusMonths(scrub!!.toLong()).toEpochDay()) else label, style = K.type.label, color = c.textSecondary)
        RollingText(Fmt.eur(shown ?: value, hidden), K.type.hero, c.text, animate = scrub == null)
        val end = points.last()
        Text("In $years Jahren ≈ " + Fmt.eur(end, hidden, false), style = K.type.label, color = c.positive)
    }
    Spacer(Modifier.height(12.dp))
    LineChart(points, Modifier.fillMaxWidth().height(180.dp), onScrub = { scrub = it })
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 5, 10, 20).forEach { y -> Pill("$y J", years == y) { haptics.selection(); years = y } }
    }
    Spacer(Modifier.height(8.dp))
    StatGrid(stats)
    note?.let { Text(it, style = K.type.caption, color = c.textSecondary, modifier = Modifier.padding(20.dp)) }
    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        de.felitendo.kontor.ui.components.SecondaryButton("Kontostand aktualisieren", icon = Icons.Rounded.Edit, onClick = onUpdate)
    }
}

@Composable
private fun MetalDetail(m: MetalHolding, cache: MarketCache, hidden: Boolean) {
    val vm = LocalVm.current
    val c = K.colors
    val quote = cache.metals[m.metal]
    var range by remember { mutableStateOf(ChartRange.MONTH) }
    var series by remember { mutableStateOf<List<SeriesPoint>>(emptyList()) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(range, quote?.price) { series = vm.c.market.metalSeries(m.metal, range, quote?.price) }
    val values = series.map { it.v * m.ounces }
    val value = quote?.price?.let { it * m.ounces }
    val shown = scrub?.let { values.getOrNull(it) } ?: value
    val base = if (range == ChartRange.DAY) quote?.previousClose?.times(m.ounces) else values.firstOrNull()

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("${Fmt.ounces(m.ounces)} ${m.metal.title}", style = K.type.label, color = c.textSecondary)
        RollingText(shown?.let { Fmt.eur(it, hidden) } ?: "—", K.type.hero, c.text, animate = scrub == null)
        if (shown != null && base != null && base != 0.0) {
            ChangeLabel(shown - base, (shown - base) / base * 100, hidden,
                suffix = scrub?.let { series.getOrNull(it)?.let { p -> Fmt.timestamp(p.t, range.days <= 31) } } ?: rangeText(range), animate = scrub == null)
        }
    }
    Spacer(Modifier.height(12.dp))
    if (values.size > 1) {
        LineChart(values, Modifier.fillMaxWidth().height(200.dp), baseline = if (range == ChartRange.DAY) base else null, live = range == ChartRange.DAY, onScrub = { scrub = it })
    } else {
        Box(Modifier.fillMaxWidth().height(200.dp))
    }
    Spacer(Modifier.height(8.dp))
    RangeSelector(range, { range = it }, ranges = ChartRange.entries.filter { it != ChartRange.MAX })
    Spacer(Modifier.height(12.dp))
    StatGrid(
        listOfNotNull(
            quote?.let { "Preis je Unze" to Fmt.eur(it.price) },
            quote?.let { "Preis je Gramm" to Fmt.eur(it.price / GRAMS_PER_OUNCE) },
            "Menge" to Fmt.ounces(m.ounces),
            "In Gramm" to Fmt.number(m.ounces * GRAMS_PER_OUNCE) + " g",
            m.purchasePrice?.let { "Kaufpreis" to Fmt.eur(it, hidden) },
            if (m.purchasePrice != null && value != null) "Gewinn" to Fmt.signedEur(value - m.purchasePrice, hidden) else null,
        ),
    )
    Text("Spotpreis via gold-api.com, Verlauf nach Terminkontrakt (COMEX) in Euro umgerechnet.", style = K.type.caption, color = c.textTertiary, modifier = Modifier.padding(20.dp))
}

@Composable
private fun DepotDetail(d: Depot, cache: MarketCache, hidden: Boolean, nav: NavController) {
    val c = K.colors
    val value = d.value(cache)
    val change = d.dayChange(cache)
    val invested = d.invested()
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Depotwert", style = K.type.label, color = c.textSecondary)
        RollingText(Fmt.eur(value, hidden), K.type.hero, c.text)
        ChangeLabel(change, if (value - change != 0.0) change / (value - change) * 100 else null, hidden, suffix = "Heute")
    }
    if (invested != null) {
        Spacer(Modifier.height(12.dp))
        StatGrid(listOf("Investiert" to Fmt.eur(invested, hidden), "Gewinn" to Fmt.signedEur(value - invested, hidden)))
    }
    SectionHeader("Positionen")
    d.positions.forEach { p ->
        PositionRow(p, cache, hidden) {
            nav.navigate(Routes.Instrument(isin = p.instrument.isin, name = p.instrument.name, wkn = p.instrument.wkn, type = p.instrument.type.name, depotId = d.id, positionId = p.id))
        }
    }
    AddMoreRow("Wertpapier hinzufügen") { nav.navigate(Routes.Search(d.id)) }
    Attribution()
}

/** Quick "Kontostand aktualisieren": only the amount, everything else stays. */
@Composable
private fun BalanceUpdateOverlay(asset: Asset, onDone: () -> Unit) {
    val vm = LocalVm.current
    val haptics = LocalHaptics.current
    val state = remember { FlowState(1) }
    val current = asset.value(MarketCache())
    Box(Modifier.fillMaxSize()) {
        FlowScaffold(state, onExit = onDone, closeIcon = true) {
            AmountStep("Neuer Kontostand", "Stand heute", initial = de.felitendo.kontor.ui.components.rawOf(current, 2), button = "Speichern") { raw ->
                val v = Fmt.parse(raw) ?: return@AmountStep
                val updated = when (asset) {
                    is SavingsAccount -> asset.copy(balance = v, balanceDay = today())
                    is PensionContract -> asset.copy(balance = v, balanceDay = today())
                    else -> asset
                }
                haptics.success()
                vm.save(updated, refreshAfter = false)
                onDone()
            }
        }
    }
}

// ---------------------------------------------------------------- Hinzufügen / Bearbeiten

@Composable
fun AddAssetScreen(nav: NavController, kind: AssetKind) {
    val vm = LocalVm.current
    AssetFlow(kind, null, onSaved = { asset ->
        vm.save(asset)
        nav.popBackStack()
        if (asset is Depot) nav.navigate(Routes.Search(asset.id))
    }, onExit = { nav.popBackStack() })
}

@Composable
fun EditAssetScreen(nav: NavController, id: String) {
    val vm = LocalVm.current
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val asset = remember { portfolio?.assets?.firstOrNull { it.id == id } }
    if (asset == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    AssetFlow(asset.kind, asset, onSaved = { vm.save(it); nav.popBackStack() }, onExit = { nav.popBackStack() })
}
