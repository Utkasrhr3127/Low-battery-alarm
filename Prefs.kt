package com.lowbatt.alarm

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("lowbatt", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun threshold(c: Context) = sp(c).getInt("threshold", 30)
    fun setThreshold(c: Context, v: Int) = sp(c).edit().putInt("threshold", v).apply()

    fun volume(c: Context) = sp(c).getInt("volume", 100)
    fun setVolume(c: Context, v: Int) = sp(c).edit().putInt("volume", v).apply()

    fun vibrate(c: Context) = sp(c).getBoolean("vibrate", true)
    fun setVibrate(c: Context, v: Boolean) = sp(c).edit().putBoolean("vibrate", v).apply()

    // 0 = do not repeat
    fun repeatMin(c: Context) = sp(c).getInt("repeatMin", 2)
    fun setRepeatMin(c: Context, v: Int) = sp(c).edit().putInt("repeatMin", v).apply()

    // max alarms per low-battery episode (0 = no limit)
    fun maxAlarms(c: Context) = sp(c).getInt("maxAlarms", 2)
    fun setMaxAlarms(c: Context, v: Int) = sp(c).edit().putInt("maxAlarms", v).apply()

    // each alarm goes quiet by itself after this many seconds
    fun ringSeconds(c: Context) = sp(c).getInt("ringSeconds", 30)
    fun setRingSeconds(c: Context, v: Int) = sp(c).edit().putInt("ringSeconds", v).apply()

    // original battery capacity in mAh (Nord 2T = 4500)
    fun designMah(c: Context) = sp(c).getInt("designMah", 4500)
    fun setDesignMah(c: Context, v: Int) = sp(c).edit().putInt("designMah", v).apply()

    // gentle mode: low volume, short beep, no vibration, screen NOT forced on
    fun gentle(c: Context) = sp(c).getBoolean("gentle", true)
    fun setGentle(c: Context, v: Boolean) = sp(c).edit().putBoolean("gentle", v).apply()

    fun dark(c: Context) = sp(c).getBoolean("dark", false)
    fun setDark(c: Context, v: Boolean) = sp(c).edit().putBoolean("dark", v).apply()

    // gentle mode limits (you control them)
    fun gentleVol(c: Context) = sp(c).getInt("gentleVol", 65)
    fun setGentleVol(c: Context, v: Int) = sp(c).edit().putInt("gentleVol", v).apply()
    fun gentleVib(c: Context) = sp(c).getInt("gentleVib", 50)   // 0 = off
    fun setGentleVib(c: Context, v: Int) = sp(c).edit().putInt("gentleVib", v).apply()
    fun gentleSec(c: Context) = sp(c).getInt("gentleSec", 12)
    fun setGentleSec(c: Context, v: Int) = sp(c).edit().putInt("gentleSec", v).apply()

    // vibration strength in normal mode, 0..100 (starts from your old Vibrate switch)
    fun normalVib(c: Context) = sp(c).getInt("normalVib", if (vibrate(c)) 100 else 0)
    fun setNormalVib(c: Context, v: Int) = sp(c).edit().putInt("normalVib", v).apply()

    // one-time: gentle volume + vibration start at half of normal
    fun migrateHalf(c: Context) {
        if (sp(c).getBoolean("m3", false)) return
        sp(c).edit()
            .putInt("gentleVol", (volume(c) / 2).coerceAtLeast(10))
            .putInt("gentleVib", normalVib(c) / 2)
            .putBoolean("m3", true)
            .apply()
    }

    fun seen(c: Context) = sp(c).getBoolean("seen", false)
    fun setSeen(c: Context) = sp(c).edit().putBoolean("seen", true).apply()

    fun sound(c: Context): String? = sp(c).getString("sound", null)
    fun setSound(c: Context, v: String?) = sp(c).edit().putString("sound", v).apply()
}
