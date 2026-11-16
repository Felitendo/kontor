package de.felitendo.kontor.ui.birthday

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.felitendo.kontor.birthday.Birthday
import de.felitendo.kontor.domain.value
import de.felitendo.kontor.domain.yearlyInterest
import de.felitendo.kontor.ui.Fmt
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.components.pressable
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.theme.K
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val PartyBackground = Color(0xFF07070C)

/** Keeps the white text readable on top of balloons and rain. */
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 2f), blurRadius = 10f)
private val Rainbow = listOf(
    Color(0xFFFF3B6B), Color(0xFFFF7A1A), Color(0xFFFFC21A), Color(0xFF2BD67B),
    Color(0xFF00D9D9), Color(0xFF3BA4FF), Color(0xFFA155FF), Color(0xFFFF5FD2),
)

/** Points on every outline the button morphs through; both start at the top centre, clockwise. */
private const val OUTLINE_POINTS = 180

/**
 * The classic heart curve, about 2 wide and centred on the origin, as x/y pairs spaced evenly
 * along the outline so that it lines up point by point with [pillOutline].
 */
private val HeartOutline: FloatArray by lazy {
    val m = 1440
    val xs = FloatArray(m + 1)
    val ys = FloatArray(m + 1)
    for (i in 0..m) {
        val t = 2 * PI * i / m
        val s = sin(t)
        xs[i] = (s * s * s).toFloat()
        ys[i] = (-(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)) / 16).toFloat()
    }
    val mid = (ys.min() + ys.max()) / 2
    val dist = FloatArray(m + 1)
    for (i in 1..m) dist[i] = dist[i - 1] + kotlin.math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    val out = FloatArray(OUTLINE_POINTS * 2)
    var j = 1
    for (k in 0 until OUTLINE_POINTS) {
        val target = dist[m] * k / OUTLINE_POINTS
        while (dist[j] < target) j++
        val f = (target - dist[j - 1]) / (dist[j] - dist[j - 1]).coerceAtLeast(1e-6f)
        out[k * 2] = xs[j - 1] + (xs[j] - xs[j - 1]) * f
        out[k * 2 + 1] = ys[j - 1] + (ys[j] - ys[j - 1]) * f - mid
    }
    out
}

/** The button's rounded rectangle as evenly spaced points, from the top centre, clockwise. */
private fun pillOutline(rect: Rect, corner: Float): FloatArray {
    val r = min(corner, min(rect.width, rect.height) / 2)
    val h = rect.width - 2 * r
    val v = rect.height - 2 * r
    val arc = (PI / 2 * r).toFloat()
    val total = 2 * h + 2 * v + 4 * arc
    val out = FloatArray(OUTLINE_POINTS * 2)
    for (k in 0 until OUTLINE_POINTS) {
        // Distance from the left end of the top edge; starting half way along it is the top centre.
        var d = (total * k / OUTLINE_POINTS + h / 2) % total
        fun corner(cx: Float, cy: Float, from: Double): Pair<Float, Float> {
            val a = from + d / r
            return (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()
        }
        val (x, y) = when {
            d < h -> (rect.left + r + d) to rect.top
            run { d -= h; d < arc } -> corner(rect.right - r, rect.top + r, -PI / 2)
            run { d -= arc; d < v } -> rect.right to (rect.top + r + d)
            run { d -= v; d < arc } -> corner(rect.right - r, rect.bottom - r, 0.0)
            run { d -= arc; d < h } -> (rect.right - r - d) to rect.bottom
            run { d -= h; d < arc } -> corner(rect.left + r, rect.bottom - r, PI / 2)
            run { d -= arc; d < v } -> rect.left to (rect.bottom - r - d)
            else -> { d -= v; corner(rect.left + r, rect.top + r, PI) }
        }
        out[k * 2] = x
        out[k * 2 + 1] = y
    }
    return out
}

private val HeartColors = listOf(Color(0xFFFF7A9A), Color(0xFFFF2D55), Color(0xFFB0003A))

/** Smoothly cycles through [Rainbow]; [t] is in "colours". */
private fun rainbow(t: Float): Color {
    val n = Rainbow.size
    val f = ((t % n) + n) % n
    val i = f.toInt()
    return lerp(Rainbow[i], Rainbow[(i + 1) % n], f - i)
}

/**
 * The teasing line under "N Jahre und immer noch kein Millionär", worked out from Helli's own
 * numbers: how long the interest alone would need for the first million.
 */
private fun millionLine(total: Double, yearlyInterest: Double, age: Int, hidden: Boolean): String {
    val missing = 1_000_000 - total
    if (missing <= 0) return "Ach nee, doch Millionär! Dann zahlst du heute die Runde. \uD83C\uDF7B"
    if (yearlyInterest >= 1) {
        val years = kotlin.math.ceil(missing / yearlyInterest).toLong()
        val n = java.text.NumberFormat.getIntegerInstance(Fmt.locale)
        return "Nur mit deinen Zinsen dauert die Million noch ${n.format(years)} Jahre. " +
            "Dann bist du ${n.format(age + years)}. Bleib gesund! \uD83C\uDF40"
    }
    if (!hidden && total > 0) return "Fehlen ja nur noch ${Fmt.eur(missing, decimals = false)}. Kleinkram! \uD83C\uDF40"
    return "Aber hey, die Million läuft dir ja nicht weg. \uD83D\uDC0C"
}

/** The full-screen birthday party, laid over the whole app while [visible]. */
@Composable
fun BirthdayParty(visible: Boolean, onDone: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(650))) {
        Party(onDone)
    }
}

@Composable
private fun Party(onDone: () -> Unit) {
    val vm = LocalVm.current
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cache by vm.market.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val age = remember { Birthday.age() }
    val assets = portfolio?.assets.orEmpty()
    val total = assets.sumOf { it.value(cache) }
    val millionaire = total >= 1_000_000
    val million = millionLine(total, assets.sumOf { it.yearlyInterest(cache) }, age, settings?.hideAmounts == true)
    val haptics = LocalHaptics.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val confetti = rememberConfetti()
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(0) }

    // The farewell heart: the button's outline morphs into it (morph 0..1), it travels to the
    // middle (travel 0..1) and grows (heart = half width in pixels). Plus fades around it.
    val morph = remember { Animatable(0f) }
    val travel = remember { Animatable(0f) }
    val heart = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }
    val backdropAlpha = remember { Animatable(1f) }
    var partyCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var buttonCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var button by remember { mutableStateOf<FloatArray?>(null) }
    var buttonRect by remember { mutableStateOf(Rect.Zero) }
    val corner = with(density) { 18.dp.toPx() }

    /**
     * "Danke": the button turns into a heart, which moves to the middle and pumps bigger with
     * every beat while the beats speed up, swells past the screen edges and bursts into hearts.
     * Behind it is the home screen.
     */
    fun close() {
        if (closing) return
        val party = partyCoords
        val coords = buttonCoords
        if (party != null && coords != null && party.isAttached && coords.isAttached) {
            buttonRect = party.localBoundingBoxOf(coords)
            button = pillOutline(buttonRect, corner)
        }
        closing = true
        confetti.rainRate = 0f
        confetti.balloonCount = 0
        scope.launch {
            launch { contentAlpha.animateTo(0f, tween(450)) }
            var r = with(density) { 46.dp.toPx() }
            heart.snapTo(r)
            haptics.heavy()
            morph.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            haptics.heartbeat()
            launch { travel.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
            delay(420)
            for (gap in longArrayOf(520, 450, 390, 340, 295, 255, 220, 190, 165)) {
                r *= 1.22f
                launch { heart.animateTo(r, spring(dampingRatio = 0.4f, stiffness = 450f)) }
                haptics.heartbeat()
                delay(gap)
            }
            haptics.celebrate()
            heart.animateTo(confetti.size.maxDimension * 1.15f, tween(420, easing = FastOutLinearInEasing))

            // Pop.
            confetti.burst(Offset(confetti.size.width / 2, confetti.size.height * 0.46f), 340, power = 1.7f, emojiShare = 0.3f, hearts = true)
            confetti.cannons(150, power = 1.05f)
            haptics.boom()
            launch { flash.snapTo(0.8f); flash.animateTo(0f, tween(480)) }
            launch { heart.animateTo(heart.value * 1.3f, tween(200)) }
            launch { heartAlpha.animateTo(0f, tween(200)) }
            backdropAlpha.animateTo(0f, tween(520))
            delay(1000)
            onDone()
        }
    }

    BackHandler { close() }
    LaunchedEffect(Unit) { Birthday.markSeen(context) }

    // The choreography: cannons, a drum roll, the name, the big bang, then fireworks until "Danke".
    // Every step has its own vibration; the vibrator plays one effect at a time, so they take turns.
    LaunchedEffect(confetti) {
        snapshotFlow { confetti.size }.first { it != Size.Zero }
        confetti.balloonCount = 9
        delay(120)
        confetti.cannons(170); haptics.celebrate()
        stage = 1
        launch { delay(280); haptics.drumroll(360) }
        delay(650)
        confetti.shower(160); confetti.rainRate = 35f
        stage = 2 // the letters drop in and click as they land, see Name
        delay(760)
        confetti.burst(confetti.center, 260, power = 1.25f, emojiShare = 0.1f); haptics.boom()
        stage = 3
        delay(700)
        stage = 4
        launch { repeat(3) { haptics.heartbeat(); delay(800) } }
        delay(600)
        stage = 5
        var n = 0
        while (!closing) {
            delay(900L + Random.nextLong(900))
            if (closing) break
            n++
            if (n % 5 == 0) {
                confetti.cannons(140); haptics.celebrate()
            } else {
                confetti.firework(); haptics.boom()
            }
        }
    }

    // Read only in draw and layer blocks, so the animation never recomposes the screen.
    val loop = rememberInfiniteTransition(label = "party")
    val hue = loop.animateFloat(0f, 8f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "hue")
    val t = remember(hue) { { hue.value } }

    val labelStyle = K.type.bodyStrong.copy(fontSize = 17.sp)
    val label = "Danke, ${Birthday.FROM}! 🥳"
    val measurer = rememberTextMeasurer()
    val labelLayout = remember(label, labelStyle) { measurer.measure(label, labelStyle) }
    val morphPath = remember { Path() }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { partyCoords = it }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    if (!closing) {
                        confetti.burst(pos, 90, power = 0.75f, emojiShare = 0.15f)
                        haptics.heavy()
                    }
                }
            },
    ) {
        // Everything but the flying confetti and the heart; fades away when the heart bursts.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = backdropAlpha.value }.background(PartyBackground)) {
            // A slow colourful glow behind everything.
            Canvas(Modifier.fillMaxSize()) {
                val h = t()
                drawRect(
                    Brush.radialGradient(
                        listOf(rainbow(h).copy(alpha = 0.38f), rainbow(h + 3f).copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * (0.5f + 0.2f * sin(h * 0.785f)), size.height * 0.38f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            }
            BalloonLayer(confetti)
            ConfettiLayer(confetti, front = false)

            Column(
                Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }
                    .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Cake(t)
                        Spacer(Modifier.height(8.dp))
                        Appear(stage >= 1, delayMillis = 0) {
                            Text(
                                "Alles Gute zum $age.", style = K.type.headline.copy(shadow = TextShadow), color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Name(Birthday.NAME + "!", start = stage >= 2, t = t)
                        Spacer(Modifier.height(18.dp))
                        Appear(stage >= 3) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (millionaire) "$age Jahre und tatsächlich Millionär. Respekt! \uD83E\uDD11" else "$age Jahre und immer noch kein Millionär. \uD83D\uDE04",
                                    style = K.type.title.copy(fontSize = 20.sp, lineHeight = 26.sp, shadow = TextShadow), color = Color.White,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    million,
                                    style = K.type.body.copy(fontSize = 17.sp, lineHeight = 24.sp, shadow = TextShadow), color = Color.White.copy(alpha = 0.88f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Appear(stage >= 4) { Signature(t) }
                    }
                }
                Appear(stage >= 5) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Tipp irgendwo hin für noch mehr Konfetti", style = K.type.caption.copy(shadow = TextShadow),
                            color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .onGloballyPositioned { buttonCoords = it }
                                // From the tap on, the canvas below draws the button and morphs it.
                                .graphicsLayer { alpha = if (closing) 0f else 1f }
                                .pressable(scaleTo = 0.94f, haptic = false) { close() }
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = labelStyle, color = Color.Black, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        // The button turning into the farewell heart, and the flash when it bursts.
        Canvas(Modifier.fillMaxSize()) {
            val r = heart.value
            val a = heartAlpha.value
            if (closing && r > 0f && a > 0f) {
                val m = morph.value
                val start = if (buttonRect != Rect.Zero) buttonRect.center else Offset(size.width / 2, size.height * 0.85f)
                val home = Offset(size.width / 2, size.height * 0.46f)
                val c = start + (home - start) * travel.value
                val pill = button
                morphPath.reset()
                for (k in 0 until OUTLINE_POINTS) {
                    val hx = c.x + HeartOutline[k * 2] * r
                    val hy = c.y + HeartOutline[k * 2 + 1] * r
                    val x = if (pill != null) pill[k * 2] + (hx - pill[k * 2]) * m else hx
                    val y = if (pill != null) pill[k * 2 + 1] + (hy - pill[k * 2 + 1]) * m else hy
                    if (k == 0) morphPath.moveTo(x, y) else morphPath.lineTo(x, y)
                }
                morphPath.close()

                drawCircle(
                    Brush.radialGradient(listOf(Color(0x80FF2D55), Color.Transparent), center = c, radius = r * 1.7f),
                    radius = r * 1.7f, center = c, alpha = a * m,
                )
                // White like the button at first, then red: the gradient fades in on top.
                if (m < 1f) drawPath(morphPath, Color.White, alpha = a)
                drawPath(
                    morphPath,
                    Brush.radialGradient(HeartColors, center = c + Offset(-0.35f * r, -0.45f * r), radius = 1.7f * r),
                    alpha = a * m,
                )
                // A bit of shine on the left lobe.
                drawOval(Color.White, c + Offset(-0.7f * r, -0.55f * r), Size(0.32f * r, 0.2f * r), alpha = 0.35f * a * m)
                // The label melts away in the first third of the morph.
                val textAlpha = (1f - m * 3f).coerceIn(0f, 1f)
                if (textAlpha > 0f && buttonRect != Rect.Zero) {
                    drawText(
                        labelLayout, color = Color.Black, alpha = textAlpha,
                        topLeft = buttonRect.center - Offset(labelLayout.size.width / 2f, labelLayout.size.height / 2f),
                    )
                }
            }
            if (flash.value > 0f) drawRect(Color.White, alpha = flash.value)
        }

        ConfettiLayer(confetti, front = true)
    }
}

/** Fades and slides its content in once [visible] turns true. */
@Composable
private fun Appear(visible: Boolean, delayMillis: Int = 0, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) a.animateTo(1f, tween(650, delayMillis, FastOutSlowInEasing))
    }
    val density = LocalDensity.current
    Box(Modifier.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * with(density) { 24.dp.toPx() }
    }) { content() }
}

/** The cake pops in with an overshoot and then keeps wobbling. */
@Composable
private fun Cake(t: () -> Float) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 260f)) }
    Text(
        "🎂",
        fontSize = 92.sp,
        modifier = Modifier.graphicsLayer {
            val h = t()
            scaleX = pop.value * (1f + 0.05f * sin(h * 4.7f))
            scaleY = pop.value * (1f + 0.05f * sin(h * 4.7f + 1.4f))
            rotationZ = 7f * sin(h * 2.4f)
        },
    )
}

/** Every letter of the name drops in on its own, bounces, then dances in rainbow colours. */
@Composable
private fun Name(text: String, start: Boolean, t: () -> Float) {
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val drops = remember(text) { text.map { Animatable(0f) } }
    LaunchedEffect(start) {
        if (!start) return@LaunchedEffect
        drops.forEachIndexed { i, a ->
            launch {
                delay(i * 95L)
                launch { delay(150); haptics.click() } // roughly when the letter hits its line
                a.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 320f))
            }
        }
    }
    val style = K.type.hero.copy(fontSize = 76.sp, lineHeight = 88.sp, letterSpacing = 0.sp)
    Row(horizontalArrangement = Arrangement.Center) {
        text.forEachIndexed { i, ch ->
            val d = drops[i]
            BasicText(
                ch.toString(),
                style = style,
                color = { rainbow(t() * 1.5f + i * 0.9f) },
                modifier = Modifier.graphicsLayer {
                    val wave = if (d.value > 0.98f) sin(t() * 5f + i * 0.8f) else 0f
                    alpha = d.value.coerceIn(0f, 1f)
                    translationY = (1f - d.value) * with(density) { -260.dp.toPx() } + wave * with(density) { 6.dp.toPx() }
                    rotationZ = (1f - d.value) * (if (i % 2 == 0) -40f else 40f) + wave * 6f
                },
            )
        }
    }
}

@Composable
private fun Signature(t: () -> Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Dein ${Birthday.FROM}", style = K.type.title.copy(fontSize = 22.sp, lineHeight = 28.sp, shadow = TextShadow), color = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(
            "❤️", fontSize = 24.sp,
            modifier = Modifier.graphicsLayer {
                // A heart that beats twice, then rests.
                val beat = (t() * 1.25f) % 1f
                val pulse = when {
                    beat < 0.12f -> beat / 0.12f
                    beat < 0.24f -> 1f - (beat - 0.12f) / 0.12f
                    beat < 0.36f -> (beat - 0.24f) / 0.12f
                    beat < 0.5f -> 1f - (beat - 0.36f) / 0.14f
                    else -> 0f
                }
                scaleX = 1f + 0.28f * pulse
                scaleY = 1f + 0.28f * pulse
            },
        )
    }
}

/** Colourful card at the top of the home screen; a tap starts the party again. */
@Composable
fun BirthdayCard(millionaire: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val loop = rememberInfiniteTransition(label = "card")
    val hue = loop.animateFloat(0f, 8f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "cardHue")
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .pressable(scaleTo = 0.97f, onClick = onClick)
            .clip(shape)
            .drawBehind {
                val h = hue.value
                // Darkened so the white text stays readable on yellow and cyan too.
                drawRect(Brush.linearGradient(listOf(rainbow(h), rainbow(h + 1.6f), rainbow(h + 3.2f)).map { lerp(it, Color.Black, 0.3f) }))
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎂", fontSize = 34.sp, modifier = Modifier.graphicsLayer { rotationZ = 8f * sin(hue.value * 3f) })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("${Birthday.age()} Jahre, ${Birthday.NAME}!", style = K.type.bodyStrong, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(
                if (millionaire) "Und Millionär! Tippen für Konfetti \uD83C\uDF89" else "Immer noch kein Millionär. Tippen für Trost-Konfetti \uD83C\uDF89",
                style = K.type.caption, color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}
