package com.samcod3.meditrack.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.samcod3.meditrack.domain.model.Reminder
import java.util.Calendar

/**
 * Schedules alarms for medication reminders.
 */
class ReminderAlarmScheduler(private val context: Context) {
    
    companion object {
        private const val TAG = "ReminderAlarmScheduler"
        const val ACTION_REMINDER = "com.samcod3.meditrack.REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_DOSAGE = "dosage"
    }
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    fun scheduleReminder(reminder: Reminder) {
        if (!reminder.enabled) {
            cancelReminder(reminder.id)
            return
        }
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_MEDICATION_NAME, reminder.medicationName)
            putExtra(EXTRA_DOSAGE, reminder.dosageFormatted)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val calendar = getNextTriggerTime(reminder)
        
        Log.d(TAG, "Scheduling reminder ${reminder.id} for ${calendar.time}")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fall back to inexact alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }
    
    fun cancelReminder(reminderId: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled reminder $reminderId")
    }
    
    private fun getNextTriggerTime(reminder: Reminder): Calendar {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // If time has passed today, schedule for tomorrow or next valid day
        if (trigger.before(now) || trigger.equals(now)) {
            trigger.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        // If specific days are set, find next valid day
        if (reminder.daysOfWeek != 0) {
            var daysChecked = 0
            while (daysChecked < 7) {
                val dayOfWeek = trigger.get(Calendar.DAY_OF_WEEK)
                val dayFlag = when (dayOfWeek) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 4
                    Calendar.THURSDAY -> 8
                    Calendar.FRIDAY -> 16
                    Calendar.SATURDAY -> 32
                    Calendar.SUNDAY -> 64
                    else -> 0
                }
                
                if ((reminder.daysOfWeek and dayFlag) != 0) {
                    break
                }
                
                trigger.add(Calendar.DAY_OF_YEAR, 1)
                daysChecked++
            }
        }
        
        return trigger
    }
}
