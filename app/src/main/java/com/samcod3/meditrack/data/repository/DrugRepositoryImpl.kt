package com.samcod3.meditrack.data.repository

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
    
    override suspend fun getMedicationByNationalCode(nationalCode: String): Result<Medication> {
        return withContext(Dispatchers.IO) {
            try {
                val response = cimaApi.getMedicationByNationalCode(nationalCode)
                if (response != null) {
                    Result.success(response.toDomain(nationalCode))
                } else {
                    Result.failure(Exception("Medicamento no encontrado"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getLeaflet(registrationNumber: String): Result<Leaflet> {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch all 6 sections in parallel
                val sectionDeferreds = (1..6).map { sectionNum ->
                    async {
                        try {
                            cimaApi.getLeafletSection(registrationNumber, sectionNum)
                        } catch (e: Exception) {
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
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getLeafletSection(registrationNumber: String, section: Int): Result<LeafletSection?> {
        return withContext(Dispatchers.IO) {
            try {
                val response = cimaApi.getLeafletSection(registrationNumber, section)
                if (response != null) {
                    Result.success(
                        LeafletSection(
                            number = section,
                            title = response.title ?: LeafletSection.SECTION_TITLES.getOrElse(section - 1) { "Sección $section" },
                            content = response.content ?: ""
                        )
                    )
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private suspend fun getMedicationByCode(registrationNumber: String): Result<Medication> {
        return try {
            // Search by registration number - we need to find by nregistro
            val medications = cimaApi.searchMedicationsByName("")
            val medication = medications.find { it.registrationNumber == registrationNumber }
            if (medication != null) {
                Result.success(medication.toDomain(null))
            } else {
                // Return a minimal medication object if not found
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
            }
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
