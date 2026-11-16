package de.felitendo.kontor.ui.birthday

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

val PartyColors = listOf(
    0xFFFF3B6B, 0xFFFFC21A, 0xFF2BD67B, 0xFF3BA4FF, 0xFFA155FF,
    0xFFFF7A1A, 0xFF00D9D9, 0xFFFFFFFF, 0xFFF5C542, 0xFFFF5FD2,
).map { Color(it) }

private val PartyEmojis = listOf("🎉", "🎈", "🥳", "🎂", "💶", "💰", "⭐", "🎁")
private val HeartEmojis = listOf("❤️", "💖", "💕", "💗", "💘")
private val Emojis = PartyEmojis + HeartEmojis

private const val MAX_PARTICLES = 1200
private const val DEG = (PI / 180).toFloat()

/** Confetti shapes as triangle lists in unit coordinates (x, y pairs). */
private val Shapes: Array<FloatArray> = arrayOf(
    quad(-0.5f, -0.32f, 0.5f, 0.32f),
    fan(8) { 0.42f },
    quad(-0.95f, -0.14f, 0.95f, 0.14f),
    floatArrayOf(0f, -0.6f, 0.55f, 0.4f, -0.55f, 0.4f),
    fan(10) { if (it % 2 == 0) 0.7f else 0.3f },
)
private val MaxShapeFloats = Shapes.maxOf { it.size }

private fun quad(l: Float, t: Float, r: Float, b: Float) = floatArrayOf(l, t, r, t, r, b, l, t, r, b, l, b)

/** A polygon around the origin as a triangle fan; [radius] per corner (stars alternate). */
private fun fan(corners: Int, radius: (Int) -> Float): FloatArray {
    val out = FloatArray(corners * 6)
    fun corner(i: Int): Pair<Float, Float> {
        val a = -PI / 2 + (i % corners) * 2 * PI / corners
        val r = radius(i % corners)
        return (cos(a) * r).toFloat() to (sin(a) * r).toFloat()
    }
    for (i in 0 until corners) {
        val (x1, y1) = corner(i)
        val (x2, y2) = corner(i + 1)
        out[i * 6 + 2] = x1; out[i * 6 + 3] = y1
        out[i * 6 + 4] = x2; out[i * 6 + 5] = y2
    }
    return out
}

private class Particle {
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var rot = 0f; var vrot = 0f
    var flip = 0f; var vflip = 0f
    var sway = 0f; var vsway = 0f; var swayAmp = 0f
    var size = 0f
    var argb = 0
    /** The back side is a bit darker, which sells the 3D flip. */
    var argbBack = 0
    /** >= 0: index into [Shapes], < 0: emoji index -1 - shape. */
    var shape = 0
    var drag = 2.6f
    var age = 0f; var life = 10f
    /** Bursts fly in front of the content, the steady rain falls behind it. */
    var front = true
}

private class Balloon {
    var x = 0f; var y = 0f; var vy = 0f; var r = 0f
    var color = Color.White
    var phase = 0f; var freq = 1f; var swayAmp = 0f
}

/**
 * Small particle system for the birthday: confetti, emoji and balloons, stepped once per
 * frame by [rememberConfetti] and drawn by [ConfettiLayer] / [BalloonLayer]. Positions are
 * in pixels; speeds are given in dp and scaled by [density].
 *
 * All paper pieces of a layer go out in a single drawVertices call, so even a thousand of
 * them cost about as much as one shape.
 */
@Stable
class Confetti internal constructor(private val density: Float, private val emojis: List<ImageBitmap>) {
    private val particles = ArrayList<Particle>(MAX_PARTICLES)
    private val pool = ArrayDeque<Particle>()
    private val balloons = ArrayList<Balloon>()
    private val rnd = Random(System.nanoTime())
    private val string = Path()

    private val verts = FloatArray(MAX_PARTICLES * MaxShapeFloats)
    private val colors = IntArray(MAX_PARTICLES * MaxShapeFloats / 2)
    private val vertexPaint = android.graphics.Paint()
    private val batched = Build.VERSION.SDK_INT >= 29 // hardware drawVertices

    var size by mutableStateOf(Size.Zero)
        internal set

    /** Bumped on every frame that moved something; reading it in a draw block redraws it. */
    var tick by mutableLongStateOf(0L)
        private set

    /** Seconds since start, drives sways. */
    var time = 0f
        private set

    /** Confetti falling from the top, pieces per second. */
    var rainRate = 0f

    /** Share of rain pieces that are emoji instead of paper. */
    var emojiShare = 0.08f

    /** Balloons kept floating up; 0 lets the current ones drift away. */
    var balloonCount = 0

    private var rainCarry = 0f
    private var wasActive = false

    val center: Offset get() = Offset(size.width / 2, size.height / 2)

    private fun spawn(x: Float, y: Float, vx: Float, vy: Float, emoji: Boolean = false, front: Boolean = true, hearts: Boolean = false) {
        if (particles.size >= MAX_PARTICLES) return
        val p = pool.removeLastOrNull() ?: Particle()
        p.front = front
        p.x = x; p.y = y; p.vx = vx; p.vy = vy
        p.rot = rnd.nextFloat() * 360f
        p.vrot = (rnd.nextFloat() - 0.5f) * 720f
        p.flip = rnd.nextFloat() * 6.28f
        p.vflip = 4f + rnd.nextFloat() * 10f
        p.sway = rnd.nextFloat() * 6.28f
        p.vsway = 2f + rnd.nextFloat() * 3f
        p.swayAmp = (20f + rnd.nextFloat() * 50f) * density
        val color = PartyColors[rnd.nextInt(PartyColors.size)]
        p.argb = color.toArgb()
        p.argbBack = color.copy(red = color.red * 0.72f, green = color.green * 0.72f, blue = color.blue * 0.72f).toArgb()
        p.age = 0f
        p.life = 9f + rnd.nextFloat() * 5f
        if (emoji && emojis.size > PartyEmojis.size) {
            val index = if (hearts) PartyEmojis.size + rnd.nextInt(HeartEmojis.size) else rnd.nextInt(PartyEmojis.size)
            p.shape = -1 - index
            p.size = 0.8f + rnd.nextFloat() * 0.6f
            p.vrot *= 0.25f
            p.drag = 1.8f + rnd.nextFloat() * 0.6f
        } else {
            p.shape = rnd.nextInt(Shapes.size)
            p.size = (7f + rnd.nextFloat() * 7f) * density
            p.drag = 2.2f + rnd.nextFloat() * 1.2f
        }
        particles += p
    }

    /** Explosion in all directions, slightly upwards. [hearts] makes the emoji all hearts. */
    fun burst(at: Offset, count: Int, power: Float = 1f, emojiShare: Float = 0.06f, hearts: Boolean = false) {
        repeat(count) {
            val a = rnd.nextFloat() * 2 * PI
            val speed = (250f + rnd.nextFloat() * 1000f) * power * density
            spawn(at.x, at.y, (cos(a) * speed).toFloat(), (sin(a) * speed).toFloat() - 250f * density, rnd.nextFloat() < emojiShare, hearts = hearts)
        }
    }

    /** A cone of confetti, [angle] in degrees with -90 pointing straight up. */
    fun cannon(from: Offset, angle: Float, count: Int, power: Float = 1f) {
        repeat(count) {
            val a = Math.toRadians((angle + (rnd.nextFloat() - 0.5f) * 34f).toDouble())
            val speed = (1000f + rnd.nextFloat() * 1300f) * power * density
            spawn(from.x, from.y, (cos(a) * speed).toFloat(), (sin(a) * speed).toFloat(), rnd.nextFloat() < 0.05f)
        }
    }

    /** Both bottom corners at once. */
    fun cannons(count: Int = 150, power: Float = 1f) {
        if (size == Size.Zero) return
        cannon(Offset(-10f, size.height + 10f), -64f, count, power)
        cannon(Offset(size.width + 10f, size.height + 10f), -116f, count, power)
    }

    /** A burst somewhere in the upper half. */
    fun firework(count: Int = 100) {
        if (size == Size.Zero) return
        val at = Offset(size.width * (0.15f + rnd.nextFloat() * 0.7f), size.height * (0.12f + rnd.nextFloat() * 0.38f))
        burst(at, count, power = 0.7f + rnd.nextFloat() * 0.4f)
    }

    /** One heavy shower from the top edge. */
    fun shower(count: Int = 200) {
        if (size == Size.Zero) return
        repeat(count) { spawnRain(spread = 1.6f) }
    }

    private fun spawnRain(spread: Float = 1f) {
        val x = rnd.nextFloat() * size.width
        val y = -20f * density - rnd.nextFloat() * 120f * density * spread
        spawn(x, y, (rnd.nextFloat() - 0.5f) * 120f * density, (80f + rnd.nextFloat() * 160f) * density, rnd.nextFloat() < emojiShare, front = false)
    }

    private fun spawnBalloon(initial: Boolean) {
        val b = Balloon()
        b.r = (22f + rnd.nextFloat() * 14f) * density
        b.x = b.r + rnd.nextFloat() * (size.width - 2 * b.r)
        b.y = size.height + b.r * 2 + if (initial) rnd.nextFloat() * size.height * 0.9f else rnd.nextFloat() * 60f * density
        b.vy = (55f + rnd.nextFloat() * 55f) * density
        b.color = PartyColors.filter { it != Color.White }.let { it[rnd.nextInt(it.size)] }
        b.phase = rnd.nextFloat() * 6.28f
        b.freq = 0.8f + rnd.nextFloat() * 0.8f
        b.swayAmp = (8f + rnd.nextFloat() * 14f) * density
        balloons += b
    }

    internal fun step(dt: Float) {
        if (size == Size.Zero) return
        time += dt
        val g = 650f * density

        if (rainRate > 0f) {
            rainCarry += rainRate * dt
            while (rainCarry >= 1f) { rainCarry -= 1f; spawnRain() }
        }

        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.age += dt
            p.vx -= p.vx * p.drag * dt
            p.vy += g * dt
            p.vy -= p.vy * p.drag * dt
            p.sway += p.vsway * dt
            // Fluttering only shows once the piece has slowed down.
            p.x += (p.vx + sin(p.sway) * p.swayAmp) * dt
            p.y += p.vy * dt
            p.rot += p.vrot * dt
            p.flip += p.vflip * dt
            if (p.y > size.height + 80f * density || p.age > p.life) {
                val last = particles.removeAt(particles.lastIndex)
                if (i < particles.size) particles[i] = last
                pool.addLast(p)
            } else {
                i++
            }
        }

        if (balloons.size < balloonCount) {
            val initial = balloons.isEmpty()
            repeat(balloonCount - balloons.size) { spawnBalloon(initial) }
        }
        var j = 0
        while (j < balloons.size) {
            val b = balloons[j]
            b.y -= b.vy * dt
            if (b.y < -b.r * 5) {
                if (balloons.size <= balloonCount) {
                    b.y = size.height + b.r * 2
                    b.x = b.r + rnd.nextFloat() * (size.width - 2 * b.r)
                    j++
                } else {
                    balloons.removeAt(j)
                }
            } else {
                j++
            }
        }

        val active = particles.isNotEmpty() || balloons.isNotEmpty()
        if (active || wasActive) tick++
        wasActive = active
    }

    private fun alphaOf(p: Particle): Float {
        val left = p.life - p.age
        return if (left < 0.8f) (left / 0.8f).coerceIn(0f, 1f) else 1f
    }

    internal fun DrawScope.drawParticles(front: Boolean?) {
        if (tick < 0) return // reading tick redraws this layer on every frame
        if (batched) drawPaperBatched(front) else drawPaperSlow(front)
        for (p in particles) {
            if (p.shape >= 0 || (front != null && p.front != front)) continue
            val img = emojis[-1 - p.shape]
            withTransform({
                translate(p.x, p.y)
                rotate(p.rot * 0.3f, Offset.Zero)
                scale(p.size, p.size, Offset.Zero)
            }) {
                drawImage(img, Offset(-img.width / 2f, -img.height / 2f), alpha = alphaOf(p))
            }
        }
    }

    private fun DrawScope.drawPaperBatched(front: Boolean?) {
        var n = 0
        var c = 0
        for (p in particles) {
            if (p.shape < 0 || (front != null && p.front != front)) continue
            val shape = Shapes[p.shape]
            val flip = cos(p.flip)
            val sx = p.size * max(abs(flip), 0.08f)
            val sy = p.size
            val cr = cos(p.rot * DEG)
            val sr = sin(p.rot * DEG)
            val base = if (flip < 0) p.argbBack else p.argb
            val argb = ((alphaOf(p) * 255).toInt() shl 24) or (base and 0xFFFFFF)
            var k = 0
            while (k < shape.size) {
                val lx = shape[k] * sx
                val ly = shape[k + 1] * sy
                verts[n++] = p.x + lx * cr - ly * sr
                verts[n++] = p.y + lx * sr + ly * cr
                colors[c++] = argb
                k += 2
            }
        }
        if (n == 0) return
        drawIntoCanvas {
            it.nativeCanvas.drawVertices(
                android.graphics.Canvas.VertexMode.TRIANGLES, n, verts, 0, null, 0, colors, 0, null, 0, 0, vertexPaint,
            )
        }
    }

    /** Before Android 10 drawVertices is not hardware accelerated; plain rectangles instead. */
    private fun DrawScope.drawPaperSlow(front: Boolean?) {
        for (p in particles) {
            if (p.shape < 0 || (front != null && p.front != front)) continue
            val flip = cos(p.flip)
            withTransform({
                translate(p.x, p.y)
                rotate(p.rot, Offset.Zero)
                scale(p.size * max(abs(flip), 0.08f), p.size, Offset.Zero)
            }) {
                drawRect(Color(if (flip < 0) p.argbBack else p.argb), Offset(-0.5f, -0.32f), Size(1f, 0.64f), alphaOf(p))
            }
        }
    }

    internal fun DrawScope.drawBalloons() {
        if (tick < 0) return
        val stroke = Stroke(1.2f * density)
        for (b in balloons) {
            val x = b.x + sin(time * b.freq + b.phase) * b.swayAmp
            val tilt = cos(time * b.freq + b.phase) * 6f
            val bottom = b.y + b.r * 1.2f
            string.reset()
            string.moveTo(x, bottom + b.r * 0.18f)
            string.quadraticTo(x - tilt * density * 2, bottom + b.r * 1.6f, x + tilt * density, bottom + b.r * 3.2f)
            drawPath(string, Color.White.copy(alpha = 0.45f), style = stroke)
            withTransform({ rotate(tilt, Offset(x, bottom)) }) {
                drawOval(b.color, Offset(x - b.r, b.y - b.r * 1.2f), Size(b.r * 2, b.r * 2.4f), alpha = 0.85f)
                // Knot
                drawCircle(b.color, b.r * 0.16f, Offset(x, bottom + b.r * 0.06f), alpha = 0.85f)
                // Shine
                drawOval(Color.White.copy(alpha = 0.38f), Offset(x - b.r * 0.62f, b.y - b.r * 0.85f), Size(b.r * 0.42f, b.r * 0.72f))
            }
        }
    }
}

/** Renders an emoji once into a bitmap; drawing bitmaps is far cheaper than laying out text. */
private fun emojiBitmap(emoji: String, px: Int): ImageBitmap {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = px * 0.78f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val fm = paint.fontMetrics
    android.graphics.Canvas(bitmap).drawText(emoji, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, paint)
    return bitmap.asImageBitmap()
}

/** A [Confetti] system that steps itself every frame for as long as it is in the composition. */
@Composable
fun rememberConfetti(): Confetti {
    val density = LocalDensity.current.density
    val confetti = remember(density) {
        val px = (30 * density).toInt()
        Confetti(density, Emojis.map { emojiBitmap(it, px) })
    }
    LaunchedEffect(confetti) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(1f / 20)
                last = now
                confetti.step(dt)
            }
        }
    }
    return confetti
}

/**
 * Draws the confetti. Never takes touches, so whatever lies below stays usable.
 * [front] true / false draws only the bursts / only the rain; null draws everything.
 */
@Composable
fun ConfettiLayer(confetti: Confetti, modifier: Modifier = Modifier, front: Boolean? = null) {
    Canvas(modifier.fillMaxSize().onSizeChanged { confetti.size = it.toSize() }) {
        with(confetti) { drawParticles(front) }
    }
}

@Composable
fun BalloonLayer(confetti: Confetti, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        with(confetti) { drawBalloons() }
    }
}
