package de.felitendo.kontor

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.felitendo.kontor.birthday.Birthday
import de.felitendo.kontor.data.model.ThemeMode
import de.felitendo.kontor.ui.AppViewModel
import de.felitendo.kontor.ui.KontorRoot
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.haptics.rememberHaptics
import de.felitendo.kontor.ui.theme.KontorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    private var backgroundAt = 0L

    /** Deep link from a notification ("update", "asset:<id>"). */
    val pendingOpen = MutableStateFlow<String?>(null)

    /** Set before launching system screens (file picker, installer) so returning does not lock. */
    var suppressNextLock = false

    /** The PIN fallback of the biometric prompt is its own activity; don't re-lock right after it. */
    var unlockedAt = 0L

    /** Whether the content was built for the birthday; checked again whenever the app returns. */
    private var birthdayLook = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Before the lifecycle observer below: it gets an immediate onStart when the activity is
        // recreated while the app is in the foreground, and compares against this value.
        birthdayLook = Birthday.isToday()
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { vm.settings.value == null || vm.portfolio.value == null }
        pendingOpen.value = intent.getStringExtra(EXTRA_OPEN)

        lifecycleScope.launch {
            val s = vm.settings.filterNotNull().first()
            if (s.lockEnabled && savedInstanceState == null) vm.locked.value = true
            if (savedInstanceState == null) vm.requestParty()
        }
        lifecycleScope.launch {
            vm.settings.filterNotNull().collect { s ->
                if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!s.lockEnabled)
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(lockObserver)
        setContentRoot()
    }

    private val lockObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            backgroundAt = SystemClock.elapsedRealtime()
        }

        override fun onStart(owner: LifecycleOwner) {
            val s = vm.settings.value ?: return
            vm.refresh()
            // Left open over midnight into or out of the birthday: rebuild with the right look.
            if (Birthday.isToday() != birthdayLook) recreate()
            if (suppressNextLock) { suppressNextLock = false; return }
            if (SystemClock.elapsedRealtime() - unlockedAt < 3000) return
            val away = if (backgroundAt > 0) SystemClock.elapsedRealtime() - backgroundAt else 0L
            if (s.lockEnabled && backgroundAt > 0 && away >= s.lockTimeout * 1000L) {
                vm.locked.value = true
            }
            if (away >= 60_000L) vm.requestParty()
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lockObserver)
        super.onDestroy()
    }

    private fun setContentRoot() {
        val birthday = birthdayLook
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings?.theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> systemDark
            }
            val hapticsOn by rememberUpdatedState(settings?.haptics != false)
            val hapticsStrong by rememberUpdatedState(settings?.hapticsStrong == true)
            DisposableEffect(dark) {
                // The activity handles uiMode itself, so the bar icons follow the app theme here.
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
                onDispose {}
            }
            KontorTheme(dark, birthday) {
                val haptics = rememberHaptics(enabled = { hapticsOn }, strong = { hapticsStrong })
                CompositionLocalProvider(LocalVm provides vm, LocalHaptics provides haptics) {
                    KontorRoot(this@MainActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { pendingOpen.value = it }
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
