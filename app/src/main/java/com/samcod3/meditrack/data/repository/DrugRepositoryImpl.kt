package com.samcod3.meditrack.data.repository

import android.util.Log
import com.samcod3.meditrack.data.remote.api.CimaApiService
import com.samcod3.meditrack.data.remote.dto.MedicationDto
import com.samcod3.meditrack.domain.model.ActiveIngredient
import com.samcod3.meditrack.domain.model.Leaflet
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class DrugRepositoryImpl(
    private val cimaApi: CimaApiService
) : DrugRepository {
    
    companion object {
        private const val TAG = "DrugRepository"
    }
    
    override suspend fun getMedicationByNationalCode(nationalCode: String): Result<Medication> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching medication for CN: $nationalCode")
                val response = cimaApi.getMedicationByNationalCode(nationalCode)
                
                Log.d(TAG, "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
                
                if (response.isSuccessful && response.body() != null) {
                    val medication = response.body()!!.toDomain(nationalCode)
                    Log.d(TAG, "Medication found: ${medication.name}")
                    Result.success(medication)
                } else {
                    Log.w(TAG, "Medication not found. Response code: ${response.code()}")
                    Result.failure(Exception("Medicamento no encontrado (código: ${response.code()})"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching medication", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getLeaflet(registrationNumber: String): Result<Leaflet> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching leaflet for registration: $registrationNumber")
                
                // Fetch all 6 sections in parallel
                val sectionDeferreds = (1..6).map { sectionNum ->
                    async {
                        try {
                            val response = cimaApi.getLeafletSection(registrationNumber, sectionNum)
                            if (response.isSuccessful) response.body() else null
                        } catch (e: Exception) {
                            Log.w(TAG, "Error fetching section $sectionNum", e)
                            null
                        }
                    }
                }
                
                val sections = sectionDeferreds.awaitAll()
                    .mapIndexedNotNull { index, dto ->
                        dto?.let {
                            LeafletSection(
                                number = index + 1,
                                title = it.title ?: LeafletSection.SECTION_TITLES.getOrElse(index) { "Sección ${index + 1}" },
                                content = it.content ?: ""
                            )
                        }
                    }
                
                Log.d(TAG, "Fetched ${sections.size} sections")
                
                // Get medication info
                val medicationResult = getMedicationByCode(registrationNumber)
                if (medicationResult.isSuccess) {
                    Result.success(
                        Leaflet(
                            medication = medicationResult.getOrThrow(),
                            sections = sections
                        )
                    )
                } else {
                    Result.failure(Exception("No se pudo obtener información del medicamento"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching leaflet", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getLeafletSection(registrationNumber: String, section: Int): Result<LeafletSection?> {
        return withContext(Dispatchers.IO) {
            try {
                val response = cimaApi.getLeafletSection(registrationNumber, section)
                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!
                    Result.success(
                        LeafletSection(
                            number = section,
                            title = dto.title ?: LeafletSection.SECTION_TITLES.getOrElse(section - 1) { "Sección $section" },
                            content = dto.content ?: ""
                        )
                    )
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching section $section", e)
                Result.failure(e)
            }
        }
    }
    
    private suspend fun getMedicationByCode(registrationNumber: String): Result<Medication> {
        return try {
            // Return a minimal medication object - we already have the data from the scan
            Result.success(
                Medication(
                    registrationNumber = registrationNumber,
                    name = "Medicamento",
                    laboratory = "",
                    prescriptionRequired = false,
                    affectsDriving = false,
                    hasWarningTriangle = false,
                    activeIngredients = emptyList(),
                    leafletUrl = null,
                    photoUrl = null,
                    nationalCode = null
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun MedicationDto.toDomain(nationalCode: String?): Medication {
        return Medication(
            registrationNumber = registrationNumber ?: "",
            name = name ?: "Sin nombre",
            laboratory = laboratory ?: "",
            prescriptionRequired = prescriptionCondition?.isNotEmpty() == true,
            affectsDriving = affectsDriving ?: false,
            hasWarningTriangle = hasTriangle ?: false,
            activeIngredients = activeIngredients?.map {
                ActiveIngredient(
                    name = it.name ?: "",
                    quantity = it.quantity ?: "",
                    unit = it.unit ?: ""
                )
            } ?: emptyList(),
            leafletUrl = documents?.find { it.type == 2 }?.htmlUrl,
            photoUrl = photos?.firstOrNull()?.url,
            nationalCode = nationalCode ?: presentations?.firstOrNull()?.nationalCode
        )
    }
}

