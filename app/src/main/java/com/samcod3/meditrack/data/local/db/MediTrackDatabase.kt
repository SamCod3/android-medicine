package com.samcod3.meditrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.samcod3.meditrack.data.local.dao.MedicationDao
import com.samcod3.meditrack.data.local.dao.ProfileDao
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.data.local.entity.ProfileEntity

@Database(
    entities = [
        ProfileEntity::class,
        MedicationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MediTrackDatabase : RoomDatabase() {
    abstract val profileDao: ProfileDao
    abstract val medicationDao: MedicationDao
}
