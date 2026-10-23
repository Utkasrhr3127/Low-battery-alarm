package com.lowbatt.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class BatteryService : Service() {

    companion object {
        const val CH_MONITOR = "monitor"
        const val CH_ALARM = "alarm"
        const val ID_MONITOR = 1
        const val ID_ALARM = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private var armed = true
    private var lastLevel = 100
    private var charging = false
    private var fired = 0

    private fun moreAllowed(): Boolean {
        val max = Prefs.maxAlarms(this)
        return max == 0 || fired < max
    }

    private val autoStop = Runnable {
        AlarmPlayer.stop()
        getSystemService(NotificationManager::class.java).cancel(ID_ALARM)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { handle(i) }
    }

    private val repeater = object : Runnable {
        override fun run() {
            if (Prefs.enabled(this@BatteryService) && !charging &&
                lastLevel <= Prefs.threshold(this@BatteryService) && moreAllowed()) {
                fire()
                scheduleRepeat()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "Battery watcher", NotificationManager.IMPORTANCE_LOW)
        )
        val alarmCh = NotificationChannel(CH_ALARM, "Low battery alarm", NotificationManager.IMPORTANCE_HIGH)
        alarmCh.setSound(null, null)
        alarmCh.enableVibration(false)
        alarmCh.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        try { alarmCh.setBypassDnd(true) } catch (e: Exception) {}
        nm.createNotificationChannel(alarmCh)

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun startWatching() {
        val n = Notification.Builder(this, CH_MONITOR)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle("Low battery alarm is ON")
            .setContentText("Will ring at ${Prefs.threshold(this)}%")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_MONITOR, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_MONITOR, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startWatching()
        when (intent?.action) {
            "STOP" -> {
                AlarmPlayer.stop()
                getSystemService(NotificationManager::class.java).cancel(ID_ALARM)
            }
            "TEST" -> {
                val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                sticky?.let { readBattery(it) }
                fire()
                fired = 0
            }
            else -> {
                val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                sticky?.let { handle(it) }
            }
        }
        return START_STICKY
    }

    private fun readBattery(i: Intent): Boolean {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return false
        lastLevel = level * 100 / scale
        charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return true
    }

    private fun handle(i: Intent) {
        if (!readBattery(i)) return
        if (!Prefs.enabled(this)) return
        val th = Prefs.threshold(this)

        if (charging || lastLevel > th) {
            armed = true
            fired = 0
            handler.removeCallbacks(repeater)
            if (charging && AlarmPlayer.ringing) {
                AlarmPlayer.stop()
                getSystemService(NotificationManager::class.java).cancel(ID_ALARM)
            }
        } else if (armed) {
            armed = false
            fire()
            scheduleRepeat()
        }
    }

    private fun scheduleRepeat() {
        handler.removeCallbacks(repeater)
        val mins = Prefs.repeatMin(this)
        if (mins > 0 && moreAllowed()) handler.postDelayed(repeater, mins * 60_000L)
    }

    @Suppress("DEPRECATION")
    private fun fire() {
        fired++
        AlarmPlayer.start(this)
        handler.removeCallbacks(autoStop)
        val secs = Prefs.ringSeconds(this)
        if (secs > 0) handler.postDelayed(autoStop, secs * 1000L)

        val openIntent = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val full = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, BatteryService::class.java).setAction("STOP")
        val stop = PendingIntent.getForegroundService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = Notification.Builder(this, CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle("LOW BATTERY: $lastLevel%")
            .setContentText("Charge your phone now!")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ALARM", stop)
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_ALARM, n)
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        handler.removeCallbacksAndMessages(null)
        AlarmPlayer.stop()
        getSystemService(NotificationManager::class.java).cancel(ID_ALARM)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
