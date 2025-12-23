package com.samcod3.meditrack.ui.screens.treatment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.data.local.entity.ScheduleType
import com.samcod3.meditrack.domain.model.BackupImportResult
import com.samcod3.meditrack.domain.model.ImportStrategy
import com.samcod3.meditrack.domain.model.ProfileBackup
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.model.SavedMedication
import com.samcod3.meditrack.domain.model.VersionValidation
import com.samcod3.meditrack.domain.repository.ReminderRepository
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import com.samcod3.meditrack.domain.usecase.BackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Groups reminders by schedule type for the "My Treatment" overview.
 */
data class TreatmentGrouped(
    val daily: List<Reminder> = emptyList(),
    val weekly: Map<String, List<Reminder>> = emptyMap(),
    val monthly: List<Reminder> = emptyList(),
    val interval: List<Reminder> = emptyList(),
    val unscheduled: List<SavedMedication> = emptyList()
) {
    val totalCount: Int
        get() = daily.size + weekly.values.sumOf { it.size } + monthly.size + interval.size + unscheduled.size
}

/**
 * UI state for backup preview dialog.
 */
data class BackupPreview(
    val profileName: String,
    val medicationCount: Int,
    val reminderCount: Int,
    val exportDate: Long,
    val version: Int,
    val validation: VersionValidation
)

class MyTreatmentViewModel(
    private val profileId: String,
    private val reminderRepository: ReminderRepository,
    private val userMedicationRepository: UserMedicationRepository,
    private val backupUseCase: BackupUseCase
) : ViewModel() {
    
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    
    private val _backupJson = MutableStateFlow<String?>(null)
    val backupJson: StateFlow<String?> = _backupJson.asStateFlow()
    
    private val _backupError = MutableStateFlow<String?>(null)
    val backupError: StateFlow<String?> = _backupError.asStateFlow()
    
    private val _backupPreview = MutableStateFlow<BackupPreview?>(null)
    val backupPreview: StateFlow<BackupPreview?> = _backupPreview.asStateFlow()
    
    private val _importResult = MutableStateFlow<BackupImportResult?>(null)
    val importResult: StateFlow<BackupImportResult?> = _importResult.asStateFlow()
    
    // Pending JSON for import after user confirms
    private var pendingImportJson: String? = null
    
    val treatment: StateFlow<TreatmentGrouped> = combine(
        reminderRepository.getEnabledRemindersForProfile(profileId),
        userMedicationRepository.getMedicationsForProfile(profileId)
    ) { reminders, medications ->
        val daily = reminders
            .filter { it.scheduleType == ScheduleType.DAILY }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
        
        val weekly = reminders
            .filter { it.scheduleType == ScheduleType.WEEKLY }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
            .groupBy { it.scheduleFormatted } 
        
        val monthly = reminders
            .filter { it.scheduleType == ScheduleType.MONTHLY }
            .sortedWith(compareBy({ it.dayOfMonth }, { it.hour }, { it.minute }))
        
        val interval = reminders
            .filter { it.scheduleType == ScheduleType.INTERVAL }
            .sortedWith(compareBy({ it.intervalDays }, { it.hour }, { it.minute }))
            
        val activeMedicationIds = reminders.map { it.medicationId }.toSet()
        val unscheduled = medications.filter { it.id !in activeMedicationIds }
        
        TreatmentGrouped(
            daily = daily,
            weekly = weekly,
            monthly = monthly,
            interval = interval,
            unscheduled = unscheduled
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TreatmentGrouped()
    )
    
    /**
     * Export current profile to JSON for backup (v2 format).
     */
    fun exportBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = backupUseCase.exportProfile(profileId)
            if (result.isSuccess) {
                val json = result.getOrThrow()
                _backupJson.value = json
                onResult(json)
            } else {
                _backupError.value = result.exceptionOrNull()?.message ?: "Error al exportar"
            }
        }
    }
    
    /**
     * Preview backup before importing - validates and shows summary.
     */
    fun previewBackup(jsonString: String) {
        val validation = backupUseCase.validateVersion(jsonString)
        
        if (validation is VersionValidation.Error) {
            _backupError.value = validation.message
            return
        }
        
        val previewResult = backupUseCase.previewBackup(jsonString)
        if (previewResult.isFailure) {
            _backupError.value = previewResult.exceptionOrNull()?.message ?: "JSON inválido"
            return
        }
        
        val backup = previewResult.getOrThrow()
        pendingImportJson = jsonString
        
        _backupPreview.value = BackupPreview(
            profileName = backup.profile.name,
            medicationCount = backup.medications.size,
            reminderCount = backup.medications.sumOf { it.reminders.size },
            exportDate = backup.exportDate,
            version = backup.version,
            validation = validation
        )
    }
    
    /**
     * Confirm import after preview.
     */
    fun confirmImport(strategy: ImportStrategy = ImportStrategy.MERGE_SKIP_EXISTING) {
        val json = pendingImportJson ?: return
        
        viewModelScope.launch {
            _isImporting.value = true
            _backupPreview.value = null
            
            val result = backupUseCase.importToProfile(profileId, json, strategy)
            
            _isImporting.value = false
            pendingImportJson = null
            
            if (result.isSuccess) {
                _importResult.value = result.getOrThrow()
            } else {
                _backupError.value = result.exceptionOrNull()?.message ?: "Error al importar"
            }
        }
    }
    
    /**
     * Cancel pending import.
     */
    fun cancelImport() {
        pendingImportJson = null
        _backupPreview.value = null
    }
    
    /**
     * Legacy import method for backward compatibility.
     * Returns total processed (imported + skipped) to avoid showing "error" when all already exist.
     */
    fun importBackup(jsonString: String, onResult: (BackupImportResult?) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = backupUseCase.importToProfile(profileId, jsonString)
            _isImporting.value = false
            
            if (result.isSuccess) {
                val importResult = result.getOrThrow()
                _importResult.value = importResult
                onResult(importResult)
            } else {
                _backupError.value = result.exceptionOrNull()?.message ?: "Error al importar"
                onResult(null)
            }
        }
    }
    
    fun clearBackupState() {
        _backupJson.value = null
        _backupError.value = null
        _backupPreview.value = null
        _importResult.value = null
        pendingImportJson = null
    }
}
