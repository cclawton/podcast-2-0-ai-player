package com.podcast.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.podcast.app.ui.components.BottomNavBar
import com.podcast.app.ui.screens.diagnostics.DiagnosticsScreen
import com.podcast.app.ui.screens.downloads.DownloadsScreen
import com.podcast.app.ui.screens.episodes.EpisodesScreen
import com.podcast.app.ui.screens.library.LibraryScreen
import com.podcast.app.ui.navigation.NavHostViewModel
import com.podcast.app.ui.screens.onboarding.OnboardingScreen
import com.podcast.app.ui.screens.player.PlayerScreen
import com.podcast.app.ui.screens.search.SearchScreen
import com.podcast.app.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object Player : Screen("player")
    data object Episodes : Screen("episodes/{podcastId}") {
        fun createRoute(podcastId: Long) = "episodes/$podcastId"
    }
    data object Settings : Screen("settings")
    data object Diagnostics : Screen("diagnostics")
    data object Downloads : Screen("downloads")
}

@Composable
fun PodcastNavHost(
    viewModel: NavHostViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check onboarding status
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState(initial = true)

    // Determine start destination based on onboarding status
    val startDestination = if (isOnboardingCompleted) {
        Screen.Library.route
    } else {
        Screen.Onboarding.route
    }

    // Show bottom nav only on main screens (not onboarding)
    val showBottomNav = currentRoute in listOf(
        Screen.Library.route,
        Screen.Search.route,
        Screen.Player.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Library.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(navController = navController)
            }

            composable(Screen.Library.route) {
                LibraryScreen(navController = navController)
            }

            composable(Screen.Search.route) {
                SearchScreen(navController = navController)
            }

            composable(Screen.Player.route) {
                PlayerScreen(navController = navController)
            }

            composable(
                route = Screen.Episodes.route,
                arguments = listOf(
                    navArgument("podcastId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val podcastId = backStackEntry.arguments?.getString("podcastId")?.toLongOrNull()
                EpisodesScreen(
                    podcastId = podcastId,
                    navController = navController
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }

            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(navController = navController)
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(navController = navController)
            }
        }
    }
}
