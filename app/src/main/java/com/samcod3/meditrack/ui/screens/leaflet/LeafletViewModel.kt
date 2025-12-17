package com.samcod3.meditrack.ui.screens.leaflet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.data.repository.DrugRepository
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LeafletUiState(
    val isLoading: Boolean = true,
    val medication: Medication? = null,
    val sections: List<LeafletSection> = emptyList(),
    val selectedSectionIndex: Int = 0,
    val error: String? = null
)

class LeafletViewModel(
    private val nationalCode: String,
    private val drugRepository: DrugRepository
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
            val medicationResult = drugRepository.getMedicationByNationalCode(nationalCode)
            
            if (medicationResult.isSuccess) {
                val medication = medicationResult.getOrThrow()
                _uiState.value = _uiState.value.copy(medication = medication)
                
                // Then load leaflet sections
                val leafletResult = drugRepository.getLeaflet(medication.registrationNumber)
                
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
    
    fun selectSection(index: Int) {
        _uiState.value = _uiState.value.copy(selectedSectionIndex = index)
    }
    
    fun retry() {
        loadMedicationAndLeaflet()
    }
}
