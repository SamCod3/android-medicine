package com.samcod3.meditrack.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE profileId = :profileId AND active = 1 ORDER BY startDate DESC")
    fun getActiveMedicationsForProfile(profileId: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE profileId = :profileId ORDER BY startDate DESC")
    fun getAllMedicationsForProfile(profileId: String): Flow<List<MedicationEntity>>
    
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: String): MedicationEntity?
    
    @Query("SELECT * FROM medications WHERE nationalCode = :nationalCode AND profileId = :profileId AND active = 1 LIMIT 1")
    suspend fun getMedicationByNationalCodeAndProfile(nationalCode: String, profileId: String): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: MedicationEntity)

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Delete
    suspend fun deleteMedication(medication: MedicationEntity)
}
