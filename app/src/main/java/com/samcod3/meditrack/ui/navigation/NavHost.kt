package com.samcod3.meditrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.samcod3.meditrack.ui.screens.leaflet.LeafletScreen
import com.samcod3.meditrack.ui.screens.profiles.ProfilesScreen
import com.samcod3.meditrack.ui.screens.search.SearchScreen
import com.samcod3.meditrack.ui.screens.scanner.ScannerScreen

sealed class Screen(val route: String) {
    data object Profiles : Screen("profiles")
    data object Search : Screen("search/{profileId}") {
        fun createRoute(profileId: String) = "search/$profileId"
    }
    data object Scanner : Screen("scanner/{profileId}") {
        fun createRoute(profileId: String) = "scanner/$profileId"
    }
    data object Leaflet : Screen("leaflet/{nationalCode}/{profileId}") {
        fun createRoute(nationalCode: String, profileId: String) = "leaflet/$nationalCode/$profileId"
    }
}

@Composable
fun MediTrackNavHost() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Profiles.route
    ) {
        composable(Screen.Profiles.route) {
            ProfilesScreen(
                onProfileSelected = { profileId ->
                    navController.navigate(Screen.Scanner.createRoute(profileId))
                }
            )
        }
        

        
        composable(
            route = Screen.Search.route,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onMedicationClick = { medication ->
                     val code = medication.nationalCode ?: medication.registrationNumber
                     if (code.isNotBlank()) {
                         navController.navigate(Screen.Leaflet.createRoute(code, profileId))
                     }
                }
            )
        }
        
        composable(
            route = Screen.Scanner.route,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            ScannerScreen(
                onMedicationScanned = { nationalCode ->
                    navController.navigate(Screen.Leaflet.createRoute(nationalCode, profileId)) {
                        // Pop scanner so back button goes to dashboard (future) or profile
                        popUpTo(Screen.Scanner.route) { inclusive = true }
                    }
                },
                onSearchRequested = {
                    navController.navigate(Screen.Search.createRoute(profileId))
                }
            )
        }
        
        composable(
            route = Screen.Leaflet.route,
            arguments = listOf(
                navArgument("nationalCode") { type = NavType.StringType },
                navArgument("profileId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val nationalCode = backStackEntry.arguments?.getString("nationalCode") ?: ""
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            
            LeafletScreen(
                nationalCode = nationalCode,
                profileId = profileId,
                onNavigateBack = { navController.popBackStack() },
                onMedicationSaved = {
                    // Navigate back to Profiles (or Dashboard in future)
                    // Clear back stack to avoid loops
                    navController.navigate(Screen.Profiles.route) {
                        popUpTo(Screen.Profiles.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
