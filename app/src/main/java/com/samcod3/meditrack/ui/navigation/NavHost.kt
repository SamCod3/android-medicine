package com.samcod3.meditrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.samcod3.meditrack.ui.screens.leaflet.LeafletScreen
import com.samcod3.meditrack.ui.screens.main.MainScreen
import com.samcod3.meditrack.ui.screens.profiles.ProfileViewModel
import com.samcod3.meditrack.ui.screens.profiles.ProfilesScreen
import org.koin.androidx.compose.koinViewModel

sealed class Screen(val route: String) {
    data object Profiles : Screen("profiles")
    data object Main : Screen("main/{profileId}/{profileName}") {
        fun createRoute(profileId: String, profileName: String) = 
            "main/$profileId/${profileName.replace("/", "-")}"
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
            val viewModel: ProfileViewModel = koinViewModel()
            val profiles by viewModel.profiles.collectAsState()
            
            ProfilesScreen(
                onProfileSelected = { profileId ->
                    val profile = profiles.find { it.id == profileId }
                    val name = profile?.name ?: "Usuario"
                    navController.navigate(Screen.Main.createRoute(profileId, name)) {
                        popUpTo(Screen.Profiles.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.Main.route,
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType },
                navArgument("profileName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            val profileName = backStackEntry.arguments?.getString("profileName") ?: "Usuario"
            
            MainScreen(
                profileId = profileId,
                profileName = profileName,
                onMedicationScanned = { nationalCode ->
                    navController.navigate(Screen.Leaflet.createRoute(nationalCode, profileId))
                },
                onMedicationClicked = { nationalCode ->
                    navController.navigate(Screen.Leaflet.createRoute(nationalCode, profileId))
                },
                onChangeProfile = {
                    navController.navigate(Screen.Profiles.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
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
                    navController.popBackStack()
                }
            )
        }
    }
}
