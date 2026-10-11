package com.sudhir.safecopy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.sudhir.safecopy.ui.home.HomeRoute

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor"
    // M3 inserts the Watermark screen between EDITOR and PREVIEW (spec 5.1).
    const val PREVIEW = "preview"
    const val DONE = "done"
}

private const val KEY_AUTO_PICK = "autoPick"

@Composable
fun SafeCopyNavGraph() {
    val navController = rememberNavController()
    val incomingShare by LocalContext.current.appContainer.incomingShare.collectAsStateWithLifecycle()

    // A share-sheet image (spec 5.1) always goes through Home, which imports it and then opens
    // the editor. Any edit in progress is replaced: sharing a new image is an explicit action.
    LaunchedEffect(incomingShare) {
        if (incomingShare != null && navController.currentDestination?.route != Routes.HOME) {
            navController.backToHome()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { entry ->
            val autoPick by entry.savedStateHandle.getStateFlow(KEY_AUTO_PICK, false).collectAsStateWithLifecycle()
            HomeRoute(
                onOpenEditor = { navController.navigate(Routes.EDITOR) { launchSingleTop = true } },
                autoPick = autoPick,
                onAutoPickHandled = { entry.savedStateHandle[KEY_AUTO_PICK] = false },
            )
        }
        composable(Routes.EDITOR) {
            EditorRoute(
                onNext = { navController.navigate(Routes.PREVIEW) { launchSingleTop = true } },
                onExit = { navController.backToHome() },
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
