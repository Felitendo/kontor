package de.felitendo.kontor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.felitendo.kontor.ui.haptics.LocalHaptics
import kotlin.math.atan2
import kotlin.math.hypot

/** Allocation ring. Segments sweep in once; tapping one lifts it out. */
@Composable
fun Donut(
    shares: List<Float>,
    colors: List<Color>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    val lift = shares.indices.map { i ->
        animateFloatAsState(if (selected == i) 1f else 0f, spring(0.6f, 400f), label = "lift$i").value
    }

    Canvas(
        modifier.pointerInput(shares) {
            detectTapGestures { pos ->
                val cx = size.width / 2f; val cy = size.height / 2f
                val r = hypot(pos.x - cx, pos.y - cy)
                val outer = minOf(size.width, size.height) / 2f
                if (r < outer * 0.55f || r > outer) { onSelect(null); return@detectTapGestures }
                var angle = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat() + 90f
                if (angle < 0) angle += 360f
                var acc = 0f
                shares.forEachIndexed { i, s ->
                    acc += s * 360f
                    if (angle <= acc) {
                        haptics.selection()
                        onSelect(if (selected == i) null else i)
                        return@detectTapGestures
                    }
                }
            }
        },
    ) {
        val strokeBase = 18.dp.toPx()
        val gap = if (shares.count { it > 0.001f } > 1) 2.2f else 0f
        val d = minOf(size.width, size.height) - strokeBase * 2
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        var start = -90f
        val total = 360f * sweep.value
        shares.forEachIndexed { i, s ->
            val sw = s * total
            if (sw > gap) {
                val w = strokeBase + 8.dp.toPx() * lift[i]
                drawArc(
                    color = colors[i % colors.size],
                    startAngle = start + gap / 2,
                    sweepAngle = sw - gap,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(d, d),
                    style = Stroke(width = w, cap = StrokeCap.Butt),
                    alpha = if (selected == null || selected == i) 1f else 0.35f,
                )
            }
            start += sw
        }
    }
}
