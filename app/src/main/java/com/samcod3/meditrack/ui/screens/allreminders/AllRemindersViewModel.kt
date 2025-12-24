package com.samcod3.meditrack.ui.screens.allreminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for the global reminders screen.
 * Shows all enabled reminders grouped by time.
 */
class AllRemindersViewModel(
    private val profileId: String,
    private val reminderRepository: ReminderRepository
) : ViewModel() {
    
    /**
     * Enabled reminders for current profile grouped by hour:minute, sorted by time.
     */
    val remindersByTime: StateFlow<Map<String, List<Reminder>>> = reminderRepository
        .getEnabledRemindersForProfile(profileId)
        .map { reminders ->
            reminders
                .sortedWith(compareBy({ it.hour }, { it.minute }))
                .groupBy { it.timeFormatted }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )
    
    /**
     * Total count of enabled reminders for current profile.
     */
    val totalReminders: StateFlow<Int> = reminderRepository
        .getEnabledRemindersForProfile(profileId)
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    /**
     * Get ALL reminders for current profile that should fire today based on schedule type.
     * Includes both enabled and disabled reminders.
     */
    val todayReminders: StateFlow<List<Reminder>> = reminderRepository
        .getAllRemindersForProfile(profileId)
        .map { reminders ->
            val today = Calendar.getInstance()
            val dayOfWeek = when (today.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 4
                Calendar.THURSDAY -> 8
                Calendar.FRIDAY -> 16
                Calendar.SATURDAY -> 32
                Calendar.SUNDAY -> 64
                else -> 0
            }
            val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)
            
            reminders.filter { reminder ->
                when (reminder.scheduleType) {
                    com.samcod3.meditrack.data.local.entity.ScheduleType.DAILY -> true
                    com.samcod3.meditrack.data.local.entity.ScheduleType.WEEKLY -> 
                        (reminder.daysOfWeek and dayOfWeek) != 0
                    com.samcod3.meditrack.data.local.entity.ScheduleType.MONTHLY -> 
                        reminder.dayOfMonth == dayOfMonth
                    com.samcod3.meditrack.data.local.entity.ScheduleType.INTERVAL -> {
                        val startDate = Calendar.getInstance().apply { timeInMillis = reminder.startDate }
                        val daysSinceStart = ((today.timeInMillis - startDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                        daysSinceStart % reminder.intervalDays == 0
                    }
                }
            }.sortedWith(compareBy({ it.hour }, { it.minute }))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
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
