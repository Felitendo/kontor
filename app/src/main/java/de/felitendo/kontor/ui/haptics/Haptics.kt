package de.felitendo.kontor.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Central place for every vibration in the app, so the feel stays consistent.
 * Uses rich composition primitives where the device supports them and falls back
 * to view haptic constants otherwise.
 */
class Haptics(private val view: View, private val enabled: () -> Boolean) {
    private val vibrator: Vibrator? = run {
        val ctx = view.context
        if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val primitives: Set<Int> = if (Build.VERSION.SDK_INT >= 30 && vibrator != null) {
        val all = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
        ) + if (Build.VERSION.SDK_INT >= 31) intArrayOf(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, VibrationEffect.Composition.PRIMITIVE_SPIN) else intArrayOf()
        val supported = vibrator.arePrimitivesSupported(*all)
        all.filterIndexed { i, _ -> supported[i] }.toSet()
    } else emptySet()

    private fun compose(vararg steps: Triple<Int, Float, Int>): Boolean {
        if (Build.VERSION.SDK_INT < 30 || vibrator == null) return false
        if (steps.any { it.first !in primitives }) return false
        val c = VibrationEffect.startComposition()
        steps.forEach { (p, scale, delay) -> c.addPrimitive(p, scale, delay) }
        vibrator.vibrate(c.compose())
        return true
    }

    private fun constant(c: Int) = view.performHapticFeedback(c)

    /** Scrubbing a chart, scrolling a picker. Very light. */
    fun tick() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 31 && compose(Triple(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f, 0))) return
        if (Build.VERSION.SDK_INT >= 34) constant(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK) else constant(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Keypad keys, chips, list taps. */
    fun click() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 30 && compose(Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f, 0))) return
        constant(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun toggle(on: Boolean) {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 34) {
            constant(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        } else click()
    }

    /** Range switch, segmented control. */
    fun selection() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 30 && compose(Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f, 0))) return
        constant(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Saving, unlocking, finishing a flow. A rising double pulse. */
    fun success() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 30 && compose(
                Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f, 0),
                Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 60),
            )
        ) return
        if (Build.VERSION.SDK_INT >= 30) constant(HapticFeedbackConstants.CONFIRM) else constant(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Errors, invalid input, delete. */
    fun reject() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 30 && compose(
                Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 0),
                Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 80),
            )
        ) return
        if (Build.VERSION.SDK_INT >= 30) constant(HapticFeedbackConstants.REJECT) else constant(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Long press, pulling to refresh past the threshold. */
    fun heavy() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 30 && compose(Triple(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 0))) return
        constant(HapticFeedbackConstants.LONG_PRESS)
    }

    /** The big reveal of the total after onboarding: a spin that settles. */
    fun celebrate() {
        if (!enabled()) return
        if (Build.VERSION.SDK_INT >= 31 && compose(
                Triple(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.6f, 0),
                Triple(VibrationEffect.Composition.PRIMITIVE_SPIN, 0.7f, 40),
                Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 120),
            )
        ) return
        success()
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }

@Composable
fun rememberHaptics(enabled: () -> Boolean): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view, enabled) }
}
