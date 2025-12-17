package com.samcod3.meditrack.di

import androidx.room.Room
import com.samcod3.meditrack.data.local.db.MediTrackDatabase
import com.samcod3.meditrack.data.repository.DrugRepositoryImpl
import com.samcod3.meditrack.data.repository.ProfileRepositoryImpl
import com.samcod3.meditrack.data.repository.UserMedicationRepositoryImpl
import com.samcod3.meditrack.domain.repository.DrugRepository
import com.samcod3.meditrack.domain.repository.ProfileRepository
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    // Database
    single {
        Room.databaseBuilder(
            androidContext(),
            MediTrackDatabase::class.java,
            "meditrack_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    // DAOs
    single { get<MediTrackDatabase>().profileDao }
    single { get<MediTrackDatabase>().medicationDao }
    
    // Repositories
    single<DrugRepository> { DrugRepositoryImpl(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get()) }
    single<UserMedicationRepository> { UserMedicationRepositoryImpl(get()) }
}
