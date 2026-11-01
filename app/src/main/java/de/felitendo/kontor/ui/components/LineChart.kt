package de.felitendo.kontor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.theme.K
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The signature chart: a single line that draws itself in, a soft gradient below and
 * finger scrubbing with a haptic tick per data point. The part right of the finger fades
 * back so the eye stays on the selected moment.
 */
@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = K.colors.text,
    baseline: Double? = null,
    live: Boolean = false,
    onScrub: (Int?) -> Unit = {},
) {
    val haptics = LocalHaptics.current
    val colors = K.colors
    val progress = remember { Animatable(0f) }
    var scrub by remember { mutableIntStateOf(-1) }
    val currentOnScrub by rememberUpdatedState(onScrub)

    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseScale",
    )

    Canvas(
        modifier.pointerInput(values.size) {
            if (values.size < 2) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                val slop = viewConfiguration.touchSlop
                // Hold briefly or move sideways to scrub; vertical movement belongs to the page.
                var decision: String? = null
                withTimeoutOrNull(180) {
                    while (decision == null) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        val d = (change?.position ?: start) - start
                        decision = when {
                            change == null || !change.pressed -> "up"
                            abs(d.y) > slop && abs(d.y) > abs(d.x) -> "scroll"
                            abs(d.x) > slop -> "drag"
                            else -> null
                        }
                    }
                }
                if (decision == "up" || decision == "scroll") return@awaitEachGesture

                fun indexAt(x: Float): Int =
                    ((x / size.width) * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)

                haptics.heavy()
                scrub = indexAt(start.x)
                currentOnScrub(scrub)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    val i = indexAt(change.position.x)
                    if (i != scrub) {
                        scrub = i
                        haptics.tick()
                        currentOnScrub(i)
                    }
                }
                scrub = -1
                currentOnScrub(null)
            }
        },
    ) {
        if (values.size < 2) return@Canvas
        val padTop = 14.dp.toPx()
        val padBottom = 14.dp.toPx()
        var min = values.min()
        var max = values.max()
        baseline?.let { min = minOf(min, it); max = maxOf(max, it) }
        if (max - min < 1e-9) { max += 1; min -= 1 }
        val h = size.height - padTop - padBottom
        fun y(v: Double) = padTop + (h * (1 - (v - min) / (max - min))).toFloat()
        fun x(i: Int) = size.width * i / (values.size - 1)

        val path = Path()
        val smooth = values.size < 90
        values.forEachIndexed { i, v ->
            val px = x(i); val py = y(v)
            if (i == 0) path.moveTo(px, py)
            else if (!smooth) path.lineTo(px, py)
            else {
                val pxPrev = x(i - 1); val pyPrev = y(values[i - 1])
                val cx = (pxPrev + px) / 2
                path.cubicTo(cx, pyPrev, cx, py, px, py)
            }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        baseline?.let {
            val by = y(it)
            drawLine(
                colors.textTertiary, Offset(0f, by), Offset(size.width, by), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
        }

        val reveal = size.width * progress.value
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val split = if (scrub >= 0) x(scrub) else size.width

        clipRect(right = minOf(reveal, split)) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0f)), startY = padTop, endY = size.height))
            drawPath(path, color, style = stroke)
        }
        if (split < size.width) {
            clipRect(left = split, right = reveal) {
                drawPath(path, colors.textTertiary, style = stroke)
            }
        }

        if (scrub >= 0) {
            val sx = x(scrub); val sy = y(values[scrub])
            drawLine(colors.textSecondary.copy(alpha = 0.6f), Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(color.copy(alpha = 0.18f), radius = 11.dp.toPx(), center = Offset(sx, sy))
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(sx, sy))
            drawCircle(colors.background, radius = 2.dp.toPx(), center = Offset(sx, sy))
        } else if (progress.value >= 1f) {
            val lx = x(values.size - 1); val ly = y(values.last())
            if (live) drawCircle(color.copy(alpha = 0.35f * (1 - pulseScale)), radius = (4 + 12 * pulseScale).dp.toPx(), center = Offset(lx, ly))
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(lx, ly))
        }
    }
}
