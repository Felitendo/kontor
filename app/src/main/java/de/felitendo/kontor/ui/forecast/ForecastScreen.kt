package de.felitendo.kontor.ui.forecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.CashHolding
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.FixedDeposit
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.MetalHolding
import de.felitendo.kontor.data.model.OtherAsset
import de.felitendo.kontor.data.model.PensionContract
import de.felitendo.kontor.data.model.SavingsAccount
import de.felitendo.kontor.domain.projectBalance
import de.felitendo.kontor.domain.today
import de.felitendo.kontor.domain.value
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.components.LineChart
import de.felitendo.kontor.ui.components.RollingText
import de.felitendo.kontor.ui.components.SectionHeader
import de.felitendo.kontor.ui.components.StatGrid
import de.felitendo.kontor.ui.components.TopBar
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.theme.K
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * "Was wäre wenn": projects every asset with its own logic — interest and savings plans for
 * accounts, an adjustable return for securities, gold and cash flat.
 */
@Composable
fun ForecastScreen(nav: NavController) {
    val vm = LocalVm.current
    val c = K.colors
    val haptics = LocalHaptics.current
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val assets = portfolio?.assets.orEmpty()
    val hidden = settings?.hideAmounts == true

    var years by remember { mutableFloatStateOf(10f) }
    var stockReturn by remember { mutableFloatStateOf((settings?.forecastStockReturn ?: 5.0).toFloat()) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    val y = years.roundToInt()
    val now = LocalDate.now()

    val points = remember(assets, cache, y, stockReturn) {
        (0..y * 12).map { m -> assets.sumOf { project(it, cache, m, now, stockReturn / 100.0) } }
    }
    val contributions = remember(assets, y) {
        assets.sumOf { a ->
            when (a) {
                is SavingsAccount -> a.monthlyDeposit
                is PensionContract -> a.monthlyContribution
                else -> 0.0
            }
        } * y * 12
    }
    val start = points.first()
    val end = points.last()
    val shown = scrub?.let { points.getOrNull(it) } ?: end
    val shownDate = now.plusMonths((scrub ?: (y * 12)).toLong())

    Column(Modifier.fillMaxSize().background(c.background)) {
        TopBar("Prognose", onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Vermögen ${Fmt.monthYear(shownDate.toEpochDay())}", style = K.type.label, color = c.textSecondary)
                RollingText(Fmt.eur(shown, hidden, decimals = false), K.type.hero, c.text, animate = scrub == null)
                Text("+" + Fmt.eur(shown - start, hidden, decimals = false) + " gegenüber heute", style = K.type.label, color = c.positive)
            }
            Spacer(Modifier.height(12.dp))
            LineChart(points, Modifier.fillMaxWidth().height(220.dp), onScrub = { scrub = it })

            SectionHeader("Zeitraum: $y Jahre")
            Slider(
                years, { v -> if (v.roundToInt() != years.roundToInt()) haptics.tick(); years = v },
                valueRange = 1f..30f, steps = 28,
                colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.text, inactiveTrackColor = c.surfaceHigh, activeTickColor = c.text, inactiveTickColor = c.textTertiary),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            SectionHeader("Rendite Wertpapiere: ${Fmt.rate(stockReturn.toDouble())} p. a.")
            Slider(
                stockReturn,
                { v ->
                    val r = (v * 2).roundToInt() / 2f
                    if (r != stockReturn) haptics.tick()
                    stockReturn = r
                },
                onValueChangeFinished = { vm.updateSettings { it.copy(forecastStockReturn = stockReturn.toDouble()) } },
                valueRange = 0f..10f,
                colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.text, inactiveTrackColor = c.surfaceHigh),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            StatGrid(
                listOf(
                    "Heute" to Fmt.eur(start, hidden, false),
                    "In $y Jahren" to Fmt.eur(end, hidden, false),
                    "Eingezahlt" to Fmt.eur(contributions, hidden, false),
                    "Zinsen & Erträge" to Fmt.eur((end - start - contributions).coerceAtLeast(0.0), hidden, false),
                ),
            )
            Row(Modifier.padding(20.dp)) {
                Text(
                    "Annahmen: Fest- und Tagesgeld mit ihrem Zinssatz (Festgeld wird nach Ablauf zum selben Zins wieder angelegt), Sparraten laufen weiter, Wertpapiere mit der gewählten Rendite, Edelmetalle und Bargeld ohne Wertänderung. Keine Steuern, keine Inflation. Eine Orientierung, keine Anlageberatung.",
                    style = K.type.caption, color = c.textTertiary,
                )
            }
        }
    }
}

/** Total wealth in [years] under the forecast assumptions. */
fun projectTotal(assets: List<Asset>, cache: MarketCache, years: Int, stockReturn: Double): Double =
    assets.sumOf { project(it, cache, years * 12, LocalDate.now(), stockReturn / 100.0) }

private fun project(a: Asset, cache: MarketCache, months: Int, now: LocalDate, stockReturn: Double): Double {
    val day = now.plusMonths(months.toLong()).toEpochDay()
    val years = months / 12.0
    return when (a) {
        is FixedDeposit -> a.value(cache, today()) * (1 + a.rate / 100).pow(years)
        is SavingsAccount -> projectBalance(a.balance, a.rate, a.monthlyDeposit, a.balanceDay, day)
        is PensionContract -> projectBalance(a.balance, a.rate ?: 0.0, a.monthlyContribution, a.balanceDay, day, yearlyCompounding = true)
        is Depot -> a.value(cache) * (1 + stockReturn).pow(years)
        is MetalHolding, is CashHolding, is OtherAsset -> a.value(cache)
    }
}
