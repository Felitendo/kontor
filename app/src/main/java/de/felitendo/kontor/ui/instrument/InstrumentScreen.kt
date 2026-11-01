package de.felitendo.kontor.ui.instrument

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.felitendo.kontor.data.market.MarketApi
import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.Instrument
import de.felitendo.kontor.data.model.InstrumentType
import de.felitendo.kontor.data.model.Quote
import de.felitendo.kontor.data.model.SeriesPoint
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.Routes
import de.felitendo.kontor.ui.components.Card
import de.felitendo.kontor.ui.components.ChangeLabel
import de.felitendo.kontor.ui.components.IconCircleButton
import de.felitendo.kontor.ui.components.InstrumentLogo
import de.felitendo.kontor.ui.components.LineChart
import de.felitendo.kontor.ui.components.PrimaryButton
import de.felitendo.kontor.ui.components.RangeSelector
import de.felitendo.kontor.ui.components.RollingText
import de.felitendo.kontor.ui.components.SectionHeader
import de.felitendo.kontor.ui.components.StatGrid
import de.felitendo.kontor.ui.components.Tag
import de.felitendo.kontor.ui.components.TopBar
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.search.Attribution
import de.felitendo.kontor.ui.theme.K

@Composable
fun InstrumentScreen(nav: NavController, route: Routes.Instrument) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hidden = settings?.hideAmounts == true

    val depot = portfolio?.assets?.filterIsInstance<Depot>()?.firstOrNull { d ->
        if (route.depotId != null) d.id == route.depotId else d.positions.any { it.instrument.isin == route.isin }
    }
    val position = depot?.positions?.firstOrNull { if (route.positionId != null) it.id == route.positionId else it.instrument.isin == route.isin }

    val hit = remember(route) {
        MarketApi.SearchHit(
            Instrument(
                isin = route.isin, wkn = route.wkn, name = route.name,
                type = route.type?.let { runCatching { InstrumentType.valueOf(it) }.getOrNull() } ?: InstrumentType.OTHER,
                onvistaType = route.onvistaType, onvistaId = route.onvistaId,
            ),
            route.onvistaSymbol,
        )
    }
    var instrument by remember { mutableStateOf(position?.instrument) }
    var quote by remember { mutableStateOf<Quote?>(cache.quotes[route.isin]) }
    var range by remember { mutableStateOf(ChartRange.DAY) }
    var series by remember { mutableStateOf<List<SeriesPoint>?>(null) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(route.isin) {
        if (instrument?.yahooSymbol == null) {
            val (i, q) = vm.c.market.resolve(hit)
            instrument = i
            if (q != null) quote = q
        } else {
            vm.c.market.quote(instrument!!)?.let { quote = it }
        }
    }
    LaunchedEffect(instrument, range) {
        val i = instrument ?: return@LaunchedEffect
        series = null
        series = vm.c.market.instrumentSeries(i, range)
    }

    val values = series?.map { it.v }.orEmpty()
    val price = quote?.price
    val shown = scrub?.let { values.getOrNull(it) } ?: price
    val base = if (range == ChartRange.DAY) quote?.previousClose ?: values.firstOrNull() else values.firstOrNull()
    val change = if (shown != null && base != null) shown - base else null
    val title = instrument?.name ?: route.name
    val scroll = rememberScrollState()
    val showTitle by remember { derivedStateOf { scroll.value > 160 } }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title, showTitle, onBack = { nav.popBackStack() }) {
                if (position != null && depot != null) {
                    IconCircleButton(Icons.Rounded.DeleteOutline, "Entfernen", size = 40.dp) { haptics.heavy(); confirmDelete = true }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    InstrumentLogo(route.isin, title, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = K.type.title, color = c.text, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag((instrument ?: hit.instrument).type.title)
                            route.wkn?.let { Tag("WKN $it") }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (shown != null) RollingText(Fmt.eur(shown), K.type.hero, c.text, animate = scrub == null)
                    else Text("…", style = K.type.hero, color = c.textTertiary)
                    Spacer(Modifier.height(2.dp))
                    if (change != null && base != null && base != 0.0) {
                        ChangeLabel(
                            change, change / base * 100, hidden = false,
                            suffix = scrub?.let { series?.getOrNull(it)?.let { p -> Fmt.timestamp(p.t, range.days <= 31) } } ?: rangeText(range),
                            animate = scrub == null,
                        )
                    } else Spacer(Modifier.height(22.dp))
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    if (values.size > 1) {
                        LineChart(
                            values, Modifier.fillMaxSize(),
                            baseline = if (range == ChartRange.DAY) quote?.previousClose else null,
                            live = range == ChartRange.DAY,
                            onScrub = { scrub = it },
                        )
                    } else if (series != null) {
                        Text("Für diesen Zeitraum gibt es keine Kursdaten.", style = K.type.caption, color = c.textSecondary, modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(Modifier.height(8.dp))
                RangeSelector(range, { range = it })

                if (position != null && price != null) {
                    SectionHeader("Deine Position")
                    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { editing = true }) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Column(Modifier.weight(1f)) {
                                    Text("${Fmt.qty(position.quantity)} Stück", style = K.type.caption, color = c.textSecondary)
                                    Text(Fmt.eur(price * position.quantity, hidden), style = K.type.headline, color = c.text)
                                }
                                quote?.change?.let { ch ->
                                    ChangeLabel(ch * position.quantity, quote?.changePct, hidden, suffix = "Heute")
                                }
                            }
                            position.buyPrice?.let { bp ->
                                Spacer(Modifier.height(14.dp))
                                val gain = (price - bp) * position.quantity
                                Row {
                                    Text("Kaufkurs ${Fmt.eur(bp)}", style = K.type.label, color = c.textSecondary, modifier = Modifier.weight(1f))
                                    Text(
                                        Fmt.signedEur(gain, hidden) + "  " + Fmt.signedPct((price - bp) / bp * 100),
                                        style = K.type.label, color = if (gain >= 0) c.positive else c.negative,
                                    )
                                }
                            }
                        }
                    }
                }

                SectionHeader("Kennzahlen")
                StatGrid(
                    listOfNotNull(
                        quote?.dayHigh?.let { "Tageshoch" to Fmt.eur(it) },
                        quote?.dayLow?.let { "Tagestief" to Fmt.eur(it) },
                        quote?.yearHigh?.let { "52 W Hoch" to Fmt.eur(it) },
                        quote?.yearLow?.let { "52 W Tief" to Fmt.eur(it) },
                        quote?.previousClose?.let { "Vortag" to Fmt.eur(it) },
                        quote?.exchange?.let { "Handelsplatz" to it },
                        "ISIN" to route.isin,
                        route.wkn?.let { "WKN" to it },
                    ),
                )
                Attribution()
                Spacer(Modifier.height(96.dp))
            }
        }

        PrimaryButton(
            if (position != null) "Position bearbeiten" else "Zum Depot hinzufügen",
            Modifier.align(Alignment.BottomCenter).padding(20.dp).navigationBarsPadding(),
            enabled = instrument != null,
        ) { if (position != null) editing = true else adding = true }

        AnimatedVisibility(
            adding || editing,
            enter = slideInVertically(tween(380)) { it } + fadeIn(),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(),
        ) {
            PositionFlow(
                depotId = route.depotId ?: depot?.id,
                resolvedInstrument = instrument,
                existing = if (editing) position else null,
                onDone = { adding = false; editing = false },
                onExit = { adding = false; editing = false },
            )
        }
    }

    if (confirmDelete && depot != null && position != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Position entfernen?") },
            text = { Text("${position.instrument.name} wird aus „${depot.name}“ gelöscht.") },
            confirmButton = {
                TextButton({
                    haptics.reject(); confirmDelete = false
                    vm.deletePosition(depot.id, position.id)
                    nav.popBackStack()
                }) { Text("Entfernen", color = c.negative) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen", color = c.text) } },
            containerColor = c.surfaceHigh,
        )
    }
}

fun rangeText(range: ChartRange) = when (range) {
    ChartRange.DAY -> "Heute"
    ChartRange.WEEK -> "1 Woche"
    ChartRange.MONTH -> "1 Monat"
    ChartRange.HALF_YEAR -> "6 Monate"
    ChartRange.YEAR -> "1 Jahr"
    ChartRange.FIVE_YEARS -> "5 Jahre"
    ChartRange.MAX -> "Gesamt"
}
