package com.galaxyalarm.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibrationAttributes
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
import java.util.concurrent.atomic.AtomicBoolean

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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fadeJob: Job? = null
    private var prepareTimeout: Runnable? = null
    private var playbackGeneration = 0

    fun start(
        soundMode: SoundMode,
        ringtoneUri: String?,
        vibrationEnabled: Boolean,
        pattern: VibrationPattern,
        fadeInSeconds: Int = 0,
        fadeInStartVolume: Int = 5,
        onOutputStarted: () -> Unit = {},
        onOutputFailed: () -> Unit = {},
    ) {
        val resolved = AtomicBoolean(false)
        val outputStarted = AtomicBoolean(false)
        fun started() {
            outputStarted.set(true)
            if (resolved.compareAndSet(false, true)) onOutputStarted()
        }
        fun failed() {
            if (!outputStarted.get() && resolved.compareAndSet(false, true)) onOutputFailed()
        }

        when (soundMode) {
            SoundMode.SOUND -> {
                if (vibrationEnabled && vibrate(pattern)) started()
                playSound(
                    ringtoneUri = ringtoneUri,
                    fadeInSeconds = fadeInSeconds,
                    fadeInStartVolume = fadeInStartVolume,
                    onStarted = ::started,
                    onVolumeUnavailable = {
                        if (!vibrationEnabled && vibrate(pattern)) started()
                    },
                    onAllFailed = {
                        if (!vibrationEnabled && vibrate(pattern)) started() else failed()
                    },
                )
            }
            SoundMode.VIBRATE_ONLY,
            SoundMode.SILENT -> {
                if (vibrate(pattern)) started() else failed()
            }
        }
    }

    private fun playSound(
        ringtoneUri: String?,
        fadeInSeconds: Int,
        fadeInStartVolume: Int,
        onStarted: () -> Unit,
        onVolumeUnavailable: () -> Unit,
        onAllFailed: () -> Unit,
    ) {
        val candidates = listOfNotNull(
            ringtoneUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).distinct()
        val generation = ++playbackGeneration
        val startVol = if (fadeInSeconds > 0) {
            (fadeInStartVolume / 100f).coerceIn(MIN_FADE_VOLUME, 1f)
        } else {
            1.0f
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }.onFailure {
            Log.e(TAG, "failed to raise alarm volume", it)
            onVolumeUnavailable()
        }

        fun tryCandidate(index: Int) {
            if (generation != playbackGeneration) return
            val uri = candidates.getOrNull(index)
            if (uri == null) {
                Log.e(TAG, "all alarm sound sources failed")
                onAllFailed()
                return
            }

            val mediaPlayer = MediaPlayer()
            try {
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mediaPlayer.setDataSource(context, uri)
                mediaPlayer.isLooping = true
                mediaPlayer.setOnPreparedListener { prepared ->
                    if (generation != playbackGeneration || player !== prepared) {
                        prepared.release()
                        return@setOnPreparedListener
                    }
                    clearPrepareTimeout()
                    runCatching {
                        prepared.setVolume(startVol, startVol)
                        prepared.start()
                    }.onSuccess {
                        onStarted()
                        if (fadeInSeconds > 0) startFade(fadeInSeconds, startVol)
                    }.onFailure { error ->
                        Log.e(TAG, "alarm sound failed to start: $uri", error)
                        runCatching { prepared.release() }
                        if (player === prepared) player = null
                        tryCandidate(index + 1)
                    }
                }
                mediaPlayer.setOnErrorListener { failed, _, _ ->
                    if (generation != playbackGeneration || player !== failed) {
                        runCatching { failed.release() }
                        return@setOnErrorListener true
                    }
                    clearPrepareTimeout()
                    runCatching { failed.release() }
                    if (player === failed) player = null
                    tryCandidate(index + 1)
                    true
                }
                player = mediaPlayer
                mediaPlayer.prepareAsync()
                val timeout = Runnable {
                    if (generation == playbackGeneration && player === mediaPlayer) {
                        Log.e(TAG, "alarm sound preparation timed out: $uri")
                        runCatching { mediaPlayer.release() }
                        player = null
                        tryCandidate(index + 1)
                    }
                }
                prepareTimeout = timeout
                mainHandler.postDelayed(timeout, PREPARE_TIMEOUT_MS)
            } catch (error: Exception) {
                clearPrepareTimeout()
                Log.e(TAG, "alarm sound source failed: $uri", error)
                runCatching { mediaPlayer.release() }
                if (player === mediaPlayer) player = null
                tryCandidate(index + 1)
            }
        }

        tryCandidate(0)
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

    private fun vibrate(pattern: VibrationPattern): Boolean {
        return try {
            if (!vibrator.hasVibrator()) return false
            val timings = pattern.toTimings()
            val effect = VibrationEffect.createWaveform(timings, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build(),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "vibrate failed", e)
            false
        }
    }

    private fun clearPrepareTimeout() {
        prepareTimeout?.let { mainHandler.removeCallbacks(it) }
        prepareTimeout = null
    }

    fun stop() {
        playbackGeneration += 1
        clearPrepareTimeout()
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

    companion object {
        private const val TAG = "AlarmPlayer"
        private const val PREPARE_TIMEOUT_MS = 1_500L
        private const val MIN_FADE_VOLUME = 0.05f
    }
}
