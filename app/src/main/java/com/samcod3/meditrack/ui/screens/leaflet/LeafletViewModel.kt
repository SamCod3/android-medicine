package com.samcod3.meditrack.ui.screens.leaflet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.data.local.dao.MedicationDao
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.repository.DrugRepository
import com.samcod3.meditrack.domain.repository.ReminderRepository
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LeafletUiState(
    val isLoading: Boolean = true,
    val medication: Medication? = null,
    val sections: List<LeafletSection> = emptyList(),
    val myDosages: List<Reminder> = emptyList(), // User's saved dosage/reminders
    val savedMedicationId: String? = null,       // ID if medication is saved
    val error: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

class LeafletViewModel(
    private val nationalCode: String,
    private val profileId: String,
    private val drugRepository: DrugRepository,
    private val userMedicationRepository: UserMedicationRepository,
    private val reminderRepository: ReminderRepository,
    private val medicationDao: MedicationDao
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
                
                // Check if medication is saved and load its reminders
                loadSavedMedicationInfo(medication.nationalCode ?: nationalCode)
                
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
    
    private suspend fun loadSavedMedicationInfo(code: String) {
        val savedMedication = medicationDao.getMedicationByNationalCodeAndProfile(code, profileId)
        if (savedMedication != null) {
            _uiState.value = _uiState.value.copy(savedMedicationId = savedMedication.id)
            
            // Load reminders for this medication
            val reminders = reminderRepository.getRemindersForMedication(savedMedication.id).first()
            _uiState.value = _uiState.value.copy(myDosages = reminders)
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
                _uiState.value = _uiState.value.copy(
                    isSaving = false, 
                    saveSuccess = true,
                    savedMedicationId = result.getOrNull()
                )
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
    
    /**
     * Refresh reminders/dosages. Call this when returning from ReminderScreen.
     */
    fun refreshReminders() {
        viewModelScope.launch {
            val savedMedicationId = _uiState.value.savedMedicationId ?: return@launch
            val reminders = reminderRepository.getRemindersForMedication(savedMedicationId).first()
            _uiState.value = _uiState.value.copy(myDosages = reminders)
        }
    }
}
