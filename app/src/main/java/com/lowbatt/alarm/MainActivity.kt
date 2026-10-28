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

    private var soundLabel: TextView? = null
    private var openPanel: android.app.Dialog? = null
    private val summaries = ArrayList<() -> Unit>()

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

        // keep saved values valid for the chip controls
        if (Prefs.repeatMin(this) < 1) Prefs.setRepeatMin(this, 2)
        val m0 = Prefs.maxAlarms(this)
        Prefs.setMaxAlarms(this, when (m0) { 0, 1, 2, 3, 5 -> m0; 4 -> 3; else -> 2 })
        val g0 = Prefs.repeatMin(this)
        Prefs.setRepeatMin(this, when { g0 in listOf(1, 2, 5, 10) -> g0; g0 <= 1 -> 1; g0 <= 3 -> 2; g0 <= 7 -> 5; else -> 10 })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(16))
        }
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        outer.addView(
            ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(root) },
            LinearLayout.LayoutParams(MATCH, 0, 1f)
        )
        setContentView(outer)

        root.addView(label("LOW BATTERY ALARM").apply {
            setTextColor(TEXT)
            setPadding(dp(4), dp(8), 0, dp(4))
        })

        // ---------- Status card: on/off, level, battery bar, alarm level ----------
        val hero = card(root)
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val onOff = label("").apply { setTextColor(TEXT); textSize = 13f }
        fun setOnOff(on: Boolean) { onOff.text = if (on) "ALARM  ON" else "ALARM  OFF" }
        setOnOff(Prefs.enabled(this))
        top.addView(onOff, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(styledSwitch(Prefs.enabled(this)) { on ->
            Prefs.setEnabled(this, on)
            setOnOff(on)
            applyChanges()
        })
        hero.addView(top)
        hero.addView(label("RINGS AT").apply { setPadding(0, dp(14), 0, 0) })
        val big = TextView(this).apply {
            text = "${Prefs.threshold(this@MainActivity)}%"
            textSize = 76f
            setTextColor(TEXT)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
            setPadding(0, dp(6), 0, dp(6))
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

        // ---------- Three tappable rows ----------
        navRow(root, "Alarm behaviour", { behaviourSummary() }) { showBehaviour() }
        navRow(root, "Alarm sound", { soundSummary() }) { showSound() }
        navRow(root, "Device reliability", { reliabilitySummary() }) { showReliability() }

        // ---------- Test button, pinned at the bottom ----------
        val test = pill("TEST ALARM", true) {
            if (!Prefs.enabled(this)) {
                toast("Turn the alarm ON first")
            } else {
                startForegroundService(Intent(this, BatteryService::class.java).setAction("TEST"))
            }
        }
        test.textSize = 15f
        test.setPadding(dp(8), dp(18), dp(8), dp(18))
        outer.addView(test, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(dp(16), dp(4), dp(16), dp(20))
        })

        applyChanges()
    }

    override fun onDestroy() {
        openPanel?.dismiss()
        super.onDestroy()
    }

    // ---------- Rows + popups ----------

    private fun navRow(parent: LinearLayout, title: String, sub: () -> String, onClick: () -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = rounded(CARD, 22)
        row.setPadding(dp(18), dp(18), dp(14), dp(18))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(TextView(this).apply { text = title; textSize = 17f; setTextColor(TEXT) })
        val s = TextView(this).apply {
            typeface = MONO; textSize = 11f; setTextColor(GREY)
            setPadding(0, dp(4), 0, 0)
        }
        col.addView(s)
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(TextView(this).apply { text = "\u203A"; textSize = 28f; setTextColor(GREY) })
        row.setOnClickListener { onClick() }
        summaries.add { s.text = sub() }
        s.text = sub()
        parent.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
    }

    private fun refreshSummaries() = summaries.forEach { it() }

    private fun behaviourSummary(): String {
        val n = Prefs.maxAlarms(this)
        val rings = when (n) { 0 -> "Endless rings"; 1 -> "1 ring"; else -> "$n rings" }
        val mode = if (Prefs.gentle(this)) "Gentle" else "Normal"
        return "$rings \u00B7 every ${Prefs.repeatMin(this)} min \u00B7 $mode"
    }

    private fun soundSummary(): String {
        val vol = if (Prefs.gentle(this)) Prefs.gentleVol(this) else Prefs.volume(this)
        return "${soundName()} \u00B7 vol $vol%"
    }

    private fun reliabilitySummary(): String {
        val ok = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        return (if (ok) "Background allowed" else "Setup needed") + " \u00B7 battery health"
    }

    private fun panel(title: String, build: (LinearLayout, android.app.Dialog) -> Unit) {
        val d = android.app.Dialog(this)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(20), dp(20), dp(20))
        box.addView(label(title.uppercase()).apply { setTextColor(TEXT); textSize = 12f })
        build(box, d)
        box.addView(pill("DONE", true) { d.dismiss() },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(22) })
        val sc = ScrollView(this)
        sc.isVerticalScrollBarEnabled = false
        sc.background = rounded(BG, 26, LINE)
        sc.addView(box)
        d.setContentView(sc)
        d.setOnDismissListener {
            soundLabel = null
            openPanel = null
            refreshSummaries()
        }
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        openPanel = d
        d.show()
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), WRAP)
    }

    private fun showBehaviour() = panel("Alarm behaviour") { b, _ ->
        b.addView(label("RINGS").apply { setPadding(0, dp(16), 0, 0) })
        chips(b, listOf("1", "2", "3", "5", "\u221E"), listOf(1, 2, 3, 5, 0), Prefs.maxAlarms(this)) {
            Prefs.setMaxAlarms(this, it)
        }
        b.addView(note("Then silent until you charge."))

        b.addView(label("REPEAT EVERY").apply { setPadding(0, dp(18), 0, 0) })
        chips(b, listOf("1 min", "2 min", "5 min", "10 min"), listOf(1, 2, 5, 10), Prefs.repeatMin(this)) {
            Prefs.setRepeatMin(this, it)
        }

        b.addView(label("STYLE").apply { setPadding(0, dp(18), 0, 0) })
        val modeNote = note("")
        fun setNote() {
            modeNote.text = if (Prefs.gentle(this))
                "Gentle: short beep, screen stays off. Easy on a weak battery."
            else "Normal: full-screen red alarm that wakes the screen."
        }
        chips(b, listOf("GENTLE", "NORMAL"), listOf(1, 0), if (Prefs.gentle(this)) 1 else 0) { v ->
            Prefs.setGentle(this, v == 1)
            setNote()
        }
        b.addView(modeNote)
        setNote()
    }

    private fun showSound() = panel("Alarm sound") { b, _ ->
        val sl = label("").apply {
            setTextColor(TEXT)
            setPadding(0, dp(16), 0, 0)
        }
        soundLabel = sl
        b.addView(sl)
        updateSoundLabel()
        pillRow(
            b,
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
        val g = Prefs.gentle(this)
        b.addView(note(
            if (g) "Sliders are for GENTLE mode (change mode in Alarm behaviour)."
            else "Sliders are for NORMAL mode (change mode in Alarm behaviour)."
        ))
        addSlider(
            b, { "VOLUME  $it%" }, 10, 100,
            if (g) Prefs.gentleVol(this) else Prefs.volume(this)
        ) { v -> if (g) Prefs.setGentleVol(this, v) else Prefs.setVolume(this, v) }
        addSlider(
            b, { if (it == 0) "VIBRATION  off" else "VIBRATION  $it%" }, 0, 100,
            if (g) Prefs.gentleVib(this) else Prefs.normalVib(this)
        ) { v -> if (g) Prefs.setGentleVib(this, v) else Prefs.setNormalVib(this, v) }
        if (g) {
            addSlider(b, { "RING LENGTH  $it sec" }, 3, 60, Prefs.gentleSec(this)) {
                Prefs.setGentleSec(this, it)
            }
        } else {
            addSlider(
                b, { if (it == 0) "RING LENGTH  until stopped" else "RING LENGTH  $it sec" },
                0, 120, Prefs.ringSeconds(this)
            ) { Prefs.setRingSeconds(this, it) }
        }
    }

    private fun showReliability() = panel("Device reliability") { b, d ->
        b.addView(label("SETUP  (ONCE)").apply { setPadding(0, dp(16), 0, 0) })
        pillRow(
            b,
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
        b.addView(note("Also lock this app in your recent apps screen."))

        b.addView(label("BATTERY HEALTH").apply { setPadding(0, dp(22), 0, 0) })
        addSlider(
            b, { "ORIGINAL SIZE  $it mAh" },
            3000, 6000, Prefs.designMah(this)
        ) { Prefs.setDesignMah(this, it) }
        b.addView(note("Nord 2T = 4500 mAh."))
        pillRow(b, pill("CHECK HEALTH", true) { showHealth() })

        b.addView(label("LOOK").apply { setPadding(0, dp(22), 0, 0) })
        pillRow(b, pill(if (Prefs.dark(this)) "SWITCH TO LIGHT" else "SWITCH TO DARK", false) {
            Prefs.setDark(this, !Prefs.dark(this))
            d.dismiss()
            recreate()
        })
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
        soundLabel?.text = "Now: ${soundName()}"
        refreshSummaries()
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
