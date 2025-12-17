package com.samcod3.meditrack.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.samcod3.meditrack.ui.screens.home.HomeScreen
import com.samcod3.meditrack.ui.screens.scanner.ScannerScreen
import com.samcod3.meditrack.ui.screens.search.SearchScreen

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem("Botiquín", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("Escanear", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    BottomNavItem("Buscar", Icons.Filled.Search, Icons.Outlined.Search)
)

@Composable
fun MainScreen(
    profileId: String,
    profileName: String,
    onMedicationScanned: (String) -> Unit,
    onMedicationClicked: (String) -> Unit,
    onChangeProfile: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    profileId = profileId,
                    profileName = profileName,
                    onMedicationClick = onMedicationClicked,
                    onChangeProfile = onChangeProfile
                )
                1 -> ScannerScreen(
                    onMedicationScanned = onMedicationScanned,
                    onSearchRequested = { selectedTab = 2 }
                )
                2 -> SearchScreen(
                    onBackClick = { selectedTab = 0 },
                    onMedicationClick = { medication ->
                        val code = medication.nationalCode ?: medication.registrationNumber
                        if (code.isNotBlank()) {
                            onMedicationClicked(code)
                        }
                    }
                )
            }
        }
    }
}
