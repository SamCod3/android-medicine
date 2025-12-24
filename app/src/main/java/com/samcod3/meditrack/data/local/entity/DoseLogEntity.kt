package com.samcod3.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity for logging medication dose events.
 * Tracks when a scheduled reminder was taken, skipped, or missed.
 */
@Entity(
    tableName = "dose_logs",
    indices = [
        Index(value = ["reminderId"]),
        Index(value = ["scheduledDate"]),
        Index(value = ["profileId", "scheduledDate"])
    ]
)
data class DoseLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val reminderId: String,
    val medicationId: String,
    val medicationName: String,
    val profileId: String,
    val scheduledDate: Long,    // Date only (start of day) for easy querying
    val scheduledHour: Int,     // 0-23
    val scheduledMinute: Int,   // 0-59
    val status: String,         // "TAKEN", "SKIPPED"
    val loggedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_TAKEN = "TAKEN"
        const val STATUS_SKIPPED = "SKIPPED"
    }
}
