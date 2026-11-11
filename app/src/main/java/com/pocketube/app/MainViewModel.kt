package com.pocketube.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

data class AddState(
    val link: String = "", val loading: Boolean = false,
    val preview: VideoPreview? = null, val quality: Int = 720,
    val error: String? = null, val updating: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PocketubeApp>()
    private val mutableState = MutableStateFlow(AddState())
    val state = mutableState.asStateFlow()
    val downloads = app.downloads.items
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val messages = mutableMessages.asSharedFlow()
    private val mutableBusy = MutableStateFlow<Set<String>>(emptySet())
    val busy = mutableBusy.asStateFlow()

    fun link(value: String) {
        if (!mutableState.value.loading) mutableState.value = mutableState.value.copy(link = value, preview = null, error = null)
    }
    fun quality(value: Int) { mutableState.value = mutableState.value.copy(quality = value) }

    fun inspect() {
        if (mutableState.value.loading || mutableState.value.updating) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null, preview = null)
            try {
                val url = YouTubeUrl.normalize(mutableState.value.link)
                app.engine.initialize(app)
                val info = runInterruptible(Dispatchers.IO) {
                    YoutubeDL.getInstance().getInfo(YoutubeDLRequest(url).apply {
                        addOption("--no-playlist")
                        addOption("--socket-timeout", 20)
                        addOption("--retries", 2)
                    })
                }
                val qualities = info.formats.orEmpty().map { it.height }.filter { it > 0 }
                    .distinct().sortedDescending().ifEmpty { listOf(1080, 720, 480, 360) }
                val preview = VideoPreview(url, info.title ?: "Видео YouTube", info.uploader ?: "YouTube",
                    info.thumbnail.orEmpty(), info.duration, qualities)
                mutableState.value = mutableState.value.copy(loading = false, preview = preview,
                    quality = qualities.firstOrNull { it <= 720 } ?: qualities.last())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(loading = false,
                    error = if (e is IllegalArgumentException) e.message else
                        "Не удалось получить видео. Проверьте интернет и доступность ссылки. Попробуйте обновить загрузчик кнопкой вверху.\n${e.message.orEmpty().takeLast(350)}")
            }
        }
    }

    fun download(onAdded: () -> Unit) {
        val current = mutableState.value
        val preview = current.preview ?: return
        viewModelScope.launch {
            try {
                val added = withContext(Dispatchers.IO) { app.downloads.add(preview, current.quality) }
                mutableMessages.emit(if (added) "Видео добавлено в загрузки" else "Это видео уже есть в загрузках")
                onAdded()
            } catch (e: Exception) { mutableMessages.emit("Не удалось добавить: ${e.message}") }
        }
    }

    fun control(item: DownloadItem, delete: Boolean = false) {
        if (item.id in mutableBusy.value) return
        mutableBusy.value += item.id
        viewModelScope.launch {
            try {
                if (delete) app.downloads.delete(item.id)
                else if (item.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)) app.downloads.pause(item.id)
                else withContext(Dispatchers.IO) { app.downloads.resume(item.id) }
            } catch (e: Exception) { mutableMessages.emit("Не удалось выполнить действие: ${e.message}") }
            finally { mutableBusy.value -= item.id }
        }
    }

    fun export(item: DownloadItem, destination: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = File(item.filePath)
                    check(file.isFile) { "Файл не найден" }
                    val output = app.contentResolver.openOutputStream(destination)
                        ?: error("Не удалось открыть выбранную папку")
                    output.use { target -> file.inputStream().use { it.copyTo(target) } }
                }
                mutableMessages.emit("Видео сохранено в выбранную папку")
            } catch (e: Exception) { mutableMessages.emit("Не удалось сохранить: ${e.message}") }
        }
    }

    fun updateEngine() {
        if (mutableState.value.updating || mutableState.value.loading) return
        if (downloads.value.any { it.status in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED) }) {
            mutableMessages.tryEmit("Остановите загрузки перед обновлением")
            return
        }
        mutableState.value = mutableState.value.copy(updating = true)
        viewModelScope.launch {
            try {
                app.engine.update(app)
                mutableMessages.emit("Загрузчик обновлён")
            } catch (e: Exception) { mutableMessages.emit("Не удалось обновить: ${e.message.orEmpty().take(150)}") }
            finally { mutableState.value = mutableState.value.copy(updating = false) }
        }
    }
}
