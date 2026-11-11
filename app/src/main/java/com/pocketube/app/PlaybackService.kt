package com.pocketube.app

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var enhancer: LoudnessEnhancer? = null
    private var volumePercent = 100
    private var boostUnsupported = false

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                releaseEnhancer()
                boostUnsupported = false
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
                    try { enhancer = LoudnessEnhancer(audioSessionId) }
                    catch (_: RuntimeException) { boostUnsupported = true }
                }
                applyVolume()
            }
        })
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(PlaybackVolume.ACTION, Bundle.EMPTY)).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands).build()
                }
                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                    command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (command.customAction != PlaybackVolume.ACTION)
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    volumePercent = PlaybackVolume.clamp(args.getInt(PlaybackVolume.LEVEL, 100))
                    applyVolume()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
        publishVolume()
    }

    private fun applyVolume() {
        if (boostUnsupported) volumePercent = volumePercent.coerceAtMost(100)
        player.volume = PlaybackVolume.playerVolume(volumePercent)
        try {
            enhancer?.apply {
                setTargetGain(PlaybackVolume.gainMillibels(volumePercent))
                check(setEnabled(volumePercent > 100) == AudioEffect.SUCCESS) { "Audio effect rejected volume change" }
            }
        } catch (_: RuntimeException) {
            releaseEnhancer()
            boostUnsupported = true
            volumePercent = volumePercent.coerceAtMost(100)
            player.volume = PlaybackVolume.playerVolume(volumePercent)
        }
        publishVolume()
    }

    private fun publishVolume() {
        session?.setSessionExtras(Bundle().apply {
            putInt(PlaybackVolume.LEVEL, volumePercent)
            putBoolean(PlaybackVolume.UNSUPPORTED, boostUnsupported)
        })
    }
    private fun releaseEnhancer() {
        try { enhancer?.release() } catch (_: RuntimeException) { }
        enhancer = null
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        releaseEnhancer()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
