package de.felitendo.kontor.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.felitendo.kontor.MainActivity
import de.felitendo.kontor.R
import de.felitendo.kontor.container
import de.felitendo.kontor.data.model.Depot
import de.felitendo.kontor.data.model.MarketCache
import de.felitendo.kontor.data.model.Metal
import de.felitendo.kontor.data.model.MetalHolding
import de.felitendo.kontor.data.model.Portfolio
import de.felitendo.kontor.data.model.SeriesPoint
import de.felitendo.kontor.data.model.Settings
import de.felitendo.kontor.domain.byKind
import de.felitendo.kontor.domain.dayChange
import de.felitendo.kontor.domain.value
import de.felitendo.kontor.ui.Fmt
import kotlinx.coroutines.flow.first

object Widgets {
    suspend fun updateAll(context: Context) {
        runCatching {
            WealthWidget().updateAll(context)
            DepotWidget().updateAll(context)
            GoldWidget().updateAll(context)
        }
    }

    suspend fun hasAny(context: Context): Boolean {
        val m = GlanceAppWidgetManager(context)
        return listOf(WealthWidget::class.java, DepotWidget::class.java, GoldWidget::class.java)
            .any { m.getGlanceIds(it).isNotEmpty() }
    }
}

private data class Snapshot(val portfolio: Portfolio, val cache: MarketCache, val settings: Settings) {
    val hidden get() = settings.hideAmounts || !settings.widgetShowAmounts
}

private suspend fun snapshot(context: Context): Snapshot {
    val c = context.container
    return Snapshot(c.portfolio.data.first(), c.market.cache.first(), c.settings.data.first())
}

/**
 * Glance keeps a widget session alive between updates, so the data has to be observed
 * inside the composition; values read before provideContent would go stale.
 */
@Composable
private fun liveSnapshot(context: Context, initial: Snapshot): Snapshot {
    val c = context.container
    val p by c.portfolio.data.collectAsState(initial.portfolio)
    val m by c.market.cache.collectAsState(initial.cache)
    val st by c.settings.data.collectAsState(initial.settings)
    return Snapshot(p, m, st)
}

// Widgets follow the system's light/dark mode but stay strictly monochrome.
private val Bg = ColorProvider(day = Color.White, night = Color.Black)
private val Fg = ColorProvider(day = Color.Black, night = Color.White)
private val Muted = ColorProvider(day = Color(0xFF6B6B70), night = Color(0xFF8E8E93))
private val Up = ColorProvider(day = Color(0xFF00A35C), night = Color(0xFF2BD67B))
private val Down = ColorProvider(day = Color(0xFFE5372D), night = Color(0xFFFF5247))

@Composable
private fun Frame(content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(Bg).cornerRadius(26.dp).padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) { content() }
}

@Composable
private fun Label(text: String) = Text(text, style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)

@Composable
private fun Big(text: String, size: Int = 26) =
    Text(text, style = TextStyle(color = Fg, fontSize = size.sp, fontWeight = FontWeight.Bold), maxLines = 1)

@Composable
private fun Change(change: Double, base: Double, hidden: Boolean, suffix: String = "Heute") {
    val pct = if (base - change != 0.0) change / (base - change) * 100 else 0.0
    val color = if (change >= 0) Up else Down
    val arrow = if (change >= 0) "▲" else "▼"
    val text = if (hidden) "$arrow ${Fmt.pct(pct)}" else "$arrow ${Fmt.eur(kotlin.math.abs(change))}\u00A0· ${Fmt.pct(pct)}"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text("  $suffix", style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
    }
}

class WealthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = snapshot(context)
        provideContent {
            val s = liveSnapshot(context, initial)
            val total = s.portfolio.assets.sumOf { it.value(s.cache) }
            val change = s.portfolio.assets.sumOf { it.dayChange(s.cache) }
            val kinds = s.portfolio.assets.byKind(s.cache)
            val spark = s.cache.wealthSeries
            val size = LocalSize.current
            Frame {
                Column(GlanceModifier.fillMaxSize()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(R.drawable.ic_widget_mark), null, GlanceModifier.size(14.dp))
                        Spacer(GlanceModifier.width(6.dp))
                        Label("Vermögen")
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Big(Fmt.eur(total, s.hidden, decimals = size.width > 200.dp), if (size.width > 200.dp) 28 else 22)
                    Change(change, total, s.hidden)
                    if (size.height >= 110.dp && size.width >= 250.dp && spark.size > 2) {
                        Spacer(GlanceModifier.height(8.dp))
                        // The chart takes whatever height is left; the bitmap is stretched to fit.
                        val h = when {
                            size.height >= 270.dp -> 90
                            size.height >= 170.dp -> 70
                            else -> 30
                        }
                        Image(
                            ImageProvider(sparkline(context, spark, size.width.value.toInt() - 32, h)), "Verlauf",
                            GlanceModifier.fillMaxWidth().defaultWeight(),
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                    if (size.height >= 270.dp && kinds.isNotEmpty()) {
                        Spacer(GlanceModifier.height(8.dp))
                        kinds.take(3).forEach { k ->
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(k.kind.title, style = TextStyle(color = Fg, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
                                Text(
                                    if (s.hidden) Fmt.pct(k.share * 100.0) else Fmt.eur(k.value, decimals = false),
                                    style = TextStyle(color = Muted, fontSize = 13.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class DepotWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = snapshot(context)
        provideContent {
            val s = liveSnapshot(context, initial)
            val depots = s.portfolio.assets.filterIsInstance<Depot>()
            val value = depots.sumOf { it.value(s.cache) }
            val change = depots.sumOf { it.dayChange(s.cache) }
            val positions = depots.flatMap { it.positions }
                .groupBy { it.instrument.isin }
                .map { (_, list) -> list.first().instrument to list.sumOf { it.quantity } }
                .sortedByDescending { (i, q) -> (s.cache.quotes[i.isin]?.price ?: 0.0) * q }
            val size = LocalSize.current
            Frame {
                Column(GlanceModifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(R.drawable.ic_widget_mark), null, GlanceModifier.size(14.dp))
                        Spacer(GlanceModifier.width(6.dp))
                        Label(depots.singleOrNull()?.name ?: "Depot")
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    if (depots.isEmpty()) {
                        Text("Noch kein Depot angelegt", style = TextStyle(color = Muted, fontSize = 13.sp))
                    } else {
                        Big(Fmt.eur(value, s.hidden, decimals = size.width > 200.dp), if (size.width > 200.dp) 26 else 22)
                        Change(change, value, s.hidden)
                        if (size.height >= 180.dp) {
                            Spacer(GlanceModifier.height(10.dp))
                            // As many rows as the widget is tall.
                            val rows = ((size.height.value - 130) / 26).toInt().coerceIn(1, 8)
                            positions.take(rows).forEach { (instrument, qty) ->
                                val q = s.cache.quotes[instrument.isin]
                                val pct = q?.changePct ?: 0.0
                                Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(instrument.name, style = TextStyle(color = Fg, fontSize = 13.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                                    if (!s.hidden && size.width >= 300.dp && q != null) {
                                        Text(Fmt.eur(q.price * qty, decimals = false) + "  ", style = TextStyle(color = Muted, fontSize = 13.sp), maxLines = 1)
                                    }
                                    Text(Fmt.signedPct(pct), style = TextStyle(color = if (pct >= 0) Up else Down, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class GoldWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = snapshot(context)
        provideContent {
            val s = liveSnapshot(context, initial)
            val quote = s.cache.metals[Metal.GOLD]
            val ounces = s.portfolio.assets.filterIsInstance<MetalHolding>().filter { it.metal == Metal.GOLD }.sumOf { it.ounces }
            val gold = s.cache.goldSeries
            val size = LocalSize.current
            Frame {
                Column(GlanceModifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(R.drawable.ic_widget_gold), null, GlanceModifier.size(16.dp))
                        Spacer(GlanceModifier.width(6.dp))
                        Label("Gold\u00A0· 1 oz")
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Big(quote?.let { Fmt.eur(it.price, decimals = size.width > 150.dp) } ?: "…", if (size.width > 150.dp) 24 else 20)
                    quote?.changePct?.let { pct ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (pct >= 0) "▲ " else "▼ ") + Fmt.pct(pct),
                                style = TextStyle(color = if (pct >= 0) Up else Down, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            )
                            Text("  Heute", style = TextStyle(color = Muted, fontSize = 12.sp))
                        }
                    }
                    if (size.height >= 150.dp && gold.size > 2) {
                        Spacer(GlanceModifier.height(8.dp))
                        Image(
                            ImageProvider(sparkline(context, gold, size.width.value.toInt() - 32, 50)), "Goldpreis, 1 Monat",
                            GlanceModifier.fillMaxWidth().defaultWeight(),
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                    if (ounces > 0 && size.height >= 110.dp && quote != null) {
                        Spacer(GlanceModifier.height(6.dp))
                        Text("Dein Gold: " + Fmt.eur(quote.price * ounces, s.hidden, decimals = false), style = TextStyle(color = Muted, fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

class WealthWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WealthWidget() }
class DepotWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DepotWidget() }
class GoldWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GoldWidget() }

private fun sparkline(context: Context, points: List<SeriesPoint>, widthDp: Int, heightDp: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val w = (widthDp.coerceAtLeast(60) * d).toInt()
    val h = (heightDp * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val color = if (night) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val min = points.minOf { it.v }; val max = points.maxOf { it.v }
    val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
    val pad = 3 * d
    fun x(i: Int) = i * (w - 2 * pad) / (points.size - 1) + pad
    fun y(v: Double) = (pad + (h - 2 * pad) * (1 - (v - min) / range)).toFloat()
    val path = Path()
    points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.v)) else path.lineTo(x(i), y(p.v)) }
    val fill = Path(path).apply { lineTo(x(points.size - 1), h.toFloat()); lineTo(x(0), h.toFloat()); close() }
    canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), (color and 0x00FFFFFF) or 0x30000000, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
    })
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * d; this.color = color; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    })
    return bmp
}
