package com.sudhir.safecopy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sudhir.safecopy.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
}

@Composable
fun SafeCopyNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen() }
    }
}
