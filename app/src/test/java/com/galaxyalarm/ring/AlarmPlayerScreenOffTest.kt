package com.galaxyalarm.ring

import android.app.Application
import android.media.MediaPlayer
import android.os.Looper
import android.os.PowerManager
import com.galaxyalarm.data.model.SoundMode
import com.galaxyalarm.data.model.VibrationPattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaPlayer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AlarmPlayerScreenOffTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var output: AlarmPlayer
    private lateinit var media: MediaPlayer
    private var recoveries = 0

    @Before fun setup() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(600_000, 0) }
        ShadowMediaPlayer.setCreateListener { player, _ -> media = player }
        output = AlarmPlayer(context)
        output.start(
            SoundMode.SOUND, null, false, VibrationPattern.SHORT,
            onPlaybackRecovered = { recoveries++ },
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(media.isPlaying)
    }

    @After fun tearDown() {
        output.stop()
        ShadowMediaPlayer.resetStaticState()
    }

    private fun screenOff() {
        shadowOf(context.getSystemService(PowerManager::class.java)).turnScreenOn(false)
    }

    @Test fun pausedOutputWhileScreenOffIsResumed() {
        screenOff()
        media.pause()
        assertFalse(media.isPlaying)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(media.isPlaying)
        assertEquals(1, recoveries)
    }

    @Test fun explicitStopWhileScreenOffDoesNotRestartOutput() {
        screenOff()
        media.pause()
        output.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(media).state)
        assertEquals(0, recoveries)
    }

    @Test fun healthyPlaybackContinuesWithoutRestartOnScreenOff() {
        screenOff()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(media.isPlaying)
        assertEquals(0, recoveries)
    }

    @Test fun explicitStopReleasesPlayerEvenWhenStatusQueryFails() {
        shadowOf(media).invalidStateBehavior = ShadowMediaPlayer.InvalidStateBehavior.ASSERT
        shadowOf(media).state = ShadowMediaPlayer.State.ERROR
        output.stop()
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(media).state)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(0, recoveries)
    }

    @Test fun mediaPlayerOwnsWakeLockIndependentlyOfActivity() {
        val field = MediaPlayer::class.java.getDeclaredField("mWakeLock").apply { isAccessible = true }
        val lock = field.get(media) as? PowerManager.WakeLock
        assertNotNull(lock)
        // Robolectric creates WakeLocks without their native flags/constructor.
        assertFalse(shadowOf(lock!!).isReferenceCounted)
    }
}
