package com.samcod3.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.samcod3.meditrack.data.local.entity.SummaryCacheEntity

@Dao
interface SummaryCacheDao {
    
    @Query("SELECT * FROM summary_cache WHERE registrationNumber = :regNum")
    suspend fun getSummary(regNum: String): SummaryCacheEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SummaryCacheEntity)
    
    @Query("DELETE FROM summary_cache WHERE registrationNumber = :regNum")
    suspend fun deleteSummary(regNum: String)
    
    @Query("DELETE FROM summary_cache WHERE cachedAt < :threshold")
    suspend fun deleteExpired(threshold: Long)
}
