package com.samcod3.meditrack.data.repository

import com.samcod3.meditrack.data.local.dao.MedicationDao
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.domain.model.SavedMedication
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class UserMedicationRepositoryImpl(
    private val medicationDao: MedicationDao
) : UserMedicationRepository {

    override fun getMedicationsForProfile(profileId: String): Flow<List<SavedMedication>> {
        return medicationDao.getActiveMedicationsForProfile(profileId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun saveMedication(
        profileId: String,
        nationalCode: String,
        name: String,
        description: String?,
        notes: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Check exact match (idempotency)
            val existing = medicationDao.getMedicationByNationalCodeAndProfile(nationalCode, profileId)
            if (existing != null) {
                return@withContext Result.success(existing.id)
            }
            
            // 2. Check FUZZY match by NAME (Smart Deduplication)
            // Fetch all active medications to check names
            // Note: For performance with large lists, this should be a DB query, but for simple user profiles it's fine.
            val allMedications = medicationDao.getAllMedicationsForProfile(profileId).first()
            
            val cleanName = name.lowercase().replace(Regex("\\s\\d+.*"), "").trim()
            val fuzzyMatch = allMedications.find { 
                it.active && 
                it.name.lowercase().replace(Regex("\\s\\d+.*"), "").trim() == cleanName 
            }
            
            if (fuzzyMatch != null) {
                // Found a likely duplicate (e.g. Imported "Enalapril" vs Scanned "Enalapril 20mg").
                // UPDATE the existing one to the new (better) data.
                val updatedEntity = fuzzyMatch.copy(
                    nationalCode = nationalCode, // Update to Real CN
                    name = name,
                    description = description,
                    notes = notes ?: fuzzyMatch.notes // Keep existing notes if new ones empty? Or override? Usually keep.
                )
                medicationDao.updateMedication(updatedEntity)
                return@withContext Result.success(updatedEntity.id)
            }

            val entity = MedicationEntity(
                profileId = profileId,
                nationalCode = nationalCode,
                name = name,
                description = description,
                notes = notes
            )
            medicationDao.insertMedication(entity)
            Result.success(entity.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMedication(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = medicationDao.getMedicationById(id)
            if (entity != null) {
                medicationDao.deleteMedication(entity)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Medication not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun MedicationEntity.toDomain() = SavedMedication(
        id = id,
        profileId = profileId,
        nationalCode = nationalCode,
        name = name,
        description = description,
        notes = notes,
        startDate = startDate,
        isActive = active
    )
}
