package com.samcod3.meditrack.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog
    
    fun showAddReminderDialog() {
        _showAddDialog.value = true
    }
    
    fun hideAddReminderDialog() {
        _showAddDialog.value = false
    }
    
    fun addReminder(hour: Int, minute: Int, daysOfWeek: Int, dosage: String?) {
        viewModelScope.launch {
            reminderRepository.createReminder(
                medicationId = medicationId,
                hour = hour,
                minute = minute,
                daysOfWeek = daysOfWeek,
                dosage = dosage
            )
            _showAddDialog.value = false
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
