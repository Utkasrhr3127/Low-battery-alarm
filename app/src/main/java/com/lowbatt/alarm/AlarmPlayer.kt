package com.lowbatt.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

object AlarmPlayer {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val h = Handler(Looper.getMainLooper())
    private var step = 0
    @Volatile var ringing = false

    private fun makePlayer(c: Context, uri: Uri): MediaPlayer {
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        mp.setDataSource(c.applicationContext, uri)
        mp.isLooping = true
        mp.prepare()
        return mp
    }

    // sound fades in over ~2 seconds instead of hitting the battery all at once
    private val ramp = object : Runnable {
        override fun run() {
            step++
            val v = (0.3f + 0.7f * step / 5f).coerceAtMost(1f)
            try { player?.setVolume(v, v) } catch (e: Exception) {}
            if (step < 5) h.postDelayed(this, 400)
        }
    }

    @Suppress("DEPRECATION")
    fun start(c: Context, gentle: Boolean = Prefs.gentle(c)) {
        if (ringing) return
        ringing = true

        // Force the alarm volume to the level chosen in the app
        val am = c.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val pct = if (gentle) Prefs.gentleVol(c) else Prefs.volume(c)
        val vol = (max * pct / 100).coerceIn(1, max)
        try { am.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0) } catch (e: Exception) {}

        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val chosen = Prefs.sound(c)?.let { Uri.parse(it) }
        try {
            player = makePlayer(c, chosen ?: defaultUri)
        } catch (e: Exception) {
            try { player = makePlayer(c, defaultUri) } catch (e2: Exception) { player = null }
        }
        step = 0
        player?.setVolume(0.3f, 0.3f)
        player?.start()
        h.postDelayed(ramp, 400)

        // vibration starts a moment AFTER the sound, so the two never spike together
        val strength = if (gentle) Prefs.gentleVib(c) else Prefs.normalVib(c)
        if (strength > 0) {
            h.postDelayed({
                if (!ringing) return@postDelayed
                val v = c.getSystemService(Vibrator::class.java)
                vibrator = v
                // gentle = short light pulses, normal = long pulses
                val times = if (gentle) longArrayOf(0, 400, 1100) else longArrayOf(0, 900, 500)
                val effect = if (v != null && v.hasAmplitudeControl()) {
                    val amp = (strength * 255 / 100).coerceIn(1, 255)
                    VibrationEffect.createWaveform(times, intArrayOf(0, amp, 0), 0)
                } else {
                    VibrationEffect.createWaveform(times, 0)
                }
                v?.vibrate(effect)
            }, 1200)
        }
    }

    fun stop() {
        h.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (e: Exception) {}
        vibrator = null
        ringing = false
    }
}
