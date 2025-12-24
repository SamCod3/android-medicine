package com.samcod3.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.samcod3.meditrack.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    
    @Query("SELECT * FROM reminders WHERE medicationId = :medicationId ORDER BY hour, minute")
    fun getRemindersForMedication(medicationId: String): Flow<List<ReminderEntity>>

    @Query("UPDATE reminders SET medicationId = :toMedId WHERE medicationId = :fromMedId")
    suspend fun moveReminders(fromMedId: String, toMedId: String)
    
    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY hour, minute")
    fun getAllEnabledReminders(): Flow<List<ReminderEntity>>

    @Query("""
        SELECT r.* FROM reminders r
        INNER JOIN medications m ON r.medicationId = m.id
        WHERE r.enabled = 1 AND m.profileId = :profileId
        ORDER BY r.hour, r.minute
    """)
    fun getEnabledRemindersForProfile(profileId: String): Flow<List<ReminderEntity>>
    
    @Query("""
        SELECT r.* FROM reminders r
        INNER JOIN medications m ON r.medicationId = m.id
        WHERE m.profileId = :profileId
        ORDER BY r.hour, r.minute
    """)
    fun getAllRemindersForProfile(profileId: String): Flow<List<ReminderEntity>>
    
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: String): ReminderEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)
    
    @Update
    suspend fun update(reminder: ReminderEntity)
    
    @Delete
    suspend fun delete(reminder: ReminderEntity)
    
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
