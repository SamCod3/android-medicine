package com.samcod3.meditrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.samcod3.meditrack.ui.screens.leaflet.LeafletScreen
import com.samcod3.meditrack.ui.screens.profiles.ProfilesScreen
import com.samcod3.meditrack.ui.screens.scanner.ScannerScreen

sealed class Screen(val route: String) {
    data object Profiles : Screen("profiles")
    data object Scanner : Screen("scanner")
    data object Leaflet : Screen("leaflet/{nationalCode}") {
        fun createRoute(nationalCode: String) = "leaflet/$nationalCode"
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
                    // Navigate to Scanner (future: Dashboard/Medication List)
                    navController.navigate(Screen.Scanner.route)
                }
            )
        }
        
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onMedicationScanned = { nationalCode ->
                    navController.navigate(Screen.Leaflet.createRoute(nationalCode))
                }
            )
        }
        
        composable(
            route = Screen.Leaflet.route,
            arguments = listOf(
                navArgument("nationalCode") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val nationalCode = backStackEntry.arguments?.getString("nationalCode") ?: ""
            LeafletScreen(
                nationalCode = nationalCode,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
