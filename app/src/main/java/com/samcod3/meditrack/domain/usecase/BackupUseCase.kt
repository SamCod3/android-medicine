package com.samcod3.meditrack.domain.usecase

import android.util.Log
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.data.local.entity.ReminderEntity
import com.samcod3.meditrack.domain.model.MedicationBackup
import com.samcod3.meditrack.domain.model.ProfileBackup
import com.samcod3.meditrack.domain.model.ProfileData
import com.samcod3.meditrack.domain.model.ReminderBackup
import com.samcod3.meditrack.domain.model.toBackup
import com.samcod3.meditrack.domain.model.toProfileData
import com.samcod3.meditrack.domain.repository.ProfileRepository
import com.samcod3.meditrack.domain.repository.ReminderRepository
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Use case for backup and restore operations.
 */
class BackupUseCase(
    private val profileRepository: ProfileRepository,
    private val medicationRepository: UserMedicationRepository,
    private val reminderRepository: ReminderRepository
) {
    companion object {
        private const val TAG = "BackupUseCase"
    }

    /**
     * Export a profile and all its data to JSON string.
     */
    suspend fun exportProfile(profileId: String): Result<String> {
        return try {
            val profileResult = profileRepository.getProfileById(profileId)
            val profile = profileResult.getOrNull()
                ?: return Result.failure(Exception("Profile not found"))
            
            val medications = medicationRepository.getMedicationsForProfile(profileId).first()
            val medicationBackups = mutableListOf<MedicationBackup>()
            
            var successCount = 0
            var failCount = 0

            for (med in medications) {
                try {
                    val reminders = reminderRepository.getRemindersForMedication(med.id).first()
                    val reminderEntities = reminders.mapNotNull { reminder ->
                        try {
                            ReminderEntity(
                                id = reminder.id,
                                medicationId = med.id,
                                hour = reminder.hour,
                                minute = reminder.minute,
                                scheduleType = reminder.scheduleType.name, // Convert enum to string
                                daysOfWeek = reminder.daysOfWeek,
                                intervalDays = reminder.intervalDays,
                                dayOfMonth = reminder.dayOfMonth,
                                dosageQuantity = reminder.dosageQuantity,
                                dosageType = reminder.dosageType.name, // Convert enum to string
                                dosagePortion = reminder.dosagePortion?.name, // Convert enum to string
                                enabled = reminder.enabled
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to map reminder ${reminder.id} for med ${med.name}", e)
                            null
                        }
                    }

                    val backupMed = MedicationEntity(
                        id = med.id,
                        profileId = profileId,
                        nationalCode = med.nationalCode,
                        name = med.name,
                        description = med.description,
                        notes = med.notes,
                        active = med.isActive // Uses actual active state
                    ).toBackup(reminderEntities)
                    
                    medicationBackups.add(backupMed)
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to export medication: ${med.name} (${med.nationalCode})", e)
                    failCount++
                    // Continue with next medication
                }
            }
            
            // Create ProfileData directly from profile
            val profileData = ProfileData(
                name = profile.name,
                notes = null // Profile domain model doesn't have notes
            )
            
            val backup = ProfileBackup(
                profile = profileData,
                medications = medicationBackups
            )
            
            val json = backupToJson(backup)
            Log.d(TAG, "Exported $successCount medications ($failCount failed)")
            Result.success(json)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed at top level", e)
            Result.failure(e)
        }
    }

    /**
     * Import profile data from JSON string.
     * Creates medications and reminders for the specified profile.
     */
    suspend fun importToProfile(profileId: String, jsonString: String): Result<Int> {
        return try {
            val backup = jsonToBackup(jsonString)
            var importedCount = 0
            
            for (medBackup in backup.medications) {
                // Save medication
                val saveResult = medicationRepository.saveMedication(
                    profileId = profileId,
                    nationalCode = medBackup.nationalCode,
                    name = medBackup.name,
                    description = medBackup.description,
                    notes = medBackup.notes
                )
                
                if (saveResult.isSuccess) {
                    val medicationId = saveResult.getOrThrow()
                    
                    // Create reminders
                    for (reminder in medBackup.reminders) {
                        try {
                            reminderRepository.createReminder(
                                medicationId = medicationId,
                                hour = reminder.hour,
                                minute = reminder.minute,
                                scheduleType = com.samcod3.meditrack.data.local.entity.ScheduleType.valueOf(reminder.scheduleType),
                                daysOfWeek = reminder.daysOfWeek,
                                intervalDays = reminder.intervalDays,
                                dayOfMonth = reminder.dayOfMonth,
                                dosageQuantity = reminder.dosageQuantity,
                                dosageType = com.samcod3.meditrack.data.local.entity.DosageType.valueOf(reminder.dosageType),
                                dosagePortion = reminder.dosagePortion?.let { 
                                    com.samcod3.meditrack.data.local.entity.Portion.valueOf(it) 
                                }
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to create reminder: ${e.message}")
                        }
                    }
                    importedCount++
                }
            }
            
            Log.d(TAG, "Imported $importedCount medications")
            Result.success(importedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            Result.failure(e)
        }
    }

    private fun backupToJson(backup: ProfileBackup): String {
        val json = JSONObject()
        json.put("version", backup.version)
        json.put("exportDate", backup.exportDate)
        
        val profileJson = JSONObject()
        profileJson.put("name", backup.profile.name)
        profileJson.put("notes", backup.profile.notes ?: JSONObject.NULL)
        json.put("profile", profileJson)
        
        val medsArray = JSONArray()
        for (med in backup.medications) {
            val medJson = JSONObject()
            medJson.put("nationalCode", med.nationalCode)
            medJson.put("name", med.name)
            medJson.put("description", med.description ?: JSONObject.NULL)
            medJson.put("notes", med.notes ?: JSONObject.NULL)
            medJson.put("active", med.active)
            
            val remindersArray = JSONArray()
            for (rem in med.reminders) {
                val remJson = JSONObject()
                remJson.put("hour", rem.hour)
                remJson.put("minute", rem.minute)
                remJson.put("scheduleType", rem.scheduleType)
                remJson.put("daysOfWeek", rem.daysOfWeek)
                remJson.put("intervalDays", rem.intervalDays)
                remJson.put("dayOfMonth", rem.dayOfMonth)
                remJson.put("dosageQuantity", rem.dosageQuantity)
                remJson.put("dosageType", rem.dosageType)
                remJson.put("dosagePortion", rem.dosagePortion ?: JSONObject.NULL)
                remJson.put("enabled", rem.enabled)
                remindersArray.put(remJson)
            }
            medJson.put("reminders", remindersArray)
            medsArray.put(medJson)
        }
        json.put("medications", medsArray)
        
        return json.toString(2)
    }

    private fun jsonToBackup(jsonString: String): ProfileBackup {
        // Log start of JSON for debugging
        Log.d(TAG, "Parsing JSON backup (len=${jsonString.length}): ${jsonString.take(100)}...")
        
        val json = JSONObject(jsonString)
        
        // Profile is optional for import (we use the target profileId)
        val profileData = if (json.has("profile")) {
            val profileJson = json.getJSONObject("profile")
            ProfileData(
                name = profileJson.optString("name", "Unknown"),
                notes = if (profileJson.isNull("notes")) null else profileJson.getString("notes")
            )
        } else {
            ProfileData(name = "Imported", notes = null)
        }
        
        val medsArray = json.optJSONArray("medications") ?: JSONArray()
        val medications = mutableListOf<MedicationBackup>()
        
        for (i in 0 until medsArray.length()) {
            val medJson = medsArray.getJSONObject(i)
            
            val remindersArray = medJson.optJSONArray("reminders") ?: JSONArray()
            val reminders = mutableListOf<ReminderBackup>()
            
            for (j in 0 until remindersArray.length()) {
                val remJson = remindersArray.getJSONObject(j)
                reminders.add(ReminderBackup(
                    hour = remJson.getInt("hour"),
                    minute = remJson.getInt("minute"),
                    scheduleType = remJson.getString("scheduleType"),
                    daysOfWeek = remJson.getInt("daysOfWeek"),
                    intervalDays = remJson.getInt("intervalDays"),
                    dayOfMonth = remJson.getInt("dayOfMonth"),
                    dosageQuantity = remJson.getInt("dosageQuantity"),
                    dosageType = remJson.getString("dosageType"),
                    dosagePortion = if (remJson.isNull("dosagePortion")) null else remJson.getString("dosagePortion"),
                    enabled = remJson.getBoolean("enabled")
                ))
            }
            
            medications.add(MedicationBackup(
                nationalCode = medJson.getString("nationalCode"),
                name = medJson.getString("name"),
                description = if (medJson.isNull("description")) null else medJson.getString("description"),
                notes = if (medJson.isNull("notes")) null else medJson.getString("notes"),
                active = medJson.getBoolean("active"),
                reminders = reminders
            ))
        }
        
        return ProfileBackup(
            version = json.optInt("version", 1),
            exportDate = json.optLong("exportDate", System.currentTimeMillis()),
            profile = profileData,
            medications = medications
        )
    }
}
