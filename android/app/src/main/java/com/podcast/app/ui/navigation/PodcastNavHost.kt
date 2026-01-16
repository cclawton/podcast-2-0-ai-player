package com.podcast.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object Player : Screen("player")
    data object Episodes : Screen("episodes/{podcastId}") {
        fun createRoute(podcastId: Long) = "episodes/$podcastId"
    }
    data object Settings : Screen("settings")
}

@Composable
fun PodcastNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Library.route
    ) {
        composable(Screen.Library.route) {
            // TODO: LibraryScreen(navController)
        }

        composable(Screen.Search.route) {
            // TODO: SearchScreen(navController)
        }

        composable(Screen.Player.route) {
            // TODO: PlayerScreen(navController)
        }

        composable(Screen.Episodes.route) { backStackEntry ->
            val podcastId = backStackEntry.arguments?.getString("podcastId")?.toLongOrNull()
            // TODO: EpisodesScreen(podcastId, navController)
        }

        composable(Screen.Settings.route) {
            // TODO: SettingsScreen(navController)
        }
    }
}
