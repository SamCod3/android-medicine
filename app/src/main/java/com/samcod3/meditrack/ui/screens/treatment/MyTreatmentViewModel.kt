package com.samcod3.meditrack.ui.screens.treatment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.data.local.entity.ScheduleType
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.model.SavedMedication
import com.samcod3.meditrack.domain.repository.ReminderRepository
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import com.samcod3.meditrack.domain.usecase.BackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Groups reminders by schedule type for the "My Treatment" overview.
 */
data class TreatmentGrouped(
    val daily: List<Reminder> = emptyList(),
    val weekly: Map<String, List<Reminder>> = emptyMap(), // Grouped by formatted days
    val monthly: List<Reminder> = emptyList(),
    val interval: List<Reminder> = emptyList(),
    val unscheduled: List<SavedMedication> = emptyList()
) {
    val totalCount: Int
        get() = daily.size + weekly.values.sumOf { it.size } + monthly.size + interval.size + unscheduled.size
}

class MyTreatmentViewModel(
    private val profileId: String,
    private val reminderRepository: ReminderRepository,
    private val userMedicationRepository: UserMedicationRepository,
    private val backupUseCase: BackupUseCase
) : ViewModel() {
    
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    
    // Backup state
    private val _backupJson = MutableStateFlow<String?>(null)
    val backupJson: StateFlow<String?> = _backupJson.asStateFlow()
    
    private val _backupError = MutableStateFlow<String?>(null)
    val backupError: StateFlow<String?> = _backupError.asStateFlow()
    
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
            
        // Find medications that don't have any ACTIVE reminders
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
     * Export current profile to JSON for backup
     */
    fun exportBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = backupUseCase.exportProfile(profileId)
            if (result.isSuccess) {
                val json = result.getOrThrow()
                _backupJson.value = json
                onResult(json)
            } else {
                _backupError.value = result.exceptionOrNull()?.message ?: "Export failed"
            }
        }
    }
    
    /**
     * Import from JSON backup
     */
    fun importBackup(jsonString: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = backupUseCase.importToProfile(profileId, jsonString)
            _isImporting.value = false
            _isImporting.value = false
            
            if (result.isSuccess) {
                onResult(result.getOrThrow())
            } else {
                _backupError.value = result.exceptionOrNull()?.message ?: "Import failed"
                onResult(0)
            }
        }
    }
    
    fun clearBackupState() {
        _backupJson.value = null
        _backupError.value = null
    }
}

