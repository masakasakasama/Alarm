package com.galaxyalarm.ring

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeInVolumeTest {

    @Test
    fun zeroPercentIsActuallySilentAtStart() {
        assertEquals(0.0f, FadeInVolume.initial(fadeEnabled = true, startPercent = 0), 0.0001f)
    }

    @Test
    fun disabledFadeAlwaysStartsAtFullVolume() {
        assertEquals(1.0f, FadeInVolume.initial(fadeEnabled = false, startPercent = 0), 0.0001f)
    }

    @Test
    fun zeroPercentRampsLinearlyToFullVolume() {
        val start = FadeInVolume.initial(fadeEnabled = true, startPercent = 0)

        assertEquals(0.02f, FadeInVolume.atStep(start, step = 1, totalSteps = 50), 0.0001f)
        assertEquals(0.50f, FadeInVolume.atStep(start, step = 25, totalSteps = 50), 0.0001f)
        assertEquals(1.00f, FadeInVolume.atStep(start, step = 50, totalSteps = 50), 0.0001f)
    }

    @Test
    fun volumeInputsAreClampedSafely() {
        assertEquals(0.0f, FadeInVolume.initial(fadeEnabled = true, startPercent = -10), 0.0001f)
        assertEquals(1.0f, FadeInVolume.initial(fadeEnabled = true, startPercent = 150), 0.0001f)
        assertEquals(1.0f, FadeInVolume.atStep(0.0f, step = 99, totalSteps = 10), 0.0001f)
    }
}
