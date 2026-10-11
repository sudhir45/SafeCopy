package com.sudhir.safecopy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sudhir.safecopy.appContainer
import com.sudhir.safecopy.ui.editor.EditorRoute
import com.sudhir.safecopy.ui.home.HomeRoute

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor"
}

@Composable
fun SafeCopyNavGraph() {
    val navController = rememberNavController()
    val incomingShare by LocalContext.current.appContainer.incomingShare.collectAsStateWithLifecycle()

    // A share-sheet image (spec 5.1) always goes through Home, which imports it and then opens
    // the editor. Any editor already open is replaced: sharing a new image is an explicit action.
    LaunchedEffect(incomingShare) {
        if (incomingShare != null && navController.currentDestination?.route != Routes.HOME) {
            navController.popBackStack(Routes.HOME, inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeRoute(onOpenEditor = {
                navController.navigate(Routes.EDITOR) { launchSingleTop = true }
            })
        }
        composable(Routes.EDITOR) {
            EditorRoute(onExit = { navController.popBackStack(Routes.HOME, inclusive = false) })
        }
    }
}
