package com.samcod3.meditrack.ui.screens.profiles

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.domain.model.Profile
import com.samcod3.meditrack.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ProfilesUiState(
    val isLoading: Boolean = true,
    val profiles: List<Profile> = emptyList(),
    val error: String? = null
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            profileRepository.getAllProfiles()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error loading profiles"
                    )
                }
                .collect { profiles ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        profiles = profiles,
                        error = null
                    )
                }
        }
    }

    fun createProfile(name: String) {
        if (name.isBlank()) return
        
        viewModelScope.launch {
            // Generate a random pastel color
            val color = generateRandomColor()
            profileRepository.createProfile(name, color)
        }
    }
    
    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profile)
        }
    }
    
    private fun generateRandomColor(): Long {
        val random = Random.Default
        // Generate pastel colors (high value/lightness)
        val red = random.nextInt(150, 256)
        val green = random.nextInt(150, 256)
        val blue = random.nextInt(150, 256)
        
        // Convert to ARGB Long (Alpha = 255)
        return (0xFF000000 or 
                (red.toLong() shl 16) or 
                (green.toLong() shl 8) or 
                blue.toLong())
    }
}
