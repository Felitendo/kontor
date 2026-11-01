package de.felitendo.kontor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.felitendo.kontor.data.banks.Banks
import de.felitendo.kontor.data.model.AssetKind
import de.felitendo.kontor.data.model.BankRef
import de.felitendo.kontor.data.model.Metal
import de.felitendo.kontor.ui.theme.K

@Composable
fun BankLogo(bank: BankRef?, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val entry = Banks.find(bank?.id) ?: bank?.name?.let { Banks.match(it) }
    val c = K.colors
    val ring = Modifier.border(1.dp, c.outline, CircleShape)
    when {
        entry?.logo != null -> Image(
            painterResource(entry.logo), entry.name,
            modifier = modifier.size(size).clip(CircleShape).then(ring),
            contentScale = ContentScale.Crop,
        )
        entry != null -> Monogram(entry.monogram, Color(entry.color), size, modifier)
        else -> Monogram(initials(bank?.name ?: "?"), c.surfaceHigh, size, modifier)
    }
}

@Composable
fun Monogram(text: String, background: Color, size: Dp, modifier: Modifier = Modifier) {
    val fg = if (background.luminance() > 0.55f) Color.Black else Color.White
    val fg2 = if (background == K.colors.surfaceHigh) K.colors.text else fg
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = fg2,
            style = K.type.label.copy(fontSize = (size.value * (if (text.length > 2) 0.28f else 0.36f)).sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

/** Company / fund logos by ISIN, courtesy of the Parqet logo API (attribution in the UI). */
@Composable
fun InstrumentLogo(isin: String, name: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val c = K.colors
    var failed by remember(isin) { mutableStateOf(false) }
    Box(modifier.size(size).clip(CircleShape).background(Color.White).border(1.dp, c.outline, CircleShape), contentAlignment = Alignment.Center) {
        if (failed) {
            Monogram(initials(name), c.surfaceHigh, size)
        } else {
            AsyncImage(
                model = "https://assets.parqet.com/logos/isin/$isin?format=png&size=128",
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        }
    }
}

@Composable
fun KindIcon(kind: AssetKind, size: Dp = 44.dp, modifier: Modifier = Modifier, metal: Metal? = null) {
    val c = K.colors
    val tint = if (kind == AssetKind.METAL) metalColor(metal ?: Metal.GOLD) else c.text
    Box(modifier.size(size).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
        Icon(kindVector(kind), kind.title, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

fun kindVector(kind: AssetKind): ImageVector = when (kind) {
    AssetKind.FIXED_DEPOSIT -> Icons.Rounded.Lock
    AssetKind.SAVINGS -> Icons.Rounded.Savings
    AssetKind.METAL -> GoldBars
    AssetKind.DEPOT -> Icons.Rounded.ShowChart
    AssetKind.PENSION -> Icons.Rounded.WbTwilight
    AssetKind.CASH -> Icons.Rounded.Payments
    AssetKind.OTHER -> Icons.Rounded.Category
}

fun metalColor(metal: Metal): Color = when (metal) {
    Metal.GOLD -> Color(0xFFE2B33C)
    Metal.SILVER -> Color(0xFFC4C8CE)
    Metal.PLATINUM -> Color(0xFF9FB3C8)
}

fun initials(name: String): String {
    val words = name.split(' ', '-', '.').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).replaceFirstChar { it.uppercase() }
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** Three stacked bullion bars. */
val GoldBars: ImageVector by lazy {
    ImageVector.Builder("GoldBars", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            // bottom left bar
            moveTo(3.2f, 20f); lineTo(4.6f, 14.6f); lineTo(10.9f, 14.6f); lineTo(12.3f, 20f); close()
            // bottom right bar
            moveTo(11.7f, 20f); lineTo(13.1f, 14.6f); lineTo(19.4f, 14.6f); lineTo(20.8f, 20f); close()
            // top bar
            moveTo(7.5f, 13.4f); lineTo(8.9f, 8f); lineTo(15.1f, 8f); lineTo(16.5f, 13.4f); close()
            // sparkle
            moveTo(18.2f, 3.2f); lineTo(18.8f, 4.9f); lineTo(20.5f, 5.5f); lineTo(18.8f, 6.1f); lineTo(18.2f, 7.8f)
            lineTo(17.6f, 6.1f); lineTo(15.9f, 5.5f); lineTo(17.6f, 4.9f); close()
        }
    }.build()
}
