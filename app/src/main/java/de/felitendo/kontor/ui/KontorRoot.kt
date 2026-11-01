package de.felitendo.kontor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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

    Box(Modifier.fillMaxSize().background(K.colors.background)) {
        KontorNavHost(nav, startOnboarding = !s.onboardingDone, activity = activity)
        AnimatedVisibility(
            visible = locked,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(380)) + scaleOut(tween(420), targetScale = 1.08f),
        ) {
            LockScreen(activity)
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
            OnboardingScreen(activity) {
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
    }
}
