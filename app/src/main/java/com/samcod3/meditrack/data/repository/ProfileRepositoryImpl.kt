package com.samcod3.meditrack.data.repository

import com.samcod3.meditrack.data.local.dao.ProfileDao
import com.samcod3.meditrack.data.local.entity.ProfileEntity
import com.samcod3.meditrack.domain.model.Profile
import com.samcod3.meditrack.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProfileRepositoryImpl(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override fun getAllProfiles(): Flow<List<Profile>> {
        return profileDao.getAllProfiles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getProfileById(id: String): Result<Profile?> = withContext(Dispatchers.IO) {
        try {
            val entity = profileDao.getProfileById(id)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createProfile(name: String, color: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val entity = ProfileEntity(
                name = name,
                avatarColor = color
            )
            profileDao.insertProfile(entity)
            Result.success(entity.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProfile(profile: Profile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = ProfileEntity(
                id = profile.id,
                name = profile.name,
                avatarColor = profile.avatarColor,
                createdAt = profile.createdAt
            )
            profileDao.deleteProfile(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateProfile(profile: Profile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = ProfileEntity(
                id = profile.id,
                name = profile.name,
                avatarColor = profile.avatarColor,
                createdAt = profile.createdAt
            )
            profileDao.updateProfile(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ProfileEntity.toDomain() = Profile(
        id = id,
        name = name,
        avatarColor = avatarColor,
        createdAt = createdAt
    )
}
