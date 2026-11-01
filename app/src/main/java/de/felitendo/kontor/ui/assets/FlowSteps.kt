package de.felitendo.kontor.ui.assets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.felitendo.kontor.data.banks.Bank
import de.felitendo.kontor.data.banks.Banks
import de.felitendo.kontor.data.banks.Role
import de.felitendo.kontor.data.model.BankRef
import de.felitendo.kontor.ui.components.AmountDisplay
import de.felitendo.kontor.ui.components.BankLogo
import de.felitendo.kontor.ui.components.IconCircleButton
import de.felitendo.kontor.ui.components.Key
import de.felitendo.kontor.ui.components.Keypad
import de.felitendo.kontor.ui.components.Monogram
import de.felitendo.kontor.ui.components.Pill
import de.felitendo.kontor.ui.components.PrimaryButton
import de.felitendo.kontor.ui.components.applyKey
import de.felitendo.kontor.ui.components.initials
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.theme.K

/**
 * Step-by-step flow: one question per screen, a segmented progress bar on top and
 * horizontal slides between steps (direction follows forward/back).
 */
class FlowState(val count: Int) {
    var step by mutableIntStateOf(0)
    var forward by mutableStateOf(true)
    /** Steps below this are skipped (e.g. search when the instrument is already known). */
    var minStep = 0
    fun next() { forward = true; if (step < count - 1) step++ }
    fun back(): Boolean { forward = false; return if (step > minStep) { step--; true } else false }
    fun goTo(i: Int) { forward = i > step; step = i }
}

@Composable
fun FlowScaffold(
    state: FlowState,
    onExit: () -> Unit,
    closeIcon: Boolean = false,
    content: @Composable ColumnScope.(Int) -> Unit,
) {
    val c = K.colors
    BackHandler { if (!state.back()) onExit() }
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(
                if (closeIcon && state.step == state.minStep) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                "Zurück", size = 40.dp,
            ) { if (!state.back()) onExit() }
            Spacer(Modifier.width(16.dp))
            StepBar(state.count, state.step, Modifier.weight(1f))
            Spacer(Modifier.width(28.dp))
        }
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val dir = if (state.forward) 1 else -1
                (slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it / 3 * dir } + fadeIn(tween(300, 60))) togetherWith
                    (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 4 * dir } + fadeOut(tween(160)))
            },
            label = "flowStep",
            modifier = Modifier.weight(1f),
        ) { step ->
            Column(Modifier.fillMaxSize()) { content(step) }
        }
    }
}

@Composable
private fun StepBar(count: Int, step: Int, modifier: Modifier) {
    val c = K.colors
    Row(modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            val fill by animateFloatAsState(if (i <= step) 1f else 0f, spring(0.8f, 300f), label = "seg")
            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(c.surfaceHigh)) {
                Box(Modifier.fillMaxWidth(fill).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(c.text))
            }
        }
    }
}

@Composable
fun StepTitle(title: String, subtitle: String? = null) {
    val c = K.colors
    Column(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 8.dp)) {
        Text(title, style = K.type.headline, color = c.text)
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = K.type.body, color = c.textSecondary)
        }
    }
}

/** Amount entry with the custom keypad. */
@Composable
fun ColumnScope.AmountStep(
    title: String,
    subtitle: String? = null,
    initial: String,
    suffix: String = "€",
    decimals: Int = 2,
    maxInt: Int = 9,
    allowZero: Boolean = false,
    button: String = "Weiter",
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    hint: @Composable (String) -> Unit = {},
    accessory: @Composable () -> Unit = {},
    onDone: (String) -> Unit,
) {
    val haptics = LocalHaptics.current
    var raw by remember(suffix) { mutableStateOf(initial) }
    var shake by remember { mutableIntStateOf(0) }
    val value = de.felitendo.kontor.ui.Fmt.parse(raw)
    val valid = value != null && (allowZero || value > 0)

    StepTitle(title, subtitle)
    accessory()
    Spacer(Modifier.weight(1f))
    AmountDisplay(raw, suffix, shake)
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) { hint(raw) }
    Spacer(Modifier.weight(1f))
    Keypad(onKey = { key ->
        val next = applyKey(raw, key, decimals, maxInt)
        if (next == null) {
            if (key != Key.Clear) { haptics.reject(); shake++ }
        } else raw = next
    }, allowComma = decimals > 0)
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp).navigationBarsPadding()) {
        if (secondary != null) {
            Box(Modifier.fillMaxWidth().height(44.dp).pressable { onSecondary() }, contentAlignment = Alignment.Center) {
                Text(secondary, style = K.type.label, color = K.colors.textSecondary)
            }
        }
        PrimaryButton(button, enabled = valid || (allowZero && raw.isEmpty())) { onDone(raw) }
    }
}

/** Bank / provider picker with logos and free text entry for anything not in the catalog. */
@Composable
fun ColumnScope.BankStep(
    title: String,
    subtitle: String? = null,
    role: Role,
    onPick: (BankRef) -> Unit,
) {
    val c = K.colors
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Banks.search(query, role) }
    StepTitle(title, subtitle)
    SearchField(query, { query = it }, "Bank suchen oder Namen eingeben", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        if (query.isNotBlank() && results.none { it.name.equals(query.trim(), true) }) {
            item {
                PickRow(
                    leading = { Monogram(initials(query), c.surfaceHigh, 44.dp) },
                    title = query.trim(),
                    subtitle = "Eigene Eingabe verwenden",
                ) { onPick(BankRef(Banks.match(query)?.id, query.trim())) }
            }
        }
        items(results, key = { it.id }) { bank ->
            PickRow(leading = { BankLogo(bank.ref()) }, title = bank.name) { onPick(bank.ref()) }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

@Composable
fun PickRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val c = K.colors
    Row(
        Modifier.fillMaxWidth().pressable(scaleTo = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = K.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = K.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing()
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
) {
    val c = K.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = K.type.body, color = c.textSecondary, maxLines = 1)
            BasicTextField(
                value, onChange,
                textStyle = K.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.text),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Leeren", tint = c.textSecondary, modifier = Modifier.size(20.dp).pressable { onChange("") })
        }
    }
}

/** Free text (labels such as "Tresor"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.TextStep(
    title: String,
    subtitle: String? = null,
    initial: String,
    placeholder: String,
    suggestions: List<String> = emptyList(),
    onDone: (String) -> Unit,
) {
    val c = K.colors
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val text = field.text
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    StepTitle(title, subtitle)
    Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        if (text.isEmpty()) Text(placeholder, style = K.type.display, color = c.textTertiary)
        BasicTextField(
            field, { field = it },
            textStyle = K.type.display.copy(color = c.text),
            cursorBrush = SolidColor(c.text),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text.trim()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
    if (suggestions.isNotEmpty()) {
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { s -> Pill(s, selected = text == s) { field = TextFieldValue(s, TextRange(s.length)) } }
        }
    }
    Spacer(Modifier.weight(1f))
    PrimaryButton("Weiter", Modifier.padding(20.dp).navigationBarsPadding(), enabled = text.isNotBlank()) { onDone(text.trim()) }
}

/** Large selectable tiles (asset kinds, metals, terms). */
@Composable
fun ChoiceTile(
    title: String,
    subtitle: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val c = K.colors
    val border by animateColorAsState(if (selected) c.text else c.surface, label = "tileBorder")
    Row(
        modifier
            .pressable(scaleTo = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.5.dp, border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = K.type.bodyStrong, color = c.text)
            subtitle?.let { Text(it, style = K.type.caption, color = c.textSecondary) }
        }
        CheckDot(selected)
    }
}

/** Round check that draws its tick when selected. */
@Composable
fun CheckDot(checked: Boolean, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val c = K.colors
    val t = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { t.animateTo(if (checked) 1f else 0f, tween(260, easing = FastOutSlowInEasing)) }
    val bg by animateColorAsState(if (checked) c.text else c.surfaceHigh, label = "chk")
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.5f)) {
            if (t.value <= 0f) return@Canvas
            val w = this.size.width; val h = this.size.height
            val a = Offset(w * 0.08f, h * 0.55f); val b = Offset(w * 0.4f, h * 0.85f); val e = Offset(w * 0.95f, h * 0.18f)
            val first = (t.value / 0.4f).coerceAtMost(1f)
            val second = ((t.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
            val stroke = 2.4.dp.toPx()
            drawLine(c.background, a, a + (b - a) * first, stroke, StrokeCap.Round)
            if (second > 0) drawLine(c.background, b, b + (e - b) * second, stroke, StrokeCap.Round)
        }
    }
}

/** Summary list and save button at the end of each flow. */
@Composable
fun ColumnScope.SummaryStep(
    title: String,
    header: @Composable () -> Unit,
    rows: List<Pair<String, String>>,
    button: String = "Speichern",
    onEditRow: ((Int) -> Unit)? = null,
    onSave: () -> Unit,
) {
    val c = K.colors
    StepTitle(title)
    Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        header()
        Spacer(Modifier.height(18.dp))
        Column(Modifier.clip(RoundedCornerShape(22.dp)).background(c.surface)) {
            rows.forEachIndexed { i, (k, v) ->
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (onEditRow != null) Modifier.pressable(scaleTo = 0.99f) { onEditRow(i) } else Modifier)
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                ) {
                    Text(k, style = K.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
                    Text(v, style = K.type.bodyStrong, color = c.text)
                }
                if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(c.outline))
            }
        }
    }
    PrimaryButton(button, Modifier.padding(20.dp).navigationBarsPadding()) { onSave() }
}

@Composable
fun AddMoreRow(text: String, onClick: () -> Unit) {
    val c = K.colors
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = c.text)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = K.type.bodyStrong, color = c.text)
    }
}

fun bankRefOf(bank: Bank) = BankRef(bank.id, bank.name)
