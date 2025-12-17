package com.samcod3.meditrack.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives alarm broadcasts and shows notifications.
 */
class ReminderReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ReminderReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderAlarmScheduler.ACTION_REMINDER) return
        
        val reminderId = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_REMINDER_ID) ?: return
        val medicationName = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medicamento"
        val dosage = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_DOSAGE)
        
        Log.d(TAG, "Received reminder: $reminderId for $medicationName")
        
        val notificationHelper = ReminderNotificationHelper(context)
        notificationHelper.showReminderNotification(reminderId, medicationName, dosage)
        
        // TODO: Reschedule for next occurrence if it's a repeating alarm
        // This would require accessing the repository, which is tricky in a BroadcastReceiver
        // Consider using WorkManager for more complex scheduling
    }
}
