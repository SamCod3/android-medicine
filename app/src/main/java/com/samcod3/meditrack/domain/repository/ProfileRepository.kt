package com.samcod3.meditrack.domain.repository

import com.samcod3.meditrack.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getAllProfiles(): Flow<List<Profile>>
    suspend fun getProfileById(id: String): Result<Profile?>
    suspend fun createProfile(name: String, color: Long): Result<String>
    suspend fun deleteProfile(profile: Profile): Result<Unit>
    suspend fun updateProfile(profile: Profile): Result<Unit>
}
