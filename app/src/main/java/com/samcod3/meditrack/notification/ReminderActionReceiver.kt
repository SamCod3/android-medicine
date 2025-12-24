package com.samcod3.meditrack.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.samcod3.meditrack.data.local.dao.DoseLogDao
import com.samcod3.meditrack.data.local.entity.DoseLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar

/**
 * Receives notification action button clicks (Taken/Skipped).
 */
class ReminderActionReceiver : BroadcastReceiver(), KoinComponent {
    
    companion object {
        private const val TAG = "ReminderActionReceiver"
        const val ACTION_MARK_TAKEN = "com.samcod3.meditrack.MARK_TAKEN"
        const val ACTION_MARK_SKIPPED = "com.samcod3.meditrack.MARK_SKIPPED"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
    }
    
    private val doseLogDao: DoseLogDao by inject()
    
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: ""
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: ""
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: ""
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        
        val status = when (intent.action) {
            ACTION_MARK_TAKEN -> DoseLogEntity.STATUS_TAKEN
            ACTION_MARK_SKIPPED -> DoseLogEntity.STATUS_SKIPPED
            else -> return
        }
        
        Log.d(TAG, "Marking reminder $reminderId as $status")
        
        // Dismiss the notification
        NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
        
        // Save to database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = getStartOfDay()
                val log = DoseLogEntity(
                    reminderId = reminderId,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    profileId = profileId,
                    scheduledDate = today,
                    scheduledHour = hour,
                    scheduledMinute = minute,
                    status = status
                )
                doseLogDao.insert(log)
                Log.d(TAG, "Saved dose log: $status")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save dose log", e)
            }
        }
    }
    
    private fun getStartOfDay(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
