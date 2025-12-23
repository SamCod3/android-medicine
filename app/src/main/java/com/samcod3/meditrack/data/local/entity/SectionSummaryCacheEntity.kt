package com.samcod3.meditrack.data.local.entity

import androidx.room.Entity
import java.security.MessageDigest

/**
 * Cache entity for AI-generated section summaries.
 * Uses content hash for invalidation - if the source content changes, the cache is invalid.
 */
@Entity(
    tableName = "section_summary_cache",
    primaryKeys = ["registrationNumber", "sectionNumber"]
)
data class SectionSummaryCacheEntity(
    val registrationNumber: String,
    val sectionNumber: Int,
    val contentHash: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Generate SHA-256 hash of content for cache validation.
         */
        fun hashContent(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
    
    /**
     * Check if this cached summary is valid for the given content.
     */
    fun isValidFor(content: String): Boolean {
        return contentHash == hashContent(content)
    }
}
