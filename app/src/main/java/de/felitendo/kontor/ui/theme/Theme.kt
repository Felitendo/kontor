package de.felitendo.kontor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.felitendo.kontor.R

@Immutable
data class KontorColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val positive: Color,
    val negative: Color,
    /** Grey ramp used for allocation segments, brightest first. */
    val ramp: List<Color>,
) {
    /** Primary action: inverted pill. */
    val accent get() = text
    val onAccent get() = background
}

val DarkColors = KontorColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF101011),
    surfaceHigh = Color(0xFF1C1C1E),
    outline = Color(0xFF242426),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    textTertiary = Color(0xFF4A4A4E),
    positive = Color(0xFF2BD67B),
    negative = Color(0xFFFF5247),
    ramp = listOf(0xFFFFFFFF, 0xFFC7C7CC, 0xFF97979C, 0xFF6E6E73, 0xFF4F4F53, 0xFF38383B, 0xFF2A2A2D).map { Color(it) },
)

val LightColors = KontorColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F4F5),
    surfaceHigh = Color(0xFFEBEBED),
    outline = Color(0xFFE4E4E7),
    text = Color(0xFF000000),
    textSecondary = Color(0xFF6B6B70),
    textTertiary = Color(0xFFB8B8BD),
    positive = Color(0xFF00A35C),
    negative = Color(0xFFE5372D),
    ramp = listOf(0xFF000000, 0xFF3A3A3C, 0xFF636366, 0xFF8E8E93, 0xFFAEAEB2, 0xFFC7C7CC, 0xFFDEDEE2).map { Color(it) },
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val InterDisplay = FontFamily(
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
)

@Immutable
data class KontorType(
    val hero: TextStyle,
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val keypad: TextStyle,
)

private const val TABULAR = "tnum, ss01, cv05"

val Typography = KontorType(
    hero = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = (-0.035).em, fontFeatureSettings = TABULAR, lineHeight = 50.sp),
    display = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR, lineHeight = 40.sp),
    headline = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.02).em, lineHeight = 32.sp, fontFeatureSettings = TABULAR),
    title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.01).em, lineHeight = 24.sp, fontFeatureSettings = TABULAR),
    body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR),
    bodyStrong = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR),
    label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR),
    caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp, fontFeatureSettings = TABULAR),
    keypad = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Medium, fontSize = 30.sp, fontFeatureSettings = TABULAR),
)

val LocalColors = staticCompositionLocalOf { DarkColors }
val LocalType = staticCompositionLocalOf { Typography }

object K {
    val colors: KontorColors @Composable get() = LocalColors.current
    val type: KontorType @Composable get() = LocalType.current
}

@Composable
fun KontorTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.text, onPrimary = c.background, background = c.background, onBackground = c.text,
            surface = c.background, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHigh, surfaceContainerLow = c.surface,
            outline = c.outline, outlineVariant = c.outline, secondary = c.textSecondary, error = c.negative,
        )
    } else {
        lightColorScheme(
            primary = c.text, onPrimary = c.background, background = c.background, onBackground = c.text,
            surface = c.background, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHigh, surfaceContainerLow = c.surface,
            outline = c.outline, outlineVariant = c.outline, secondary = c.textSecondary, error = c.negative,
        )
    }
    CompositionLocalProvider(LocalColors provides c, LocalType provides Typography) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
