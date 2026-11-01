package de.felitendo.kontor.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import de.felitendo.kontor.ui.Fmt

/**
 * Text whose digits roll like a mechanical counter when the value changes,
 * staggered from right to left. Non-digit characters swap instantly.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val previous = remember { mutableStateOf(text) }
    val up = remember(text) { numericValue(text) >= numericValue(previous.value) }
    SideEffect { previous.value = text }

    // Large system fonts must not wrap or clip the number: shrink it to the available width.
    BoxWithConstraints(modifier) {
        val fitted = fittedStyle(text, style, constraints.maxWidth)
        if (!animate) {
            // While scrubbing the value changes every frame; rolling would only blur it.
            Text(text, style = fitted, color = color, maxLines = 1, softWrap = false)
        } else {
            Row {
                val chars = text.toList()
                chars.forEachIndexed { index, c ->
                    val fromEnd = chars.size - index
                    key(fromEnd) {
                        AnimatedContent(
                            targetState = c,
                            transitionSpec = {
                                if (!targetState.isDigit() || !initialState.isDigit()) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    val dir = if (up) 1 else -1
                                    val delay = (fromEnd * 22).coerceAtMost(220)
                                    val springIn = spring<IntOffset>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                                    (slideInVertically(springIn) { h -> h * dir } + fadeIn(tween(220, delay))) togetherWith
                                        (slideOutVertically(tween(220, delay, FastOutSlowInEasing)) { h -> -h * dir } + fadeOut(tween(160, delay)))
                                }.using(SizeTransform(clip = true))
                            },
                            label = "digit",
                        ) { ch ->
                            Text(ch.toString(), style = fitted, color = color, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** Formatted euro amount that counts up from zero the first time it is shown. */
@Composable
fun CountUpAmount(
    target: Double,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1400,
    onFinished: () -> Unit = {},
) {
    val anim = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        anim.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
        done = true
        onFinished()
    }
    val v = target * anim.value
    Text(Fmt.eur(v), style = style, color = color, modifier = modifier, maxLines = 1)
}

private fun numericValue(s: String): Double =
    s.filter { it.isDigit() || it == ',' || it == '-' || it == '−' }
        .replace('−', '-')
        .replace(',', '.')
        .toDoubleOrNull() ?: 0.0
