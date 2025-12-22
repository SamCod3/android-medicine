package com.samcod3.meditrack.domain.model

/**
 * Represents a medication parsed from imported treatment PDF.
 */
data class ParsedMedication(
    val name: String,
    val dosage: String,           // e.g., "1 comprimido", "10 mg"
    val times: List<String>,      // e.g., ["08:00", "14:00", "21:00"]
    val frequency: String         // "DAILY", "WEEKLY:L,M,X", "MONTHLY:15", "INTERVAL:3"
)

/**
 * Status updates during treatment import process.
 */
sealed class ImportStatus {
    object ReadingPdf : ImportStatus()
    object ParsingWithAI : ImportStatus()
    data class SearchingMedication(val name: String, val index: Int, val total: Int) : ImportStatus()
    data class SavingMedication(val name: String) : ImportStatus()
    data class SavedMedication(val name: String) : ImportStatus()
    data class CreatingReminders(val name: String, val count: Int) : ImportStatus()
    data class MedicationNotFound(val name: String) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
    data class Completed(val successCount: Int, val failedCount: Int) : ImportStatus()
}

/**
 * Result of the import process.
 */
data class ImportResult(
    val successCount: Int,
    val failedMedications: List<String>,
    val createdReminders: Int
)
