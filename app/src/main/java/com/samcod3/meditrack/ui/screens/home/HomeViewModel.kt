package com.samcod3.meditrack.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.domain.model.SavedMedication
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val profileId: String,
    private val userMedicationRepository: UserMedicationRepository
) : ViewModel() {
    
    val medications: StateFlow<List<SavedMedication>> = userMedicationRepository
        .getMedicationsForProfile(profileId)
        .map { list -> list.sortedBy { it.name } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun deleteMedication(id: String) {
        viewModelScope.launch {
            userMedicationRepository.deleteMedication(id)
        }
    }
}
