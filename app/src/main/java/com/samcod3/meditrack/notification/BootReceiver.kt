package com.samcod3.meditrack.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.samcod3.meditrack.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Reschedules all reminders after device boot.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    private val reminderRepository: ReminderRepository by inject()
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        
        Log.d(TAG, "Boot completed, rescheduling reminders")
        
        val scheduler = ReminderAlarmScheduler(context)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = reminderRepository.getAllEnabledReminders().first()
                reminders.forEach { reminder ->
                    scheduler.scheduleReminder(reminder)
                }
                Log.d(TAG, "Rescheduled ${reminders.size} reminders")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule reminders", e)
            }
        }
    }
}
