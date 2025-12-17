package com.samcod3.meditrack.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MedicationDto(
    @Json(name = "nregistro") val registrationNumber: String?,
    @Json(name = "nombre") val name: String?,
    @Json(name = "labtitular") val laboratory: String?,
    @Json(name = "cpresc") val prescriptionCondition: String?,
    @Json(name = "estado") val status: StateDto?,
    @Json(name = "comerc") val commercialized: Boolean?,
    @Json(name = "conduc") val affectsDriving: Boolean?,
    @Json(name = "triangulo") val hasTriangle: Boolean?,
    @Json(name = "huerfano") val isOrphan: Boolean?,
    @Json(name = "biosimilar") val isBiosimilar: Boolean?,
    @Json(name = "ema") val isEma: Boolean?,
    @Json(name = "docs") val documents: List<DocumentDto>?,
    @Json(name = "fotos") val photos: List<PhotoDto>?,
    @Json(name = "principiosActivos") val activeIngredients: List<ActiveIngredientDto>?,
    @Json(name = "atcs") val atcCodes: List<AtcDto>?,
    @Json(name = "presentaciones") val presentations: List<PresentationDto>?
)

@JsonClass(generateAdapter = true)
data class StateDto(
    @Json(name = "aut") val authorizationDate: String?,
    @Json(name = "susp") val suspensionDate: String?,
    @Json(name = "rev") val revocationDate: String?
)

@JsonClass(generateAdapter = true)
data class DocumentDto(
    @Json(name = "tipo") val type: Int?, // 1 = Ficha técnica, 2 = Prospecto
    @Json(name = "url") val url: String?,
    @Json(name = "urlHtml") val htmlUrl: String?,
    @Json(name = "sepiDet") val dated: String?
)

@JsonClass(generateAdapter = true)
data class PhotoDto(
    @Json(name = "tipo") val type: String?,
    @Json(name = "url") val url: String?
)

@JsonClass(generateAdapter = true)
data class ActiveIngredientDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "nombre") val name: String?,
    @Json(name = "cantidad") val quantity: String?,
    @Json(name = "unidad") val unit: String?
)

@JsonClass(generateAdapter = true)
data class AtcDto(
    @Json(name = "codigo") val code: String?,
    @Json(name = "nombre") val name: String?
)

@JsonClass(generateAdapter = true)
data class PresentationDto(
    @Json(name = "cn") val nationalCode: String?,
    @Json(name = "nombre") val name: String?,
    @Json(name = "estado") val status: StateDto?,
    @Json(name = "comerc") val commercialized: Boolean?,
    @Json(name = "psum") val hasSupplyProblem: Boolean?
)
