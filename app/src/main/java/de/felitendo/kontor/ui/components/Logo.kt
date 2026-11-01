package de.felitendo.kontor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import de.felitendo.kontor.ui.theme.K

/**
 * The Kontor mark — a K whose arm is a rising price line — drawn stroke by stroke.
 * Coordinates match the launcher icon (108 grid).
 */
@Composable
fun AnimatedLogo(modifier: Modifier = Modifier, color: Color = K.colors.text, animate: Boolean = true, onDrawn: () -> Unit = {}) {
    val stem = remember { Animatable(if (animate) 0f else 1f) }
    val arm = remember { Animatable(if (animate) 0f else 1f) }
    val leg = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        stem.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        arm.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        leg.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        onDrawn()
    }
    Canvas(modifier) {
        val s = size.minDimension / 66f
        val ox = (size.width - 66f * s) / 2 - 21f * s
        val oy = (size.height - 66f * s) / 2 - 21f * s
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val stroke = Stroke(width = 7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun partial(points: List<Offset>, t: Float) {
            if (t <= 0f) return
            val full = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
            val measure = PathMeasure().apply { setPath(full, false) }
            val out = Path()
            measure.getSegment(0f, measure.length * t, out, true)
            drawPath(out, color, style = stroke)
        }
        partial(listOf(p(38f, 76f), p(38f, 32f)), stem.value)
        partial(listOf(p(38f, 64f), p(49f, 52f), p(57f, 58f), p(71f, 34f)), arm.value)
        partial(listOf(p(57f, 58f), p(71f, 76f)), leg.value)
    }
}
