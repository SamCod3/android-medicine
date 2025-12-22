package com.samcod3.meditrack.domain.repository

import com.samcod3.meditrack.data.local.entity.DosageType
import com.samcod3.meditrack.data.local.entity.Portion
import com.samcod3.meditrack.data.local.entity.ScheduleType
import com.samcod3.meditrack.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getRemindersForMedication(medicationId: String): Flow<List<Reminder>>
    fun getAllEnabledReminders(): Flow<List<Reminder>>
    fun getEnabledRemindersForProfile(profileId: String): Flow<List<Reminder>>
    suspend fun getReminderById(id: String): Reminder?
    suspend fun createReminder(
        medicationId: String, 
        hour: Int, 
        minute: Int, 
        // Schedule options
        scheduleType: ScheduleType,
        daysOfWeek: Int,
        intervalDays: Int,
        dayOfMonth: Int,
        // Dosage options
        dosageQuantity: Int,
        dosageType: DosageType,
        dosagePortion: Portion?
    )
    suspend fun updateReminder(reminder: Reminder)
    suspend fun deleteReminder(id: String)
    suspend fun setReminderEnabled(id: String, enabled: Boolean)
    suspend fun moveReminders(fromMedId: String, toMedId: String)
}
