package com.samcod3.meditrack.di

import androidx.room.Room
import com.samcod3.meditrack.ai.AIService
import com.samcod3.meditrack.ai.FallbackOcrService
import com.samcod3.meditrack.ai.GeminiNanoService
import com.samcod3.meditrack.ai.HybridAIService
import com.samcod3.meditrack.data.local.db.MediTrackDatabase
import com.samcod3.meditrack.data.repository.DrugRepositoryImpl
import com.samcod3.meditrack.data.repository.ProfileRepositoryImpl
import com.samcod3.meditrack.data.repository.ReminderRepositoryImpl
import com.samcod3.meditrack.data.repository.UserMedicationRepositoryImpl
import com.samcod3.meditrack.domain.repository.DrugRepository
import com.samcod3.meditrack.domain.repository.ProfileRepository
import com.samcod3.meditrack.domain.repository.ReminderRepository
import com.samcod3.meditrack.domain.repository.UserMedicationRepository
import com.samcod3.meditrack.domain.usecase.BackupUseCase
import com.samcod3.meditrack.domain.usecase.ImportTreatmentUseCase
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
    single { get<MediTrackDatabase>().reminderDao }
    single { get<MediTrackDatabase>().summaryCacheDao }
    
    // Repositories
    single<DrugRepository> { DrugRepositoryImpl(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get()) }
    single<UserMedicationRepository> { UserMedicationRepositoryImpl(get()) }
    single<ReminderRepository> { ReminderRepositoryImpl(get(), get()) }
    
    // AI Service - HybridAIService tries Gemini Nano first, falls back to regex
    single<AIService> {
        HybridAIService(
            geminiService = GeminiNanoService(),
            fallbackService = FallbackOcrService()
        )
    }
    
    // AI Leaflet Parser with summary cache
    single { com.samcod3.meditrack.ai.AILeafletParser(get(), get()) }
    
    // Use Cases
    single { ImportTreatmentUseCase(get(), get(), get(), get()) }
    single { BackupUseCase(get(), get(), get()) }
}

