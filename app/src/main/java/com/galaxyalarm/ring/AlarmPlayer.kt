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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var fadeJob: Job? = null

    fun start(
        soundMode: SoundMode,
        ringtoneUri: String?,
        vibrationEnabled: Boolean,
        pattern: VibrationPattern,
        fadeInSeconds: Int = 0,
        fadeInStartVolume: Int = 5,
    ) {
        when (soundMode) {
            SoundMode.SOUND -> {
                playSound(ringtoneUri, fadeInSeconds, fadeInStartVolume)
                if (vibrationEnabled) vibrate(pattern)
            }
            SoundMode.VIBRATE_ONLY,
            SoundMode.SILENT -> {
                vibrate(pattern)
            }
        }
    }

    private fun playSound(ringtoneUri: String?, fadeInSeconds: Int, fadeInStartVolume: Int) {
        try {
            val uri: Uri = ringtoneUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val startVol = if (fadeInSeconds > 0) (fadeInStartVolume / 100f).coerceIn(0f, 1f) else 1.0f
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnPreparedListener { mp ->
                    mp.setVolume(startVol, startVol)
                    mp.start()
                    if (fadeInSeconds > 0) startFade(fadeInSeconds, startVol)
                }
                prepareAsync()
            }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        } catch (e: Exception) {
            Log.e(TAG, "playSound failed", e)
        }
    }

    private fun startFade(fadeInSeconds: Int, startVol: Float) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = fadeInSeconds.coerceAtLeast(1)
            val increment = (1.0f - startVol) / steps
            var vol = startVol
            repeat(steps) {
                delay(1000L)
                vol = (vol + increment).coerceAtMost(1.0f)
                player?.setVolume(vol, vol)
            }
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

    fun stop() {
        fadeJob?.cancel()
        fadeJob = null
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        player = null
        try {
            vibrator.cancel()
        } catch (_: Exception) {
        }
    }

    companion object { private const val TAG = "AlarmPlayer" }
}
