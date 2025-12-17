package com.samcod3.meditrack.domain.repository

import com.samcod3.meditrack.domain.model.SavedMedication
import kotlinx.coroutines.flow.Flow

interface UserMedicationRepository {
    fun getMedicationsForProfile(profileId: String): Flow<List<SavedMedication>>
    suspend fun saveMedication(
        profileId: String, 
        nationalCode: String, 
        name: String, 
        description: String?,
        notes: String?
    ): Result<String>
    suspend fun deleteMedication(id: String): Result<Unit>
}
