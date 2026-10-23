package com.lowbatt.alarm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var soundLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(48))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply { text = "Low Battery Alarm"; textSize = 26f })

        val sw = Switch(this)
        sw.text = "Alarm is ON"
        sw.textSize = 18f
        sw.isChecked = Prefs.enabled(this)
        sw.setPadding(0, dp(16), 0, dp(8))
        sw.setOnCheckedChangeListener { _, on ->
            Prefs.setEnabled(this, on)
            applyChanges()
        }
        root.addView(sw)

        addSlider(root, { "Ring when battery reaches: $it%" }, 5, 90, Prefs.threshold(this)) {
            Prefs.setThreshold(this, it)
        }
        addSlider(root, { "Alarm volume: $it%" }, 10, 100, Prefs.volume(this)) {
            Prefs.setVolume(this, it)
        }
        addSlider(
            root,
            { if (it == 0) "Repeat alarm: OFF" else "If still low, ring again every $it min" },
            0, 10, Prefs.repeatMin(this)
        ) {
            Prefs.setRepeatMin(this, it)
        }

        val vib = Switch(this)
        vib.text = "Vibrate"
        vib.textSize = 18f
        vib.isChecked = Prefs.vibrate(this)
        vib.setPadding(0, dp(16), 0, dp(8))
        vib.setOnCheckedChangeListener { _, on -> Prefs.setVibrate(this, on) }
        root.addView(vib)

        soundLabel = TextView(this)
        soundLabel.textSize = 17f
        soundLabel.setPadding(0, dp(16), 0, 0)
        root.addView(soundLabel)
        updateSoundLabel()

        addButton(root, "Choose alarm sound from list") {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            Prefs.sound(this)?.let {
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
            }
            startActivityForResult(i, 10)
        }
        addButton(root, "Pick my own song / audio file") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "audio/*"
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            startActivityForResult(i, 11)
        }
        addButton(root, "TEST ALARM NOW") {
            if (!Prefs.enabled(this)) {
                toast("Turn the alarm ON first")
            } else {
                startForegroundService(Intent(this, BatteryService::class.java).setAction("TEST"))
            }
        }

        root.addView(TextView(this).apply {
            text = "Important setup (do both once):"
            textSize = 17f
            setPadding(0, dp(28), 0, 0)
        })
        addButton(root, "1) Allow running in background") {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                toast("Already allowed. Good!")
            } else {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            }
        }
        addButton(root, "2) Allow alarm screen over lock screen") {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            } else {
                toast("Not needed on your Android version")
            }
        }

        applyChanges()
    }

    private fun applyChanges() {
        val i = Intent(this, BatteryService::class.java)
        if (Prefs.enabled(this)) startForegroundService(i) else stopService(i)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun addButton(root: LinearLayout, label: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { onClick() }
        root.addView(b)
    }

    private fun addSlider(
        root: LinearLayout, title: (Int) -> String,
        min: Int, max: Int, value: Int, onChange: (Int) -> Unit
    ) {
        val tv = TextView(this)
        tv.textSize = 17f
        tv.setPadding(0, dp(18), 0, 0)
        tv.text = title(value)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = value - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { tv.text = title(p + min) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {
                onChange(s.progress + min)
                applyChanges()
            }
        })
        root.addView(tv)
        root.addView(sb)
    }

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
