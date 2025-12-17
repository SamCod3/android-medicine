package com.samcod3.meditrack.data.repository

import com.samcod3.meditrack.domain.model.Leaflet
import com.samcod3.meditrack.domain.model.LeafletSection
import com.samcod3.meditrack.domain.model.Medication

interface DrugRepository {
    suspend fun getMedicationByNationalCode(nationalCode: String): Result<Medication>
    // Changed signature to accept Medication to have URL access
    suspend fun getLeaflet(medication: Medication): Result<Leaflet>
    @Deprecated("Use getLeaflet(medication)")
    suspend fun getLeaflet(registrationNumber: String): Result<Leaflet>
    suspend fun getLeafletSection(registrationNumber: String, section: Int): Result<LeafletSection?>
}
