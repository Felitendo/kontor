package de.felitendo.kontor.ui.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Central place for every vibration in the app, so the feel stays consistent.
 *
 * Everything goes straight to the [Vibrator]: view haptics are silenced by the system's
 * "touch feedback" switch on many phones, which made Kontor feel dead there. The order is
 * - [strong]: plain one-shot pulses at a fixed amplitude, felt on every motor
 * - rich composition primitives where the device really supports them (Pixel and co.)
 * - predefined effects, which every vendor maps to something sensible
 */
class Haptics(context: Context, private val enabled: () -> Boolean, private val strong: () -> Boolean) {
    private val vibrator: Vibrator? = run {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        v?.takeIf { it.hasVibrator() }
    }

    private val primitives: Boolean = Build.VERSION.SDK_INT >= 31 && vibrator != null && vibrator.areAllPrimitivesSupported(
        Composition.PRIMITIVE_TICK,
        Composition.PRIMITIVE_LOW_TICK,
        Composition.PRIMITIVE_CLICK,
        Composition.PRIMITIVE_THUD,
        Composition.PRIMITIVE_QUICK_RISE,
        Composition.PRIMITIVE_SLOW_RISE,
        Composition.PRIMITIVE_SPIN,
    )

    private val amplitude: Boolean = vibrator?.hasAmplitudeControl() == true

    /**
     * Android files short clicks under "touch" and drops them when the phone's touch
     * vibration is switched off, which is common. Kontor has its own switch, so it plays
     * them as media vibration instead.
     */
    private val attributes: VibrationAttributes? =
        if (Build.VERSION.SDK_INT >= 33) VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA) else null

    @Suppress("DEPRECATION")
    private val legacyAttributes: AudioAttributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    private fun play(effect: VibrationEffect) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, attributes!!)
            else @Suppress("DEPRECATION") v.vibrate(effect, legacyAttributes)
        }
    }

    private fun composition(vararg steps: Triple<Int, Float, Int>): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 31 || !primitives) return null
        val c = VibrationEffect.startComposition()
        steps.forEach { (p, scale, delay) -> c.addPrimitive(p, scale, delay) }
        return c.compose()
    }

    private fun predefined(id: Int): VibrationEffect? =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(id) else null

    private fun pulse(ms: Long, strength: Int): VibrationEffect =
        VibrationEffect.createOneShot(ms, if (amplitude) strength else VibrationEffect.DEFAULT_AMPLITUDE)

    /** Strong mode pattern: alternating off/on durations with amplitudes. */
    private fun pattern(timings: LongArray, amplitudes: IntArray): VibrationEffect =
        if (amplitude) VibrationEffect.createWaveform(timings, amplitudes, -1)
        else VibrationEffect.createWaveform(timings, -1)

    private fun fire(strongEffect: () -> VibrationEffect, fine: () -> VibrationEffect?) {
        if (!enabled() || vibrator == null) return
        play(if (strong()) strongEffect() else fine() ?: strongEffect())
    }

    /** Scrubbing a chart, moving a slider. */
    fun tick() = fire({ pulse(8, 110) }) {
        composition(Triple(Composition.PRIMITIVE_TICK, 0.75f, 0)) ?: predefined(VibrationEffect.EFFECT_TICK)
    }

    /** Keypad keys, buttons, list taps. */
    fun click() = fire({ pulse(14, 170) }) {
        composition(Triple(Composition.PRIMITIVE_CLICK, 0.7f, 0)) ?: predefined(VibrationEffect.EFFECT_CLICK)
    }

    fun toggle(on: Boolean) = fire({ pulse(if (on) 18 else 12, if (on) 200 else 140) }) {
        composition(Triple(if (on) Composition.PRIMITIVE_CLICK else Composition.PRIMITIVE_TICK, if (on) 0.85f else 0.7f, 0))
            ?: predefined(if (on) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK)
    }

    /** Range switch, segmented control. */
    fun selection() = fire({ pulse(10, 140) }) {
        composition(Triple(Composition.PRIMITIVE_TICK, 1f, 0)) ?: predefined(VibrationEffect.EFFECT_TICK)
    }

    /** Saving, unlocking, finishing a flow: a rising double pulse. */
    fun success() = fire({ pattern(longArrayOf(0, 14, 70, 26), intArrayOf(0, 150, 0, 255)) }) {
        composition(
            Triple(Composition.PRIMITIVE_QUICK_RISE, 0.6f, 0),
            Triple(Composition.PRIMITIVE_CLICK, 1f, 60),
        ) ?: predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }

    /** Errors, invalid input, delete. */
    fun reject() = fire({ pattern(longArrayOf(0, 22, 60, 22), intArrayOf(0, 230, 0, 230)) }) {
        composition(
            Triple(Composition.PRIMITIVE_CLICK, 0.9f, 0),
            Triple(Composition.PRIMITIVE_CLICK, 0.9f, 80),
        ) ?: predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }

    /** Long press, starting a chart scrub, pull to refresh. */
    fun heavy() = fire({ pulse(30, 255) }) {
        composition(Triple(Composition.PRIMITIVE_THUD, 0.9f, 0)) ?: predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    /** The big reveal of the total after onboarding: a spin that settles. */
    fun celebrate() = fire({ pattern(longArrayOf(0, 20, 60, 20, 60, 45), intArrayOf(0, 120, 0, 180, 0, 255)) }) {
        composition(
            Triple(Composition.PRIMITIVE_SLOW_RISE, 0.6f, 0),
            Triple(Composition.PRIMITIVE_SPIN, 0.7f, 40),
            Triple(Composition.PRIMITIVE_CLICK, 1f, 120),
        ) ?: predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    /** Settings "Testen": the whole vocabulary in a row. */
    suspend fun demo() {
        click(); kotlinx.coroutines.delay(260)
        repeat(6) { tick(); kotlinx.coroutines.delay(70) }
        kotlinx.coroutines.delay(260)
        heavy(); kotlinx.coroutines.delay(420)
        success()
    }

    val available: Boolean get() = vibrator != null
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }

@Composable
fun rememberHaptics(enabled: () -> Boolean, strong: () -> Boolean): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext, enabled, strong) }
}
