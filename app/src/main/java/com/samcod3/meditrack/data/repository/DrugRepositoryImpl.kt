package com.samcod3.meditrack.data.repository

import android.util.Log
import com.samcod3.meditrack.data.remote.api.CimaApiService
import com.samcod3.meditrack.data.remote.dto.MedicationDto
import com.samcod3.meditrack.data.remote.parser.LeafletHtmlParser
import com.samcod3.meditrack.domain.model.ActiveIngredient
import com.samcod3.meditrack.domain.model.Leaflet
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication
import com.samcod3.meditrack.domain.repository.DrugRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class DrugRepositoryImpl(
    private val cimaApi: CimaApiService
) : DrugRepository {
    
    companion object {
        private const val TAG = "DrugRepository"
        // Standard section titles prefixes
        private val SECTION_PREFIXES = listOf(
            "1. Qué es",
            "2. Qué necesita saber", "2. Antes de tomar",
            "3. Cómo tomar", "3. Cómo usar",
            "4. Posibles efectos",
            "5. Conservación",
            "6. Contenido"
        )
    }
    
    override suspend fun getMedicationByNationalCode(nationalCode: String): Result<Medication> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching medication for CN: $nationalCode")
                val response = cimaApi.getMedicationByNationalCode(nationalCode)
                
                if (response.isSuccessful && response.body() != null) {
                    val medication = response.body()!!.toDomain(nationalCode)
                    Result.success(medication)
                } else {
                    // Fallback: If CN has 7 digits (it might include the check digit), try with 6
                    if (nationalCode.length == 7) {
                         val cn6 = nationalCode.substring(0, 6)
                         Log.d(TAG, "7-digit CN failed, retrying with 6 digits: $cn6")
                         val retryResponse = cimaApi.getMedicationByNationalCode(cn6)
                         if (retryResponse.isSuccessful && retryResponse.body() != null) {
                             val medication = retryResponse.body()!!.toDomain(cn6)
                             return@withContext Result.success(medication)
                         }
                    }
                    Result.failure(Exception("No encontrado: $nationalCode"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching medication", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getMedicationByRegistrationNumber(registrationNumber: String): Result<Medication> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching medication for NReg: $registrationNumber")
                val response = cimaApi.getMedicationByRegistrationNumber(registrationNumber)
                
                if (response.isSuccessful && response.body() != null) {
                    val medication = response.body()!!.toDomain(null)
                    Result.success(medication)
                } else {
                    Result.failure(Exception("No encontrado por registro: $registrationNumber"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching medication by nreg", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getLeaflet(registrationNumber: String): Result<Leaflet> {
        return withContext(Dispatchers.IO) {
            try {
                // Get medication info first to have the URL if needed
                val medicationResult = getMedicationByCode(registrationNumber)
                if (medicationResult.isFailure) {
                    return@withContext Result.failure(medicationResult.exceptionOrNull()!!)
                }
                val medication = medicationResult.getOrThrow()

                Log.d(TAG, "Fetching leaflet for registration: $registrationNumber")
                
                // 1. Try fetching segmented sections from API
                val sectionDeferreds = (1..6).map { sectionNum ->
                    async {
                        try {
                            val response = cimaApi.getLeafletSection(registrationNumber, sectionNum)
                            if (response.isSuccessful) response.body() else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                
                var sections = sectionDeferreds.awaitAll()
                    .mapIndexedNotNull { index, dto ->
                        dto?.let {
                            LeafletSection(
                                number = index + 1,
                                title = it.title ?: LeafletSection.SECTION_TITLES.getOrElse(index) { "Sección ${index + 1}" },
                                content = it.content ?: ""
                            )
                        }
                    }
                
                // 2. Fallback: Parse HTML if segmented content is missing
                if (sections.isEmpty() && !medication.leafletUrl.isNullOrBlank()) {
                    Log.d(TAG, "Segmented content missing, parsing HTML from: ${medication.leafletUrl}")
                    try {
                        val htmlResponse = cimaApi.downloadUrl(medication.leafletUrl)
                        if (htmlResponse.isSuccessful && htmlResponse.body() != null) {
                            val html = htmlResponse.body()!!.string()
                            val parsedSections = LeafletHtmlParser.parse(html)
                            if (parsedSections.isNotEmpty()) {
                                sections = parsedSections
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "HTML parsing failed", e)
                    }
                }
                
                Result.success(Leaflet(medication, sections))
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching leaflet", e)
                Result.failure(e)
            }
        }
    }


    override suspend fun getLeafletSection(registrationNumber: String, section: Int): Result<LeafletSection?> {
        // ... same as before
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
                Result.failure(e)
            }
        }
    }
    
    private suspend fun getMedicationByCode(registrationNumber: String): Result<Medication> {
        // Need to use getMedicationByNationalCode logic or search?
        // Actually we don't have an easy way to get medication just by registration number via API without CN.
        // But for getLeaflet we already HAVE the Medication object in the ViewModel usually.
        // However, this method signature getLeaflet takes only reg number. 
        // We cheated before by returning a dummy.
        // To get the URL, we might need to search by registration number?
        // Wait, CIMA API doesn't seem to have "get by registration number".
        // BUT, usually we call this AFTER getMedicationByNationalCode.
        // If we want the URL, we need the Medication object associated with this registration number.
        // If we don't have it (because we only passed reg number), we might fail to get the URL unless we search.
        // IMPROVEMENT: change getLeaflet to take Medication object or fetch it if possible.
        // Or assume the caller (ViewModel) has the URL and passes it.
        // Refactor: getLeaflet(medication: Medication).
        
        // But to avoid breaking Interface changes right now, let's look at getMedicationByCode usage.
        // In the previous code, getMedicationByCode returned a dummy.
        // If I want the leafletUrl, I need it.
        
        // WORKAROUND: The ViewModel has the medication.
        // But Repositories shouldn't depend on ViewModels.
        // Ideally getLeaflet interface should accept the data it needs (URL) or fetch it.
        
        // Since I'm in DrugRepositoryImpl, I can fetch by CN? I don't have CN here, only reg number.
        // BUT, I can see getMedicationByCode in previous implementation was mostly dummy.
        
        // Let's assume for this specific flow, we might need to change the interface of getLeaflet to accept `leafletUrl` as optional,
        // or accept `Medication` object.
        
        // Let's change `getLeaflet(registrationNumber: String)` to `getLeaflet(medication: Medication)`.
        // This requires updating interface and ViewModel. This is cleaner.
        return Result.failure(Exception("Use overload with Medication object"))
    }
    
    // OVERLOAD or Change Interface
    override suspend fun getLeaflet(medication: Medication): Result<Leaflet> {
         val registrationNumber = medication.registrationNumber
         // Copy logic from above, but now we have 'medication.leafletUrl' valid
         
         return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching leaflet for ${medication.name}")
                
                 val sectionDeferreds = (1..6).map { sectionNum ->
                    async {
                        try {
                            val response = cimaApi.getLeafletSection(registrationNumber, sectionNum)
                            if (response.isSuccessful) response.body() else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                
                var sections = sectionDeferreds.awaitAll()
                    .mapIndexedNotNull { index, dto ->
                        dto?.let {
                            LeafletSection(
                                number = index + 1,
                                title = it.title ?: LeafletSection.SECTION_TITLES.getOrElse(index) { "Sección ${index + 1}" },
                                content = it.content ?: ""
                            )
                        }
                    }
                
                if (sections.isEmpty() && !medication.leafletUrl.isNullOrBlank()) {
                     Log.d(TAG, "Parsing HTML from ${medication.leafletUrl}")
                     try {
                         val htmlResponse = cimaApi.downloadUrl(medication.leafletUrl!!)
                         if (htmlResponse.isSuccessful && htmlResponse.body() != null) {
                             val html = htmlResponse.body()!!.string()
                             val parsed = LeafletHtmlParser.parse(html)
                             if (parsed.isNotEmpty()) sections = parsed
                         }
                     } catch (e: Exception) {
                         Log.e(TAG, "HTML parsing failed", e)
                     }
                }
                
                Result.success(Leaflet(medication, sections))
            } catch (e: Exception) {
                Result.failure(e)
            }
         }
    }

    override suspend fun getLeafletHtml(medication: Medication): Result<String> {
        return withContext(Dispatchers.IO) {
            if (medication.leafletUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("URL de prospecto no disponible"))
            }

            try {
                Log.d(TAG, "Downloading leaflet HTML from: ${medication.leafletUrl}")
                val response = cimaApi.downloadUrl(medication.leafletUrl)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!.string())
                } else {
                    Result.failure(Exception("Error descargando prospecto: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading leaflet HTML", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun searchMedications(query: String): Result<List<Medication>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching medications with query: $query")
                val response = cimaApi.searchMedicationsByName(query)
                
                if (response.isSuccessful && response.body() != null) {
                    val searchResponse = response.body()!!
                    val medications = searchResponse.results?.map { it.toDomain(null) } ?: emptyList()
                    Result.success(medications)
                } else {
                    Result.failure(Exception("Error en la búsqueda (código: ${response.code()})"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching medications", e)
                Result.failure(e)
            }
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
