package com.samcod3.meditrack.domain.model

import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.data.local.entity.ReminderEntity
import com.samcod3.meditrack.data.local.entity.ProfileEntity

/**
 * Complete backup of a profile's data.
 */
data class ProfileBackup(
    val version: Int = 1,
    val exportDate: Long = System.currentTimeMillis(),
    val profile: ProfileData,
    val medications: List<MedicationBackup>
)

data class ProfileData(
    val name: String,
    val notes: String? = null
)

data class MedicationBackup(
    val nationalCode: String,
    val name: String,
    val description: String?,
    val notes: String?,
    val active: Boolean,
    val reminders: List<ReminderBackup>
)

data class ReminderBackup(
    val hour: Int,
    val minute: Int,
    val scheduleType: String,
    val daysOfWeek: Int,
    val intervalDays: Int,
    val dayOfMonth: Int,
    val dosageQuantity: Int,
    val dosageType: String,
    val dosagePortion: String?,
    val enabled: Boolean
)

/**
 * Extension functions for converting entities to backup models.
 */
fun MedicationEntity.toBackup(reminders: List<ReminderEntity>): MedicationBackup {
    return MedicationBackup(
        nationalCode = this.nationalCode,
        name = this.name,
        description = this.description,
        notes = this.notes,
        active = this.active,
        reminders = reminders.map { it.toBackup() }
    )
}

fun ReminderEntity.toBackup(): ReminderBackup {
    return ReminderBackup(
        hour = this.hour,
        minute = this.minute,
        scheduleType = this.scheduleType,
        daysOfWeek = this.daysOfWeek,
        intervalDays = this.intervalDays,
        dayOfMonth = this.dayOfMonth,
        dosageQuantity = this.dosageQuantity,
        dosageType = this.dosageType,
        dosagePortion = this.dosagePortion,
        enabled = this.enabled
    )
}

fun ProfileEntity.toProfileData(): ProfileData {
    return ProfileData(
        name = this.name,
        notes = null // ProfileEntity doesn't have notes field
    )
}
