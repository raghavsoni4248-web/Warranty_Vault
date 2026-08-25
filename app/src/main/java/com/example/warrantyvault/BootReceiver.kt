package com.example.warrantyvault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Reschedules AlarmManager alerts automatically upon Android system boot
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Background alert restoration logic runs here
        }
    }
}