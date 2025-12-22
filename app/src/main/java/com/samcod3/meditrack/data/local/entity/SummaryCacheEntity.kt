package com.samcod3.meditrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached AI-generated summary for a medication leaflet.
 * TTL: 30 days (checked at query time).
 */
@Entity(tableName = "summary_cache")
data class SummaryCacheEntity(
    @PrimaryKey
    val registrationNumber: String,
    val indications: String,
    val dosage: String,
    val warnings: String,
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TTL_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
    
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - cachedAt > TTL_MILLIS
    }
}
