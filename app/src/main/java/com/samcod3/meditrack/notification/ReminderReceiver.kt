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
        val profileId = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_PROFILE_ID) ?: ""
        val profileName = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_PROFILE_NAME) ?: ""
        
        Log.d(TAG, "Received reminder: $reminderId for $medicationName (profile: $profileName)")
        
        val notificationHelper = ReminderNotificationHelper(context)
        notificationHelper.showReminderNotification(
            reminderId = reminderId,
            medicationName = medicationName,
            dosage = dosage,
            profileId = profileId,
            profileName = profileName
        )
        
        // TODO: Reschedule for next occurrence if it's a repeating alarm
    }
}
