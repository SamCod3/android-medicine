package com.samcod3.meditrack.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.data.local.entity.DosageType
import com.samcod3.meditrack.data.local.entity.Portion
import com.samcod3.meditrack.data.local.entity.ScheduleType
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(
    private val medicationId: String,
    private val medicationName: String,
    private val reminderRepository: ReminderRepository
) : ViewModel() {
    
    val reminders: StateFlow<List<Reminder>> = reminderRepository
        .getRemindersForMedication(medicationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog
    
    private val _editingReminder = MutableStateFlow<Reminder?>(null)
    val editingReminder: StateFlow<Reminder?> = _editingReminder
    
    fun showAddDialog() {
        _editingReminder.value = null
        _showDialog.value = true
    }
    
    fun showEditDialog(reminder: Reminder) {
        _editingReminder.value = reminder
        _showDialog.value = true
    }
    
    fun hideDialog() {
        _showDialog.value = false
        _editingReminder.value = null
    }
    
    fun saveReminder(
        hour: Int, 
        minute: Int,
        scheduleType: ScheduleType,
        daysOfWeek: Int,
        intervalDays: Int,
        dayOfMonth: Int,
        dosageQuantity: Int,
        dosageType: DosageType,
        dosagePortion: Portion?
    ) {
        viewModelScope.launch {
            val editing = _editingReminder.value
            if (editing != null) {
                // Update existing
                reminderRepository.updateReminder(
                    editing.copy(
                        hour = hour,
                        minute = minute,
                        scheduleType = scheduleType,
                        daysOfWeek = daysOfWeek,
                        intervalDays = intervalDays,
                        dayOfMonth = dayOfMonth,
                        dosageQuantity = dosageQuantity,
                        dosageType = dosageType,
                        dosagePortion = dosagePortion
                    )
                )
            } else {
                // Create new
                reminderRepository.createReminder(
                    medicationId = medicationId,
                    hour = hour,
                    minute = minute,
                    scheduleType = scheduleType,
                    daysOfWeek = daysOfWeek,
                    intervalDays = intervalDays,
                    dayOfMonth = dayOfMonth,
                    dosageQuantity = dosageQuantity,
                    dosageType = dosageType,
                    dosagePortion = dosagePortion
                )
            }
            hideDialog()
        }
    }
    
    fun toggleReminder(reminderId: String, enabled: Boolean) {
        viewModelScope.launch {
            reminderRepository.setReminderEnabled(reminderId, enabled)
        }
    }
    
    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(reminderId)
        }
    }
}
