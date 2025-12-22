package com.samcod3.meditrack.domain.repository

import com.samcod3.meditrack.domain.model.Leaflet
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication

/**
 * Repository interface for drug/medication data operations.
 * This interface is in the domain layer and should be implemented by the data layer.
 */
interface DrugRepository {
    suspend fun getMedicationByNationalCode(nationalCode: String): Result<Medication>
    suspend fun getMedicationByRegistrationNumber(registrationNumber: String): Result<Medication>
    suspend fun getLeaflet(medication: Medication): Result<Leaflet>
    @Deprecated("Use getLeaflet(medication)")
    suspend fun getLeaflet(registrationNumber: String): Result<Leaflet>
    suspend fun getLeafletSection(registrationNumber: String, section: Int): Result<LeafletSection?>
    suspend fun getLeafletHtml(medication: Medication): Result<String>
    suspend fun searchMedications(query: String): Result<List<Medication>>
}
