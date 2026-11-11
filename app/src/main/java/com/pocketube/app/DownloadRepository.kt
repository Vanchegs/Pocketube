package com.pocketube.app

import android.content.Context
import android.os.Environment
import android.util.AtomicFile
import androidx.work.*
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DownloadRepository(private val context: Context) {
    private val stateFile = AtomicFile(File(context.filesDir, "downloads.json"))
    private val mutableItems = MutableStateFlow(read())
    val items = mutableItems.asStateFlow()
    private val work get() = WorkManager.getInstance(context)
    private val root = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: File(context.filesDir, "videos"), "Pocketube").apply { mkdirs() }

    fun directory(id: String) = File(root, id).apply { mkdirs() }

    @Synchronized
    private fun save(values: List<DownloadItem>) {
        val json = DownloadJson.encode(values)
        val stream = stateFile.startWrite()
        try {
            stream.write(json.toByteArray(Charsets.UTF_8))
            stateFile.finishWrite(stream)
        } catch (e: Exception) {
            stateFile.failWrite(stream)
            throw e
        }
        mutableItems.value = values
    }

    private fun read(): List<DownloadItem> {
        if (!stateFile.baseFile.exists()) return emptyList()
        // Do not replace an unreadable history with an empty file.
        return DownloadJson.decode(stateFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
    }

    @Synchronized
    fun get(id: String) = mutableItems.value.find { it.id == id }

    @Synchronized
    fun change(id: String, runId: String, transform: (DownloadItem) -> DownloadItem) {
        val current = get(id) ?: return
        if (current.runId != runId) return
        save(mutableItems.value.map { if (it.id == id) transform(it) else it })
    }

    @Synchronized
    fun add(video: VideoPreview, quality: Int, mediaType: MediaType = MediaType.VIDEO): Boolean {
        if (mutableItems.value.any { it.matchesSelection(video.url, quality, mediaType) }) return false
        val item = DownloadItem(UUID.randomUUID().toString(), UUID.randomUUID().toString(), video.url,
            video.title, video.author, video.thumbnail, video.duration,
            if (mediaType == MediaType.AUDIO) 0 else quality, mediaType = mediaType)
        save(listOf(item) + mutableItems.value)
        enqueue(item)
        return true
    }

    private fun enqueue(item: DownloadItem) {
        val request = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setId(UUID.fromString(item.runId))
            .setInputData(workDataOf("id" to item.id, "runId" to item.runId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        work.enqueueUniqueWork(item.id, ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun pause(id: String) = withContext(Dispatchers.IO) {
        val item = get(id) ?: return@withContext
        change(id, item.runId) { it.copy(status = DownloadStatus.PAUSED, eta = 0) }
        YoutubeDL.getInstance().destroyProcessById(item.runId)
        work.cancelWorkById(UUID.fromString(item.runId)).result.get()
    }

    @Synchronized
    fun resume(id: String) {
        val item = get(id) ?: return
        if (item.status !in listOf(DownloadStatus.PAUSED, DownloadStatus.ERROR)) return
        val updated = item.copy(runId = UUID.randomUUID().toString(), status = DownloadStatus.QUEUED, error = "")
        change(id, item.runId) { updated }
        enqueue(updated)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        pause(id)
        synchronized(this@DownloadRepository) {
            save(mutableItems.value.filterNot { it.id == id })
        }
        File(root, id).deleteRecursively()
        Unit
    }
}
