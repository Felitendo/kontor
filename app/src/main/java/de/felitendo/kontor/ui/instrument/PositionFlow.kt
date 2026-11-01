package de.felitendo.kontor.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.felitendo.kontor.data.market.MarketApi
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.Instrument
import de.felitendo.kontor.data.model.Position
import de.felitendo.kontor.data.model.Quote
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.assets.AmountStep
import de.felitendo.kontor.ui.assets.ChoiceTile
import de.felitendo.kontor.ui.assets.FlowScaffold
import de.felitendo.kontor.ui.assets.FlowState
import de.felitendo.kontor.ui.assets.StepTitle
import de.felitendo.kontor.ui.components.BankLogo
import de.felitendo.kontor.ui.components.InstrumentLogo
import de.felitendo.kontor.ui.components.rawOf
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.search.SearchContent
import de.felitendo.kontor.ui.theme.K

/**
 * Adds or edits a depot position: search → quantity → buy-in → (depot, if ambiguous).
 * Adding an instrument that is already in the depot tops up the existing position.
 */
@Composable
fun PositionFlow(
    depotId: String?,
    startHit: MarketApi.SearchHit? = null,
    resolvedInstrument: Instrument? = null,
    existing: Position? = null,
    onDone: (depotId: String) -> Unit,
    onExit: () -> Unit,
) {
    val vm = LocalVm.current
    val haptics = LocalHaptics.current
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val depots = portfolio?.assets?.filterIsInstance<Depot>().orEmpty()
    val needsDepotChoice = depotId == null && depots.size > 1 && existing == null
    val startStep = if (startHit != null || resolvedInstrument != null || existing != null) 1 else 0
    val state = remember { FlowState(if (needsDepotChoice) 4 else 3).apply { minStep = startStep; step = startStep } }

    var hit by remember { mutableStateOf(startHit) }
    var instrument by remember { mutableStateOf(resolvedInstrument ?: existing?.instrument) }
    var quote by remember { mutableStateOf<Quote?>(instrument?.let { cache.quotes[it.isin] }) }
    var quantity by remember { mutableStateOf(rawOf(existing?.quantity, 6)) }
    var buyPrice by remember { mutableStateOf(rawOf(existing?.buyPrice, 4)) }
    var target by remember { mutableStateOf(depotId ?: depots.singleOrNull()?.id) }

    LaunchedEffect(hit) {
        val h = hit ?: return@LaunchedEffect
        if (instrument?.yahooSymbol != null && instrument?.isin == h.instrument.isin) return@LaunchedEffect
        val (i, q) = vm.c.market.resolve(h)
        instrument = i
        quote = q
    }

    fun save() {
        val i = instrument ?: hit?.instrument ?: return
        val qty = Fmt.parse(quantity) ?: return
        val buy = Fmt.parse(buyPrice)?.takeIf { it > 0 }
        val depot = depots.firstOrNull { it.id == target }
        haptics.success()
        if (depot == null) {
            val d = Depot(name = "Depot", positions = listOf(Position(instrument = i, quantity = qty, buyPrice = buy)))
            vm.save(d)
            onDone(d.id)
            return
        }
        val same = depot.positions.firstOrNull { it.instrument.isin == i.isin && it.id != existing?.id }
        val position = when {
            existing != null -> existing.copy(instrument = i, quantity = qty, buyPrice = buy)
            same != null -> {
                val total = same.quantity + qty
                val avg = if (same.buyPrice != null && buy != null) (same.buyPrice * same.quantity + buy * qty) / total else same.buyPrice ?: buy
                same.copy(quantity = total, buyPrice = avg, instrument = i)
            }
            else -> Position(instrument = i, quantity = qty, buyPrice = buy)
        }
        vm.savePosition(depot.id, position)
        onDone(depot.id)
    }

    FlowScaffold(state, onExit = onExit, closeIcon = true) { step ->
        when (step) {
            0 -> {
                StepTitle("Welches Wertpapier?", "Suche nach WKN, ISIN oder Name")
                SearchContent { h -> hit = h; instrument = null; quote = null; state.next() }
            }
            1 -> {
                val name = instrument?.name ?: hit?.instrument?.name ?: ""
                val isin = instrument?.isin ?: hit?.instrument?.isin ?: ""
                AmountStep(
                    "Wie viele Stück?",
                    initial = quantity, suffix = "Stk.", decimals = 6, maxInt = 8,
                    accessory = {
                        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            InstrumentLogo(isin, name, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(name, style = K.type.label, color = K.colors.text, maxLines = 1)
                                Text(
                                    quote?.let { "Kurs " + Fmt.eur(it.price) } ?: "Kurs wird geladen…",
                                    style = K.type.caption, color = K.colors.textSecondary,
                                )
                            }
                        }
                    },
                    hint = { raw ->
                        val q = Fmt.parse(raw) ?: 0.0
                        val p = quote?.price
                        if (q > 0 && p != null) Text("= " + Fmt.eur(q * p), style = K.type.title, color = K.colors.textSecondary)
                    },
                ) { quantity = it; state.next() }
            }
            2 -> AmountStep(
                "Zu welchem Kurs gekauft?", "Optional — durchschnittlicher Kaufkurs je Stück",
                initial = buyPrice, decimals = 4, allowZero = true,
                secondary = "Überspringen",
                onSecondary = { buyPrice = ""; if (needsDepotChoice) state.next() else save() },
                button = if (needsDepotChoice) "Weiter" else "Speichern",
                hint = { raw ->
                    val b = Fmt.parse(raw) ?: 0.0; val q = Fmt.parse(quantity) ?: 0.0; val p = quote?.price
                    if (b > 0 && p != null) {
                        val g = (p - b) * q
                        Text(
                            (if (g >= 0) "+" else "−") + Fmt.eur(kotlin.math.abs(g)) + " seit Kauf",
                            style = K.type.label, color = if (g >= 0) K.colors.positive else K.colors.negative,
                        )
                    }
                },
            ) { buyPrice = it; if (needsDepotChoice) state.next() else save() }
            else -> {
                StepTitle("In welches Depot?")
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    depots.forEach { d ->
                        ChoiceTile(d.name, "${d.positions.size} Positionen", selected = target == d.id, leading = { BankLogo(d.bank) }) {
                            target = d.id; save()
                        }
                    }
                }
            }
        }
    }
}
