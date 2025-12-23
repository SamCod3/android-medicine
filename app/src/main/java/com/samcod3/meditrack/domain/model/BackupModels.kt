package com.samcod3.meditrack.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Complete backup of a profile's data.
 * Version 2: Full serialization with Moshi, all fields preserved.
 */
@JsonClass(generateAdapter = true)
data class ProfileBackup(
    @Json(name = "version") val version: Int = CURRENT_VERSION,
    @Json(name = "exportDate") val exportDate: Long = System.currentTimeMillis(),
    @Json(name = "appVersion") val appVersion: String = "1.0.0",
    @Json(name = "profile") val profile: ProfileData,
    @Json(name = "medications") val medications: List<MedicationBackup>
) {
    companion object {
        const val CURRENT_VERSION = 2
        const val MIN_SUPPORTED_VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class ProfileData(
    @Json(name = "name") val name: String,
    @Json(name = "notes") val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class MedicationBackup(
    @Json(name = "nationalCode") val nationalCode: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "notes") val notes: String?,
    @Json(name = "active") val active: Boolean = true,
    @Json(name = "startDate") val startDate: Long? = null,
    @Json(name = "reminders") val reminders: List<ReminderBackup>
)

@JsonClass(generateAdapter = true)
data class ReminderBackup(
    @Json(name = "hour") val hour: Int,
    @Json(name = "minute") val minute: Int,
    @Json(name = "scheduleType") val scheduleType: String,
    @Json(name = "daysOfWeek") val daysOfWeek: Int,
    @Json(name = "intervalDays") val intervalDays: Int,
    @Json(name = "dayOfMonth") val dayOfMonth: Int,
    @Json(name = "dosageQuantity") val dosageQuantity: Int,
    @Json(name = "dosageType") val dosageType: String,
    @Json(name = "dosagePortion") val dosagePortion: String?,
    @Json(name = "enabled") val enabled: Boolean = true
)

/**
 * Result of a backup import operation.
 */
data class BackupImportResult(
    val medicationsImported: Int,
    val medicationsSkipped: Int,
    val remindersImported: Int,
    val remindersFailed: Int,
    val warnings: List<String>
) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
    val isSuccess: Boolean get() = medicationsImported > 0 || medicationsSkipped > 0
}

/**
 * Import strategy options.
 */
enum class ImportStrategy {
    REPLACE_ALL,      // Delete existing, import all
    MERGE_SKIP_EXISTING, // Keep existing, add new only  
    MERGE_UPDATE_EXISTING // Update existing with backup data, add new
}

/**
 * Backup version validation result.
 */
sealed class VersionValidation {
    data object Ok : VersionValidation()
    data class Warning(val message: String) : VersionValidation()
    data class Error(val message: String) : VersionValidation()
}
