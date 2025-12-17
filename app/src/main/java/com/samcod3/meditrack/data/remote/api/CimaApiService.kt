package com.samcod3.meditrack.data.remote.api

import com.samcod3.meditrack.data.remote.dto.MedicationDto
import com.samcod3.meditrack.data.remote.dto.LeafletSectionDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CimaApiService {
    
    /**
     * Search medication by national code (código nacional)
     * The national code is embedded in the barcode of Spanish medications
     */
    @GET("medicamento")
    suspend fun getMedicationByNationalCode(
        @Query("cn") nationalCode: String
    ): Response<MedicationDto>
    
    /**
     * Search medications by name
     */
    @GET("medicamentos")
    suspend fun searchMedicationsByName(
        @Query("nombre") name: String
    ): Response<List<MedicationDto>>
    
    /**
     * Get leaflet section content by registration number
     * @param nregistro Registration number of the medication
     * @param seccion Section number (1-6)
     */
    @GET("docSegmentado")
    suspend fun getLeafletSection(
        @Query("nregistro") registrationNumber: String,
        @Query("seccion") section: Int
    ): Response<LeafletSectionDto>
    
    @GET
    suspend fun downloadUrl(@retrofit2.http.Url url: String): Response<okhttp3.ResponseBody>
}

