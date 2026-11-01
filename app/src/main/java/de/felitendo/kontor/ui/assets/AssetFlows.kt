package de.felitendo.kontor.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.felitendo.kontor.data.banks.Role
import de.felitendo.kontor.data.model.Asset
import de.felitendo.kontor.data.model.AssetKind
import de.felitendo.kontor.data.model.BankRef
import de.felitendo.kontor.data.model.CashHolding
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.FixedDeposit
import de.felitendo.kontor.data.model.Metal
import de.felitendo.kontor.data.model.MetalHolding
import de.felitendo.kontor.data.model.OtherAsset
import de.felitendo.kontor.data.model.PensionContract
import de.felitendo.kontor.data.model.SavingsAccount
import de.felitendo.kontor.domain.maturityDay
import de.felitendo.kontor.domain.maturityValue
import de.felitendo.kontor.domain.today
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.components.BankLogo
import de.felitendo.kontor.ui.components.KindIcon
import de.felitendo.kontor.ui.components.Pill
import de.felitendo.kontor.ui.components.PrimaryButton
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.components.rawOf
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.theme.K
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Entry flow for one asset. [existing] switches it into edit mode with everything prefilled. */
@Composable
fun AssetFlow(kind: AssetKind, existing: Asset?, onSaved: (Asset) -> Unit, onExit: () -> Unit) {
    when (kind) {
        AssetKind.FIXED_DEPOSIT -> FixedDepositFlow(existing as? FixedDeposit, onSaved, onExit)
        AssetKind.SAVINGS -> SavingsFlow(existing as? SavingsAccount, onSaved, onExit)
        AssetKind.METAL -> MetalFlow(existing as? MetalHolding, onSaved, onExit)
        AssetKind.DEPOT -> DepotFlow(existing as? Depot, onSaved, onExit)
        AssetKind.PENSION -> PensionFlow(existing as? PensionContract, onSaved, onExit)
        AssetKind.CASH -> LabelValueFlow(kind, existing, onSaved, onExit)
        AssetKind.OTHER -> LabelValueFlow(kind, existing, onSaved, onExit)
    }
}

@Composable
private fun BankHeader(bank: BankRef?, title: String, value: String) {
    val c = K.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        BankLogo(bank, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = K.type.caption, color = c.textSecondary)
            Text(value, style = K.type.display, color = c.text)
        }
    }
}

@Composable
private fun FixedDepositFlow(existing: FixedDeposit?, onSaved: (Asset) -> Unit, onExit: () -> Unit) {
    val haptics = LocalHaptics.current
    val state = remember { FlowState(5) }
    var bank by remember { mutableStateOf(existing?.bank) }
    var amount by remember { mutableStateOf(rawOf(existing?.amount, 2)) }
    var rate by remember { mutableStateOf(rawOf(existing?.rate, 3)) }
    var term by remember { mutableStateOf(existing?.termMonths ?: 12) }
    var start by remember { mutableStateOf(existing?.startDay ?: today()) }

    fun build() = FixedDeposit(
        id = existing?.id ?: de.felitendo.kontor.data.model.newId(),
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        bank = bank ?: BankRef(null, "Bank"),
        amount = Fmt.parse(amount) ?: 0.0,
        rate = Fmt.parse(rate) ?: 0.0,
        startDay = start,
        termMonths = term,
    )

    FlowScaffold(state, onExit, closeIcon = true) { step ->
        when (step) {
            0 -> BankStep("Bei welcher Bank liegt dein Festgeld?", role = Role.BANK) { bank = it; state.next() }
            1 -> AmountStep("Wie viel hast du angelegt?", initial = amount) { amount = it; state.next() }
            2 -> AmountStep(
                "Zu welchem Zinssatz?", "Effektiver Jahreszins", initial = rate, suffix = "%", decimals = 3, maxInt = 2,
                hint = { raw ->
                    val a = Fmt.parse(amount) ?: 0.0; val r = Fmt.parse(raw) ?: 0.0
                    if (r > 0) Text("≈ ${Fmt.eur(a * r / 100)} Zinsen pro Jahr", style = K.type.label, color = K.colors.textSecondary)
                },
            ) { rate = it; state.next() }
            3 -> TermStep(term, start, onTerm = { term = it }, onStart = { start = it }) { state.next() }
            else -> {
                val fd = build()
                SummaryStep(
                    "Alles richtig?",
                    header = { BankHeader(fd.bank, fd.bank.name, Fmt.eur(fd.amount)) },
                    rows = listOfNotNull(
                        "Zinssatz" to Fmt.rate(fd.rate),
                        "Laufzeit" to Fmt.months(fd.termMonths),
                        "Angelegt am" to Fmt.shortDate(fd.startDay),
                        fd.maturityDay?.let { "Fällig am" to Fmt.shortDate(it) },
                        fd.maturityValue?.let { "Zinsen gesamt" to "+" + Fmt.eur(it - fd.amount) },
                    ),
                    onEditRow = { i -> state.goTo(if (i == 0) 2 else 3) },
                ) { haptics.success(); onSaved(fd) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.TermStep(
    term: Int,
    start: Long,
    onTerm: (Int) -> Unit,
    onStart: (Long) -> Unit,
    onNext: () -> Unit,
) {
    val c = K.colors
    val haptics = LocalHaptics.current
    var picking by remember { mutableStateOf(false) }
    StepTitle("Wie lange läuft es?", "Laufzeit und Startdatum")
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 6, 9, 12, 18, 24, 36, 48, 60, 84, 120).forEach { m ->
                Pill(Fmt.months(m), selected = term == m) { haptics.selection(); onTerm(m) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
                .pressable { picking = true }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Angelegt am", style = K.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(Fmt.date(start), style = K.type.bodyStrong, color = c.text)
        }
        Spacer(Modifier.height(12.dp))
        val end = LocalDate.ofEpochDay(start).plusMonths(term.toLong())
        Text("Fällig am ${Fmt.date(end.toEpochDay())}", style = K.type.label, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
    }
    PrimaryButton("Weiter", Modifier.padding(20.dp).navigationBarsPadding()) { onNext() }

    if (picking) {
        val dp = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(start).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    dp.selectedDateMillis?.let { onStart(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    picking = false
                }) { Text("Übernehmen", color = c.text) }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Abbrechen", color = c.textSecondary) } },
        ) { DatePicker(dp, showModeToggle = false) }
    }
}

@Composable
private fun SavingsFlow(existing: SavingsAccount?, onSaved: (Asset) -> Unit, onExit: () -> Unit) {
    val haptics = LocalHaptics.current
    val state = remember { FlowState(5) }
    var bank by remember { mutableStateOf(existing?.bank) }
    var balance by remember { mutableStateOf(rawOf(existing?.balance, 2)) }
    var rate by remember { mutableStateOf(rawOf(existing?.rate, 3)) }
    var monthly by remember { mutableStateOf(rawOf(existing?.monthlyDeposit, 2)) }

    fun build() = SavingsAccount(
        id = existing?.id ?: de.felitendo.kontor.data.model.newId(),
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        bank = bank ?: BankRef(null, "Bank"),
        balance = Fmt.parse(balance) ?: 0.0,
        rate = Fmt.parse(rate) ?: 0.0,
        monthlyDeposit = Fmt.parse(monthly) ?: 0.0,
        balanceDay = today(),
    )

    FlowScaffold(state, onExit, closeIcon = true) { step ->
        when (step) {
            0 -> BankStep("Wo liegt dein Tagesgeld?", role = Role.BANK) { bank = it; state.next() }
            1 -> AmountStep("Wie hoch ist der Kontostand?", "Stand heute", initial = balance) { balance = it; state.next() }
            2 -> AmountStep("Welcher Zinssatz gilt?", "Aktueller Zins pro Jahr", initial = rate, suffix = "%", decimals = 3, maxInt = 2) { rate = it; state.next() }
            3 -> AmountStep(
                "Sparst du monatlich etwas dazu?", "Kontor rechnet den Kontostand dann automatisch weiter.",
                initial = monthly, allowZero = true, secondary = "Keine Sparrate",
                onSecondary = { monthly = ""; state.next() },
                hint = { raw ->
                    val m = Fmt.parse(raw) ?: 0.0
                    if (m > 0) Text("${Fmt.eur(m * 12)} pro Jahr", style = K.type.label, color = K.colors.textSecondary)
                },
            ) { monthly = it; state.next() }
            else -> {
                val a = build()
                SummaryStep(
                    "Alles richtig?",
                    header = { BankHeader(a.bank, a.bank.name, Fmt.eur(a.balance)) },
                    rows = listOf(
                        "Zinssatz" to Fmt.rate(a.rate),
                        "Sparrate" to if (a.monthlyDeposit > 0) Fmt.eur(a.monthlyDeposit) + " / Monat" else "keine",
                        "Zinsen pro Jahr" to "≈ " + Fmt.eur(a.balance * a.rate / 100),
                    ),
                    onEditRow = { i -> state.goTo(i + 2) },
                ) { haptics.success(); onSaved(a) }
            }
        }
    }
}

@Composable
private fun PensionFlow(existing: PensionContract?, onSaved: (Asset) -> Unit, onExit: () -> Unit) {
    val haptics = LocalHaptics.current
    val state = remember { FlowState(5) }
    var provider by remember { mutableStateOf(existing?.provider) }
    var balance by remember { mutableStateOf(rawOf(existing?.balance, 2)) }
    var rate by remember { mutableStateOf(rawOf(existing?.rate, 3)) }
    var monthly by remember { mutableStateOf(rawOf(existing?.monthlyContribution, 2)) }

    fun build() = PensionContract(
        id = existing?.id ?: de.felitendo.kontor.data.model.newId(),
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        provider = provider ?: BankRef(null, "Riester"),
        balance = Fmt.parse(balance) ?: 0.0,
        rate = Fmt.parse(rate)?.takeIf { it > 0 },
        monthlyContribution = Fmt.parse(monthly) ?: 0.0,
        balanceDay = today(),
    )

    FlowScaffold(state, onExit, closeIcon = true) { step ->
        when (step) {
            0 -> BankStep("Wer ist dein Riester-Anbieter?", role = Role.PENSION) { provider = it; state.next() }
            1 -> AmountStep("Wie viel ist angespart?", "Laut letzter Standmitteilung", initial = balance) { balance = it; state.next() }
            2 -> AmountStep(
                "Gibt es einen Zinssatz?", "Optional — z. B. Garantiezins oder erwartete Verzinsung",
                initial = rate, suffix = "%", decimals = 3, maxInt = 2, allowZero = true,
                secondary = "Kein Zinssatz", onSecondary = { rate = ""; state.next() },
            ) { rate = it; state.next() }
            3 -> AmountStep(
                "Zahlst du monatlich ein?", "Optional — dein Eigenbeitrag",
                initial = monthly, allowZero = true, secondary = "Keine Einzahlung", onSecondary = { monthly = ""; state.next() },
            ) { monthly = it; state.next() }
            else -> {
                val a = build()
                SummaryStep(
                    "Alles richtig?",
                    header = { BankHeader(a.provider, a.provider.name, Fmt.eur(a.balance)) },
                    rows = listOf(
                        "Zinssatz" to (a.rate?.let { Fmt.rate(it) } ?: "—"),
                        "Beitrag" to if (a.monthlyContribution > 0) Fmt.eur(a.monthlyContribution) + " / Monat" else "—",
                    ),
                    onEditRow = { i -> state.goTo(i + 2) },
                ) { haptics.success(); onSaved(a) }
            }
        }
    }
}

private enum class MetalUnit(val label: String, val suffix: String, val decimals: Int) {
    OUNCES("Unzen", "oz", 3), GRAMS("Gramm", "g", 2), EURO("Euro", "€", 2)
}

const val GRAMS_PER_OUNCE = 31.1034768

@Composable
private fun MetalFlow(existing: MetalHolding?, onSaved: (Asset) -> Unit, onExit: () -> Unit) {
    val vm = LocalVm.current
    val haptics = LocalHaptics.current
    val cache by vm.market.collectAsStateWithLifecycle()
    val state = remember { FlowState(4) }
    var metal by remember { mutableStateOf(existing?.metal ?: Metal.GOLD) }
    var ounces by remember { mutableStateOf(existing?.ounces ?: 0.0) }
    var unit by remember { mutableStateOf(MetalUnit.OUNCES) }
    var purchase by remember { mutableStateOf(rawOf(existing?.purchasePrice, 2)) }
    val price = cache.metals[metal]?.price
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.c.market.ensureMetals() }

    fun build() = MetalHolding(
        id = existing?.id ?: de.felitendo.kontor.data.model.newId(),
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        metal = metal,
        ounces = ounces,
        purchasePrice = Fmt.parse(purchase)?.takeIf { it > 0 },
        label = existing?.label,
    )

    FlowScaffold(state, onExit, closeIcon = true) { step ->
        when (step) {
            0 -> {
                StepTitle("Welches Edelmetall?", "Preise live, je Feinunze")
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metal.entries.forEach { m ->
                        val p = cache.metals[m]?.price
                        ChoiceTile(
                            m.title, p?.let { Fmt.eur(it) + " / oz" } ?: "Preis wird geladen…", selected = metal == m,
                            leading = { KindIcon(AssetKind.METAL, metal = m) },
                        ) { haptics.selection(); metal = m; state.next() }
                    }
                }
            }
            1 -> AmountStep(
                "Wie viel ${metal.title} besitzt du?",
                initial = when (unit) {
                    MetalUnit.OUNCES -> rawOf(ounces, 3)
                    MetalUnit.GRAMS -> rawOf(ounces * GRAMS_PER_OUNCE, 2)
                    MetalUnit.EURO -> rawOf(price?.let { ounces * it }, 2)
                },
                suffix = unit.suffix, decimals = unit.decimals,
                accessory = {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetalUnit.entries.forEach { u ->
                            if (u != MetalUnit.EURO || price != null) {
                                Pill(u.label, selected = unit == u) { haptics.selection(); unit = u }
                            }
                        }
                    }
                },
                hint = { raw ->
                    val v = Fmt.parse(raw) ?: 0.0
                    val oz = when (unit) { MetalUnit.OUNCES -> v; MetalUnit.GRAMS -> v / GRAMS_PER_OUNCE; MetalUnit.EURO -> price?.let { v / it } ?: 0.0 }
                    val text = when {
                        v <= 0 -> price?.let { "1 oz = ${Fmt.eur(it)}" } ?: ""
                        unit == MetalUnit.EURO -> "≈ " + Fmt.ounces(oz) + " · " + Fmt.number(oz * GRAMS_PER_OUNCE) + " g"
                        price != null -> "≈ " + Fmt.eur(oz * price)
                        else -> Fmt.ounces(oz)
                    }
                    Text(text, style = K.type.label, color = K.colors.textSecondary)
                },
            ) { raw ->
                val v = Fmt.parse(raw) ?: 0.0
                ounces = when (unit) { MetalUnit.OUNCES -> v; MetalUnit.GRAMS -> v / GRAMS_PER_OUNCE; MetalUnit.EURO -> v / (price ?: 1.0) }
                state.next()
            }
            2 -> AmountStep(
                "Was hast du dafür bezahlt?", "Optional — dann siehst du deinen Gewinn",
                initial = purchase, allowZero = true, secondary = "Überspringen", onSecondary = { purchase = ""; state.next() },
            ) { purchase = it; state.next() }
            else -> {
                val a = build()
                SummaryStep(
                    "Alles richtig?",
                    header = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindIcon(AssetKind.METAL, 52.dp, metal = metal)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(metal.title, style = K.type.caption, color = K.colors.textSecondary)
                                Text(price?.let { Fmt.eur(it * a.ounces) } ?: Fmt.ounces(a.ounces), style = K.type.display, color = K.colors.text)
                            }
                        }
                    },
                    rows = listOfNotNull(
                        "Menge" to Fmt.ounces(a.ounces),
                        "In Gramm" to Fmt.number(a.ounces * GRAMS_PER_OUNCE) + " g",
                        price?.let { "Preis je Unze" to Fmt.eur(it) },
                        a.purchasePrice?.let { "Kaufpreis" to Fmt.eur(it) },
                    ),
                    onEditRow = { i -> state.goTo(if (i < 2) 1 else 2) },
                ) { haptics.success(); onSaved(a) }
            }
        }
    }
}

@Composable
private fun DepotFlow(existing: Depot?, onSaved: (Asset) -> Unit, onExit: () -> Unit) {
    val haptics = LocalHaptics.current
    val state = remember { FlowState(3) }
    var bank by remember { mutableStateOf(existing?.bank) }
    var name by remember { mutableStateOf(existing?.name ?: "") }

    FlowScaffold(state, onExit, closeIcon = true) { step ->
        when (step) {
            0 -> BankStep("Wo liegt dein Depot?", role = Role.BROKER) {
                bank = it
                if (name.isBlank() || name.startsWith("Depot")) name = "Depot ${it.name}"
                state.next()
            }
            1 -> TextStep("Wie soll es heißen?", initial = name, placeholder = "Depot", suggestions = listOfNotNull(bank?.let { "Depot ${it.name}" }, "Altersvorsorge", "Gemeinschaftsdepot")) {
                name = it; state.next()
            }
            else -> {
                val d = (existing ?: Depot(name = name)).copy(name = name, bank = bank)
                SummaryStep(
                    "Depot anlegen",
                    header = { BankHeader(d.bank, "Neues Depot", d.name) },
                    rows = listOf("Positionen" to if (d.positions.isEmpty()) "fügst du gleich hinzu" else d.positions.size.toString()),
                    button = if (existing == null) "Depot anlegen" else "Speichern",
                ) { haptics.success(); onSaved(d) }
            }
        }
    }
}

@Composable
private fun LabelValueFlow(kind: AssetKind, existing: Asset?, onSaved: (Asset) -> Unit, onExit: () -> Unit) {
    val haptics = LocalHaptics.current
    val state = remember { FlowState(3) }
    val cash = kind == AssetKind.CASH
    var label by remember { mutableStateOf((existing as? CashHolding)?.label ?: (existing as? OtherAsset)?.label ?: if (cash) "Bargeld" else "") }
    var amount by remember { mutableStateOf(rawOf((existing as? CashHolding)?.amount ?: (existing as? OtherAsset)?.value, 2)) }

    FlowScaffold(state, onExit, closeIcon = true) { step ->
        when (step) {
            0 -> TextStep(
                if (cash) "Wo liegt das Bargeld?" else "Was möchtest du erfassen?",
                if (cash) "Ein Name hilft, wenn du mehrere Orte hast." else "Zum Beispiel eine Immobilie, ein Auto oder Schmuck.",
                initial = label, placeholder = if (cash) "Bargeld" else "Bezeichnung",
                suggestions = if (cash) listOf("Bargeld", "Zuhause", "Tresor", "Geldbörse", "Schließfach")
                else listOf("Immobilie", "Auto", "Schmuck", "Kunst", "Krypto", "Darlehen"),
            ) { label = it; state.next() }
            1 -> AmountStep(if (cash) "Wie viel ist es?" else "Was ist es wert?", initial = amount) { amount = it; state.next() }
            else -> {
                val v = Fmt.parse(amount) ?: 0.0
                val a: Asset = if (cash) {
                    (existing as? CashHolding)?.copy(label = label, amount = v) ?: CashHolding(label = label, amount = v)
                } else {
                    (existing as? OtherAsset)?.copy(label = label, value = v) ?: OtherAsset(label = label, value = v)
                }
                SummaryStep(
                    "Alles richtig?",
                    header = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindIcon(kind, 52.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(label, style = K.type.caption, color = K.colors.textSecondary)
                                Text(Fmt.eur(v), style = K.type.display, color = K.colors.text)
                            }
                        }
                    },
                    rows = listOf("Art" to kind.singular),
                ) { haptics.success(); onSaved(a) }
            }
        }
    }
}

@Composable
fun SavedCheck(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { CheckDot(true, 72.dp) }
}
