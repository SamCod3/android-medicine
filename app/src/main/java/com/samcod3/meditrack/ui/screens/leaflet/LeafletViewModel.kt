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
import com.samcod3.meditrack.domain.usecase.SectionSummaryUseCase
import kotlinx.coroutines.Job
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

/**
 * State for viewing a single section with its AI summary.
 */
data class SectionViewState(
    val sectionIndex: Int = -1,
    val section: ParsedSection? = null,
    val summary: String? = null,
    val isLoadingSummary: Boolean = false,
    val summaryError: String? = null,
    val showFullContent: Boolean = false,
    // Retry state - to show progress and allow cancellation when stuck
    val retryAttempt: Int = 0,  // 0 = first attempt, 1-3 = retry number
    val isRetrying: Boolean = false  // True when waiting for retry after BUSY error
)

data class LeafletUiState(
    val isLoading: Boolean = true,
    val medication: Medication? = null,
    val sections: List<LeafletSection> = emptyList(),
    val myDosages: List<Reminder> = emptyList(),
    val savedMedicationId: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val parsedLeaflet: ParsedLeaflet? = null,
    val isAiProcessing: Boolean = false,
    // Section view state
    val sectionViewState: SectionViewState = SectionViewState()
)

class LeafletViewModel(
    private val nationalCode: String,
    private val profileId: String,
    private val drugRepository: DrugRepository,
    private val userMedicationRepository: UserMedicationRepository,
    private val reminderRepository: ReminderRepository,
    private val medicationDao: MedicationDao,
    private val aiLeafletParser: AILeafletParser,
    private val sectionSummaryUseCase: SectionSummaryUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LeafletUiState())
    val uiState: StateFlow<LeafletUiState> = _uiState.asStateFlow()
    
    private val _events = Channel<LeafletEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    
    // Job for current summary generation - cancellable
    private var summaryJob: Job? = null
    // Track which section is currently being generated
    private var currentSummarySection: Int = -1
    
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
    
    // ==================== Section Summary Functions ====================
    
    /**
     * Select a section to view its summary.
     * If summary not cached, triggers AI generation.
     * If selecting same section that's already generating, just shows the panel.
     * If selecting different section, cancels previous and starts new.
     */
    fun selectSection(sectionIndex: Int) {
        val sections = _uiState.value.parsedLeaflet?.sections ?: return
        val section = sections.getOrNull(sectionIndex) ?: return
        
        // Check if we're already generating this section
        val isAlreadyGenerating = summaryJob?.isActive == true && currentSummarySection == sectionIndex
        
        if (isAlreadyGenerating) {
            android.util.Log.d("LeafletSummary", "⏳ Section $sectionIndex already generating - just showing panel")
            // Just update UI to show the panel, job will complete
            _uiState.value = _uiState.value.copy(
                sectionViewState = SectionViewState(
                    sectionIndex = sectionIndex,
                    section = section,
                    isLoadingSummary = true
                )
            )
            return
        }
        
        // Cancel any previous summary job for DIFFERENT section
        if (summaryJob?.isActive == true) {
            android.util.Log.d("LeafletSummary", "🛑 CANCELLED section $currentSummarySection - switching to section $sectionIndex")
            summaryJob?.cancel()
        }
        summaryJob = null
        currentSummarySection = sectionIndex
        
        _uiState.value = _uiState.value.copy(
            sectionViewState = SectionViewState(
                sectionIndex = sectionIndex,
                section = section,
                isLoadingSummary = true
            )
        )
        
        // Generate summary
        generateSummaryForSection(sectionIndex, section)
    }
    
    /**
     * Clear the selected section (go back to section list).
     * Cancels if retrying (stuck in BUSY), otherwise lets it complete in background.
     */
    fun clearSelectedSection() {
        val currentState = _uiState.value.sectionViewState
        
        // If we're retrying (BUSY state), cancel - it's likely stuck
        if (currentState.isRetrying && summaryJob?.isActive == true) {
            android.util.Log.d("LeafletSummary", "🛑 CANCELLED - was stuck in retry mode")
            summaryJob?.cancel()
            summaryJob = null
        }
        // Otherwise let it complete in background and cache result
        
        _uiState.value = _uiState.value.copy(
            sectionViewState = SectionViewState()
        )
    }
    
    /**
     * Toggle between showing summary and full content.
     */
    fun toggleFullContent() {
        val current = _uiState.value.sectionViewState
        _uiState.value = _uiState.value.copy(
            sectionViewState = current.copy(showFullContent = !current.showFullContent)
        )
    }
    
    /**
     * Regenerate summary for current section.
     * Delegates to refineSummary with REGENERATE mode.
     */
    fun regenerateSummary() {
        refineSummary(com.samcod3.meditrack.domain.model.RefinementMode.REGENERATE)
    }
    
    /**
     * Generate a refined summary using a specific mode.
     * Each mode produces a different type of summary.
     */
    fun refineSummary(mode: com.samcod3.meditrack.domain.model.RefinementMode) {
        val current = _uiState.value.sectionViewState
        val medication = _uiState.value.medication ?: return
        val section = current.section ?: return
        
        android.util.Log.d("LeafletSummary", "🔧 Refining section ${current.sectionIndex} with mode: ${mode.name}")
        
        // Cancel any ongoing generation
        summaryJob?.cancel()
        
        // Reset state to loading
        _uiState.value = _uiState.value.copy(
            sectionViewState = current.copy(
                summary = null,
                summaryError = null,
                isLoadingSummary = true,
                isRetrying = false,
                retryAttempt = 0
            )
        )
        
        summaryJob = viewModelScope.launch {
            // Convert section content to text
            val contentText = section.content.joinToString("\n") { block ->
                when (block) {
                    is ContentBlock.Paragraph -> block.text
                    is ContentBlock.BulletItem -> "• ${block.text}"
                    is ContentBlock.NumberedItem -> "${block.number}. ${block.text}"
                    is ContentBlock.Bold -> block.text
                    is ContentBlock.Italic -> block.text
                    is ContentBlock.SubHeading -> block.text
                }
            }
            
            // Start a parallel job to show "possibly retrying" after 5 seconds
            val slowWarningJob = viewModelScope.launch {
                var attempt = 1
                while (true) {
                    kotlinx.coroutines.delay(5000) // 5 seconds
                    val currentState = _uiState.value.sectionViewState
                    if (currentState.sectionIndex == current.sectionIndex && currentState.isLoadingSummary) {
                        android.util.Log.d("LeafletSummary", "⏳ Refine slow warning - attempt $attempt")
                        _uiState.value = _uiState.value.copy(
                            sectionViewState = currentState.copy(
                                isRetrying = true,
                                retryAttempt = attempt
                            )
                        )
                        attempt++
                    }
                }
            }
            
            val result = sectionSummaryUseCase.generateRefinedSummary(
                registrationNumber = medication.registrationNumber,
                sectionNumber = current.sectionIndex,
                sectionTitle = section.title,
                sectionContent = contentText,
                mode = mode
            )
            
            // Cancel slow warning job
            slowWarningJob.cancel()
            
            // Check if panel is still open for this section
            val panelOpen = _uiState.value.sectionViewState.sectionIndex == current.sectionIndex
            
            if (result.isSuccess && panelOpen) {
                _uiState.value = _uiState.value.copy(
                    sectionViewState = _uiState.value.sectionViewState.copy(
                        summary = result.getOrNull(),
                        isLoadingSummary = false,
                        isRetrying = false
                    )
                )
            } else if (panelOpen) {
                _uiState.value = _uiState.value.copy(
                    sectionViewState = _uiState.value.sectionViewState.copy(
                        summaryError = result.exceptionOrNull()?.message ?: "Error generando resumen",
                        isLoadingSummary = false,
                        isRetrying = false
                    )
                )
            }
        }
    }
    
    /**
     * Generate AI summary for a section.
     * Stores job reference so it can be cancelled if user changes section.
     */
    private fun generateSummaryForSection(sectionIndex: Int, section: ParsedSection) {
        android.util.Log.d("LeafletSummary", "📝 STARTED section $sectionIndex: ${section.title}")
        
        summaryJob = viewModelScope.launch {
            val medication = _uiState.value.medication ?: return@launch
            val registrationNumber = medication.registrationNumber
            
            // Convert ContentBlocks to plain text for summarization
            val contentText = section.content.joinToString("\n") { block ->
                when (block) {
                    is ContentBlock.Paragraph -> block.text
                    is ContentBlock.BulletItem -> "• ${block.text}"
                    is ContentBlock.NumberedItem -> "${block.number}. ${block.text}"
                    is ContentBlock.Bold -> block.text
                    is ContentBlock.Italic -> block.text
                    is ContentBlock.SubHeading -> block.text
                }
            }
            
            android.util.Log.d("LeafletSummary", "🔄 CALLING UseCase for section $sectionIndex (${contentText.length} chars)")
            
            // Start a parallel job to show "possibly retrying" after 5 seconds
            val slowWarningJob = viewModelScope.launch {
                var attempt = 1
                while (true) {
                    kotlinx.coroutines.delay(5000) // 5 seconds
                    val currentState = _uiState.value.sectionViewState
                    if (currentState.sectionIndex == sectionIndex && currentState.isLoadingSummary) {
                        android.util.Log.d("LeafletSummary", "⏳ Slow warning - updating to retry attempt $attempt")
                        _uiState.value = _uiState.value.copy(
                            sectionViewState = currentState.copy(
                                isRetrying = true,
                                retryAttempt = attempt
                            )
                        )
                        attempt++
                    }
                }
            }
            
            try {
                val result = sectionSummaryUseCase.getSectionSummary(
                    registrationNumber = registrationNumber,
                    sectionNumber = sectionIndex,
                    sectionTitle = section.title,
                    sectionContent = contentText
                )
                
                val panelOpen = _uiState.value.sectionViewState.sectionIndex == sectionIndex
                android.util.Log.d("LeafletSummary", "✅ FINISHED section $sectionIndex - Success: ${result.isSuccess}, Panel open: $panelOpen")
                
                if (result.isSuccess) {
                    val currentState = _uiState.value.sectionViewState
                    // Only update if still viewing the same section
                    if (currentState.sectionIndex == sectionIndex) {
                        _uiState.value = _uiState.value.copy(
                            sectionViewState = currentState.copy(
                                summary = result.getOrNull(),
                                isLoadingSummary = false,
                                isRetrying = false,
                                retryAttempt = 0
                            )
                        )
                        android.util.Log.d("LeafletSummary", "📱 UI updated with summary for section $sectionIndex")
                    } else {
                        android.util.Log.d("LeafletSummary", "💾 Cached but panel closed - section $sectionIndex result not shown")
                    }
                } else {
                    android.util.Log.e("LeafletSummary", "❌ ERROR section $sectionIndex: ${result.exceptionOrNull()?.message}")
                    val currentState = _uiState.value.sectionViewState
                    if (currentState.sectionIndex == sectionIndex) {
                        _uiState.value = _uiState.value.copy(
                            sectionViewState = currentState.copy(
                                summaryError = result.exceptionOrNull()?.message ?: "Error generando resumen",
                                isLoadingSummary = false,
                                isRetrying = false,
                                retryAttempt = 0
                            )
                        )
                    }
                }
            } finally {
                // Cancel the slow warning job when done
                slowWarningJob.cancel()
            }
        }
    }
}
