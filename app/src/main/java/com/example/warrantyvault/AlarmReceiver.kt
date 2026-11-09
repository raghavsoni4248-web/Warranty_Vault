package com.example.warrantyvault

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val isMaintenance = intent.getBooleanExtra("is_maintenance", false)
        val itemName = intent.getStringExtra("item_name") ?: "Item"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "warranty_priority_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Vault Alerts & Upkeep",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for warranty expirations and routine maintenance tasks"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val accentColor: Int
        val alertTitle: String
        val alertMessage: String

        if (isMaintenance) {
            val taskTitle = intent.getStringExtra("task_title") ?: "Scheduled Task"
            accentColor = Color.parseColor("#059669") // Green
            alertTitle = "🛠️ Maintenance Due: $itemName"
            alertMessage = "Time for routine care: $taskTitle on your $itemName."
        } else {
            val daysLeft = intent.getIntExtra("days_left", 30)
            val triple = when (daysLeft) {
                30 -> Triple(Color.parseColor("#2563EB"), "🟦 30 Days Left: $itemName", "Expires in 30 days.")
                15 -> Triple(Color.parseColor("#D97706"), "🟨 15 Days Warning: $itemName", "15 days remaining on warranty.")
                1 -> Triple(Color.parseColor("#DC2626"), "🟥 FINAL DAY: $itemName Expiring!", "Expires in 24 hours.")
                else -> Triple(Color.parseColor("#2563EB"), "Warranty Alert: $itemName", "Expiration approaching.")
            }
            accentColor = triple.first
            alertTitle = triple.second
            alertMessage = triple.third
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(alertTitle)
            .setContentText(alertMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alertMessage))
            .setColor(accentColor)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}