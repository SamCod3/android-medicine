package com.samcod3.meditrack.domain.usecase

import android.util.Log
import com.samcod3.meditrack.data.local.dao.BackupDao
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.data.local.entity.ReminderEntity
import com.samcod3.meditrack.data.local.entity.ScheduleType
import com.samcod3.meditrack.data.local.entity.DosageType
import com.samcod3.meditrack.data.local.entity.Portion
import com.samcod3.meditrack.domain.model.*
import com.samcod3.meditrack.domain.repository.ProfileRepository
import com.samcod3.meditrack.domain.repository.ReminderRepository
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID

/**
 * Use case for backup and restore operations.
 * Supports both v1 (legacy JSON) and v2 (Moshi) formats.
 */
class BackupUseCase(
    private val profileRepository: ProfileRepository,
    private val medicationRepository: UserMedicationRepository,
    private val reminderRepository: ReminderRepository,
    private val backupDao: BackupDao
) {
    companion object {
        private const val TAG = "BackupUseCase"
    }
    
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val backupAdapter = moshi.adapter(ProfileBackup::class.java).indent("  ")

    /**
     * Validate backup version before import.
     */
    fun validateVersion(jsonString: String): VersionValidation {
        return try {
            val json = JSONObject(jsonString)
            val version = json.optInt("version", 1)
            
            when {
                version > ProfileBackup.CURRENT_VERSION -> 
                    VersionValidation.Warning("Backup de versión $version (esta app soporta hasta v${ProfileBackup.CURRENT_VERSION}). Algunos datos podrían perderse.")
                version < ProfileBackup.MIN_SUPPORTED_VERSION ->
                    VersionValidation.Error("Backup demasiado antiguo (v$version). Versión mínima soportada: v${ProfileBackup.MIN_SUPPORTED_VERSION}")
                else -> VersionValidation.Ok
            }
        } catch (e: Exception) {
            VersionValidation.Error("JSON inválido: ${e.message}")
        }
    }
    
    /**
     * Preview backup contents without importing.
     */
    fun previewBackup(jsonString: String): Result<ProfileBackup> {
        return try {
            val backup = parseBackup(jsonString)
            Result.success(backup)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export a profile and all its data to JSON string (v2 format).
     */
    suspend fun exportProfile(profileId: String): Result<String> {
        return try {
            val profileResult = profileRepository.getProfileById(profileId)
            val profile = profileResult.getOrNull()
                ?: return Result.failure(Exception("Perfil no encontrado"))
            
            val medications = medicationRepository.getMedicationsForProfile(profileId).first()
            val medicationBackups = mutableListOf<MedicationBackup>()
            
            var successCount = 0
            var failCount = 0

            for (med in medications) {
                try {
                    val reminders = reminderRepository.getRemindersForMedication(med.id).first()
                    val reminderBackups = reminders.map { reminder ->
                        ReminderBackup(
                            hour = reminder.hour,
                            minute = reminder.minute,
                            scheduleType = reminder.scheduleType.name,
                            daysOfWeek = reminder.daysOfWeek,
                            intervalDays = reminder.intervalDays,
                            dayOfMonth = reminder.dayOfMonth,
                            dosageQuantity = reminder.dosageQuantity,
                            dosageType = reminder.dosageType.name,
                            dosagePortion = reminder.dosagePortion?.name,
                            enabled = reminder.enabled
                        )
                    }

                    medicationBackups.add(MedicationBackup(
                        nationalCode = med.nationalCode,
                        name = med.name,
                        description = med.description,
                        notes = med.notes,
                        active = med.isActive,
                        startDate = med.startDate,
                        reminders = reminderBackups
                    ))
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error exportando medicamento: ${med.name}", e)
                    failCount++
                }
            }
            
            val backup = ProfileBackup(
                version = ProfileBackup.CURRENT_VERSION,
                appVersion = "1.0.0", // Could use BuildConfig.VERSION_NAME
                profile = ProfileData(name = profile.name),
                medications = medicationBackups
            )
            
            val json = backupAdapter.toJson(backup)
            Log.d(TAG, "Exportados $successCount medicamentos ($failCount fallidos)")
            Result.success(json)
        } catch (e: Exception) {
            Log.e(TAG, "Export falló", e)
            Result.failure(e)
        }
    }

    /**
     * Import profile data from JSON string with strategy selection.
     * Uses atomic transactions - all or nothing.
     */
    suspend fun importToProfile(
        profileId: String,
        jsonString: String,
        strategy: ImportStrategy = ImportStrategy.MERGE_SKIP_EXISTING
    ): Result<BackupImportResult> {
        return try {
            val backup = parseBackup(jsonString)
            
            when (strategy) {
                ImportStrategy.REPLACE_ALL -> replaceAllImport(profileId, backup)
                ImportStrategy.MERGE_SKIP_EXISTING -> mergeSkipExistingImport(profileId, backup)
                ImportStrategy.MERGE_UPDATE_EXISTING -> mergeUpdateExistingImport(profileId, backup)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import falló", e)
            Result.failure(e)
        }
    }
    
    /**
     * REPLACE_ALL: Delete all existing medications and import backup.
     */
    private suspend fun replaceAllImport(profileId: String, backup: ProfileBackup): Result<BackupImportResult> {
        val warnings = mutableListOf<String>()
        
        // Convert backup to entities
        val (medications, reminders) = backupToEntities(profileId, backup, warnings)
        
        // Atomic transaction
        backupDao.restoreProfile(profileId, medications, reminders)
        
        Log.d(TAG, "REPLACE_ALL: ${medications.size} medicamentos, ${reminders.size} recordatorios")
        
        return Result.success(BackupImportResult(
            medicationsImported = medications.size,
            medicationsSkipped = 0,
            remindersImported = reminders.size,
            remindersFailed = 0,
            warnings = warnings
        ))
    }
    
    /**
     * MERGE_SKIP_EXISTING: Keep existing medications, add only new ones.
     */
    private suspend fun mergeSkipExistingImport(profileId: String, backup: ProfileBackup): Result<BackupImportResult> {
        val warnings = mutableListOf<String>()
        
        // Get existing national codes
        val existingCodes = backupDao.getActiveNationalCodesForProfile(profileId).toSet()
        
        // Convert all to entities
        val (allMedications, allReminders) = backupToEntities(profileId, backup, warnings)
        
        // Filter out existing
        val newMedications = allMedications.filter { it.nationalCode !in existingCodes }
        val newMedicationIds = newMedications.map { it.id }.toSet()
        val newReminders = allReminders.filter { it.medicationId in newMedicationIds }
        
        val skipped = allMedications.size - newMedications.size
        if (skipped > 0) {
            warnings.add("$skipped medicamentos ya existían y fueron omitidos")
        }
        
        // Atomic insert of new items only
        if (newMedications.isNotEmpty()) {
            backupDao.insertAllMedications(newMedications)
            backupDao.insertAllReminders(newReminders)
        }
        
        Log.d(TAG, "MERGE_SKIP: ${newMedications.size} nuevos, $skipped omitidos")
        
        return Result.success(BackupImportResult(
            medicationsImported = newMedications.size,
            medicationsSkipped = skipped,
            remindersImported = newReminders.size,
            remindersFailed = 0,
            warnings = warnings
        ))
    }
    
    /**
     * MERGE_UPDATE_EXISTING: Update existing with backup data, add new.
     */
    private suspend fun mergeUpdateExistingImport(profileId: String, backup: ProfileBackup): Result<BackupImportResult> {
        // For now, same as MERGE_SKIP. Could be enhanced to update notes/description.
        return mergeSkipExistingImport(profileId, backup)
    }
    
    /**
     * Convert backup models to Room entities.
     */
    private fun backupToEntities(
        profileId: String, 
        backup: ProfileBackup,
        warnings: MutableList<String>
    ): Pair<List<MedicationEntity>, List<ReminderEntity>> {
        val medications = mutableListOf<MedicationEntity>()
        val reminders = mutableListOf<ReminderEntity>()
        
        for (medBackup in backup.medications) {
            val medicationId = UUID.randomUUID().toString()
            
            medications.add(MedicationEntity(
                id = medicationId,
                profileId = profileId,
                nationalCode = medBackup.nationalCode,
                name = medBackup.name,
                description = medBackup.description,
                notes = medBackup.notes,
                startDate = medBackup.startDate ?: System.currentTimeMillis(),
                active = medBackup.active // ← FIXED: Now preserving active state
            ))
            
            for (remBackup in medBackup.reminders) {
                try {
                    reminders.add(ReminderEntity(
                        id = UUID.randomUUID().toString(),
                        medicationId = medicationId,
                        hour = remBackup.hour,
                        minute = remBackup.minute,
                        scheduleType = remBackup.scheduleType,
                        daysOfWeek = remBackup.daysOfWeek,
                        intervalDays = remBackup.intervalDays,
                        dayOfMonth = remBackup.dayOfMonth,
                        dosageQuantity = remBackup.dosageQuantity,
                        dosageType = remBackup.dosageType,
                        dosagePortion = remBackup.dosagePortion,
                        enabled = remBackup.enabled
                    ))
                } catch (e: Exception) {
                    warnings.add("Recordatorio inválido para ${medBackup.name}: ${e.message}")
                }
            }
        }
        
        return medications to reminders
    }
    
    /**
     * Parse backup JSON, supporting both v1 (legacy) and v2 (Moshi) formats.
     */
    private fun parseBackup(jsonString: String): ProfileBackup {
        Log.d(TAG, "Parsing backup (${jsonString.length} chars)")
        
        // Try Moshi first (v2 format)
        try {
            val backup = backupAdapter.fromJson(jsonString)
            if (backup != null) {
                Log.d(TAG, "Parsed as v2 format")
                return backup
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not v2 format, trying v1: ${e.message}")
        }
        
        // Fallback to v1 legacy parsing
        return parseV1Backup(jsonString)
    }
    
    /**
     * Parse v1 legacy JSON format (manual JSONObject parsing).
     */
    private fun parseV1Backup(jsonString: String): ProfileBackup {
        Log.d(TAG, "Parsing as v1 legacy format")
        val json = JSONObject(jsonString)
        
        val profileData = if (json.has("profile")) {
            val profileJson = json.getJSONObject("profile")
            ProfileData(
                name = profileJson.optString("name", "Importado"),
                notes = if (profileJson.isNull("notes")) null else profileJson.getString("notes")
            )
        } else {
            ProfileData(name = "Importado")
        }
        
        val medsArray = json.optJSONArray("medications") ?: org.json.JSONArray()
        val medications = mutableListOf<MedicationBackup>()
        
        for (i in 0 until medsArray.length()) {
            val medJson = medsArray.getJSONObject(i)
            
            val remindersArray = medJson.optJSONArray("reminders") ?: org.json.JSONArray()
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
                    enabled = remJson.optBoolean("enabled", true)
                ))
            }
            
            medications.add(MedicationBackup(
                nationalCode = medJson.getString("nationalCode"),
                name = medJson.getString("name"),
                description = if (medJson.isNull("description")) null else medJson.getString("description"),
                notes = if (medJson.isNull("notes")) null else medJson.getString("notes"),
                active = medJson.optBoolean("active", true), // ← v1 also respects active now
                startDate = null, // v1 didn't have this
                reminders = reminders
            ))
        }
        
        return ProfileBackup(
            version = json.optInt("version", 1),
            exportDate = json.optLong("exportDate", System.currentTimeMillis()),
            appVersion = "1.0.0",
            profile = profileData,
            medications = medications
        )
    }
}
