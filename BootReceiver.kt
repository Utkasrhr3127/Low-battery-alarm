package com.lowbatt.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Prefs.enabled(c)) {
            c.startForegroundService(Intent(c, BatteryService::class.java))
        }
    }
}
