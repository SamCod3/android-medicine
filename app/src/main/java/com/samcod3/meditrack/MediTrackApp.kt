package com.samcod3.meditrack

import android.app.Application
import android.util.Log
import com.samcod3.meditrack.ai.AIService
import com.samcod3.meditrack.di.appModule
import com.samcod3.meditrack.di.dataModule
import com.samcod3.meditrack.di.networkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

class MediTrackApp : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@MediTrackApp)
            modules(
                appModule,
                dataModule,
                networkModule
            )
        }
        
        // Pre-warm Gemini Nano model in background
        preWarmGeminiNano()
    }
    
    /**
     * Pre-initialize Gemini Nano to avoid cold-start latency when viewing leaflets.
     * This runs in background and doesn't block app startup.
     */
    private fun preWarmGeminiNano() {
        applicationScope.launch {
            try {
                val aiService: AIService = getKoin().get()
                // Just check availability - this triggers lazy initialization
                val available = aiService.isAvailable()
                Log.d("MediTrackApp", "Gemini Nano pre-warmed. Available: $available")
            } catch (e: Exception) {
                Log.w("MediTrackApp", "Failed to pre-warm Gemini Nano", e)
            }
        }
    }
}
