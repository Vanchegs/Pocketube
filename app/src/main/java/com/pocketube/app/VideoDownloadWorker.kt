package com.pocketube.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class VideoDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app get() = applicationContext as PocketubeApp
    private val repository get() = app.downloads
    private val itemId get() = inputData.getString("id").orEmpty()
    private val runId get() = inputData.getString("runId").orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = notification()

    private fun notification(): ForegroundInfo {
        val item = repository.get(itemId)
        val open = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val value = NotificationCompat.Builder(applicationContext, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item?.title ?: "Pocketube")
            .setContentText("Загрузка ${if (item?.mediaType == MediaType.AUDIO) "аудио" else "видео"} · ${item?.progress?.toInt() ?: 0}%")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, item?.progress?.toInt() ?: 0, item?.progress == 0f)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", cancel)
            .build()
        return ForegroundInfo(itemId.hashCode(), value, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val item = repository.get(itemId) ?: return@withContext Result.success()
            if (item.runId != runId || item.status !in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
                return@withContext Result.success()
            setForeground(notification())
            slots.withPermit {
                app.engine.initialize(app)
                if (repository.get(itemId)?.let { it.runId == runId && it.status != DownloadStatus.PAUSED } != true)
                    return@withPermit
                repository.change(itemId, runId) { it.copy(status = DownloadStatus.DOWNLOADING, error = "") }
                val directory = repository.directory(itemId)
                val request = DownloadRequestFactory.create(item, directory, File(applicationContext.cacheDir, "yt-dlp"))
                var lastUpdate = 0L
                runInterruptible {
                    YoutubeDL.getInstance().execute(request, runId) { progress, eta, _ ->
                        if (System.currentTimeMillis() - lastUpdate > 700) {
                            lastUpdate = System.currentTimeMillis()
                            repository.change(itemId, runId) {
                                if (it.status == DownloadStatus.DOWNLOADING)
                                    it.copy(progress = progress.coerceIn(0f, 99f), eta = eta) else it
                            }
                            if (!isStopped) setForegroundAsync(notification())
                        }
                    }
                }
                val file = DownloadRequestFactory.completedFile(item, directory)
                    ?: error("Загрузка завершилась без ${if (item.mediaType == MediaType.AUDIO) "MP3-файла" else "видеофайла"}")
                repository.change(itemId, runId) {
                    if (it.status == DownloadStatus.DOWNLOADING)
                        it.copy(status = DownloadStatus.COMPLETED, progress = 100f, eta = 0,
                            filePath = file.absolutePath, size = file.length()) else it
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            YoutubeDL.getInstance().destroyProcessById(runId)
            repository.change(itemId, runId) {
                if (it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
                    it.copy(status = DownloadStatus.PAUSED, eta = 0) else it
            }
            throw e
        } catch (e: Exception) {
            repository.change(itemId, runId) {
                if (it.status == DownloadStatus.PAUSED) it else it.copy(status = DownloadStatus.ERROR,
                    error = e.message?.takeLast(800) ?: "Не удалось скачать файл")
            }
            Result.failure()
        } finally {
            // A deleted task must not leave a late-created partial file behind.
            if (repository.get(itemId) == null) repository.directory(itemId).deleteRecursively()
        }
    }

    companion object { private val slots = Semaphore(2) }
}
