package com.pocketube.app

import org.junit.Assert.*
import org.junit.Test

class PlaybackVolumeTest {
    @Test fun attenuationAndMuteDoNotApplyBoost() {
        assertEquals(0f, PlaybackVolume.playerVolume(0), 0.0001f)
        assertEquals(.5f, PlaybackVolume.playerVolume(50), 0.0001f)
        assertEquals(0, PlaybackVolume.gainMillibels(50))
        assertEquals(1f, PlaybackVolume.playerVolume(100), 0.0001f)
        assertEquals(0, PlaybackVolume.gainMillibels(100))
    }
    @Test fun boostUsesLogarithmicAmplitudeAndNeverOverdrivesPlayerVolume() {
        assertEquals(352, PlaybackVolume.gainMillibels(150))
        assertEquals(602, PlaybackVolume.gainMillibels(200))
        assertEquals(1f, PlaybackVolume.playerVolume(200), 0.0001f)
        assertTrue((101..200).zipWithNext().all { (a,b) -> PlaybackVolume.gainMillibels(b) >= PlaybackVolume.gainMillibels(a) })
    }
    @Test fun externalCommandValuesAreBounded() {
        assertEquals(0f, PlaybackVolume.playerVolume(Int.MIN_VALUE), 0.0001f)
        assertEquals(0, PlaybackVolume.gainMillibels(Int.MIN_VALUE))
        assertEquals(602, PlaybackVolume.gainMillibels(Int.MAX_VALUE))
        assertEquals(200, PlaybackVolume.clamp(Int.MAX_VALUE))
    }
}
