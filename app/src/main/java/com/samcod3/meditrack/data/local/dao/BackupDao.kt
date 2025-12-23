package com.samcod3.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.data.local.entity.ReminderEntity

/**
 * DAO for backup/restore operations with transactional support.
 */
@Dao
interface BackupDao {
    
    /**
     * Atomically restore medications and reminders for a profile.
     * Deletes existing data and inserts new data in a single transaction.
     */
    @Transaction
    suspend fun restoreProfile(
        profileId: String,
        medications: List<MedicationEntity>,
        reminders: List<ReminderEntity>
    ) {
        // Delete existing medications (cascades to reminders via ForeignKey)
        deleteAllMedicationsForProfile(profileId)
        // Insert new medications
        insertAllMedications(medications)
        // Insert new reminders
        insertAllReminders(reminders)
    }
    
    /**
     * Atomically merge medications and reminders into a profile.
     * Keeps existing data, only adds new medications by nationalCode.
     */
    @Transaction
    suspend fun mergeIntoProfile(
        profileId: String,
        newMedications: List<MedicationEntity>,
        newReminders: List<ReminderEntity>,
        existingNationalCodes: Set<String>
    ) {
        // Filter out medications that already exist
        val toInsert = newMedications.filter { it.nationalCode !in existingNationalCodes }
        val insertedIds = toInsert.map { it.id }.toSet()
        
        // Insert new medications
        insertAllMedications(toInsert)
        
        // Insert only reminders for newly inserted medications
        val remindersToInsert = newReminders.filter { it.medicationId in insertedIds }
        insertAllReminders(remindersToInsert)
    }
    
    @Query("DELETE FROM medications WHERE profileId = :profileId")
    suspend fun deleteAllMedicationsForProfile(profileId: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMedications(medications: List<MedicationEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReminders(reminders: List<ReminderEntity>)
    
    @Query("SELECT nationalCode FROM medications WHERE profileId = :profileId AND active = 1")
    suspend fun getActiveNationalCodesForProfile(profileId: String): List<String>
}
