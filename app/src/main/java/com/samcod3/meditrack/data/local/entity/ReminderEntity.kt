package com.samcod3.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity for medication reminders/alarms.
 * Each reminder represents a scheduled dose time for a medication.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"])]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val hour: Int,           // 0-23
    val minute: Int,         // 0-59
    val daysOfWeek: Int,     // Bitmask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64, 0=every day
    val dosage: String?,     // e.g., "1 comprimido", "5ml"
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MONDAY = 1
        const val TUESDAY = 2
        const val WEDNESDAY = 4
        const val THURSDAY = 8
        const val FRIDAY = 16
        const val SATURDAY = 32
        const val SUNDAY = 64
        const val EVERY_DAY = 0
        
        fun isDayEnabled(daysOfWeek: Int, dayFlag: Int): Boolean {
            return if (daysOfWeek == EVERY_DAY) true
            else (daysOfWeek and dayFlag) != 0
        }
    }
}
