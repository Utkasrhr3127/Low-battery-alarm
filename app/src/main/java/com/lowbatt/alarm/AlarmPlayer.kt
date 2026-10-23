package com.lowbatt.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator

object AlarmPlayer {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
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

    @Suppress("DEPRECATION")
    fun start(c: Context) {
        if (ringing) return
        ringing = true

        // Force the alarm volume to the level chosen in the app
        val am = c.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val vol = (max * Prefs.volume(c) / 100).coerceIn(1, max)
        try { am.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0) } catch (e: Exception) {}

        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val chosen = Prefs.sound(c)?.let { Uri.parse(it) }
        try {
            player = makePlayer(c, chosen ?: defaultUri)
        } catch (e: Exception) {
            try { player = makePlayer(c, defaultUri) } catch (e2: Exception) { player = null }
        }
        player?.start()

        if (Prefs.vibrate(c)) {
            vibrator = c.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 500), 0))
        }
    }

    fun stop() {
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (e: Exception) {}
        vibrator = null
        ringing = false
    }
}
