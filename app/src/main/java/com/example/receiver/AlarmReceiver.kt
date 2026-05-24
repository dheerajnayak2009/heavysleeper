package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val alarmLabel = intent.getStringExtra("ALARM_LABEL") ?: "Wake Up!"
        val shakesRequired = intent.getIntExtra("ALARM_SHAKES", 20)

        Log.d("AlarmReceiver", "Alarm broadcast received! ID: $alarmId, Label: $alarmLabel")

        // 1. Create notification channel for modern Android
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "heavy_sleeper_alarm_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Heavy Alarm Siren Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Severe volume and vibration notification for heavy sleepers."
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Setup intent to launch MainActivity in firing takeover mode
        val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ALARM_FIRING", true)
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", alarmLabel)
            putExtra("ALARM_SHAKES", shakesRequired)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            if (alarmId != -1) alarmId else 9999,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // 3. Build aggressive full-screen-compliant notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🚨 HEAVY SLEEPER ALARM TRIGGERED!")
            .setContentText("SHAKE PHONE IMMEDIATELY: $alarmLabel")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true) // Force full screen takeover on lockscreen
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        // Give the notification a severe alert look
        notificationManager.notify(if (alarmId != -1) alarmId else 9999, builder.build())

        // 4. Try direct activity launch as fallback to guarantee screen override
        try {
            context.startActivity(mainActivityIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Unable to start Activity directly. Relying on Notification FullScreenIntent fallback.", e)
        }
    }
}
