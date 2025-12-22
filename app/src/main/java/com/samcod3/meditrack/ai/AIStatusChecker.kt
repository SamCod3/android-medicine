package com.samcod3.meditrack.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Utility object to check Gemini Nano availability status.
 * This is separate from the service to allow quick status checks.
 */
object AIStatusChecker {
    
    private const val TAG = "AIStatusChecker"
    
    enum class AIStatus {
        CHECKING,
        AVAILABLE,
        DOWNLOADABLE,
        DOWNLOADING,
        UNAVAILABLE
    }
    
    private var cachedStatus: AIStatus? = null
    private var generativeModel: GenerativeModel? = null
    
    /**
     * Check if Gemini Nano is available on this device.
     * Returns cached result if already checked.
     */
    suspend fun checkStatus(forceRefresh: Boolean = false): AIStatus {
        // Return cached status if available and not forcing refresh
        if (!forceRefresh) {
            cachedStatus?.let { return it }
        }
        
        return try {
            if (generativeModel == null) {
                generativeModel = Generation.getClient()
            }
            
            val featureStatus = withContext(Dispatchers.IO) {
                generativeModel!!.checkStatus()
            }
            
            val status = when (featureStatus) {
                FeatureStatus.AVAILABLE -> AIStatus.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> AIStatus.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> AIStatus.DOWNLOADING
                else -> AIStatus.UNAVAILABLE
            }
            
            Log.d(TAG, "Gemini Nano status: $status")
            cachedStatus = status
            status
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Gemini Nano status", e)
            cachedStatus = AIStatus.UNAVAILABLE
            AIStatus.UNAVAILABLE
        }
    }
    
    /**
     * Start downloading the Gemini Nano model.
     * Returns a flow of download status updates.
     */
    fun startDownload(): Flow<AIStatus> = flow {
        emit(AIStatus.DOWNLOADING)
        cachedStatus = AIStatus.DOWNLOADING
        
        try {
            if (generativeModel == null) {
                generativeModel = Generation.getClient()
            }
            
            // Collect download flow - it will emit updates until download completes
            val downloadFlow = generativeModel!!.download()
            downloadFlow.collect { _ ->
                // Just keep emitting downloading status
                emit(AIStatus.DOWNLOADING)
            }
            
            // After flow completes, recheck actual status
            val finalStatus = checkStatus(forceRefresh = true)
            Log.d(TAG, "Download completed, final status: $finalStatus")
            emit(finalStatus)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Gemini Nano", e)
            cachedStatus = AIStatus.UNAVAILABLE
            emit(AIStatus.UNAVAILABLE)
        }
    }
    
    /**
     * Get status description in Spanish
     */
    fun getStatusDescription(status: AIStatus): String = when (status) {
        AIStatus.CHECKING -> "Comprobando IA..."
        AIStatus.AVAILABLE -> "✓ Gemini Nano disponible"
        AIStatus.DOWNLOADABLE -> "⬇ Gemini Nano disponible - Toca para descargar"
        AIStatus.DOWNLOADING -> "⏳ Descargando Gemini Nano..."
        AIStatus.UNAVAILABLE -> "✗ IA no disponible en este dispositivo"
    }
}
