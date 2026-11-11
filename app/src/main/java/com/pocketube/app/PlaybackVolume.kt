package com.pocketube.app

import kotlin.math.log10
import kotlin.math.roundToInt

/** Player attenuation below unity; platform LoudnessEnhancer gain above unity. */
internal object PlaybackVolume {
    const val MAX = 200
    const val ACTION = "com.pocketube.app.SET_VOLUME"
    const val LEVEL = "volumePercent"
    const val UNSUPPORTED = "boostUnsupported"
    fun clamp(percent: Int) = percent.coerceIn(0, MAX)
    fun playerVolume(percent: Int) = clamp(percent).coerceAtMost(100) / 100f
    fun gainMillibels(percent: Int): Int =
        if (percent <= 100) 0 else (2000 * log10(clamp(percent) / 100.0)).roundToInt()
}
