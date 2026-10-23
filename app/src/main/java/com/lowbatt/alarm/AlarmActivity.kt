package com.lowbatt.alarm

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = if (i != null) {
            i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 /
                i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        } else 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#C62828"))
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "LOW BATTERY"; textSize = 34f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "$level%"; textSize = 80f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Plug in your charger now"; textSize = 20f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 64)
        })
        root.addView(Button(this).apply {
            text = "STOP ALARM"; textSize = 22f
            setOnClickListener {
                AlarmPlayer.stop()
                getSystemService(NotificationManager::class.java).cancel(BatteryService.ID_ALARM)
                finish()
            }
        })
        setContentView(root)
    }
}
