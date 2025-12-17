package com.samcod3.meditrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.samcod3.meditrack.data.local.dao.ProfileDao
import com.samcod3.meditrack.data.local.entity.ProfileEntity

@Database(
    entities = [
        ProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MediTrackDatabase : RoomDatabase() {
    abstract val profileDao: ProfileDao
}
