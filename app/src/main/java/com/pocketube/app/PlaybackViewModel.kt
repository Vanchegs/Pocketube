package com.pocketube.app

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val connected: Boolean = false, val currentId: String? = null,
    val title: String = "", val author: String = "", val thumbnail: String = "",
    val isAudio: Boolean = false, val playing: Boolean = false, val buffering: Boolean = false,
    val positionMs: Long = 0, val durationMs: Long = 0,
    val hasPrevious: Boolean = false, val hasNext: Boolean = false, val error: String? = null,
    val volumePercent: Int = 100, val boostUnsupported: Boolean = false
)

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(PlaybackState())
    val state = mutableState.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = mutableMessages.asSharedFlow()
    private var controller: MediaController? = null
    val player: Player? get() = controller
    private var pending: Pair<DownloadItem, List<DownloadItem>>? = null
    private val future = MediaController.Builder(application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)))
        .setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) { synchronize() }
        }).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { synchronize() }
        override fun onPlayerError(error: PlaybackException) {
            val message = "Не удалось воспроизвести файл: ${error.errorCodeName}"
            mutableMessages.tryEmit(message)
            synchronize()
        }
    }

    init {
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                synchronize()
                pending?.let { (item, queue) -> pending = null; play(item, queue) }
            } catch (error: Exception) {
                mutableMessages.tryEmit("Не удалось открыть плеер: ${error.cause?.message ?: error.message}")
            }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch {
            while (isActive) { synchronize(); delay(500) }
        }
    }

    private fun synchronize() {
        val current = controller ?: return
        val item = current.currentMediaItem
        val metadata = item?.mediaMetadata
        val duration = current.duration.coerceAtLeast(0)
        mutableState.value = PlaybackState(
            connected = true, currentId = item?.mediaId,
            title = metadata?.title?.toString().orEmpty(), author = metadata?.artist?.toString().orEmpty(),
            thumbnail = metadata?.artworkUri?.toString().orEmpty(),
            isAudio = metadata?.extras?.getBoolean("audio") == true,
            playing = current.isPlaying, buffering = current.playbackState == Player.STATE_BUFFERING,
            positionMs = current.currentPosition.coerceAtLeast(0), durationMs = duration,
            hasPrevious = current.hasPreviousMediaItem() || current.currentPosition > 0,
            hasNext = current.hasNextMediaItem(),
            error = current.playerError?.let { "Ошибка воспроизведения: ${it.errorCodeName}" },
            volumePercent = current.sessionExtras.getInt(PlaybackVolume.LEVEL, 100),
            boostUnsupported = current.sessionExtras.getBoolean(PlaybackVolume.UNSUPPORTED)
        )
    }

    fun play(item: DownloadItem, queue: List<DownloadItem>) {
        if (item.status != DownloadStatus.COMPLETED || !File(item.filePath).isFile) {
            mutableMessages.tryEmit("Файл не найден на устройстве")
            return
        }
        val current = controller
        if (current == null) { pending = item to queue; return }
        val available = queue.filter { it.status == DownloadStatus.COMPLETED && File(it.filePath).isFile }
            .distinctBy { it.id }.let { if (it.any { queued -> queued.id == item.id }) it else listOf(item) + it }
        val sameItem = current.currentMediaItem?.mediaId == item.id && current.playbackState != Player.STATE_IDLE
        val sameQueue = current.mediaItemCount == available.size && available.indices.all {
            current.getMediaItemAt(it).mediaId == available[it].id
        }
        if (sameItem && sameQueue) {
            if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
            current.play()
            return
        }
        val startPosition = if (sameItem && current.playbackState != Player.STATE_ENDED) current.currentPosition else 0L
        val mediaItems = available.map { entry ->
            val metadata = MediaMetadata.Builder().setTitle(entry.title).setArtist(entry.author)
                .setExtras(Bundle().apply { putBoolean("audio", entry.mediaType == MediaType.AUDIO) })
            if (entry.thumbnail.isNotBlank()) metadata.setArtworkUri(entry.thumbnail.toUri())
            MediaItem.Builder().setMediaId(entry.id).setUri(File(entry.filePath).toUri())
                .setMediaMetadata(metadata.build()).build()
        }
        current.setMediaItems(mediaItems, available.indexOfFirst { it.id == item.id }, startPosition)
        current.prepare()
        current.play()
        synchronize()
    }

    fun toggle() {
        val current = controller ?: return
        if (current.playWhenReady && current.playbackState != Player.STATE_ENDED) current.pause() else {
            if (current.playbackState == Player.STATE_ENDED) current.seekToDefaultPosition()
            if (current.playbackState == Player.STATE_IDLE) current.prepare()
            current.play()
        }
        synchronize()
    }

    fun setVolume(percent: Int) {
        val current = controller ?: return
        val value = PlaybackVolume.clamp(percent)
        current.sendCustomCommand(SessionCommand(PlaybackVolume.ACTION, Bundle.EMPTY),
            Bundle().apply { putInt(PlaybackVolume.LEVEL, value) })
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceIn(0, mutableState.value.durationMs)); synchronize() }
    fun previous() { controller?.seekToPrevious(); synchronize() }
    fun next() { controller?.seekToNextMediaItem(); synchronize() }
    fun stop() { pending = null; controller?.run { stop(); clearMediaItems() }; synchronize() }

    fun remove(itemId: String) {
        if (pending?.first?.id == itemId) pending = null
        val current = controller ?: return
        if (current.currentMediaItem?.mediaId == itemId) current.stop()
        for (index in current.mediaItemCount - 1 downTo 0) {
            if (current.getMediaItemAt(index).mediaId == itemId) current.removeMediaItem(index)
        }
        synchronize()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        super.onCleared()
    }
}
