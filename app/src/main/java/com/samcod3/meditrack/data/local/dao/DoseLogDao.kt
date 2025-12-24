package com.samcod3.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.samcod3.meditrack.data.local.entity.DoseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    
    /**
     * Get all logs for a specific date (for a profile)
     */
    @Query("""
        SELECT * FROM dose_logs 
        WHERE profileId = :profileId AND scheduledDate = :date
        ORDER BY scheduledHour, scheduledMinute
    """)
    fun getLogsForDate(profileId: String, date: Long): Flow<List<DoseLogEntity>>
    
    /**
     * Check if a specific reminder at a specific time has been logged today
     */
    @Query("""
        SELECT * FROM dose_logs 
        WHERE reminderId = :reminderId 
        AND scheduledDate = :date 
        AND scheduledHour = :hour 
        AND scheduledMinute = :minute
        LIMIT 1
    """)
    suspend fun getLog(reminderId: String, date: Long, hour: Int, minute: Int): DoseLogEntity?
    
    /**
     * Log a dose as taken or skipped
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DoseLogEntity)
    
    /**
     * Delete logs older than a certain date (cleanup)
     */
    @Query("DELETE FROM dose_logs WHERE scheduledDate < :beforeDate")
    suspend fun deleteOldLogs(beforeDate: Long)
    
    /**
     * Delete all logs for a reminder (when reminder is deleted)
     */
    @Query("DELETE FROM dose_logs WHERE reminderId = :reminderId")
    suspend fun deleteLogsForReminder(reminderId: String)
}
