package com.samcod3.meditrack.ui.screens.allreminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.data.local.dao.DoseLogDao
import com.samcod3.meditrack.data.local.entity.DoseLogEntity
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for the global reminders screen.
 * Shows all enabled reminders grouped by time with Pending/Past tabs.
 */
class AllRemindersViewModel(
    private val profileId: String,
    private val reminderRepository: ReminderRepository,
    private val doseLogDao: DoseLogDao
) : ViewModel() {
    
    // Current time for UI refresh
    private val _currentTime = MutableStateFlow(getCurrentTimeMinutes())
    
    // Logs for today
    private val todayLogs = doseLogDao.getLogsForDate(profileId, getStartOfDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
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
                reminder.enabled && when (reminder.scheduleType) {
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
    
    /**
     * Pendientes: hora >= ahora, no marcados
     */
    val pendingReminders: StateFlow<Map<String, List<Reminder>>> = combine(
        todayReminders,
        todayLogs,
        _currentTime
    ) { reminders, logs, currentMinutes ->
        val loggedKeys = logs.map { "${it.reminderId}_${it.scheduledHour}_${it.scheduledMinute}" }.toSet()
        reminders
            .filter { r -> 
                val reminderMinutes = r.hour * 60 + r.minute
                reminderMinutes >= currentMinutes && 
                    "${r.id}_${r.hour}_${r.minute}" !in loggedKeys
            }
            .groupBy { it.timeFormatted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    /**
     * Pasados: hora < ahora, no marcados
     */
    val pastReminders: StateFlow<Map<String, List<Reminder>>> = combine(
        todayReminders,
        todayLogs,
        _currentTime
    ) { reminders, logs, currentMinutes ->
        val loggedKeys = logs.map { "${it.reminderId}_${it.scheduledHour}_${it.scheduledMinute}" }.toSet()
        reminders
            .filter { r -> 
                val reminderMinutes = r.hour * 60 + r.minute
                reminderMinutes < currentMinutes && 
                    "${r.id}_${r.hour}_${r.minute}" !in loggedKeys
            }
            .groupBy { it.timeFormatted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    fun markDose(reminder: Reminder, status: String) {
        viewModelScope.launch {
            val log = DoseLogEntity(
                reminderId = reminder.id,
                medicationId = reminder.medicationId,
                medicationName = reminder.medicationName,
                profileId = profileId,
                scheduledDate = getStartOfDay(),
                scheduledHour = reminder.hour,
                scheduledMinute = reminder.minute,
                status = status
            )
            doseLogDao.insert(log)
        }
    }
    
    fun refreshTime() {
        _currentTime.value = getCurrentTimeMinutes()
    }
    
    fun toggleReminder(reminderId: String, enabled: Boolean) {
        viewModelScope.launch {
            reminderRepository.setReminderEnabled(reminderId, enabled)
        }
    }
    
    private val _expandedSections = MutableStateFlow<Set<String>>(emptySet())
    val expandedSections: StateFlow<Set<String>> = _expandedSections

    fun toggleSection(time: String) {
        val current = _expandedSections.value
        if (current.contains(time)) {
             _expandedSections.value = current - time
        } else {
             _expandedSections.value = current + time
        }
    }

    fun initExpandedSection(time: String) {
        if (_expandedSections.value.isEmpty()) {
            _expandedSections.value = setOf(time)
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(reminderId)
        }
    }
    
    private fun getCurrentTimeMinutes(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }
    
    private fun getStartOfDay(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
