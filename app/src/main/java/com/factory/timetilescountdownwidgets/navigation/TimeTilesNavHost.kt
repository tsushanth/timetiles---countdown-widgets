package com.factory.timetilescountdownwidgets.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.factory.timetilescountdownwidgets.ui.addedit.AddEditScreen
import com.factory.timetilescountdownwidgets.ui.addedit.AddEditViewModel
import com.factory.timetilescountdownwidgets.ui.detail.DetailScreen
import com.factory.timetilescountdownwidgets.ui.detail.DetailViewModel
import com.factory.timetilescountdownwidgets.ui.home.HomeScreen
import com.factory.timetilescountdownwidgets.ui.home.HomeViewModel
import com.factory.timetilescountdownwidgets.ui.onboarding.OnboardingScreen
import com.factory.timetilescountdownwidgets.ui.paywall.PaywallScreen
import com.factory.timetilescountdownwidgets.ui.paywall.PaywallViewModel
import com.factory.timetilescountdownwidgets.ui.settings.SettingsScreen
import com.factory.timetilescountdownwidgets.util.AppPreferences
import com.factory.timetilescountdownwidgets.util.billingManager
import com.factory.timetilescountdownwidgets.util.countdownRepository
import com.factory.timetilescountdownwidgets.util.premiumManager
import kotlinx.coroutines.launch

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ADD = "add"
    const val EDIT = "edit/{eventId}"
    const val DETAIL = "detail/{eventId}"
    const val PAYWALL = "paywall"
    const val SETTINGS = "settings"
    fun edit(id: Long) = "edit/$id"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun TimeTilesNavHost() {
    val context = LocalContext.current
    val repository = context.countdownRepository()
    val billingManager = context.billingManager()
    val premiumManager = context.premiumManager()
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val onboardingCompleted by appPreferences.onboardingCompleted.collectAsState(initial = null)

    // Wait for the initial DataStore read before picking a start destination so a
    // returning user never flashes onboarding before landing on Home.
    val startDestination = onboardingCompleted ?: return

    val navController = rememberNavController()
    val isPremium by premiumManager.isPremium.collectAsState()
    val adsRemoved by premiumManager.adsRemoved.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (startDestination) Routes.HOME else Routes.ONBOARDING
    ) {
        composable(Routes.ONBOARDING) {
            val scope = rememberCoroutineScope()
            OnboardingScreen(
                onFinished = {
                    scope.launch {
                        appPreferences.setOnboardingCompleted(true)
                        // Paywall trigger point: shown once, right after onboarding.
                        navController.navigate(Routes.PAYWALL) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))
            HomeScreen(
                viewModel = viewModel,
                isPremium = isPremium,
                adsRemoved = adsRemoved,
                onAddClick = { navController.navigate(Routes.ADD) },
                onItemClick = { id -> navController.navigate(Routes.detail(id)) },
                onUpgradeRequired = { navController.navigate(Routes.PAYWALL) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.ADD) {
            val viewModel: AddEditViewModel = viewModel(
                factory = AddEditViewModel.Factory(repository, null)
            )
            AddEditScreen(
                viewModel = viewModel,
                isPremium = isPremium,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onUpgradeRequired = { navController.navigate(Routes.PAYWALL) }
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId") ?: 0L
            val viewModel: AddEditViewModel = viewModel(
                factory = AddEditViewModel.Factory(repository, eventId)
            )
            AddEditScreen(
                viewModel = viewModel,
                isPremium = isPremium,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onUpgradeRequired = { navController.navigate(Routes.PAYWALL) }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId") ?: 0L
            val viewModel: DetailViewModel = viewModel(
                factory = DetailViewModel.Factory(repository, eventId)
            )
            DetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.edit(id)) },
                onDeleted = { navController.popBackStack(Routes.HOME, false) }
            )
        }
        composable(Routes.PAYWALL) {
            val viewModel: PaywallViewModel = viewModel(
                factory = PaywallViewModel.Factory(billingManager, premiumManager)
            )
            PaywallScreen(
                viewModel = viewModel,
                onClose = {
                    // Falls back to Home when the paywall is the only backstack entry,
                    // which happens right after onboarding.
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PAYWALL) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                billingManager = billingManager,
                premiumManager = premiumManager,
                onUpgradeClick = { navController.navigate(Routes.PAYWALL) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
