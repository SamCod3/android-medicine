package com.samcod3.meditrack.ui.screens.treatment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samcod3.meditrack.data.local.entity.ScheduleType
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Groups reminders by schedule type for the "My Treatment" overview.
 */
data class TreatmentGrouped(
    val daily: List<Reminder> = emptyList(),
    val weekly: Map<String, List<Reminder>> = emptyMap(), // Grouped by formatted days
    val monthly: List<Reminder> = emptyList(),
    val interval: List<Reminder> = emptyList()
) {
    val totalCount: Int
        get() = daily.size + weekly.values.sumOf { it.size } + monthly.size + interval.size
}

class MyTreatmentViewModel(
    private val reminderRepository: ReminderRepository
) : ViewModel() {
    
    val treatment: StateFlow<TreatmentGrouped> = reminderRepository
        .getAllEnabledReminders()
        .map { reminders ->
            val daily = reminders
                .filter { it.scheduleType == ScheduleType.DAILY }
                .sortedWith(compareBy({ it.hour }, { it.minute }))
            
            val weekly = reminders
                .filter { it.scheduleType == ScheduleType.WEEKLY }
                .sortedWith(compareBy({ it.hour }, { it.minute }))
                .groupBy { it.scheduleFormatted } // Group by "L M X" or "J V" etc.
            
            val monthly = reminders
                .filter { it.scheduleType == ScheduleType.MONTHLY }
                .sortedWith(compareBy({ it.dayOfMonth }, { it.hour }, { it.minute }))
            
            val interval = reminders
                .filter { it.scheduleType == ScheduleType.INTERVAL }
                .sortedWith(compareBy({ it.intervalDays }, { it.hour }, { it.minute }))
            
            TreatmentGrouped(
                daily = daily,
                weekly = weekly,
                monthly = monthly,
                interval = interval
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TreatmentGrouped()
        )
}
