package com.galaxyalarm.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern

/**
 * 音とバイブの再生制御。SoundMode に応じて：
 * SOUND=音(+任意でバイブ) / VIBRATE_ONLY=音なしバイブのみ / SILENT=音もバイブも無し。
 */
class AlarmPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun start(
        soundMode: SoundMode,
        ringtoneUri: String?,
        vibrationEnabled: Boolean,
        pattern: VibrationPattern,
    ) {
        when (soundMode) {
            SoundMode.SOUND -> {
                playSound(ringtoneUri)
                if (vibrationEnabled) vibrate(pattern)
            }
            SoundMode.VIBRATE_ONLY -> {
                // 音は鳴らさず、バイブのみ(設定でバイブOFFなら何もしない)。
                if (vibrationEnabled) vibrate(pattern)
            }
            SoundMode.SILENT -> {
                // 完全無音: 音もバイブも出さない(画面と通知のみ)。
            }
        }
    }

    private fun playSound(ringtoneUri: String?) {
        try {
            val uri: Uri = ringtoneUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
            // アラーム音量を最大化(端末ポリシー内で)。
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "playSound failed", e)
        }
    }

    private fun vibrate(pattern: VibrationPattern) {
        try {
            val timings = pattern.toTimings()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "vibrate failed", e)
        }
    }

    /** 音・バイブを確実に止める。 */
    fun stop() {
        try {
            player?.let { if (it.isPlaying) it.stop(); it.release() }
        } catch (_: Exception) {}
        player = null
        try { vibrator.cancel() } catch (_: Exception) {}
    }

    companion object { private const val TAG = "AlarmPlayer" }
}
