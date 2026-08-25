package com.example.warrantyvault

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Increment channel ID to 'v2' so Android updates channel audio settings
        val channelId = "warranty_alerts_channel_v2"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Fetch system default alarm sound URI
        val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Create Notification Channel with high importance and audio attributes for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Warranty Expiration Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Plays alarm audio when item warranties expire"
                setSound(alarmSoundUri, audioAttributes)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val itemName = intent.getStringExtra("item_name") ?: "Stored Item"

        // Build system notification with alarm sound and vibration pattern
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Warranty Vault Expiration Alert")
            .setContentText("Warning: Warranty for $itemName expires soon!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(alarmSoundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}