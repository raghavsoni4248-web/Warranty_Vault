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
        val itemName = intent.getStringExtra("item_name") ?: "Warranty Item"
        val daysLeft = intent.getIntExtra("days_left", 30)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "warranty_priority_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Tiered Warranty Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Priority alerts for 30, 15, and 1 day warranty expirations"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val (accentColor, alertTitle, alertMessage) = when (daysLeft) {
            30 -> Triple(
                Color.parseColor("#2563EB"), // Blue
                "🟦 30 Days Left: $itemName",
                "Your warranty for $itemName expires in 30 days. Time to inspect the item!"
            )
            15 -> Triple(
                Color.parseColor("#D97706"), // Yellow / Amber
                "🟨 15 Days Warning: $itemName",
                "Only 15 days remaining on your $itemName warranty."
            )
            1 -> Triple(
                Color.parseColor("#DC2626"), // Red
                "🟥 FINAL DAY: $itemName Expiring Tomorrow!",
                "Urgent! Your warranty for $itemName expires in 24 hours."
            )
            else -> Triple(
                Color.parseColor("#2563EB"),
                "Warranty Alert: $itemName",
                "Your warranty expiration is approaching."
            )
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