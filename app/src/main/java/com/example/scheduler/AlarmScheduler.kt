package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.Alarm
import com.example.receiver.AlarmReceiver
import java.util.*

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_LABEL", alarm.label)
            putExtra("ALARM_SHAKES", alarm.shakesRequired)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val triggerTime = getNextTriggerTime(alarm.hour, alarm.minute, alarm)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Alarm ${alarm.id} scheduled for ${Date(triggerTime)}")
        } catch (e: SecurityException) {
            // Fallback for security exceptions on exact alarms
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.e("AlarmScheduler", "SecurityException. Rescheduled on lower precision.", e)
        }
    }

    fun cancel(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("AlarmScheduler", "Alarm ${alarm.id} canceled.")
        }
    }

    private fun getNextTriggerTime(hour: Int, minute: Int, alarm: Alarm): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Check if target time has already passed today
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Handle repeat days
        val hasRepeatDays = alarm.monday || alarm.tuesday || alarm.wednesday || 
                            alarm.thursday || alarm.friday || alarm.saturday || alarm.sunday

        if (hasRepeatDays) {
            // Find the next active day
            var daysFound = false
            for (i in 0..7) {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val isDayActive = when (dayOfWeek) {
                    Calendar.MONDAY -> alarm.monday
                    Calendar.TUESDAY -> alarm.tuesday
                    Calendar.WEDNESDAY -> alarm.wednesday
                    Calendar.THURSDAY -> alarm.thursday
                    Calendar.FRIDAY -> alarm.friday
                    Calendar.SATURDAY -> alarm.saturday
                    Calendar.SUNDAY -> alarm.sunday
                    else -> false
                }
                if (isDayActive && calendar.timeInMillis > System.currentTimeMillis()) {
                    daysFound = true
                    break
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (!daysFound) {
                // Keep the tomorrow setting
            }
        }

        return calendar.timeInMillis
    }
}
