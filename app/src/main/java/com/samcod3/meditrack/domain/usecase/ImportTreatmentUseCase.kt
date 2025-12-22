package com.samcod3.meditrack.domain.usecase

import android.util.Log
import com.samcod3.meditrack.ai.AIService
import com.samcod3.meditrack.data.local.entity.DosageType
import com.samcod3.meditrack.data.local.entity.Portion
import com.samcod3.meditrack.data.local.entity.ScheduleType
import com.samcod3.meditrack.domain.model.ImportResult
import com.samcod3.meditrack.domain.model.ImportStatus
import com.samcod3.meditrack.domain.model.ParsedMedication
import com.samcod3.meditrack.domain.repository.DrugRepository
import com.samcod3.meditrack.domain.repository.ReminderRepository
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use case for importing treatment data from PDF text.
 * Uses Gemini Nano to parse the text and creates medications + reminders.
 */
class ImportTreatmentUseCase(
    private val aiService: AIService,
    private val drugRepository: DrugRepository,
    private val userMedicationRepository: UserMedicationRepository,
    private val reminderRepository: ReminderRepository
) {
    companion object {
        private const val TAG = "ImportTreatmentUseCase"
    }

    /**
     * Import treatment from OCR text.
     * Emits progress updates as Flow.
     */
    fun importFromText(profileId: String, ocrText: String): Flow<ImportStatus> = flow {
        emit(ImportStatus.ParsingWithAI)
        
        // Parse text with Gemini Nano
        val medications = aiService.parseTreatmentText(ocrText)
        
        if (medications.isEmpty()) {
            emit(ImportStatus.Error("No se encontraron medicamentos en el texto"))
            return@flow
        }
        
        Log.d(TAG, "Parsed ${medications.size} medications from text")
        
        var successCount = 0
        var createdReminders = 0
        val failedMedications = mutableListOf<String>()
        
        medications.forEachIndexed { index, parsedMed ->
            emit(ImportStatus.SearchingMedication(parsedMed.name, index + 1, medications.size))
            
            // Search in CIMA
            val searchResult = drugRepository.searchMedications(parsedMed.name)
            
            if (searchResult.isSuccess) {
                val results = searchResult.getOrThrow()
                val medication = results.firstOrNull()
                
                if (medication != null) {
                    emit(ImportStatus.SavingMedication(parsedMed.name))
                    
                    // Save medication
                    val saveResult = userMedicationRepository.saveMedication(
                        profileId = profileId,
                        nationalCode = medication.nationalCode ?: medication.registrationNumber,
                        name = medication.name,
                        description = medication.activeIngredients.joinToString(", ") { 
                            "${it.name} ${it.quantity}${it.unit}" 
                        },
                        notes = null
                    )
                    
                    if (saveResult.isSuccess) {
                        val medicationId = saveResult.getOrThrow()
                        emit(ImportStatus.CreatingReminders(parsedMed.name, parsedMed.times.size))
                        
                        // Create reminders for each time
                        val scheduleInfo = parseSchedule(parsedMed.frequency)
                        
                        parsedMed.times.forEach { timeStr ->
                            val (hour, minute) = parseTime(timeStr)
                            val (dosageQty, dosageType, portion) = parseDosage(parsedMed.dosage)
                            
                            try {
                                reminderRepository.createReminder(
                                    medicationId = medicationId,
                                    hour = hour,
                                    minute = minute,
                                    scheduleType = scheduleInfo.type,
                                    daysOfWeek = scheduleInfo.daysOfWeek,
                                    intervalDays = scheduleInfo.intervalDays,
                                    dayOfMonth = scheduleInfo.dayOfMonth,
                                    dosageQuantity = dosageQty,
                                    dosageType = dosageType,
                                    dosagePortion = portion
                                )
                                createdReminders++
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to create reminder for ${parsedMed.name}", e)
                            }
                        }
                        
                        emit(ImportStatus.SavedMedication(parsedMed.name))
                        successCount++
                    } else {
                        Log.e(TAG, "Failed to save medication: ${parsedMed.name}")
                        failedMedications.add(parsedMed.name)
                        emit(ImportStatus.MedicationNotFound(parsedMed.name))
                    }
                } else {
                    Log.w(TAG, "Medication not found in CIMA: ${parsedMed.name}")
                    failedMedications.add(parsedMed.name)
                    emit(ImportStatus.MedicationNotFound(parsedMed.name))
                }
            } else {
                Log.e(TAG, "Search failed for: ${parsedMed.name}")
                failedMedications.add(parsedMed.name)
                emit(ImportStatus.MedicationNotFound(parsedMed.name))
            }
        }
        
        emit(ImportStatus.Completed(successCount, failedMedications.size))
    }
    
    private fun parseTime(timeStr: String): Pair<Int, Int> {
        return try {
            val parts = timeStr.split(":")
            Pair(parts[0].toInt(), parts.getOrElse(1) { "0" }.toInt())
        } catch (e: Exception) {
            Pair(8, 0) // Default to 08:00
        }
    }
    
    private data class ScheduleInfo(
        val type: ScheduleType,
        val daysOfWeek: Int = 0,
        val intervalDays: Int = 1,
        val dayOfMonth: Int = 1
    )
    
    private fun parseSchedule(frequency: String): ScheduleInfo {
        return when {
            frequency.startsWith("WEEKLY:") -> {
                val days = frequency.removePrefix("WEEKLY:")
                val daysOfWeek = parseDaysOfWeek(days)
                ScheduleInfo(ScheduleType.WEEKLY, daysOfWeek = daysOfWeek)
            }
            frequency.startsWith("MONTHLY:") -> {
                val day = frequency.removePrefix("MONTHLY:").toIntOrNull() ?: 1
                ScheduleInfo(ScheduleType.MONTHLY, dayOfMonth = day)
            }
            frequency.startsWith("INTERVAL:") -> {
                val interval = frequency.removePrefix("INTERVAL:").toIntOrNull() ?: 1
                ScheduleInfo(ScheduleType.INTERVAL, intervalDays = interval)
            }
            else -> ScheduleInfo(ScheduleType.DAILY)
        }
    }
    
    private fun parseDaysOfWeek(days: String): Int {
        var bitmask = 0
        if (days.contains("L", ignoreCase = true)) bitmask = bitmask or (1 shl 1) // Monday
        if (days.contains("M", ignoreCase = true)) bitmask = bitmask or (1 shl 2) // Tuesday
        if (days.contains("X", ignoreCase = true)) bitmask = bitmask or (1 shl 3) // Wednesday
        if (days.contains("J", ignoreCase = true)) bitmask = bitmask or (1 shl 4) // Thursday
        if (days.contains("V", ignoreCase = true)) bitmask = bitmask or (1 shl 5) // Friday
        if (days.contains("S", ignoreCase = true)) bitmask = bitmask or (1 shl 6) // Saturday
        if (days.contains("D", ignoreCase = true)) bitmask = bitmask or (1 shl 0) // Sunday
        return bitmask
    }
    
    private fun parseDosage(dosage: String): Triple<Int, DosageType, Portion?> {
        val lower = dosage.lowercase()
        
        // Extract quantity
        val qty = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        
        // Determine type and portion based on actual DosageType enum values
        return when {
            lower.contains("comprimido") -> Triple(qty, DosageType.COMPRIMIDO, null)
            lower.contains("cápsula") || lower.contains("capsula") -> Triple(qty, DosageType.CAPSULAS, null)
            lower.contains("sobre") -> Triple(qty, DosageType.SOBRE, null)
            lower.contains("ml") -> Triple(qty, DosageType.ML, null)
            lower.contains("gota") -> Triple(qty, DosageType.GOTA, null)
            lower.contains("parche") -> Triple(qty, DosageType.PARCHE, null)
            lower.contains("inhalaci") -> Triple(qty, DosageType.INHALACION, null)
            lower.contains("cucharada") && lower.contains("ita") -> Triple(qty, DosageType.CUCHARADITA, null)
            lower.contains("cucharada") -> Triple(qty, DosageType.CUCHARADA, null)
            // Handle fractions using PORCION type with Portion enum
            lower.contains("media") || lower.contains("1/2") -> Triple(1, DosageType.PORCION, Portion.MEDIA)
            lower.contains("cuarto") || lower.contains("1/4") -> Triple(1, DosageType.PORCION, Portion.CUARTO)
            lower.contains("tres cuartos") || lower.contains("3/4") -> Triple(1, DosageType.PORCION, Portion.TRES_CUARTOS)
            else -> Triple(qty, DosageType.COMPRIMIDO, null)
        }
    }
}
