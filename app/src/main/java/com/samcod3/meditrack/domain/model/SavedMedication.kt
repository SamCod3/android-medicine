package com.samcod3.meditrack.domain.model

data class SavedMedication(
    val id: String,
    val profileId: String,
    val nationalCode: String,
    val name: String,
    val description: String?,
    val notes: String?,
    val startDate: Long,
    val isActive: Boolean
)
