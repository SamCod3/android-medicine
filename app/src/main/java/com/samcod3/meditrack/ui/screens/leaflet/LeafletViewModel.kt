package com.samcod3.meditrack.ui.screens.leaflet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.data.repository.DrugRepository
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LeafletUiState(
    val isLoading: Boolean = true,
    val medication: Medication? = null,
    val sections: List<LeafletSection> = emptyList(),
    val error: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

class LeafletViewModel(
    private val nationalCode: String,
    private val profileId: String,
    private val drugRepository: DrugRepository,
    private val userMedicationRepository: UserMedicationRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LeafletUiState())
    val uiState: StateFlow<LeafletUiState> = _uiState.asStateFlow()
    
    init {
        loadMedicationAndLeaflet()
    }
    
    private fun loadMedicationAndLeaflet() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // First get medication info
            // Support both National Code (6-7 digits) and Registration Number (5 digits)
            val medicationResult = if (nationalCode.length == 5) {
                drugRepository.getMedicationByRegistrationNumber(nationalCode)
            } else {
                drugRepository.getMedicationByNationalCode(nationalCode)
            }
            
            if (medicationResult.isSuccess) {
                val medication = medicationResult.getOrThrow()
                _uiState.value = _uiState.value.copy(medication = medication)
                
                // Then load leaflet sections using medication object (for URL fallback)
                val leafletResult = drugRepository.getLeaflet(medication)
                
                if (leafletResult.isSuccess) {
                    val leaflet = leafletResult.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sections = leaflet.sections
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = leafletResult.exceptionOrNull()?.message ?: "Error cargando prospecto"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = medicationResult.exceptionOrNull()?.message ?: "Medicamento no encontrado"
                )
            }
        }
    }
    
    fun saveMedication() {
        val medication = _uiState.value.medication ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            
            val result = userMedicationRepository.saveMedication(
                profileId = profileId,
                nationalCode = medication.nationalCode ?: nationalCode,
                name = medication.name,
                description = medication.activeIngredients.joinToString(", ") { "${it.name} ${it.quantity}${it.unit}" },
                notes = null
            )
            
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al guardar: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
    
    fun retry() {
        loadMedicationAndLeaflet()
    }
}
