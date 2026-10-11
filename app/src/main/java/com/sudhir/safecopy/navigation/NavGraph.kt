package com.sudhir.safecopy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sudhir.safecopy.appContainer
import com.sudhir.safecopy.ui.done.DoneScreen
import com.sudhir.safecopy.ui.editor.EditorRoute
import com.sudhir.safecopy.ui.export.PreviewRoute
import com.sudhir.safecopy.ui.help.HelpScreen
import com.sudhir.safecopy.ui.home.HomeRoute
import com.sudhir.safecopy.ui.onboarding.OnboardingScreen
import com.sudhir.safecopy.ui.settings.SettingsRoute
import com.sudhir.safecopy.ui.watermark.WatermarkRoute
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val EDITOR = "editor"
    const val WATERMARK = "watermark"
    const val PREVIEW = "preview"
    const val DONE = "done"
}

private const val KEY_AUTO_PICK = "autoPick"

@Composable
fun SafeCopyNavGraph() {
    val container = LocalContext.current.appContainer
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    // Wait for DataStore once, to pick the start screen; it must not change afterwards.
    val onboardingDone = settings?.onboardingDone ?: return
    val start = rememberSaveable { if (onboardingDone) Routes.HOME else Routes.ONBOARDING }
    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    val incomingShare by container.incomingShare.collectAsStateWithLifecycle()

    // A share-sheet image (spec 5.1) always goes through Home, which imports it and then opens
    // the editor. Any edit in progress is replaced: sharing a new image is an explicit action.
    LaunchedEffect(incomingShare) {
        val route = navController.currentDestination?.route
        if (incomingShare != null && route != Routes.HOME && route != Routes.ONBOARDING) {
            navController.backToHome()
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                scope.launch { container.settings.update { it.copy(onboardingDone = true) } }
                navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onHelp = { navController.navigate(Routes.HELP) { launchSingleTop = true } },
                onCleared = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HELP) { HelpScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.HOME) { entry ->
            val autoPick by entry.savedStateHandle.getStateFlow(KEY_AUTO_PICK, false).collectAsStateWithLifecycle()
            HomeRoute(
                onOpenEditor = { navController.navigate(Routes.EDITOR) { launchSingleTop = true } },
                autoPick = autoPick,
                onAutoPickHandled = { entry.savedStateHandle[KEY_AUTO_PICK] = false },
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onHelp = { navController.navigate(Routes.HELP) { launchSingleTop = true } },
            )
        }
        composable(Routes.EDITOR) {
            EditorRoute(
                onNext = { navController.navigate(Routes.WATERMARK) { launchSingleTop = true } },
                onExit = { navController.backToHome() },
            )
        }
        composable(Routes.WATERMARK) {
            WatermarkRoute(
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Routes.PREVIEW) { launchSingleTop = true } },
                onExpired = { navController.backToHome() },
            )
        }
        composable(Routes.PREVIEW) {
            PreviewRoute(
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Routes.DONE) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onExpired = { navController.backToHome() },
            )
        }
        composable(Routes.DONE) {
            DoneScreen(
                onAnother = {
                    navController.getBackStackEntry(Routes.HOME).savedStateHandle[KEY_AUTO_PICK] = true
                    navController.backToHome()
                },
                onHome = { navController.backToHome() },
            )
        }
    }
}

private fun NavHostController.backToHome() {
    popBackStack(Routes.HOME, inclusive = false)
}
