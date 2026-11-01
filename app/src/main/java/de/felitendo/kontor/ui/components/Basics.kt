package de.felitendo.kontor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.felitendo.kontor.data.model.ChartRange
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.theme.K

/** Springy press feedback used on every tappable surface. */
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleTo: Float = 0.965f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) scaleTo else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = LocalHaptics.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interaction, indication = null, enabled = enabled) {
            if (haptic) haptics.click()
            onClick()
        }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val c = K.colors
    val bg by animateColorAsState(if (enabled) c.accent else c.surfaceHigh, label = "btnBg")
    val fg by animateColorAsState(if (enabled) c.onAccent else c.textTertiary, label = "btnFg")
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable(enabled = enabled && !loading, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Text(text, style = K.type.bodyStrong, color = fg)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = K.colors
    Row(
        modifier
            .height(48.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceHigh)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, null, tint = c.text, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = K.type.label, color = c.text)
    }
}

@Composable
fun IconCircleButton(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 44.dp, onClick: () -> Unit) {
    val c = K.colors
    Box(
        modifier
            .size(size)
            .pressable(scaleTo = 0.9f, onClick = onClick)
            .clip(CircleShape)
            .background(c.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.text, modifier = Modifier.size(size * 0.5f))
    }
}

/** Top bar with back button and a title that fades in once the big page title scrolled away. */
@Composable
fun TopBar(
    title: String = "",
    showTitle: Boolean = true,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = K.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.background)
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", size = 40.dp, onClick = onBack)
        else Spacer(Modifier.width(4.dp))
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            androidx.compose.animation.AnimatedVisibility(showTitle && title.isNotEmpty(), enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                Text(title, style = K.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun ChangeLabel(
    change: Double,
    pct: Double?,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    animate: Boolean = true,
) {
    val c = K.colors
    val color = when {
        change > 0.004 -> c.positive
        change < -0.004 -> c.negative
        else -> c.textSecondary
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (change >= 0) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown, null,
            tint = color, modifier = Modifier.size(22.dp).offset(x = (-4).dp),
        )
        val text = buildString {
            append(if (hidden) Fmt.HIDDEN + " €" else Fmt.eur(kotlin.math.abs(change)))
            pct?.let { append(" (" + Fmt.pct(it) + ")") }
        }
        RollingText(text, K.type.label, color, Modifier.offset(x = (-4).dp), animate = animate)
        suffix?.let { Text(" $it", style = K.type.label, color = c.textSecondary, modifier = Modifier.offset(x = (-4).dp)) }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val c = K.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = K.type.title, color = c.text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, style = K.type.label, color = c.textSecondary, modifier = Modifier.pressable(onClick = onAction).padding(4.dp))
        }
    }
}

/** Pills under every chart. The selection slides between them. */
@Composable
fun RangeSelector(
    selected: ChartRange,
    onSelect: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
    ranges: List<ChartRange> = ChartRange.entries,
) {
    val c = K.colors
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<ChartRange, Pair<Float, Float>>() }
    val target = positions[selected]
    val x by animateDpAsState(with(density) { (target?.first ?: 0f).toDp() }, spring(0.75f, 500f), label = "rx")
    val w by animateDpAsState(with(density) { (target?.second ?: 0f).toDp() }, spring(0.75f, 500f), label = "rw")
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (target != null) {
            Box(Modifier.offset(x = x).width(w).height(34.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ranges.forEach { r ->
                val active = r == selected
                val textColor by animateColorAsState(if (active) c.text else c.textSecondary, label = "rc")
                Box(
                    Modifier
                        .height(34.dp)
                        .onGloballyPositioned { positions[r] = it.positionInParentCompat() to it.size.width.toFloat() }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(remember { MutableInteractionSource() }, null) {
                            if (!active) { haptics.selection(); onSelect(r) }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(r.label, style = K.type.label, color = textColor, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.positionInParentCompat(): Float =
    parentLayoutCoordinates?.localPositionOf(this, androidx.compose.ui.geometry.Offset.Zero)?.x ?: 0f

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    val c = K.colors
    val base = if (onClick != null) modifier.pressable(scaleTo = 0.98f, onClick = onClick) else modifier
    Box(base.clip(RoundedCornerShape(24.dp)).background(c.surface).padding(padding)) { content() }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = K.colors
    val bg by animateColorAsState(if (selected) c.accent else c.surfaceHigh, label = "pillBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.text, label = "pillFg")
    Box(
        modifier
            .height(40.dp)
            .pressable(scaleTo = 0.93f, haptic = false) { onClick() }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = K.type.label, color = fg)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier, inset: Dp = 20.dp) {
    Box(modifier.fillMaxWidth().padding(horizontal = inset).height(1.dp).background(K.colors.outline))
}

@Composable
fun StatGrid(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = K.colors
    Column(modifier.padding(horizontal = 20.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = K.type.caption, color = c.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(value, style = K.type.bodyStrong, color = c.text)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = K.colors.textSecondary) {
    Text(
        text, style = K.type.caption, color = color,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(K.colors.surfaceHigh).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
