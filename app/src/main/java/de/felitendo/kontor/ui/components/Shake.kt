package de.felitendo.kontor.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.sqrt

/**
 * Calls [onShake] on a deliberate shake: two strong jolts within half a second.
 * Only listens while the app is in the foreground.
 */
@Composable
fun ShakeDetector(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onShake)
    LifecycleResumeEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            private var lastJolt = 0L
            private var jolts = 0
            private var lastShake = 0L

            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (g < 2.6f) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastShake < 1500) return
                jolts = if (now - lastJolt < 500) jolts + 1 else 1
                lastJolt = now
                if (jolts >= 2) {
                    jolts = 0
                    lastShake = now
                    callback()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (enabled && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sm?.unregisterListener(listener) }
    }
}
