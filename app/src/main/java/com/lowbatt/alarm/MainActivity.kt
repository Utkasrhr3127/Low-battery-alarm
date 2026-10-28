package com.lowbatt.alarm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // Palette (light = white/black/navy, dark = black/white/navy)
    private var BG = 0
    private var CARD = 0
    private var ACCENT = 0
    private var LINE = 0
    private var TRACK = 0
    private var TEXT = 0
    private var GREY = 0
    private val WHITE = Color.WHITE

    private fun applyTheme() {
        if (Prefs.dark(this)) {
            BG = Color.parseColor("#000000"); CARD = Color.parseColor("#0C1330")
            ACCENT = Color.parseColor("#3A62E0"); LINE = Color.parseColor("#26325C")
            TRACK = Color.parseColor("#2A3350"); TEXT = Color.parseColor("#F5F7FF")
            GREY = Color.parseColor("#8E98B2")
        } else {
            BG = Color.parseColor("#FFFFFF"); CARD = Color.parseColor("#F1F3F9")
            ACCENT = Color.parseColor("#12307F"); LINE = Color.parseColor("#D5DAEA")
            TRACK = Color.parseColor("#D5DAEA"); TEXT = Color.parseColor("#0A0A0A")
            GREY = Color.parseColor("#6B7385")
        }
    }

    private var bar: LevelBar? = null
    private var nowTxt: TextView? = null

    private val battReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { showLevel(i) }
    }

    private fun showLevel(i: Intent) {
        val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (lv < 0 || sc <= 0) return
        val pct = lv * 100 / sc
        val plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        bar?.level = pct
        bar?.invalidate()
        nowTxt?.text = "NOW $pct%" + (if (plug) "  CHARGING" else "") + "   |  marker = alarm level"
    }

    override fun onResume() {
        super.onResume()
        val st = if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(battReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_EXPORTED)
        else registerReceiver(battReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        st?.let { showLevel(it) }
    }

    override fun onPause() {
        try { unregisterReceiver(battReceiver) } catch (e: Exception) {}
        super.onPause()
    }

    private val MONO = Typeface.MONOSPACE
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var soundLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

        window.statusBarColor = BG
        window.navigationBarColor = BG
        if (!Prefs.dark(this)) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // keep saved values valid for the new chip controls
        if (Prefs.repeatMin(this) < 1) Prefs.setRepeatMin(this, 2)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            isVerticalScrollBarEnabled = false
            addView(root)
        })

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        head.addView(label("LOW BATTERY ALARM").apply { setTextColor(TEXT) },
            LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(pill(if (Prefs.dark(this)) "LIGHT" else "DARK", false) {
            Prefs.setDark(this, !Prefs.dark(this))
            recreate()
        }.apply { setPadding(dp(16), dp(8), dp(16), dp(8)) })
        root.addView(head)

        // ---------- 1. Threshold ----------
        val hero = card(root)
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(label("RINGS AT"), LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(styledSwitch(Prefs.enabled(this)) { on ->
            Prefs.setEnabled(this, on)
            applyChanges()
        })
        hero.addView(top)
        val big = TextView(this).apply {
            text = "${Prefs.threshold(this@MainActivity)}%"
            textSize = 76f
            setTextColor(TEXT)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
            setPadding(0, dp(10), 0, dp(6))
        }
        hero.addView(big)
        val lb = LevelBar(this, TRACK, ACCENT, TEXT)
        lb.mark = Prefs.threshold(this)
        bar = lb
        hero.addView(lb, LinearLayout.LayoutParams(MATCH, dp(12)).apply { topMargin = dp(8) })
        val nt = note("NOW ...")
        nowTxt = nt
        hero.addView(nt)
        seek(hero, 5, 90, Prefs.threshold(this),
            { big.text = "$it%"; lb.mark = it; lb.invalidate() },
            { Prefs.setThreshold(this, it); applyChanges() })

        // ---------- 2. How many rings (buttons + slider, synced) ----------
        val rings = card(root)
        rings.addView(label("NUMBER OF RINGS"))
        val ringsNow = if (Prefs.maxAlarms(this) == 0) 6 else Prefs.maxAlarms(this).coerceIn(1, 5)
        val ringsCap = TextView(this).apply {
            typeface = MONO; textSize = 15f; setTextColor(TEXT); setPadding(0, dp(14), 0, dp(2))
        }
        fun ringsText(v: Int) =
            if (v == 6) "\u221E  keeps ringing until you charge" else "$v  then silent until you charge"
        ringsCap.text = ringsText(ringsNow)
        var ringsBar: SeekBar? = null
        val paintRings = chips(
            rings, listOf("1", "2", "3", "5", "\u221E"), listOf(1, 2, 3, 5, 6), ringsNow
        ) { v ->
            Prefs.setMaxAlarms(this, if (v == 6) 0 else v)
            ringsCap.text = ringsText(v)
            ringsBar?.progress = v - 1
        }
        rings.addView(ringsCap)
        ringsBar = seek(rings, 1, 6, ringsNow,
            { v -> ringsCap.text = ringsText(v); paintRings(v) },
            { v -> Prefs.setMaxAlarms(this, if (v == 6) 0 else v) })

        rings.addView(label("GAP BETWEEN RINGS").apply { setPadding(0, dp(22), 0, 0) })
        val gapNow = Prefs.repeatMin(this).coerceIn(1, 10)
        val gapCap = TextView(this).apply {
            typeface = MONO; textSize = 15f; setTextColor(TEXT); setPadding(0, dp(14), 0, dp(2))
        }
        gapCap.text = "$gapNow min"
        var gapBar: SeekBar? = null
        val paintGap = chips(
            rings, listOf("1 min", "2 min", "5 min", "10 min"), listOf(1, 2, 5, 10), gapNow
        ) { v ->
            Prefs.setRepeatMin(this, v)
            gapCap.text = "$v min"
            gapBar?.progress = v - 1
        }
        rings.addView(gapCap)
        gapBar = seek(rings, 1, 10, gapNow,
            { v -> gapCap.text = "$v min"; paintGap(v) },
            { v -> Prefs.setRepeatMin(this, v) })

        // ---------- 3. Sound ----------
        val snd = card(root)
        snd.addView(label("SOUND"))
        addSlider(snd, { "VOLUME  $it%" }, 10, 100, Prefs.volume(this)) { Prefs.setVolume(this, it) }
        addSlider(
            snd,
            { if (it == 0) "RING LENGTH  until stopped" else "RING LENGTH  $it sec" },
            0, 120, Prefs.ringSeconds(this)
        ) { Prefs.setRingSeconds(this, it) }
        switchRow(
            snd, "Gentle mode", "40% volume, 8 sec, no vibration, screen stays off",
            Prefs.gentle(this)
        ) { Prefs.setGentle(this, it) }
        switchRow(snd, "Vibrate", "Ignored while gentle mode is on", Prefs.vibrate(this)) {
            Prefs.setVibrate(this, it)
        }
        soundLabel = label("").apply {
            setTextColor(TEXT)
            setPadding(0, dp(18), 0, 0)
        }
        snd.addView(soundLabel)
        updateSoundLabel()
        pillRow(
            snd,
            pill("LIST", false) {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                Prefs.sound(this)?.let {
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                }
                startActivityForResult(i, 10)
            },
            pill("MY FILE", false) {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "audio/*"
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                startActivityForResult(i, 11)
            }
        )
        pillRow(
            snd,
            pill("TEST ALARM", true) {
                if (!Prefs.enabled(this)) {
                    toast("Turn the alarm ON first")
                } else {
                    startForegroundService(Intent(this, BatteryService::class.java).setAction("TEST"))
                }
            }
        )

        // ---------- 4. Battery ----------
        val bat = card(root)
        bat.addView(label("BATTERY"))
        addSlider(
            bat, { "ORIGINAL SIZE  $it mAh" },
            3000, 6000, Prefs.designMah(this)
        ) { Prefs.setDesignMah(this, it) }
        bat.addView(note("Nord 2T = 4500 mAh."))
        pillRow(bat, pill("CHECK HEALTH", true) { showHealth() })

        // ---------- 5. Setup ----------
        val setup = card(root)
        setup.addView(label("SETUP  (ONCE)"))
        pillRow(
            setup,
            pill("1  BACKGROUND", false) {
                val pm = getSystemService(PowerManager::class.java)
                if (pm.isIgnoringBatteryOptimizations(packageName)) {
                    toast("Already allowed. Good!")
                } else {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    )
                }
            },
            pill("2  LOCK SCREEN", false) {
                if (Build.VERSION.SDK_INT >= 34) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                    )
                } else {
                    toast("Not needed on your Android version")
                }
            }
        )
        setup.addView(note("Also lock this app in your recent apps screen."))

        applyChanges()
    }

    // ---------- UI helpers (no libraries, tiny) ----------

    private fun rounded(fill: Int, radius: Int, stroke: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(fill)
        d.cornerRadius = dp(radius).toFloat()
        if (stroke != 0) d.setStroke(dp(1), stroke)
        return d
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        typeface = MONO
        textSize = 11f
        letterSpacing = 0.15f
        setTextColor(GREY)
    }

    private fun note(t: String) = TextView(this).apply {
        text = t
        typeface = MONO
        textSize = 11f
        setTextColor(GREY)
        setPadding(0, dp(8), 0, 0)
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(CARD, 22)
        c.setPadding(dp(18), dp(16), dp(18), dp(18))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(12)
        parent.addView(c, lp)
        return c
    }

    private fun styledSwitch(checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val s = Switch(this)
        s.isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        s.thumbTintList = ColorStateList(states, intArrayOf(WHITE, GREY))
        s.trackTintList = ColorStateList(states, intArrayOf(ACCENT, TRACK))
        s.setOnCheckedChangeListener { _, on -> onChange(on) }
        return s
    }

    private fun switchRow(
        card: LinearLayout, title: String, sub: String,
        checked: Boolean, onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(16), 0, 0)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(TEXT) })
        col.addView(TextView(this).apply {
            text = sub; textSize = 11f; typeface = MONO; setTextColor(GREY)
        })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(styledSwitch(checked, onChange))
        card.addView(row)
    }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.typeface = MONO
        t.textSize = 13f
        t.letterSpacing = 0.1f
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(14), dp(8), dp(14))
        t.setTextColor(if (filled) WHITE else ACCENT)
        t.background = if (filled) rounded(ACCENT, 30) else rounded(Color.TRANSPARENT, 30, ACCENT)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun pillRow(card: LinearLayout, vararg pills: TextView) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        pills.forEachIndexed { i, p ->
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            row.addView(p, lp)
        }
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(12)
        card.addView(row, lp)
    }

    private fun chips(
        card: LinearLayout, labels: List<String>, values: List<Int>,
        current: Int, onPick: (Int) -> Unit
    ): (Int) -> Unit {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val views = ArrayList<TextView>()
        fun paint(sel: Int) {
            views.forEachIndexed { idx, v ->
                val on = values[idx] == sel
                v.background = rounded(if (on) ACCENT else Color.TRANSPARENT, 16, if (on) ACCENT else LINE)
                v.setTextColor(if (on) WHITE else TEXT)
            }
        }
        labels.forEachIndexed { idx, l ->
            val tv = TextView(this)
            tv.text = l
            tv.typeface = MONO
            tv.textSize = 15f
            tv.gravity = Gravity.CENTER
            tv.setPadding(0, dp(14), 0, dp(14))
            tv.setOnClickListener {
                paint(values[idx])
                onPick(values[idx])
            }
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            if (idx > 0) lp.leftMargin = dp(8)
            row.addView(tv, lp)
            views.add(tv)
        }
        paint(current)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(12)
        card.addView(row, lp)
        return { sel: Int -> paint(sel) }
    }

    private fun seek(
        card: LinearLayout, min: Int, max: Int, value: Int,
        live: (Int) -> Unit, done: (Int) -> Unit
    ): SeekBar {
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = value - min
        val track = GradientDrawable()
        track.setColor(TRACK); track.cornerRadius = dp(4).toFloat(); track.setSize(-1, dp(8))
        val fill = GradientDrawable()
        fill.setColor(ACCENT); fill.cornerRadius = dp(4).toFloat(); fill.setSize(-1, dp(8))
        val layers = android.graphics.drawable.LayerDrawable(
            arrayOf(track, android.graphics.drawable.ClipDrawable(fill, Gravity.START,
                android.graphics.drawable.ClipDrawable.HORIZONTAL))
        )
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)
        sb.progressDrawable = layers
        val thumb = GradientDrawable()
        thumb.shape = GradientDrawable.OVAL
        thumb.setColor(WHITE); thumb.setStroke(dp(4), ACCENT); thumb.setSize(dp(26), dp(26))
        sb.thumb = thumb
        sb.splitTrack = false
        sb.setPadding(dp(13), dp(6), dp(13), dp(6))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { live(p + min) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { done(s.progress + min) }
        })
        card.addView(sb)
        return sb
    }

    private fun addSlider(
        card: LinearLayout, title: (Int) -> String,
        min: Int, max: Int, value: Int, size: Float = 13f, onChange: (Int) -> Unit
    ) {
        val tv = TextView(this)
        tv.typeface = MONO
        tv.textSize = size
        tv.setTextColor(TEXT)
        tv.setPadding(0, dp(18), 0, dp(2))
        tv.text = title(value)
        card.addView(tv)
        seek(card, min, max, value, { tv.text = title(it) }, { onChange(it); applyChanges() })
    }

    private fun showHealth() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = getSystemService(BatteryManager::class.java)
        val sb = StringBuilder()

        val level = i?.let {
            it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 /
                it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        } ?: -1
        sb.append("Charge now: $level%\n")

        val status = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEATING"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            else -> "Unknown"
        }
        sb.append("Phone's own status: $status\n")

        i?.let {
            val t = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            if (t > 0) sb.append("Temperature: ${t / 10.0} C\n")
            val v = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            if (v > 0) sb.append("Voltage: $v mV\n")
        }
        sb.append("\n")

        // 1) Real health % straight from the phone (only some phones give it)
        var soh = -1
        try { soh = bm.getIntProperty(10) } catch (e: Exception) {}
        if (soh in 1..100) {
            sb.append("REAL HEALTH (from phone): $soh%\n")
        } else {
            sb.append("Real health %: your phone does not share it\n")
        }

        // 2) Charge cycles (Android 14+ and only some phones)
        val cycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
        if (cycles > 0) sb.append("Charge cycles: $cycles\n")

        // 3) Estimate from the battery's own charge counter
        var cc = 0
        try { cc = bm.getIntProperty(1) } catch (e: Exception) {}
        val design = Prefs.designMah(this)
        if (cc > 0 && level in 15..95) {
            val mah = if (cc > 20000) cc / 1000.0 else cc.toDouble()
            val full = mah / (level / 100.0)
            val pct = (full * 100 / design).toInt().coerceAtMost(110)
            sb.append("\nESTIMATE: battery holds about ${full.toInt()} mAh of $design mAh = about $pct%\n")
            sb.append("(Rough guess. Check 2-3 times at different charge levels, phone not charging, not hot.)\n")
        } else {
            sb.append("\nEstimate needs charge between 15% and 95%, unplugged, and a phone that shares its counter.\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Battery health")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun applyChanges() {
        val i = Intent(this, BatteryService::class.java)
        if (Prefs.enabled(this)) startForegroundService(i) else stopService(i)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun soundName(): String {
        val s = Prefs.sound(this) ?: return "Phone's default alarm sound"
        return try {
            RingtoneManager.getRingtone(this, Uri.parse(s))?.getTitle(this) ?: "Custom sound"
        } catch (e: Exception) {
            "Custom sound"
        }
    }

    private fun updateSoundLabel() {
        soundLabel.text = "Alarm sound: ${soundName()}"
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            10 -> {
                val u = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                Prefs.setSound(this, u?.toString())
            }
            11 -> {
                val u = data.data
                if (u != null) {
                    try {
                        contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {}
                    Prefs.setSound(this, u.toString())
                }
            }
        }
        updateSoundLabel()
    }
}

private class LevelBar(c: Context, val track: Int, val fill: Int, val tick: Int) : View(c) {
    var level = 0
    var mark = 30
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2
        p.color = track
        cv.drawRoundRect(0f, 0f, w, h, r, r, p)
        p.color = fill
        cv.drawRoundRect(0f, 0f, maxOf(h, w * level / 100f), h, r, r, p)
        p.color = tick
        val x = w * mark / 100f
        cv.drawRoundRect(x - 2f, 0f, x + 2f, h, 2f, 2f, p)
    }
}
