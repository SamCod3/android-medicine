package com.samcod3.meditrack.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchResponseDto(
    @Json(name = "totalFilas") val totalRows: Int?,
    @Json(name = "pagina") val page: Int?,
    @Json(name = "tamanioPagina") val pageSize: Int?,
    @Json(name = "resultados") val results: List<MedicationDto>?
)
