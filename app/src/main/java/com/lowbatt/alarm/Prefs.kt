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

    fun sound(c: Context): String? = sp(c).getString("sound", null)
    fun setSound(c: Context, v: String?) = sp(c).edit().putString("sound", v).apply()
}
