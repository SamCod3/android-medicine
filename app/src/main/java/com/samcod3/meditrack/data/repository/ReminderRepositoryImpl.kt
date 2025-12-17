package com.samcod3.meditrack.data.repository

import com.samcod3.meditrack.data.local.dao.MedicationDao
import com.samcod3.meditrack.data.local.dao.ReminderDao
import com.samcod3.meditrack.data.local.entity.ReminderEntity
import com.samcod3.meditrack.domain.model.Reminder
import com.samcod3.meditrack.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReminderRepositoryImpl(
    private val reminderDao: ReminderDao,
    private val medicationDao: MedicationDao
) : ReminderRepository {
    
    override fun getRemindersForMedication(medicationId: String): Flow<List<Reminder>> {
        return reminderDao.getRemindersForMedication(medicationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getAllEnabledReminders(): Flow<List<Reminder>> {
        return reminderDao.getAllEnabledReminders().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getReminderById(id: String): Reminder? {
        return reminderDao.getReminderById(id)?.toDomain()
    }
    
    override suspend fun createReminder(
        medicationId: String,
        hour: Int,
        minute: Int,
        daysOfWeek: Int,
        dosage: String?
    ) {
        val entity = ReminderEntity(
            medicationId = medicationId,
            hour = hour,
            minute = minute,
            daysOfWeek = daysOfWeek,
            dosage = dosage
        )
        reminderDao.insert(entity)
    }
    
    override suspend fun updateReminder(reminder: Reminder) {
        val entity = ReminderEntity(
            id = reminder.id,
            medicationId = reminder.medicationId,
            hour = reminder.hour,
            minute = reminder.minute,
            daysOfWeek = reminder.daysOfWeek,
            dosage = reminder.dosage,
            enabled = reminder.enabled
        )
        reminderDao.update(entity)
    }
    
    override suspend fun deleteReminder(id: String) {
        reminderDao.deleteById(id)
    }
    
    override suspend fun setReminderEnabled(id: String, enabled: Boolean) {
        reminderDao.setEnabled(id, enabled)
    }
    
    private suspend fun ReminderEntity.toDomain(): Reminder {
        val medication = medicationDao.getMedicationById(medicationId)
        return Reminder(
            id = id,
            medicationId = medicationId,
            medicationName = medication?.name ?: "",
            hour = hour,
            minute = minute,
            daysOfWeek = daysOfWeek,
            dosage = dosage,
            enabled = enabled
        )
    }
}
