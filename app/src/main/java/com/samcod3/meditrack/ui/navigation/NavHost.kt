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
import com.samcod3.meditrack.ui.screens.reminders.ReminderScreen
import com.samcod3.meditrack.ui.screens.treatment.MyTreatmentScreen
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
    data object Reminders : Screen("reminders/{medicationId}/{medicationName}") {
        fun createRoute(medicationId: String, medicationName: String) = 
            "reminders/$medicationId/${java.net.URLEncoder.encode(medicationName, "UTF-8")}"
    }
    data object Treatment : Screen("treatment/{profileName}") {
        fun createRoute(profileName: String) = 
            "treatment/${java.net.URLEncoder.encode(profileName, "UTF-8")}"
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
            
            ProfilesScreen(
                onProfileSelected = { profileId ->
                    val profile = viewModel.getProfileById(profileId)
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
                onReminderClick = { medicationId, medicationName ->
                    navController.navigate(Screen.Reminders.createRoute(medicationId, medicationName))
                },
                onTreatmentClick = {
                    navController.navigate(Screen.Treatment.createRoute(profileName))
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
                },
                onAddReminderClick = { medicationId, medicationName ->
                    navController.navigate(Screen.Reminders.createRoute(medicationId, medicationName))
                }
            )
        }
        
        composable(
            route = Screen.Reminders.route,
            arguments = listOf(
                navArgument("medicationId") { type = NavType.StringType },
                navArgument("medicationName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getString("medicationId") ?: ""
            val medicationName = try {
                java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("medicationName") ?: "",
                    "UTF-8"
                )
            } catch (e: Exception) {
                backStackEntry.arguments?.getString("medicationName") ?: "Medicamento"
            }
            
            ReminderScreen(
                medicationId = medicationId,
                medicationName = medicationName,
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.Treatment.route,
            arguments = listOf(
                navArgument("profileName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawName = backStackEntry.arguments?.getString("profileName") ?: ""
            val profileName = try {
                java.net.URLDecoder.decode(rawName, "UTF-8").ifEmpty { "Usuario" }
            } catch (e: Exception) {
                rawName.ifEmpty { "Usuario" }
            }
            
            MyTreatmentScreen(
                profileName = profileName,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
