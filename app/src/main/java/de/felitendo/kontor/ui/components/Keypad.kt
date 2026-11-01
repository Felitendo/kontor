package de.felitendo.kontor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.theme.K
import kotlinx.coroutines.launch

sealed interface Key {
    data class Digit(val d: Char) : Key
    data object Comma : Key
    data object Back : Key
    data object Clear : Key
}

/** Applies a key to the raw input ("1234,5"). Returns null when the key is not allowed. */
fun applyKey(raw: String, key: Key, decimals: Int, maxInt: Int = 9): String? = when (key) {
    is Key.Digit -> {
        val intPart = raw.substringBefore(',')
        val decPart = if (raw.contains(',')) raw.substringAfter(',') else null
        when {
            decPart != null && decPart.length >= decimals -> null
            decPart == null && intPart.length >= maxInt -> null
            raw == "0" -> key.d.toString()
            else -> raw + key.d
        }
    }
    Key.Comma -> when {
        decimals == 0 || raw.contains(',') -> null
        raw.isEmpty() -> "0,"
        else -> "$raw,"
    }
    Key.Back -> if (raw.isEmpty()) null else raw.dropLast(1)
    Key.Clear -> if (raw.isEmpty()) null else ""
}

/** "1234,5" -> "1.234,5" for display. */
fun groupRaw(raw: String): String {
    if (raw.isEmpty()) return "0"
    val intPart = raw.substringBefore(',').ifEmpty { "0" }
    val grouped = intPart.reversed().chunked(3).joinToString(".").reversed()
    return if (raw.contains(',')) grouped + "," + raw.substringAfter(',') else grouped
}

fun rawOf(value: Double?, decimals: Int): String {
    if (value == null || value == 0.0) return ""
    val s = "%.${decimals}f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    return s.replace('.', ',')
}

/** The huge number above the keypad. New digits rise in; invalid input shakes. */
@Composable
fun AmountDisplay(
    raw: String,
    suffix: String,
    shake: Int,
    modifier: Modifier = Modifier,
    placeholder: Boolean = raw.isEmpty(),
) {
    val c = K.colors
    val text = groupRaw(raw)
    val size = when {
        text.length > 13 -> 34.sp
        text.length > 10 -> 42.sp
        else -> 54.sp
    }
    val style = K.type.hero.copy(fontSize = size, lineHeight = size * 1.1f)
    val offset = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake == 0) return@LaunchedEffect
        for (x in listOf(18f, -14f, 10f, -6f, 3f, 0f)) offset.animateTo(x, tween(45))
    }
    Row(
        modifier.fillMaxWidth().graphicsLayer { translationX = offset.value },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        text.forEachIndexed { i, ch ->
            key(i) { RisingChar(ch, style, if (placeholder) c.textTertiary else c.text) }
        }
        Text(" $suffix", style = style, color = if (placeholder) c.textTertiary else c.textSecondary)
    }
}

@Composable
private fun RisingChar(ch: Char, style: TextStyle, color: androidx.compose.ui.graphics.Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) }
    Text(
        ch.toString(), style = style, color = color,
        modifier = Modifier.graphicsLayer {
            alpha = anim.value.coerceIn(0f, 1f)
            translationY = (1 - anim.value) * 40f
            scaleX = 0.7f + 0.3f * anim.value
            scaleY = 0.7f + 0.3f * anim.value
        },
    )
}

@Composable
fun Keypad(onKey: (Key) -> Unit, modifier: Modifier = Modifier, allowComma: Boolean = true) {
    val rows = listOf(
        listOf(Key.Digit('1'), Key.Digit('2'), Key.Digit('3')),
        listOf(Key.Digit('4'), Key.Digit('5'), Key.Digit('6')),
        listOf(Key.Digit('7'), Key.Digit('8'), Key.Digit('9')),
        listOf(if (allowComma) Key.Comma else null, Key.Digit('0'), Key.Back),
    )
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    Box(Modifier.weight(1f).height(64.dp), contentAlignment = Alignment.Center) {
                        if (k != null) KeypadKey(k, onKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: Key, onKey: (Key) -> Unit) {
    val c = K.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val glow = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    Box(
        Modifier
            .size(76.dp, 60.dp)
            .pointerInput(key) {
                detectTapGestures(
                    onPress = {
                        scope.launch { glow.snapTo(1f) }
                        scope.launch { scale.animateTo(0.88f, spring(stiffness = Spring.StiffnessHigh)) }
                        tryAwaitRelease()
                        scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
                        scope.launch { glow.animateTo(0f, tween(320)) }
                    },
                    onTap = { haptics.click(); onKey(key) },
                    onLongPress = { if (key == Key.Back) { haptics.heavy(); onKey(Key.Clear) } },
                )
            }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(60.dp).graphicsLayer { alpha = glow.value; scaleX = 0.6f + 0.4f * glow.value; scaleY = scaleX }
                .clip(CircleShape).background(c.surfaceHigh),
        )
        when (key) {
            is Key.Digit -> Text(key.d.toString(), style = K.type.keypad, color = c.text)
            Key.Comma -> Text(",", style = K.type.keypad, color = c.text)
            Key.Back -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Löschen", tint = c.text, modifier = Modifier.size(26.dp))
            Key.Clear -> {}
        }
    }
}
