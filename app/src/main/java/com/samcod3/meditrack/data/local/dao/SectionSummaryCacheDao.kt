package com.samcod3.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.samcod3.meditrack.data.local.entity.SectionSummaryCacheEntity

/**
 * DAO for section summary cache operations.
 */
@Dao
interface SectionSummaryCacheDao {
    
    /**
     * Get cached summary for a specific section.
     */
    @Query("SELECT * FROM section_summary_cache WHERE registrationNumber = :registrationNumber AND sectionNumber = :sectionNumber LIMIT 1")
    suspend fun getSummary(registrationNumber: String, sectionNumber: Int): SectionSummaryCacheEntity?
    
    /**
     * Insert or update a cached summary.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(cache: SectionSummaryCacheEntity)
    
    /**
     * Delete all cached summaries for a medication.
     */
    @Query("DELETE FROM section_summary_cache WHERE registrationNumber = :registrationNumber")
    suspend fun deleteForMedication(registrationNumber: String)
    
    /**
     * Delete cached summary for a specific section.
     */
    @Query("DELETE FROM section_summary_cache WHERE registrationNumber = :registrationNumber AND sectionNumber = :sectionNumber")
    suspend fun deleteSectionSummary(registrationNumber: String, sectionNumber: Int)
    
    /**
     * Delete expired cache entries (older than maxAgeMs milliseconds).
     */
    @Query("DELETE FROM section_summary_cache WHERE createdAt < :cutoffTime")
    suspend fun deleteExpired(cutoffTime: Long)
    
    /**
     * Clear all cached summaries.
     */
    @Query("DELETE FROM section_summary_cache")
    suspend fun clearAll()
}
