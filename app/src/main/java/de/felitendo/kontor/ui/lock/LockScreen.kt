package de.felitendo.kontor.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.felitendo.kontor.MainActivity
import de.felitendo.kontor.ui.LocalVm
import de.felitendo.kontor.ui.components.AnimatedLogo
import de.felitendo.kontor.ui.components.SecondaryButton
import de.felitendo.kontor.ui.haptics.LocalHaptics
import de.felitendo.kontor.ui.theme.K

@Composable
fun LockScreen(activity: MainActivity) {
    val vm = LocalVm.current
    val haptics = LocalHaptics.current
    val c = K.colors

    fun unlock() {
        Biometrics.authenticate(activity, "Kontor entsperren") { ok ->
            if (ok) {
                activity.unlockedAt = android.os.SystemClock.elapsedRealtime()
                haptics.success()
                vm.locked.value = false
            } else {
                haptics.reject()
            }
        }
    }

    LaunchedEffect(Unit) { unlock() }
    BackHandler { activity.moveTaskToBack(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            // Swallow every touch so nothing below can be reached.
            .clickable(remember { MutableInteractionSource() }, null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedLogo(Modifier.size(96.dp), animate = false)
            Spacer(Modifier.height(20.dp))
            Text("Kontor ist gesperrt", style = K.type.title, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text("Entsperre mit Gesicht, Fingerabdruck oder PIN.", style = K.type.caption, color = c.textSecondary)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton("Entsperren", icon = Icons.Rounded.Face) { unlock() }
        }
    }
}
