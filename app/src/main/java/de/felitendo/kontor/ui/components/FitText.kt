package de.felitendo.kontor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified

/**
 * Shrinks [style] until [text] fits into [maxWidth] on one line.
 *
 * Digits are measured as "0": with tabular figures every digit has the same width, so the
 * size only changes when the *shape* of the number changes (e.g. 99.999 → 100.000), never
 * from frame to frame while scrubbing a chart.
 */
@Composable
fun fittedStyle(text: String, style: TextStyle, maxWidth: Int, minScale: Float = 0.4f): TextStyle {
    val measurer = rememberTextMeasurer()
    val template = remember(text) { String(CharArray(text.length) { i -> if (text[i].isDigit()) '0' else text[i] }) }
    return remember(template, style, maxWidth) {
        if (maxWidth == Constraints.Infinity || maxWidth <= 0) return@remember style
        var scale = 1f
        while (scale > minScale) {
            val s = style.scaled(scale)
            if (measurer.measure(template, s, maxLines = 1, softWrap = false).size.width <= maxWidth) return@remember s
            scale -= 0.04f
        }
        style.scaled(minScale)
    }
}

private fun TextStyle.scaled(scale: Float): TextStyle = if (scale >= 1f) this else copy(
    fontSize = fontSize * scale,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
)
