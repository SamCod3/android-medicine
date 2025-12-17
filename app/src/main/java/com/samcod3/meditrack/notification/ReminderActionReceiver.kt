package com.samcod3.meditrack.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Receives notification action button clicks.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ReminderActionReceiver"
        const val ACTION_MARK_TAKEN = "com.samcod3.meditrack.MARK_TAKEN"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        
        when (intent.action) {
            ACTION_MARK_TAKEN -> {
                Log.d(TAG, "Marking reminder $reminderId as taken")
                // Dismiss the notification
                NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
                // TODO: Log this dose as taken in database
            }
        }
    }
}
