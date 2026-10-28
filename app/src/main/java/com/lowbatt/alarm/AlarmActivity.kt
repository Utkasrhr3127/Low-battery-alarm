package com.lowbatt.alarm

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class AlarmActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // closes itself once the alarm has gone quiet (no keep-screen-on, no wasted power)
    private val watcher = object : Runnable {
        override fun run() {
            if (!AlarmPlayer.ringing) finish() else h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // dim screen: black pixels cost almost nothing, and a lower brightness is easier on a weak battery
        val lp = window.attributes
        lp.screenBrightness = 0.4f
        window.attributes = lp
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = if (i != null) {
            i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 /
                i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        } else 0

        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#2B0A10"), Color.BLACK)
            )
            setPadding(dp(24), dp(24), dp(24), dp(28))
        }

        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        root.addView(TextView(this).apply {
            text = "LOW BATTERY"
            typeface = Typeface.MONOSPACE
            textSize = 14f
            letterSpacing = 0.3f
            setTextColor(Color.parseColor("#FF6B76"))
            gravity = Gravity.CENTER
        })

        root.addView(Ring(this, level), LinearLayout.LayoutParams(dp(250), dp(250)).apply {
            topMargin = dp(28); bottomMargin = dp(28)
        })

        root.addView(TextView(this).apply {
            text = "Plug in your charger"
            textSize = 22f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.parseColor("#F5E9EA"))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "The alarm stops by itself once you charge."
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.parseColor("#8A6A6E"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })

        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        root.addView(TextView(this).apply {
            text = "STOP ALARM"
            typeface = Typeface.MONOSPACE
            textSize = 15f
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#2B0A10"))
            setPadding(dp(8), dp(18), dp(8), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5E9EA"))
                cornerRadius = dp(40).toFloat()
            }
            setOnClickListener {
                AlarmPlayer.stop()
                getSystemService(NotificationManager::class.java).cancel(BatteryService.ID_ALARM)
                finish()
            }
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(root)
        h.postDelayed(watcher, 1500)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

// Battery ring: drawn once, no animation, no images
private class Ring(c: Context, val level: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF4F4")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height).toFloat()
        val sw = s * 0.05f
        p.strokeWidth = sw
        val r = RectF(sw, sw, s - sw, s - sw)
        p.color = Color.parseColor("#3A161B")
        cv.drawArc(r, 0f, 360f, false, p)
        p.color = Color.parseColor("#FF4D5A")
        cv.drawArc(r, -90f, (360f * level / 100f).coerceAtLeast(10f), false, p)
        t.textSize = s * 0.3f
        cv.drawText("$level%", s / 2, s / 2 - (t.descent() + t.ascent()) / 2, t)
    }
}
