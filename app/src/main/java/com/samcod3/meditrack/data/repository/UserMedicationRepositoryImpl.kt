package com.samcod3.meditrack.data.repository

import com.samcod3.meditrack.data.local.dao.MedicationDao
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.domain.model.SavedMedication
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
            // Check exact match by nationalCode (idempotency)
            val existing = medicationDao.getMedicationByNationalCodeAndProfile(nationalCode, profileId)
            if (existing != null) {
                return@withContext Result.success(existing.id)
            }
            
            // Insert new medication (no fuzzy deduplication - different CN = different medication)
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
