package com.samcod3.meditrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.samcod3.meditrack.data.local.dao.BackupDao
import com.samcod3.meditrack.data.local.dao.MedicationDao
import com.samcod3.meditrack.data.local.dao.ProfileDao
import com.samcod3.meditrack.data.local.dao.ReminderDao
import com.samcod3.meditrack.data.local.dao.SectionSummaryCacheDao
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.data.local.entity.ProfileEntity
import com.samcod3.meditrack.data.local.entity.ReminderEntity
import com.samcod3.meditrack.data.local.entity.SectionSummaryCacheEntity

@Database(
    entities = [
        ProfileEntity::class,
        MedicationEntity::class,
        ReminderEntity::class,
        SectionSummaryCacheEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class MediTrackDatabase : RoomDatabase() {
    abstract val profileDao: ProfileDao
    abstract val medicationDao: MedicationDao
    abstract val reminderDao: ReminderDao
    abstract val sectionSummaryCacheDao: SectionSummaryCacheDao
    abstract val backupDao: BackupDao
}
