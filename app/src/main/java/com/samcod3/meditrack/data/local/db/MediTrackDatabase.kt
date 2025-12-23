package com.samcod3.meditrack.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.samcod3.meditrack.data.local.dao.BackupDao
import com.samcod3.meditrack.data.local.dao.MedicationDao
import com.samcod3.meditrack.data.local.dao.ProfileDao
import com.samcod3.meditrack.data.local.dao.ReminderDao
import com.samcod3.meditrack.data.local.dao.SectionSummaryCacheDao
import com.samcod3.meditrack.data.local.dao.SummaryCacheDao
import com.samcod3.meditrack.data.local.entity.MedicationEntity
import com.samcod3.meditrack.data.local.entity.ProfileEntity
import com.samcod3.meditrack.data.local.entity.ReminderEntity
import com.samcod3.meditrack.data.local.entity.SectionSummaryCacheEntity
import com.samcod3.meditrack.data.local.entity.SummaryCacheEntity

@Database(
    entities = [
        ProfileEntity::class,
        MedicationEntity::class,
        ReminderEntity::class,
        SummaryCacheEntity::class,
        SectionSummaryCacheEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class MediTrackDatabase : RoomDatabase() {
    abstract val profileDao: ProfileDao
    abstract val medicationDao: MedicationDao
    abstract val reminderDao: ReminderDao
    abstract val summaryCacheDao: SummaryCacheDao
    abstract val sectionSummaryCacheDao: SectionSummaryCacheDao
    abstract val backupDao: BackupDao
}
