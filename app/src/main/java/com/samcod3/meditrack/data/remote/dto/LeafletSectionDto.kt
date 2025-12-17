package com.samcod3.meditrack.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LeafletSectionDto(
    @Json(name = "seccion") val section: Int?,
    @Json(name = "titulo") val title: String?,
    @Json(name = "contenido") val content: String?
)
