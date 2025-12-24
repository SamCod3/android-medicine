package com.samcod3.meditrack.data.repository

import com.samcod3.meditrack.data.local.dao.MedicationDao
import com.samcod3.meditrack.data.local.dao.ReminderDao
import com.samcod3.meditrack.data.local.entity.DosageType
import com.samcod3.meditrack.data.local.entity.Portion
import com.samcod3.meditrack.data.local.entity.ReminderEntity
import com.samcod3.meditrack.data.local.entity.ScheduleType
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

    override fun getEnabledRemindersForProfile(profileId: String): Flow<List<Reminder>> {
        return reminderDao.getEnabledRemindersForProfile(profileId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getAllRemindersForProfile(profileId: String): Flow<List<Reminder>> {
        return reminderDao.getAllRemindersForProfile(profileId).map { entities ->
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
        scheduleType: ScheduleType,
        daysOfWeek: Int,
        intervalDays: Int,
        dayOfMonth: Int,
        dosageQuantity: Int,
        dosageType: DosageType,
        dosagePortion: Portion?
    ) {
        val entity = ReminderEntity(
            medicationId = medicationId,
            hour = hour,
            minute = minute,
            scheduleType = scheduleType.name,
            daysOfWeek = daysOfWeek,
            intervalDays = intervalDays,
            dayOfMonth = dayOfMonth,
            dosageQuantity = dosageQuantity,
            dosageType = dosageType.name,
            dosagePortion = dosagePortion?.name
        )
        reminderDao.insert(entity)
    }
    
    override suspend fun updateReminder(reminder: Reminder) {
        val entity = ReminderEntity(
            id = reminder.id,
            medicationId = reminder.medicationId,
            hour = reminder.hour,
            minute = reminder.minute,
            scheduleType = reminder.scheduleType.name,
            daysOfWeek = reminder.daysOfWeek,
            intervalDays = reminder.intervalDays,
            dayOfMonth = reminder.dayOfMonth,
            startDate = reminder.startDate,
            dosageQuantity = reminder.dosageQuantity,
            dosageType = reminder.dosageType.name,
            dosagePortion = reminder.dosagePortion?.name,
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

    override suspend fun moveReminders(fromMedId: String, toMedId: String) {
        reminderDao.moveReminders(fromMedId, toMedId)
    }
    
    private suspend fun ReminderEntity.toDomain(): Reminder {
        val medication = medicationDao.getMedicationById(medicationId)
        return Reminder(
            id = id,
            medicationId = medicationId,
            medicationName = medication?.name ?: "",
            nationalCode = medication?.nationalCode ?: "",
            hour = hour,
            minute = minute,
            scheduleType = ScheduleType.entries.find { it.name == scheduleType } ?: ScheduleType.DAILY,
            daysOfWeek = daysOfWeek,
            intervalDays = intervalDays,
            dayOfMonth = dayOfMonth,
            startDate = startDate,
            dosageQuantity = dosageQuantity,
            dosageType = DosageType.entries.find { it.name == dosageType } ?: DosageType.COMPRIMIDO,
            dosagePortion = dosagePortion?.let { p -> Portion.entries.find { it.name == p } },
            enabled = enabled
        )
    }
}
