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
import com.samcod3.meditrack.ai.AILeafletParser
import com.samcod3.meditrack.domain.model.ParsedLeaflet
import com.samcod3.meditrack.domain.model.ParsedSection
import com.samcod3.meditrack.domain.model.ContentBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

sealed class LeafletEvent {
    data class ShowToast(val message: String) : LeafletEvent()
}
data class LeafletUiState(
    val isLoading: Boolean = true,
    val medication: Medication? = null,
    val sections: List<LeafletSection> = emptyList(),
    val myDosages: List<Reminder> = emptyList(), // User's saved dosage/reminders
    val savedMedicationId: String? = null,       // ID if medication is saved
    val error: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val parsedLeaflet: ParsedLeaflet? = null,
    val isAiProcessing: Boolean = false
)

class LeafletViewModel(
    private val nationalCode: String,
    private val profileId: String,
    private val drugRepository: DrugRepository,
    private val userMedicationRepository: UserMedicationRepository,
    private val reminderRepository: ReminderRepository,
    private val medicationDao: MedicationDao,
    private val aiLeafletParser: AILeafletParser
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LeafletUiState())
    val uiState: StateFlow<LeafletUiState> = _uiState.asStateFlow()
    
    private val _events = Channel<LeafletEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    
    init {
        loadMedicationAndLeaflet()
    }
    
    fun retry() {
        loadMedicationAndLeaflet(isUserRetry = true)
    }
    
    private fun loadMedicationAndLeaflet(isUserRetry: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // 1. Try fetching by National Code
            val medicationResult = if (nationalCode.length == 5) {
                drugRepository.getMedicationByRegistrationNumber(nationalCode)
            } else {
                drugRepository.getMedicationByNationalCode(nationalCode)
            }
            
            var officialMedication = medicationResult.getOrNull()
            
            // 2. FALLBACK: If not found by Code (e.g. Imported/Fake CN), try to Recover via Name
            if (officialMedication == null) {
                // Check if we have it locally (Imported)
                val savedMedication = medicationDao.getMedicationByNationalCodeAndProfile(nationalCode, profileId)
                
                if (savedMedication != null) {
                    // We have a local record. Try to "Upgrade" it by searching online by name.
                    // Crude heuristic: Remove dosage to get brand name
                    val nameToSearch = savedMedication.name.replace(Regex("\\s\\d+.*"), "").trim()
                    
                    if (nameToSearch.isNotBlank()) {
                         val searchResult = drugRepository.searchMedications(nameToSearch)
                         val matches = searchResult.getOrNull()
                         
                         if (!matches.isNullOrEmpty()) {
                             val match = matches.first()
                             // Search result might be incomplete. Fetch full details.
                             val fullDetails = if (match.nationalCode != null) {
                                 drugRepository.getMedicationByNationalCode(match.nationalCode).getOrNull()
                             } else {
                                 drugRepository.getMedicationByRegistrationNumber(match.registrationNumber).getOrNull()
                             }
                             
                             officialMedication = fullDetails ?: match
                             
                             // MIGRATION / DEDUPLICATION LOGIC
                             if (officialMedication.nationalCode != null) {
                                 val newCN = officialMedication.nationalCode!!
                                 
                                 // Check target for duplicate
                                 val targetEntity = medicationDao.getMedicationByNationalCodeAndProfile(newCN, profileId)
                                 
                                 if (targetEntity != null && targetEntity.id != savedMedication.id) {
                                     // Merge: Delete duplicate target
                                     medicationDao.deleteMedication(targetEntity)
                                 }
                                 
                                 // Update Source to New CN
                                 val updatedEntity = savedMedication.copy(
                                     nationalCode = newCN,
                                     name = officialMedication.name,
                                     description = officialMedication.activeIngredients.joinToString(", ") { "${it.name} ${it.quantity}${it.unit}" }
                                 )
                                 medicationDao.updateMedication(updatedEntity)
                                 
                                 _events.trySend(LeafletEvent.ShowToast("Datos actualizados automáticamente.")) // Inform user
                                 
                                 // Update Reference ID in UI
                                 _uiState.value = _uiState.value.copy(savedMedicationId = savedMedication.id)
                             }
                         }
                    }
                }
            }

            // 3. Process Result (Official or Fallback)
            if (officialMedication != null) {
                _uiState.value = _uiState.value.copy(medication = officialMedication)
                
                // ... (rest of logic: check saved status, AI parsing) ...
                // Re-verify saved status using Real CN (in case we just updated it or loaded fresh)
                val currentCN = officialMedication.nationalCode ?: nationalCode
                loadSavedMedicationInfo(currentCN) 
                
                // Try loading HTML and parsing with AI
                val htmlResult = drugRepository.getLeafletHtml(officialMedication)
                // ... (Keep existing AI logic) ...
                if (htmlResult.isSuccess) {
                     // ...
                     val html = htmlResult.getOrThrow()
                    _uiState.value = _uiState.value.copy(isAiProcessing = true)
                    
                    try {
                        aiLeafletParser.parseFlow(html, officialMedication.registrationNumber).collect { parsed ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                parsedLeaflet = parsed
                            )
                        }
                    } finally {
                        _uiState.value = _uiState.value.copy(isAiProcessing = false)
                    }
                } else {
                     // Fallback legacy
                     val legacyResult = drugRepository.getLeaflet(officialMedication)
                     if (legacyResult.isSuccess) {
                         val leaflet = legacyResult.getOrThrow()
                         val convertedSections = leaflet.sections.map { 
                             ParsedSection(title = it.title, content = listOf(ContentBlock.Paragraph(android.text.Html.fromHtml(it.content, android.text.Html.FROM_HTML_MODE_COMPACT).toString()))) 
                         }
                         _uiState.value = _uiState.value.copy(isLoading = false, parsedLeaflet = ParsedLeaflet(convertedSections))
                     } else {
                         _uiState.value = _uiState.value.copy(isLoading = false, error = legacyResult.exceptionOrNull()?.message)
                     }
                }
            } else {
                // FINAL FALLBACK: Local Data Only (if search failed)
                val savedMedication = medicationDao.getMedicationByNationalCodeAndProfile(nationalCode, profileId)
                if (savedMedication != null) {
                    val fallbackMedication = Medication(
                        registrationNumber = "00000",
                        name = savedMedication.name,
                        laboratory = "Importado",
                        prescriptionRequired = false,
                        affectsDriving = false,
                        hasWarningTriangle = false,
                        activeIngredients = emptyList(),
                        leafletUrl = null,
                        photoUrl = null,
                        nationalCode = savedMedication.nationalCode
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        medication = fallbackMedication,
                        savedMedicationId = savedMedication.id,
                        isLoading = false,
                        error = null, // Not correct to show error if we display local data
                        parsedLeaflet = ParsedLeaflet(
                            sections = listOf(
                                ParsedSection(
                                    title = "Información Limitada",
                                    content = listOf(ContentBlock.Paragraph("Medicamento importado. No se han encontrado datos oficiales en CIMA."))
                                )
                            )
                        )
                    )
                    loadSavedMedicationInfo(nationalCode)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Medicamento no encontrado"
                    )
                }
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
    
    
    fun refreshReminders() {
        viewModelScope.launch {
            val savedMedicationId = _uiState.value.savedMedicationId ?: return@launch
            val reminders = reminderRepository.getRemindersForMedication(savedMedicationId).first()
            _uiState.value = _uiState.value.copy(myDosages = reminders)
        }
    }
}
