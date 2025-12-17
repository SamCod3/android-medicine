package com.samcod3.meditrack.domain.repository

import com.samcod3.meditrack.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getRemindersForMedication(medicationId: String): Flow<List<Reminder>>
    fun getAllEnabledReminders(): Flow<List<Reminder>>
    suspend fun getReminderById(id: String): Reminder?
    suspend fun createReminder(medicationId: String, hour: Int, minute: Int, daysOfWeek: Int, dosage: String?)
    suspend fun updateReminder(reminder: Reminder)
    suspend fun deleteReminder(id: String)
    suspend fun setReminderEnabled(id: String, enabled: Boolean)
}
