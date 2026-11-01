package de.felitendo.kontor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.felitendo.kontor.ui.components.ShakeDetector
import de.felitendo.kontor.ui.haptics.LocalHaptics
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.felitendo.kontor.MainActivity
import de.felitendo.kontor.data.model.AssetKind
import de.felitendo.kontor.ui.assets.AddAssetScreen
import de.felitendo.kontor.ui.assets.AssetDetailScreen
import de.felitendo.kontor.ui.assets.EditAssetScreen
import de.felitendo.kontor.ui.assets.KindScreen
import de.felitendo.kontor.ui.forecast.ForecastScreen
import de.felitendo.kontor.ui.home.HomeScreen
import de.felitendo.kontor.ui.instrument.InstrumentScreen
import de.felitendo.kontor.ui.lock.LockScreen
import de.felitendo.kontor.ui.onboarding.OnboardingScreen
import de.felitendo.kontor.ui.search.SearchScreen
import de.felitendo.kontor.ui.settings.SettingsScreen
import de.felitendo.kontor.ui.transfer.TransferScreen
import de.felitendo.kontor.ui.theme.K
import kotlinx.serialization.Serializable

object Routes {
    @Serializable data object Home
    @Serializable data object Onboarding
    @Serializable data class Add(val kind: AssetKind)
    @Serializable data class Edit(val id: String)
    @Serializable data class Detail(val id: String)
    @Serializable data class Kind(val kind: AssetKind)
    @Serializable data class Search(val depotId: String? = null)
    @Serializable data class Instrument(
        val isin: String,
        val name: String,
        val wkn: String? = null,
        val type: String? = null,
        val onvistaType: String? = null,
        val onvistaId: String? = null,
        val onvistaSymbol: String? = null,
        val depotId: String? = null,
        val positionId: String? = null,
    )
    @Serializable data class Settings(val openUpdate: Boolean = false)
    @Serializable data object Forecast
    /** "menu", "new", "send" or "receive"; see TransferScreen. */
    @Serializable data class Transfer(val mode: String = "menu")
}

@Composable
fun KontorRoot(activity: MainActivity) {
    val vm = LocalVm.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val s = settings ?: return
    if (portfolio == null) return
    val nav = rememberNavController()

    val open by activity.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(open, s.onboardingDone) {
        val target = open ?: return@LaunchedEffect
        if (!s.onboardingDone) return@LaunchedEffect
        activity.pendingOpen.value = null
        when {
            target == "update" -> nav.navigate(Routes.Settings(openUpdate = true))
            target.startsWith("asset:") -> nav.navigate(Routes.Detail(target.removePrefix("asset:")))
        }
    }

    val haptics = LocalHaptics.current
    var shakeToast by remember { mutableStateOf<Boolean?>(null) }
    ShakeDetector(enabled = s.shakeToHide && s.onboardingDone && !locked) {
        val hide = !s.hideAmounts
        haptics.heavy()
        vm.updateSettings { it.copy(hideAmounts = hide) }
        shakeToast = hide
    }
    LaunchedEffect(shakeToast) { if (shakeToast != null) { delay(1600); shakeToast = null } }

    Box(Modifier.fillMaxSize().background(K.colors.background)) {
        KontorNavHost(nav, startOnboarding = !s.onboardingDone, activity = activity)
        ShakeToast(shakeToast, Modifier.align(Alignment.TopCenter))
        AnimatedVisibility(
            visible = locked,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(380)) + scaleOut(tween(420), targetScale = 1.08f),
        ) {
            LockScreen(activity)
        }
    }
}

/** Small pill that confirms a shake: "Beträge verborgen" / "Beträge sichtbar". */
@Composable
private fun ShakeToast(hidden: Boolean?, modifier: Modifier) {
    val c = K.colors
    var last by remember { mutableStateOf(true) }
    if (hidden != null) last = hidden
    AnimatedVisibility(
        hidden != null, modifier = modifier.statusBarsPadding().padding(top = 12.dp),
        enter = slideInVertically(spring(0.6f, 500f)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(tween(220)) { -it * 2 } + fadeOut(),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(c.text).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (last) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = c.background, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (last) "Beträge verborgen" else "Beträge sichtbar", style = K.type.label, color = c.background)
        }
    }
}

@Composable
private fun KontorNavHost(nav: NavHostController, startOnboarding: Boolean, activity: MainActivity) {
    val duration = 340
    NavHost(
        navController = nav,
        startDestination = if (startOnboarding) Routes.Onboarding else Routes.Home,
        enterTransition = { slideInHorizontally(tween(duration)) { it / 4 } + fadeIn(tween(duration)) },
        exitTransition = { slideOutHorizontally(tween(duration)) { -it / 8 } + fadeOut(tween(duration / 2)) },
        popEnterTransition = { slideInHorizontally(tween(duration)) { -it / 8 } + fadeIn(tween(duration)) },
        popExitTransition = { slideOutHorizontally(tween(duration)) { it / 4 } + fadeOut(tween(duration / 2)) },
    ) {
        composable<Routes.Onboarding> {
            OnboardingScreen(activity, onTransfer = { nav.navigate(Routes.Transfer("new")) }) {
                nav.navigate(Routes.Home) { popUpTo(Routes.Onboarding) { inclusive = true } }
            }
        }
        composable<Routes.Home> { HomeScreen(nav) }
        composable<Routes.Add> { AddAssetScreen(nav, it.toRoute<Routes.Add>().kind) }
        composable<Routes.Edit> { EditAssetScreen(nav, it.toRoute<Routes.Edit>().id) }
        composable<Routes.Detail> { AssetDetailScreen(nav, it.toRoute<Routes.Detail>().id) }
        composable<Routes.Kind> { KindScreen(nav, it.toRoute<Routes.Kind>().kind) }
        composable<Routes.Search> { SearchScreen(nav, it.toRoute<Routes.Search>().depotId) }
        composable<Routes.Instrument> { InstrumentScreen(nav, it.toRoute<Routes.Instrument>()) }
        composable<Routes.Settings> { SettingsScreen(nav, activity, it.toRoute<Routes.Settings>().openUpdate) }
        composable<Routes.Forecast> { ForecastScreen(nav) }
        composable<Routes.Transfer> { TransferScreen(nav, activity, it.toRoute<Routes.Transfer>().mode) }
    }
}
