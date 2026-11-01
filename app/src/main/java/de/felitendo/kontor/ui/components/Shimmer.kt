package de.felitendo.kontor.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import de.felitendo.kontor.ui.theme.K

/** Loading placeholder with a soft light sweep. */
fun Modifier.shimmer(): Modifier = composed {
    val c = K.colors
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-400f, 1200f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "sx")
    background(
        Brush.linearGradient(
            listOf(c.surface, c.surfaceHigh, c.surface),
            start = Offset(x, 0f),
            end = Offset(x + 400f, 200f),
        ),
    )
}
